package com.phonghub.config;

import com.phonghub.adapter.in.web.CurrentUserInterceptor;
import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final LocalDemoAuthenticationAdapter authAdapter;
    private final boolean demoEnabled;

    public WebMvcConfig(
        LocalDemoAuthenticationAdapter authAdapter,
        @Value("${phonghub.demo.enabled:true}") boolean demoEnabled
    ) {
        this.authAdapter = authAdapter;
        this.demoEnabled = demoEnabled;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new CurrentUserInterceptor(authAdapter, demoEnabled))
            .addPathPatterns("/**");
    }
}
