package com.zeroindex.intake.domain.template

/**
 * Undo of a meal-template log: soft-deletes exactly the rows that the log wrote, by id, once each, and nothing else
 * (not other entries of the same slot or day, not an earlier log of the same template). Pure so it can be tested
 * without a database; the repository passes its own soft delete.
 */
object TemplateUndo {
    suspend fun softDeleteLogged(loggedIds: List<String>, softDelete: suspend (String) -> Unit) {
        loggedIds.distinct().forEach { softDelete(it) }
    }
}
