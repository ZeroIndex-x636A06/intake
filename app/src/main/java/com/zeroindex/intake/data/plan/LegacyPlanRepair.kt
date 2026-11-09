package com.zeroindex.intake.data.plan

import androidx.room.withTransaction
import com.zeroindex.intake.data.db.IntakeDatabase
import com.zeroindex.intake.data.db.entity.MealPlanEntity

/**
 * Gives plan weeks that arrive without a plan (a schema-2 backup, or a schema-2 peer in sync) the
 * same treatment the 2 -> 3 migration gives the weeks already on the phone: they join the plan
 * "My plan" (id [PlanImportBuilder.LEGACY_PLAN_ID]), which is created if missing, and the old
 * plan-wide row (week 0) is moved onto it and retired (soft). Idempotent; does nothing when there
 * are no such weeks.
 */
object LegacyPlanRepair {
    suspend fun run(db: IntakeDatabase, now: Long, todayEpochDay: Long) {
        val dao = db.planDao()
        val orphans = dao.orphanWeeks()
        if (orphans.isEmpty()) return
        db.withTransaction {
            val id = PlanImportBuilder.LEGACY_PLAN_ID
            val meta = orphans.firstOrNull { it.weekNumber == 0 }?.notes.orEmpty()
            val existing = dao.planIncludingArchived(id)
            if (existing == null) {
                dao.upsertPlan(
                    MealPlanEntity(
                        id = id, name = "My plan", startDate = todayEpochDay,
                        isActive = dao.activePlan() == null, metaJson = meta, createdAt = now, updatedAt = now,
                    ),
                )
            } else if (existing.metaJson.isBlank() && meta.isNotBlank()) {
                dao.upsertPlan(existing.copy(metaJson = meta, updatedAt = now))
            }
            dao.upsertWeeks(
                orphans.map {
                    if (it.weekNumber == 0) it.copy(planId = id, deletedAt = now, updatedAt = now)
                    else it.copy(planId = id, updatedAt = now)
                },
            )
        }
    }
}
