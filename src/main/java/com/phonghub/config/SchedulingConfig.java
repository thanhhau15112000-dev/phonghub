package com.phonghub.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "phonghub.invoicing.auto-generate", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
