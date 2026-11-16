package com.tulsicare.app.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PushRelayClientTest {

    @Test
    fun testPayloadSerialization() {
        val tokens = listOf("token_device_1", "token_device_2")
        val payload = PushRelayClient.buildDosePayload(
            tokens = tokens,
            type = "dose_taken",
            memberName = "Mom",
            medicineName = "Amlodipine",
            dosage = "5mg",
            status = "TAKEN",
            time = "08:00 AM",
            groupId = "grp_1"
        )

        assertTrue(payload.contains("\"type\": \"dose_taken\""))
        assertTrue(payload.contains("\"token_device_1\""))
        assertTrue(payload.contains("\"token_device_2\""))
        assertTrue(payload.contains("\"memberName\": \"Mom\""))
        assertTrue(payload.contains("\"medicineName\": \"Amlodipine\""))
        assertTrue(payload.contains("\"status\": \"TAKEN\""))
    }

    @Test
    fun testJsonEscaping() {
        assertEquals("Hello \\\"World\\\"", PushRelayClient.escapeJson("Hello \"World\""))
        assertEquals("Line 1\\nLine 2", PushRelayClient.escapeJson("Line 1\nLine 2"))
    }
}
