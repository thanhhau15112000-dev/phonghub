package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.DemoActorPort;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
@Profile("!prod")
public class UserSwitchUiController {

    private final DemoActorPort demoActorPort;
    private final boolean demoEnabled;

    public UserSwitchUiController(
        DemoActorPort demoActorPort,
        @Value("${phonghub.demo.enabled:true}") boolean demoEnabled
    ) {
        this.demoActorPort = demoActorPort;
        this.demoEnabled = demoEnabled;
    }

    @GetMapping("/switch-user")
    public String switchUser(@RequestParam UUID userId, HttpServletRequest request) {
        if (!demoEnabled) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Demo user switching is disabled.");
        }
        HttpSession session = request.getSession(true);
        session.setAttribute("currentUserId", userId);
        demoActorPort.switchActor(userId);

        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isBlank() && !referer.contains("/switch-user")) {
            return "redirect:" + referer;
        }
        return "redirect:/";
    }
}
