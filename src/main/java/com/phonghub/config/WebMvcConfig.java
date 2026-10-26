package com.phonghub.config;

import com.phonghub.adapter.in.web.CurrentUserInterceptor;
import com.phonghub.application.port.in.DemoActorPort;
import com.phonghub.application.port.out.CurrentUserPort;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final CurrentUserPort currentUserPort;
    private final Optional<DemoActorPort> demoActorPort;
    private final boolean demoEnabled;

    public WebMvcConfig(
        CurrentUserPort currentUserPort,
        Optional<DemoActorPort> demoActorPort,
        @Value("${phonghub.demo.enabled:true}") boolean demoEnabled
    ) {
        this.currentUserPort = currentUserPort;
        this.demoActorPort = demoActorPort;
        this.demoEnabled = demoEnabled;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (demoEnabled && demoActorPort.isPresent()) {
            registry.addInterceptor(new CurrentUserInterceptor(currentUserPort, demoActorPort.get(), demoEnabled))
                .addPathPatterns("/**");
        }
    }
}
