package com.phonghub.config;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.identity.LocalDemoIdentityProviderAdapter;
import com.phonghub.application.port.in.DemoActorPort;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.IdentityProviderPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Xác thực local phục vụ việc kiểm thử API/UI với dữ liệu PostgreSQL trong Docker.
 * Không được bật trong profile prod.
 */
@Configuration
@Profile("docker & !prod")
public class LocalDatabaseAuthenticationConfig {

    @Bean
    public LocalDemoAuthenticationAdapter localDemoAuthenticationAdapter() {
        return new LocalDemoAuthenticationAdapter();
    }

    @Bean
    public CurrentUserPort currentUserPort() {
        return localDemoAuthenticationAdapter();
    }

    @Bean
    public DemoActorPort demoActorPort() {
        return localDemoAuthenticationAdapter();
    }

    @Bean
    public IdentityProviderPort identityProviderPort() {
        return new LocalDemoIdentityProviderAdapter();
    }
}
