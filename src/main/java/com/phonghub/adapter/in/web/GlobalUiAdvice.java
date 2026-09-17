package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.DemoActorPort;
import com.phonghub.application.port.out.CurrentUser;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalUiAdvice {

    private final boolean demoEnabled;
    private final Optional<DemoActorPort> demoActorPort;

    public GlobalUiAdvice(
        @Value("${phonghub.demo.enabled:true}") boolean demoEnabled,
        Optional<DemoActorPort> demoActorPort
    ) {
        this.demoEnabled = demoEnabled;
        this.demoActorPort = demoActorPort;
    }

    @ModelAttribute("demoEnabled")
    public boolean isDemoEnabled() {
        return demoEnabled && demoActorPort.isPresent();
    }

    @ModelAttribute("demoUsers")
    public Map<UUID, CurrentUser> demoUsers() {
        return (demoEnabled && demoActorPort.isPresent()) ? demoActorPort.get().getAllDemoUsers() : Collections.emptyMap();
    }

    @ModelAttribute("uiText")
    public UiText uiText() {
        return UiText.INSTANCE;
    }
}
