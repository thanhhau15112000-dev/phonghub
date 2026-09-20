package com.phonghub.adapter.in.web;

import com.phonghub.adapter.in.security.DomainAuthenticationToken;
import com.phonghub.application.port.in.AuthTokenResponse;
import com.phonghub.application.port.in.AuthUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.domain.exception.AccountDisabledException;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.exception.IdentityProviderUnavailableException;
import com.phonghub.domain.exception.InvalidCredentialsException;
import com.phonghub.domain.model.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthUiController {

    private final AuthUseCase authUseCase;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthUiController(AuthUseCase authUseCase) {
        this.authUseCase = authUseCase;
    }

    @GetMapping("/login")
    public String loginPage(
        @RequestParam(defaultValue = "false") boolean error,
        @RequestParam(defaultValue = "false") boolean loggedOut,
        @RequestParam(required = false) String username,
        Model model
    ) {
        if (error) {
            model.addAttribute("errorMessage", "Tên đăng nhập hoặc mật khẩu không đúng.");
        }
        if (loggedOut) {
            model.addAttribute("successMessage", "Đã đăng xuất.");
        }
        if (username != null && !username.isBlank()) {
            model.addAttribute("username", username.trim());
        }
        return "auth/login";
    }

    @PostMapping("/login")
    public String login(
        @RequestParam(defaultValue = "") String username,
        @RequestParam(defaultValue = "") String password,
        HttpServletRequest request,
        HttpServletResponse response,
        Model model
    ) {
        try {
            AuthTokenResponse tokenResponse = authUseCase.login(username, password);
            if (tokenResponse == null || tokenResponse.user() == null
                || tokenResponse.accessToken() == null || tokenResponse.accessToken().isBlank()) {
                throw new IllegalStateException("Login response did not contain an authenticated user");
            }

            CurrentUser currentUser = toCurrentUser(tokenResponse.user());
            request.getSession(true);
            request.changeSessionId();

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new DomainAuthenticationToken(
                currentUser,
                tokenResponse.accessToken(),
                List.of(new SimpleGrantedAuthority("ROLE_" + currentUser.role().name())),
                currentUser.mustChangePassword()
            ));
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);

            return currentUser.mustChangePassword()
                ? "redirect:/account/password"
                : "redirect:/";
        } catch (InvalidCredentialsException ex) {
            return loginError(model, username, "Tên đăng nhập hoặc mật khẩu không đúng.");
        } catch (AccountDisabledException ex) {
            return loginError(model, username, "Tài khoản hiện không hoạt động.");
        } catch (IdentityProviderUnavailableException ex) {
            return loginError(model, username, "Dịch vụ xác thực tạm thời không khả dụng. Vui lòng thử lại sau.");
        } catch (IllegalStateException ex) {
            return loginError(model, username, "Không thể hoàn tất đăng nhập. Vui lòng thử lại sau.");
        } catch (DomainException ex) {
            return loginError(model, username, ex.getMessage() != null ? ex.getMessage() : "Thông tin đăng nhập không hợp lệ.");
        } catch (Exception ex) {
            return loginError(model, username, "Đã xảy ra lỗi trong quá trình đăng nhập. Vui lòng thử lại.");
        }
    }

    @GetMapping("/account/password")
    public String passwordPage(Model model) {
        model.addAttribute("currentUser", currentUser());
        return "auth/password";
    }

    @PostMapping("/account/password")
    public String changePassword(
        @RequestParam(defaultValue = "") String newPassword,
        HttpServletRequest request,
        HttpServletResponse response,
        Model model
    ) {
        try {
            authUseCase.changePassword(newPassword);

            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof DomainAuthenticationToken domainAuthentication) {
                CurrentUser oldUser = domainAuthentication.getCurrentUser();
                CurrentUser updatedUser = new CurrentUser(
                    oldUser.id(),
                    oldUser.email(),
                    oldUser.fullName(),
                    oldUser.role(),
                    false
                );
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(new DomainAuthenticationToken(
                    updatedUser,
                    domainAuthentication.getCredentials() instanceof String token ? token : null,
                    domainAuthentication.getAuthorities(),
                    false
                ));
                SecurityContextHolder.setContext(context);
                securityContextRepository.saveContext(context, request, response);
            }

            return "redirect:/";
        } catch (IllegalArgumentException ex) {
            return passwordError(model, ex.getMessage());
        } catch (IdentityProviderUnavailableException ex) {
            return passwordError(model, "Dịch vụ xác thực tạm thời không khả dụng. Vui lòng thử lại sau.");
        } catch (DomainException ex) {
            return passwordError(model, "Không thể đổi mật khẩu. Vui lòng thử lại sau.");
        }
    }

    @PostMapping("/session/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getCredentials() instanceof String accessToken) {
                authUseCase.logout(accessToken);
            }
        } finally {
            SecurityContextHolder.clearContext();
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
        }
        return "redirect:/login?loggedOut=true";
    }

    private CurrentUser toCurrentUser(AuthTokenResponse.UserInfo userInfo) {
        UserRole role = userInfo.role();
        if (role == null) {
            throw new IllegalStateException("Login response did not contain a user role");
        }
        return new CurrentUser(
            userInfo.id(),
            "",
            userInfo.fullName(),
            role,
            userInfo.mustChangePassword()
        );
    }

    private CurrentUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof DomainAuthenticationToken domainAuthentication) {
            return domainAuthentication.getCurrentUser();
        }
        return null;
    }

    private String loginError(Model model, String username, String message) {
        model.addAttribute("errorMessage", message);
        if (username != null && !username.isBlank()) {
            model.addAttribute("username", username.trim());
        }
        return "auth/login";
    }

    private String passwordError(Model model, String message) {
        model.addAttribute("currentUser", currentUser());
        model.addAttribute("errorMessage", message != null ? message : "Mật khẩu mới không hợp lệ.");
        return "auth/password";
    }
}
