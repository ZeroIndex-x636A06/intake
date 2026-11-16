package com.zeroindex.intake.data.ai.vision

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.data.ai.AiGateway
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/** The photo schema's questions, packaged-product name and hidden-calorie basis: shape, defaults and prompt rules. */
class VisionQuestionsSchemaTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val body = """
        {"items":[{"name":"cooked oatmeal","estimatedGrams":200,"gramsLow":150,"gramsHigh":250,"kcal":190,"protein":5,"carbs":33,"fat":4,
          "confidence":"low","containsAllergens":[],"allergenReasoning":""}],
         "hiddenCalorieSources":[{"source":"milk in the porridge","kcal":60,"basis":"guess"}],
         "range":{"kcalLow":150,"kcalHigh":250,"proteinLow":4,"proteinHigh":8,"carbsLow":30,"carbsHigh":40,"fatLow":2,"fatHigh":8},
         "portionAssumption":"a small bowl","overallConfidence":"low","notFood":false,
         "questions":[{"question":"Is this a packet of instant oatmeal?","choices":["Plain","Flavoured","Home-made"]}],
         "packagedProduct":"Quaker instant oatmeal"}
    """.trimIndent()

    @Test
    fun `the questions, the product and the basis decode`() {
        val e = AiGateway.decodeReply(json, VisionEstimate.serializer(), body)!!
        assertThat(e.questions.single().choices).containsExactly("Plain", "Flavoured", "Home-made").inOrder()
        assertThat(e.packagedProduct).isEqualTo("Quaker instant oatmeal")
        assertThat(e.hiddenCalorieSources.single().basis).isEqualTo("guess")
    }

    @Test
    fun `a reply without them still decodes, as before`() {
        val plain = """
            {"items":[{"name":"rice","estimatedGrams":180,"kcal":234,"protein":4,"carbs":50,"fat":1,
              "confidence":"high","containsAllergens":[],"allergenReasoning":""}],
             "hiddenCalorieSources":[{"source":"oil","kcal":40}],
             "range":{"kcalLow":1,"kcalHigh":2,"proteinLow":1,"proteinHigh":2,"carbsLow":1,"carbsHigh":2,"fatLow":1,"fatHigh":2},
             "portionAssumption":"a plate","overallConfidence":"high","notFood":false}
        """.trimIndent()
        val e = AiGateway.decodeReply(json, VisionEstimate.serializer(), plain)!!
        assertThat(e.questions).isEmpty()
        assertThat(e.packagedProduct).isNull()
        assertThat(e.hiddenCalorieSources.single().basis).isEqualTo("seen_in_photo")
    }

    @Test
    fun `the strict schema lists the new properties as required`() {
        val schema = VisionSchema.schema
        val props = schema["properties"]!!.jsonObject
        assertThat(props.keys).containsAtLeast("questions", "packagedProduct")
        assertThat(schema["required"]!!.jsonArray.map { it.jsonPrimitive.content }).containsExactlyElementsIn(props.keys)
        val hidden = props["hiddenCalorieSources"]!!.jsonObject["items"]!!.jsonObject
        assertThat(hidden["required"]!!.jsonArray.map { it.jsonPrimitive.content }).contains("basis")
        val question = props["questions"]!!.jsonObject["items"]!!.jsonObject
        assertThat(question["properties"]!!.jsonObject.keys).containsExactly("question", "choices")
    }

    @Test
    fun `the prompt asks only when unsure, never adds hidden fat by default and keeps allergen warnings`() {
        val prompt = VisionSchema.systemPrompt(listOf("dairy"))
        assertThat(prompt).contains("up to 3 short questions")
        assertThat(prompt).contains("empty list")
        assertThat(prompt).contains("Never add butter, milk or oil by default")
        assertThat(prompt).contains("packagedProduct")
        assertThat(prompt).contains("never let it lower the allergen warnings")
    }
}
