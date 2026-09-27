package com.anry88.purrrfolio.marketing.tiktok

import com.anry88.purrrfolio.config.MarketingProperties
import com.anry88.purrrfolio.config.PurrrfolioProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.server.ResponseStatusException

class TiktokMarketingControllerTest {

    private val service = mock(TiktokMarketingService::class.java)
    private val controller = TiktokMarketingController(
        properties = PurrrfolioProperties(
            marketing = MarketingProperties(adminToken = "test-admin-token"),
        ),
        service = service,
    )

    @Test
    fun `dashboard shows private login form without admin cookie`() {
        val response = controller.page(MockHttpServletRequest())

        assertEquals(200, response.statusCode.value())
        assertTrue(response.body!!.contains("This page is private"))
        assertTrue(response.body!!.contains("Admin token"))
    }

    @Test
    fun `admin actions require login`() {
        assertThrows(ResponseStatusException::class.java) {
            controller.creatorInfo(MockHttpServletRequest())
        }
    }

    @Test
    fun `login sets admin cookie and redirects to dashboard`() {
        val response = MockHttpServletResponse()
        val redirect = controller.login("test-admin-token", response)

        assertEquals("/go/tiktok", redirect.url)
        val cookie = response.getCookie("purrrfolio_tiktok_admin")
        assertEquals("test-admin-token", cookie!!.value)
        assertTrue(cookie.isHttpOnly)
    }
}
