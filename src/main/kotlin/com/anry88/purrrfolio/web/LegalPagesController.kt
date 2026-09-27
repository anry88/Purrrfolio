package com.anry88.purrrfolio.web

import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class LegalPagesController {

    @GetMapping("/privacy", produces = [MediaType.TEXT_HTML_VALUE])
    fun privacy(): String = ClassPathResource("legal/privacy.html")
        .inputStream
        .bufferedReader(Charsets.UTF_8)
        .use { it.readText() }

    @GetMapping("/terms", produces = [MediaType.TEXT_HTML_VALUE])
    fun terms(): String = ClassPathResource("legal/terms.html")
        .inputStream
        .bufferedReader(Charsets.UTF_8)
        .use { it.readText() }
}
