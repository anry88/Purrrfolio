package com.anry88.purrrfolio.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(PurrrfolioProperties::class)
class AppConfiguration
