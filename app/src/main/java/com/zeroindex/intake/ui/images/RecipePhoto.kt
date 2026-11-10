package com.zeroindex.intake.ui.images

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.domain.food.ProductImagePolicy

/**
 * The photo at the top of a recipe, from the page the user imported it from. Draws nothing while it loads, when
 * the recipe has no photo, or when [ProductImagePolicy] refuses the address, so the screen reads the same either way.
 */
@Composable
fun RecipePhoto(imageUrl: String?, modifier: Modifier = Modifier, height: Dp = 200.dp) {
    if (imageUrl == null) return
    val loader = rememberProductImageLoader()
    val px = with(LocalDensity.current) { 900.dp.roundToPx() }
    val image: ImageBitmap? by produceState<ImageBitmap?>(null, imageUrl, px) {
        value = loader.load(imageUrl, px, ProductImagePolicy.Source.RECIPE_PAGE)?.asImageBitmap()
    }
    val shown = image ?: return
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth().height(height),
    ) {
        Image(
            bitmap = shown,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(height),
        )
    }
}
