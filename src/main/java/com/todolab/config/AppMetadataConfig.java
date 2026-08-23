package com.todolab.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AppMetadataProperties.class)
public class AppMetadataConfig {
}
