package com.zeroindex.intake.ui.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Egg
import androidx.compose.material.icons.filled.EmojiFoodBeverage
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.SportsBar
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Icecream
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.domain.drinks.DrinkCategory
import com.zeroindex.intake.data.di.OffHttp
import com.zeroindex.intake.data.recipe.RecipeFetcher
import com.zeroindex.intake.data.remote.Connectivity
import com.zeroindex.intake.data.settings.FoodDataPrefs
import com.zeroindex.intake.domain.food.FoodCategory
import com.zeroindex.intake.domain.food.ProductImagePolicy
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * A tiny product-picture loader on the app's own OkHttp client (no image library has a network fetcher here).
 *
 * - memory: an LRU of decoded bitmaps, a sixteenth of the app's heap at most 6 MB;
 * - disk: raw thumbnails in `cacheDir/product-images`, trimmed to [DISK_BUDGET] oldest first. It is a cache
 *   directory, so the system may clear it and nothing here is in a backup;
 * - decoding goes through `BitmapFactory` with `inSampleSize`, so a 200 px thumbnail is never held bigger than
 *   the tile it fills;
 * - https and Open Food Facts hosts only; nothing at all when Open Food Facts search is off; offline it serves
 *   the cache and never opens a connection ([ProductImagePolicy]).
 */
@Singleton
class ProductImageLoader @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    @OffHttp private val http: OkHttpClient,
    /** For recipe photos: the plain client, so a recipe site never sees an Open Food Facts User-Agent. */
    private val plainHttp: OkHttpClient,
    private val prefs: FoodDataPrefs,
    private val connectivity: Connectivity,
) {
    private val memory = object : LruCache<String, Bitmap>(
        minOf(Runtime.getRuntime().maxMemory() / 16, 12L * 1024 * 1024).toInt(),
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val dir: File by lazy { File(context.cacheDir, "product-images").apply { mkdirs() } }

    suspend fun load(
        url: String?,
        targetPx: Int,
        source: ProductImagePolicy.Source = ProductImagePolicy.Source.OPEN_FOOD_FACTS,
    ): Bitmap? {
        val offEnabled = runCatching { prefs.settings.first().offSearch }.getOrDefault(false)
        val decision = ProductImagePolicy.decide(url, offEnabled, connectivity.isOnline(), source)
        if (url == null || decision == ProductImagePolicy.Decision.SKIP) return null
        // The same address may be wanted at two sizes (a thumbnail and the top of the recipe).
        val memoryKey = "$targetPx|$url"
        memory.get(memoryKey)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val file = File(dir, ProductImagePolicy.cacheName(url))
                var bytes: ByteArray? = file.takeIf { it.isFile }?.readBytes()
                if (bytes == null && decision == ProductImagePolicy.Decision.FETCH_OK) {
                    bytes = fetch(url, source)?.also { runCatching { file.writeBytes(it); trimDisk() } }
                }
                bytes?.let { decode(targetPx, it) }?.also { memory.put(memoryKey, it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Product picture failed (${e.javaClass.simpleName})")
                null
            }
        }
    }

    private fun fetch(url: String, kind: ProductImagePolicy.Source): ByteArray? {
        val cap = ProductImagePolicy.maxBytes(kind)
        val recipe = kind == ProductImagePolicy.Source.RECIPE_PAGE
        val client = if (recipe) plainHttp.newBuilder().followSslRedirects(false).build() else http
        val request = Request.Builder().url(url).get()
            .apply { if (recipe) header("User-Agent", RecipeFetcher.USER_AGENT) }
            .build()
        client.newCall(request).execute().use { r ->
            if (!r.isSuccessful) return null
            val body = r.body
            if (body.contentLength() > cap) return null
            val source = body.source()
            source.request(cap + 1L)
            if (source.buffer.size > cap) return null
            return source.buffer.readByteArray()
        }
    }

    private fun decode(targetPx: Int, bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = ProductImagePolicy.sampleSize(bounds.outWidth, bounds.outHeight, targetPx)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }


    private fun trimDisk() {
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (f in files) {
            if (total <= DISK_BUDGET) break
            total -= f.length()
            f.delete()
        }
    }

    private companion object {
        const val TAG = "ProductImages"
        const val DISK_BUDGET = 20L * 1024 * 1024
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ProductImageEntryPoint {
    fun loader(): ProductImageLoader
}

@Composable
internal fun rememberProductImageLoader(): ProductImageLoader {
    val context = LocalContext.current.applicationContext
    return remember(context) { EntryPointAccessors.fromApplication(context, ProductImageEntryPoint::class.java).loader() }
}

/**
 * The tile in front of a food row: its Open Food Facts thumbnail when it has one and may be shown, otherwise a
 * generic icon for its kind of food. The icon is also what shows while a picture loads or when it is skipped.
 */
@Composable
fun FoodThumb(
    food: FoodEntity,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) = ThumbTile(name = food.name, imageUrl = food.imageUrl, modifier = modifier, size = size, drinkCategory = food.drinkCategory)

@Composable
fun ThumbTile(
    name: String,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    category: String = "",
    /** True for a recipe's photo: it comes from the page the user imported, not from Open Food Facts. */
    fromRecipePage: Boolean = false,
    /** The stored drink category of a drink: its icon is a cup or glass for that category, never a food-word guess ("sugar" is not a cookie). */
    drinkCategory: String? = null,
) {
    val loader = rememberProductImageLoader()
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() }
    val source = if (fromRecipePage) ProductImagePolicy.Source.RECIPE_PAGE else ProductImagePolicy.Source.OPEN_FOOD_FACTS
    val image: ImageBitmap? by produceState<ImageBitmap?>(null, imageUrl, px, source) {
        value = if (imageUrl == null) null else loader.load(imageUrl, px, source)?.asImageBitmap()
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            val shown = image
            if (shown != null) {
                Image(
                    bitmap = shown,
                    contentDescription = null,
                    // A recipe photo fills its tile; a product shot keeps its whole pack in view.
                    contentScale = if (fromRecipePage) ContentScale.Crop else ContentScale.Fit,
                    modifier = Modifier.size(size),
                )
            } else {
                Icon(
                    imageVector = DrinkCategory.fromStored(drinkCategory)?.let(::drinkIcon) ?: categoryIcon(FoodCategory.of(name, category)),
                    contentDescription = null,
                    modifier = Modifier.size(size * 0.52f),
                )
            }
        }
    }
}

/** The icon of a saved drink, by its category: a tea cup for tea, a coffee cup for coffee, and so on. */
internal fun drinkIcon(category: DrinkCategory): ImageVector = when (category) {
    DrinkCategory.WATER -> Icons.Filled.WaterDrop
    DrinkCategory.COFFEE -> Icons.Filled.LocalCafe
    DrinkCategory.TEA -> Icons.Filled.EmojiFoodBeverage
    DrinkCategory.ALCOHOL -> Icons.Filled.LocalBar
    DrinkCategory.SPORTS_ENERGY -> Icons.Filled.SportsBar
    DrinkCategory.SODA, DrinkCategory.JUICE, DrinkCategory.MILK, DrinkCategory.OTHER -> Icons.Filled.LocalDrink
}

internal fun categoryIcon(category: FoodCategory): ImageVector = when (category) {
    FoodCategory.DAIRY -> Icons.Filled.Icecream
    FoodCategory.EGG -> Icons.Filled.Egg
    FoodCategory.MEAT -> Icons.Filled.LunchDining
    FoodCategory.FISH -> Icons.Filled.SetMeal
    FoodCategory.FRUIT -> Icons.Filled.Eco
    FoodCategory.VEGETABLE -> Icons.Filled.Grass
    FoodCategory.GRAIN -> Icons.Filled.BakeryDining
    FoodCategory.SWEET -> Icons.Filled.Cookie
    FoodCategory.DRINK -> Icons.Filled.LocalDrink
    FoodCategory.NUT -> Icons.Filled.Spa
    FoodCategory.OTHER -> Icons.Filled.Restaurant
}
