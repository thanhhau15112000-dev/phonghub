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
 * Xác thực giả lập chỉ phục vụ demo local với dữ liệu PostgreSQL trong Docker.
 * Chỉ bật bằng profile local-demo; không được dùng trên môi trường nhiều người dùng.
 */
@Configuration
@Profile("docker & local-demo")
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
