package com.zeroindex.intake.data.seed

import com.zeroindex.intake.data.db.entity.ExerciseEntity
import com.zeroindex.intake.data.db.entity.ProgressionRuleEntity
import com.zeroindex.intake.data.repository.TrainingRepository
import com.zeroindex.intake.domain.model.ProgressionType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seeds the exercise library and the three default progression rules. It no longer creates a
 * program: a new install starts with none, and the built-in programs are [ProgramTemplates] that a
 * person copies. Everything here is idempotent and only ever adds rows that are missing, so it is
 * safe on an existing install (hidden exercises stay hidden, edited exercises stay edited).
 */
@Singleton
class ProgramSeed @Inject constructor(
    private val repository: TrainingRepository,
) {
    /** Adds any catalog exercise the database does not have yet, and returns the visible library. */
    suspend fun ensureExerciseLibrary(): List<ExerciseEntity> {
        repository.ensureSeedExercises()
        return repository.exercises()
    }

    suspend fun ensureRules(): Map<ProgressionType, ProgressionRuleEntity> = repository.ensureDefaultRules()
}
