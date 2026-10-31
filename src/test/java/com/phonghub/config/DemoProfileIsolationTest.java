package com.phonghub.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.adapter.in.web.UserSwitchUiController;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Profiles;
import org.springframework.core.env.StandardEnvironment;

class DemoProfileIsolationTest {

    @Test
    void dockerUsesAuthenticatedSecurityWithoutDemoActorSwitching() {
        assertFalse(isActive(DemoSecurityConfig.class, "docker"));
        assertFalse(isActive(UserSwitchUiController.class, "docker"));
        assertFalse(isActive(LocalDatabaseAuthenticationConfig.class, "docker"));
        assertTrue(isActive(ProductionSecurityConfig.class, "docker"));
        assertTrue(isActive(ProductionConfigValidator.class, "docker"));
    }

    @Test
    void localDemoMustBeExplicitlyEnabledAlongsideDocker() {
        assertTrue(isActive(DemoSecurityConfig.class, "docker", "local-demo"));
        assertTrue(isActive(UserSwitchUiController.class, "docker", "local-demo"));
        assertTrue(isActive(LocalDatabaseAuthenticationConfig.class, "docker", "local-demo"));
        assertFalse(isActive(ProductionSecurityConfig.class, "docker", "local-demo"));
        assertFalse(isActive(ProductionConfigValidator.class, "docker", "local-demo"));
    }

    @Test
    void testProfileKeepsDemoActorSupportForAutomatedTests() {
        assertTrue(isActive(DemoSecurityConfig.class, "test"));
        assertTrue(isActive(UserSwitchUiController.class, "test"));
        assertFalse(isActive(ProductionSecurityConfig.class, "test"));
        assertFalse(isActive(ProductionConfigValidator.class, "test"));
    }

    private boolean isActive(Class<?> configClass, String... activeProfiles) {
        Profile profile = configClass.getAnnotation(Profile.class);
        if (profile == null) {
            throw new AssertionError(configClass.getName() + " must declare an explicit Spring profile");
        }

        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles(activeProfiles);
        return environment.acceptsProfiles(Profiles.of(profile.value()));
    }
}
