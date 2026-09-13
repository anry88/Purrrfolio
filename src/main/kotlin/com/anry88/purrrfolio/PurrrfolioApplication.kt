package com.anry88.purrrfolio

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@SpringBootApplication
class PurrrfolioApplication

fun main(args: Array<String>) {
    runApplication<PurrrfolioApplication>(*args)
}
