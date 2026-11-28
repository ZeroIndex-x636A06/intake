package com.zeroindex.intake.ui.foodtab

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.R
import com.zeroindex.intake.ui.components.IconTile
import com.zeroindex.intake.ui.components.ListCard
import com.zeroindex.intake.ui.components.ListDivider
import com.zeroindex.intake.ui.theme.IconSize
import com.zeroindex.intake.ui.theme.IntakeIcons
import com.zeroindex.intake.ui.theme.IntakeSpacing

/** Where the Library's rows lead. Each is an existing screen; nothing here is new data. */
class LibraryActions(
    val onOpenTemplates: () -> Unit,
    val onOpenShopping: () -> Unit,
    val onOpenEatingOut: () -> Unit,
    val onOpenMenus: () -> Unit,
    val onOpenMyFoods: () -> Unit,
    val onOpenDrinks: () -> Unit,
)

/** The rows of the hub, in order. Kept as data so a test can check the destinations stay reachable. */
enum class LibraryEntry(@StringRes val titleRes: Int, @StringRes val subRes: Int) {
    TEMPLATES(R.string.library_templates_title, R.string.library_templates_sub),
    SHOPPING(R.string.library_shopping_title, R.string.library_shopping_sub),
    EATING_OUT(R.string.library_eating_out_title, R.string.library_eating_out_sub),
    MENUS(R.string.library_menus_title, R.string.library_menus_sub),
    MY_FOODS(R.string.library_my_foods_title, R.string.library_my_foods_sub),
    DRINKS(R.string.library_drinks_title, R.string.library_drinks_sub),
}

private fun LibraryEntry.icon(): ImageVector = when (this) {
    LibraryEntry.TEMPLATES -> IntakeIcons.Clipboard
    LibraryEntry.SHOPPING -> IntakeIcons.Cart
    LibraryEntry.EATING_OUT -> IntakeIcons.MapPin
    LibraryEntry.MENUS -> IntakeIcons.FileText
    LibraryEntry.MY_FOODS -> IntakeIcons.Pencil
    LibraryEntry.DRINKS -> IntakeIcons.CupFilled
}

private fun LibraryEntry.open(actions: LibraryActions) = when (this) {
    LibraryEntry.TEMPLATES -> actions.onOpenTemplates()
    LibraryEntry.SHOPPING -> actions.onOpenShopping()
    LibraryEntry.EATING_OUT -> actions.onOpenEatingOut()
    LibraryEntry.MENUS -> actions.onOpenMenus()
    LibraryEntry.MY_FOODS -> actions.onOpenMyFoods()
    LibraryEntry.DRINKS -> actions.onOpenDrinks()
}

/**
 * Food > Library: the hub that replaces the old "More" list (owner decision 2). One list of the places food lives that are not
 * the diary, the plan or the recipes. Phase 2 (group B2) may enrich the rows with counts; the destinations are the contract.
 */
@Composable
fun LibraryScreen(
    contentPadding: PaddingValues,
    actions: LibraryActions,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
            start = IntakeSpacing.ScreenGutter,
            end = IntakeSpacing.ScreenGutter,
        ),
        verticalArrangement = Arrangement.spacedBy(IntakeSpacing.StackGap),
    ) {
        item(key = "library") {
            ListCard {
                Column {
                    LibraryEntry.entries.forEachIndexed { index, entry ->
                        if (index > 0) ListDivider()
                        LibraryRow(entry, onClick = { entry.open(actions) })
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(entry: LibraryEntry, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = IntakeSpacing.ListRowMinHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = IntakeSpacing.ListRowPadH, vertical = IntakeSpacing.ListRowPadV),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(entry.icon())
        Column(Modifier.weight(1f)) {
            Text(stringResource(entry.titleRes), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(entry.subRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            IntakeIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(IconSize.Small),
        )
    }
}
