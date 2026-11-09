package com.zeroindex.intake.data.coach

import com.zeroindex.intake.data.ai.AiError
import com.zeroindex.intake.data.ai.AiGateway
import com.zeroindex.intake.data.ai.AiPart
import com.zeroindex.intake.data.ai.S
import com.zeroindex.intake.data.db.dao.CoachDao
import com.zeroindex.intake.data.repository.AllergenRepository
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.ai.AiFeature
import com.zeroindex.intake.domain.ai.AiTrigger
import com.zeroindex.intake.domain.coach.CoachReword
import com.zeroindex.intake.domain.coach.SuggestionStatus
import com.zeroindex.intake.domain.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

@Serializable
data class RewordItem(val id: String, val title: String, val why: String)

@Serializable
data class RewordReply(val items: List<RewordItem>)

/**
 * The optional AI wording of the weekly check-in (SPEC-v3 "AI policy": the optional coach narrative).
 *
 * Off by default and only on when the person turned it on AND an OpenRouter key exists. Only the
 * suggestion titles and one-line whys go out, through [AiGateway] with the user's privacy setting.
 * The reply is accepted per item only if it keeps exactly the same numbers and does not name anything
 * the allergen engine blocks; otherwise that item keeps its original words. Apply never reads the
 * reworded text: it runs the rule's own action. Reuses the daily-insight model choice, so the usage
 * meter counts it with that feature.
 */
@Singleton
class CoachReworder @Inject constructor(
    private val gateway: AiGateway,
    private val dao: CoachDao,
    private val settings: SettingsRepository,
    private val allergens: AllergenRepository,
    private val clock: Clock,
) {
    suspend fun isEnabled(): Boolean = settings.settings.first().coachAiReword && gateway.isConfigured()

    /** Rewords this check-in's pending suggestions. Returns how many were reworded; any failure leaves the originals. */
    suspend fun rewordCheckIn(day: Long, trigger: AiTrigger): Int {
        if (!isEnabled()) return 0
        val rows = dao.forCheckIn(day).filter { it.status == SuggestionStatus.PENDING && it.aiTitle == null }
        if (rows.isEmpty()) return 0
        val reply = try {
            gateway.structured(
                feature = AiFeature.COACH_REWORD,
                trigger = trigger,
                serializer = RewordReply.serializer(),
                schema = schema,
                system = SYSTEM,
                parts = listOf(AiPart.Text(rows.joinToString("\n") { "${it.id} | ${it.title} | ${it.why}" })),
            ).value
        } catch (_: AiError) {
            return 0
        }
        val avoided = allergens.avoidedAllergens()
        val now = clock.nowMillis()
        var changed = 0
        reply.items.forEach { item ->
            val row = rows.firstOrNull { it.id == item.id } ?: return@forEach
            val title = CoachReword.accept(row.title, item.title, maxLength = 90)
            val why = CoachReword.accept(row.why, item.why)
            val blocked = avoided.isNotEmpty() && allergens.scan(name = null, ingredients = "$title $why").blocks
            if (blocked || (title == row.title && why == row.why)) return@forEach
            dao.upsert(row.copy(aiTitle = title, aiWhy = why, updatedAt = now))
            changed++
        }
        return changed
    }

    private companion object {
        const val SYSTEM = "You reword short fitness coaching notes in plain, warm, simple English. " +
            "Keep every number exactly as written and do not add any number, food, exercise or instruction. " +
            "Do not give medical advice. Keep each title under 60 characters and each reason to one sentence. " +
            "Return each item with the same id. Reply with JSON only."

        val schema = S.obj(
            "items" to S.array(
                S.obj("id" to S.string(), "title" to S.string(), "why" to S.string()),
            ),
        )
    }
}
