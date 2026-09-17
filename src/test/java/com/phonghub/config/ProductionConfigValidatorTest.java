package com.phonghub.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionConfigValidatorTest {

    @Test
    void failsFastWhenRequiredProductionPropertiesAreMissing() {
        MockEnvironment env = new MockEnvironment();
        // env does not contain spring.datasource.url, username, supabase url, anon-key

        ProductionConfigValidator validator = new ProductionConfigValidator(env);

        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            validator::validateProductionConfig
        );
        assertTrue(ex.getMessage().contains("Production configuration failure"));
        assertTrue(ex.getMessage().contains("spring.datasource.url"));
    }

    @Test
    void failsFastWhenPropertyContainsUnresolvedPlaceholder() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.datasource.url", "${SPRING_DATASOURCE_URL}");
        env.setProperty("spring.datasource.username", "postgres");
        env.setProperty("supabase.url", "https://example.supabase.co");
        env.setProperty("supabase.anon-key", "secret-anon-key");

        ProductionConfigValidator validator = new ProductionConfigValidator(env);

        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            validator::validateProductionConfig
        );
        assertTrue(ex.getMessage().contains("unresolvable"));
    }

    @Test
    void passesWhenAllRequiredPropertiesAreProvided() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/phonghub");
        env.setProperty("spring.datasource.username", "postgres");
        env.setProperty("spring.datasource.password", "secret-db-pass");
        env.setProperty("supabase.url", "https://example.supabase.co");
        env.setProperty("supabase.anon-key", "secret-anon-key");
        env.setProperty("supabase.service-role-key", "secret-service-role-key");
        env.setProperty("supabase.jwks-uri", "https://example.supabase.co/auth/v1/.well-known/jwks.json");
        env.setProperty("supabase.jwt-issuer", "https://example.supabase.co/auth/v1");
        env.setProperty("supabase.jwt-audience", "authenticated");

        ProductionConfigValidator validator = new ProductionConfigValidator(env);

        assertDoesNotThrow(validator::validateProductionConfig);
    }
}
