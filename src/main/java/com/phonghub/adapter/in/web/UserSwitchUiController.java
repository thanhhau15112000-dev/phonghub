package com.phonghub.adapter.in.web;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class UserSwitchUiController {

    private final LocalDemoAuthenticationAdapter authAdapter;
    private final boolean demoEnabled;

    public UserSwitchUiController(
        LocalDemoAuthenticationAdapter authAdapter,
        @Value("${phonghub.demo.enabled:true}") boolean demoEnabled
    ) {
        this.authAdapter = authAdapter;
        this.demoEnabled = demoEnabled;
    }

    @GetMapping("/switch-user")
    public String switchUser(@RequestParam UUID userId, HttpServletRequest request) {
        if (!demoEnabled) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Demo user switching is disabled.");
        }
        HttpSession session = request.getSession(true);
        session.setAttribute("currentUserId", userId);
        authAdapter.switchActor(userId);

        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isBlank() && !referer.contains("/switch-user")) {
            return "redirect:" + referer;
        }
        return "redirect:/";
    }
}
