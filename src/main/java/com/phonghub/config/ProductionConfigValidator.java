package com.phonghub.config;

import jakarta.annotation.PostConstruct;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionConfigValidator {

    private final Environment environment;

    public ProductionConfigValidator(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validateProductionConfig() {
        List<String> requiredProperties = List.of(
            "spring.datasource.url",
            "spring.datasource.username",
            "supabase.url",
            "supabase.anon-key"
        );

        for (String prop : requiredProperties) {
            String val;
            try {
                val = environment.getProperty(prop);
            } catch (Exception ex) {
                throw new IllegalStateException(
                    "Production configuration failure: required property '" + prop +
                    "' is missing, empty, or unresolvable. Production startup aborted.",
                    ex
                );
            }
            if (val == null || val.trim().isEmpty() || val.startsWith("${")) {
                throw new IllegalStateException(
                    "Production configuration failure: required property '" + prop +
                    "' is missing, empty, or unresolvable. Production startup aborted."
                );
            }
        }
    }
}
