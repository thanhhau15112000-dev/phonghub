package com.phonghub.config;

import com.phonghub.adapter.in.web.AiRateLimitInterceptor;
import com.phonghub.adapter.in.web.CurrentUserInterceptor;
import com.phonghub.adapter.in.web.FixedWindowRateLimiter;
import com.phonghub.application.port.in.DemoActorPort;
import com.phonghub.application.port.out.CurrentUserPort;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final CurrentUserPort currentUserPort;
    private final Optional<DemoActorPort> demoActorPort;
    private final boolean demoEnabled;
    private final RateLimitProperties rateLimitProperties;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    public WebMvcConfig(
        CurrentUserPort currentUserPort,
        Optional<DemoActorPort> demoActorPort,
        @Value("${phonghub.demo.enabled:true}") boolean demoEnabled,
        RateLimitProperties rateLimitProperties,
        ObjectProvider<MeterRegistry> meterRegistryProvider
    ) {
        this.currentUserPort = currentUserPort;
        this.demoActorPort = demoActorPort;
        this.demoEnabled = demoEnabled;
        this.rateLimitProperties = rateLimitProperties;
        this.meterRegistryProvider = meterRegistryProvider;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (demoEnabled && demoActorPort.isPresent()) {
            registry.addInterceptor(new CurrentUserInterceptor(currentUserPort, demoActorPort.get(), demoEnabled))
                .addPathPatterns("/**");
        }
        // Đăng ký sau CurrentUserInterceptor để profile demo đã gắn người dùng trước khi đếm (#311)
        registry.addInterceptor(new AiRateLimitInterceptor(rateLimitProperties,
                new FixedWindowRateLimiter(Clock.systemUTC()), currentUserPort, meterRegistryProvider.getIfAvailable()))
            .addPathPatterns("/api/v1/ai/**");
    }
}
