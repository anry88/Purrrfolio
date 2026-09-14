package com.anry88.purrrfolio.telegram

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.http.client.MultipartBodyBuilder

class TelegramMultipartTest {
    @Test
    fun `multipart body builder accepts resource parts`() {
        val builder = MultipartBodyBuilder()
        builder.part("photo", ClassPathResource("static/assets/cards/sleepy.png"))

        val body = builder.build()
        assertThat(body).containsKey("photo")
        assertThat(body["photo"]).isNotEmpty
    }
}