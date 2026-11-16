package com.tulsicare.app.core.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class BrandThemeTest {

    @Test
    fun brandColors_matchSpecification() {
        // Tulsi Green Accent
        assertEquals(Color(0xFF1F7A5C), TulsiGreenLight)
        assertEquals(Color(0xFF3DBE8B), TulsiGreenDark)

        // Light Theme Tokens
        assertEquals(Color(0xFFFFFFFF), LightBackground)
        assertEquals(Color(0xFFF6F6F6), LightSurface)
        assertEquals(Color(0xFFE4E4E4), LightBorder)
        assertEquals(Color(0xFF0A0A0A), LightTextPrimary)
        assertEquals(Color(0xFF6B6B6B), LightTextSecondary)

        // Dark Theme Tokens
        assertEquals(Color(0xFF000000), DarkBackground)
        assertEquals(Color(0xFF111111), DarkSurface)
        assertEquals(Color(0xFF262626), DarkBorder)
        assertEquals(Color(0xFFFAFAFA), DarkTextPrimary)
        assertEquals(Color(0xFF9A9A9A), DarkTextSecondary)

        // Semantic Status Tokens
        assertEquals(Color(0xFFD64545), StatusMissed)
        assertEquals(Color(0xFFC98A1B), StatusSnoozed)
    }
}
