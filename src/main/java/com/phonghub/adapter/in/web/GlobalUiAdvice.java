package com.phonghub.adapter.in.web;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.application.port.out.CurrentUser;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalUiAdvice {

    private final boolean demoEnabled;
    private final LocalDemoAuthenticationAdapter authAdapter;

    public GlobalUiAdvice(
        @Value("${phonghub.demo.enabled:true}") boolean demoEnabled,
        LocalDemoAuthenticationAdapter authAdapter
    ) {
        this.demoEnabled = demoEnabled;
        this.authAdapter = authAdapter;
    }

    @ModelAttribute("demoEnabled")
    public boolean isDemoEnabled() {
        return demoEnabled;
    }

    @ModelAttribute("demoUsers")
    public Map<UUID, CurrentUser> demoUsers() {
        return demoEnabled ? authAdapter.getAllDemoUsers() : Collections.emptyMap();
    }
}
