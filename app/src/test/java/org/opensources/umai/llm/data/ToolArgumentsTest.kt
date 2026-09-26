package org.opensources.umai.llm.data

import com.google.gson.internal.LazilyParsedNumber
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class ToolArgumentsTest {

    @Test
    fun `whole numbers come back as the integers the schema asked for, whatever their type`() {
        val arguments = mapOf(
            "servings" to LazilyParsedNumber("8.0"),
            "start" to BigDecimal("90.0"),
            "cookMinutes" to 3.0f,
            "steps" to listOf(mapOf("start" to LazilyParsedNumber("0.0"))),
        )

        assertEquals(
            """{"servings":8,"start":90,"cookMinutes":3,"steps":[{"start":0}]}""",
            ToolArguments.toJson(arguments).toString(),
        )
    }

    @Test
    fun `other values are kept as they are`() {
        val arguments = mapOf("half" to LazilyParsedNumber("12.5"), "done" to true, "name" to "Pains", "none" to null)

        assertEquals("""{"half":12.5,"done":true,"name":"Pains","none":null}""", ToolArguments.toJson(arguments).toString())
    }
}
