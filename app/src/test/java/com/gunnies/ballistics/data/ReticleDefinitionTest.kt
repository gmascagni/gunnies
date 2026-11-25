package com.gunnies.ballistics.data

import com.gunnies.ballistics.model.AngularUnit
import com.gunnies.ballistics.model.ReticleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReticleDefinitionTest {

    @Test
    fun allBuiltInReticles_haveValidDataDrivenDefinitionsAndRoundTripJson() {
        for (rt in ReticleType.entries) {
            val def = ReticleDefinitionCodec.forReticleType(rt)
            assertTrue("Built-in ${rt.name} must pass validation", ReticleDefinitionCodec.validate(def).isSuccess)
            assertEquals("Unit match for ${rt.name}", rt.isMradBased, def.isMradBased)

            val encoded = ReticleDefinitionCodec.encodeJson(def)
            val decoded = ReticleDefinitionCodec.decodeJson(encoded).getOrThrow()
            assertEquals(def, decoded)
        }
    }

    @Test
    fun decodeJson_rejectsInvalidSchemaZoomAndTicks() {
        val badVersion = """{"schemaVersion":2,"id":"x","name":"X","focalPlane":"FFP","unit":"MRAD"}"""
        assertTrue(ReticleDefinitionCodec.decodeJson(badVersion).isFailure)

        val badZoom = """{"schemaVersion":1,"id":"x","name":"X","focalPlane":"FFP","unit":"MRAD","defaultZoom":30,"minZoom":4,"maxZoom":20}"""
        assertTrue(ReticleDefinitionCodec.decodeJson(badZoom).isFailure)

        val badUnit = """{"schemaVersion":1,"id":"x","name":"X","focalPlane":"FFP","unit":"INCHES"}"""
        assertTrue(ReticleDefinitionCodec.decodeJson(badUnit).isFailure)

        val validCustom = """
            {
              "schemaVersion": 1,
              "id": "custom-mil-05",
              "name": "Custom 0.5 MIL FFP",
              "focalPlane": "FFP",
              "unit": "MRAD",
              "stadiaStyle": "CHRISTMAS_TREE",
              "defaultZoom": 10.0,
              "minZoom": 3.0,
              "maxZoom": 18.0,
              "maxSubtension": 10.0,
              "majorTickStep": 1.0,
              "minorTickStep": 0.5
            }
        """.trimIndent()
        val parsed = ReticleDefinitionCodec.decodeJson(validCustom).getOrThrow()
        assertEquals("custom-mil-05", parsed.id)
        assertEquals(AngularUnit.MRAD, parsed.unit)
    }
}
