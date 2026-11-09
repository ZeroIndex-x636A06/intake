package com.zeroindex.intake.domain.training

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.training.generator.ExerciseCatalog
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Test

/** The bundled photos: every one the index points at exists, and the map covers the catalog. */
class ExerciseImagesAssetTest {
    private val dir = listOf(File("src/main/assets/exercises"), File("app/src/main/assets/exercises")).first { it.isDirectory }
    private val index = ExerciseGuideIndex.parse(File(dir, "exercises.json").readText())

    @Test
    fun `every referenced image exists and is a webp`() {
        assertThat(index.ids).isNotEmpty()
        for (id in index.ids) for (i in 0..1) {
            val f = File(dir, "${id}_$i.webp")
            assertThat(f.isFile).isTrue()
            val head = f.inputStream().use { it.readNBytes(12) }
            assertThat(String(head, 0, 4)).isEqualTo("RIFF")
            assertThat(String(head, 8, 4)).isEqualTo("WEBP")
        }
    }

    @Test
    fun `every indexed id is a catalog exercise with the same name and has steps`() {
        for (id in index.ids) {
            val ex = ExerciseCatalog.byId(id)
            assertThat(ex).isNotNull()
            assertThat(index.guide(id)!!.name).isEqualTo(ex!!.name)
            assertThat(index.guide(id)!!.steps).isNotEmpty()
            assertThat(index.guide(id)!!.steps.size).isAtMost(5)
        }
    }

    @Test
    fun `no stray files and the total stays under the size budget`() {
        val names = dir.listFiles()!!.map { it.name }.toSet()
        val expected = index.ids.flatMap { listOf("${it}_0.webp", "${it}_1.webp") }.toSet() + "exercises.json"
        assertThat(names).isEqualTo(expected)
        assertThat(dir.listFiles()!!.sumOf { it.length() }).isLessThan(6L * 1024 * 1024)
    }

    @Test
    fun `the hand-made map covers every catalog exercise exactly once`() {
        val map = listOf(File("../tools/exercise_image_map.json"), File("tools/exercise_image_map.json")).first { it.isFile }
        val root = Json.parseToJsonElement(map.readText()).jsonObject
        val mapped = root["map"]!!.jsonObject.keys
        val unmatched = root["_unmatched"]!!.jsonObject.keys
        val names = ExerciseCatalog.ALL.map { it.name }.toSet()
        assertThat(mapped + unmatched).containsExactlyElementsIn(names)
        assertThat(mapped.intersect(unmatched)).isEmpty()
        assertThat(index.ids).containsExactlyElementsIn(mapped.map { ExerciseCatalog.requireByName(it).id })
    }

    @Test
    fun `the credit names the source and its licence`() {
        assertThat(ExerciseGuideIndex.CREDIT).contains("Free Exercise DB")
        assertThat(ExerciseGuideIndex.CREDIT).contains("Unlicense")
    }
}
