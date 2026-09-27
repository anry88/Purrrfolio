package com.anry88.purrrfolio.web

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LegalPagesControllerTest {

    private val controller = LegalPagesController()

    @Test
    fun `privacy page includes required public policy details`() {
        val html = controller.privacy()

        assertTrue(html.contains("Purrrfolio Privacy Policy"))
        assertTrue(html.contains("connected marketing platforms"))
        assertTrue(html.contains("https://t.me/PurrrfolioBot"))
    }

    @Test
    fun `terms page includes service and marketing terms`() {
        val html = controller.terms()

        assertTrue(html.contains("Purrrfolio Terms of Service"))
        assertTrue(html.contains("Telegram Stars"))
        assertTrue(html.contains("Official marketing and social platforms"))
    }
}
