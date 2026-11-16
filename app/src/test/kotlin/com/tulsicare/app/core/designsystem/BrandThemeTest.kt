package com.tulsicare.app.core.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class BrandThemeTest {

    @Test
    fun brandColors_matchSpecification() {
        assertEquals(Color(0xFF0F9D8A), PrimaryTeal)
        assertEquals(Color(0xFF12304A), DeepNavy)
        assertEquals(Color(0xFFF6FAF9), SoftBackground)
        assertEquals(Color(0xFF2E9E5B), StatusTakenGreen)
        assertEquals(Color(0xFFF2A93B), StatusSnoozeAmber)
        assertEquals(Color(0xFFF26B5B), StatusMissedCoral)
        assertEquals(Color(0xFF8A97A3), StatusSkippedGrey)
    }
}
