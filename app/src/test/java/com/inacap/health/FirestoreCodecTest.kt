package com.inacap.health

import com.inacap.health.data.remote.FirestoreCodec
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreCodecTest {

    @Test
    fun convierteTiposAlFormatoRest() {
        val datos = JSONObject()
            .put("tipo", "agua_registrada")
            .put("ml", 250)
            .put("ok", true)
            .put("x", 1.5)
            .put("ts", 0L)
            .put("props", JSONObject().put("pantalla", "inicio"))
            .put("nada", JSONObject.NULL)

        val campos = FirestoreCodec.aCampos(datos)

        assertEquals("agua_registrada", campos.getJSONObject("tipo").getString("stringValue"))
        assertEquals("250", campos.getJSONObject("ml").getString("integerValue"))
        assertEquals(true, campos.getJSONObject("ok").getBoolean("booleanValue"))
        assertEquals(1.5, campos.getJSONObject("x").getDouble("doubleValue"), 0.0)
        assertEquals("1970-01-01T00:00:00.000Z", campos.getJSONObject("ts").getString("timestampValue"))
        assertEquals(
            "inicio",
            campos.getJSONObject("props").getJSONObject("mapValue").getJSONObject("fields")
                .getJSONObject("pantalla").getString("stringValue")
        )
        assertTrue(campos.getJSONObject("nada").has("nullValue"))
    }
}
