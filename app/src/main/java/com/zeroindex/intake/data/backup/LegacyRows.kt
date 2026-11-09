package com.zeroindex.intake.data.backup

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Brings a row written by an older schema up to the current one before it is decoded.
 *
 * Columns added later all have entity defaults, so a missing key already decodes to the default
 * (for example `food.offContributedAt` -> null, `allergen_alias.level` -> CONTAINS). The one case a
 * plain default gets wrong is `allergen_alias.isCustom`, which MIGRATION_2_3 derives from the id: a
 * v2 keyword whose id is not `seed-` was typed by the user, so a v2 backup (or a v2 peer in sync) must
 * produce the same result as the migration, not the default `false`.
 */
internal object LegacyRows {
    fun upgrade(table: String, row: JsonElement): JsonElement {
        if (table != "allergen_alias" || row !is JsonObject || "isCustom" in row) return row
        val id = (row["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return row
        return if (id.startsWith("seed-")) row else JsonObject(row + ("isCustom" to JsonPrimitive(true)))
    }
}
