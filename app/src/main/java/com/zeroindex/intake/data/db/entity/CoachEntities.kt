package com.zeroindex.intake.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zeroindex.intake.domain.coach.SuggestionKind
import com.zeroindex.intake.domain.coach.SuggestionStatus
import kotlinx.serialization.Serializable

/**
 * One coach suggestion and what the person did with it (SPEC-v3 "Coach engine"). The history is kept
 * so a dismissed suggestion is not repeated this week and so the screen can list past check-ins.
 * Nothing is deleted: [deletedAt] is the only way a row goes away, like every other table.
 *
 * [actionJson] is the exact action Apply runs, so what the card showed is what happens. [aiTitle] and
 * [aiWhy] hold the optional reworded text; they never carry numbers or actions of their own.
 */
@Serializable
@Entity(
    tableName = "coach_suggestion",
    indices = [Index("checkInDay"), Index("status"), Index("deletedAt")],
)
data class CoachSuggestionEntity(
    @PrimaryKey override val id: String = newId(),
    /** Epoch day (local) of the check-in that made it. */
    val checkInDay: Long,
    val suggestionKey: String,
    val kind: SuggestionKind,
    val title: String,
    val why: String,
    val details: String? = null,
    val actionJson: String,
    val priority: Int,
    val status: SuggestionStatus = SuggestionStatus.PENDING,
    val decidedAt: Long? = null,
    val aiTitle: String? = null,
    val aiWhy: String? = null,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited
