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
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.domain.model.User;
import java.util.List;
import java.util.Optional;
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
    private final UserRepositoryPort userRepository;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthUiController(AuthUseCase authUseCase, UserRepositoryPort userRepository) {
        this.authUseCase = authUseCase;
        this.userRepository = userRepository;
    }

    private static final String SESSION_LOGIN_FAILED_ATTEMPTS = "LOGIN_FAILED_ATTEMPTS";
    private static final int FAILED_ATTEMPTS_THRESHOLD_FOR_SUGGESTION = 3;

    @GetMapping("/login")
    public String loginPage(
        @RequestParam(defaultValue = "false") boolean error,
        @RequestParam(defaultValue = "false") boolean loggedOut,
        @RequestParam(required = false) String username,
        HttpServletRequest request,
        Model model
    ) {
        HttpSession session = request.getSession(false);
        if (loggedOut && session != null) {
            session.removeAttribute(SESSION_LOGIN_FAILED_ATTEMPTS);
        }
        if (error) {
            model.addAttribute("errorMessage", "Tên đăng nhập hoặc mật khẩu không đúng.");
        }
        if (loggedOut) {
            model.addAttribute("successMessage", "Đã đăng xuất.");
        }
        if (username != null && !username.isBlank()) {
            model.addAttribute("username", username.trim());
        }
        if (session != null) {
            Integer attempts = (Integer) session.getAttribute(SESSION_LOGIN_FAILED_ATTEMPTS);
            if (attempts != null && attempts >= FAILED_ATTEMPTS_THRESHOLD_FOR_SUGGESTION) {
                model.addAttribute("suggestForgotPassword", true);
                model.addAttribute("failedAttempts", attempts);
            }
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
            HttpSession existingSession = request.getSession(false);
            if (existingSession != null) {
                existingSession.removeAttribute(SESSION_LOGIN_FAILED_ATTEMPTS);
            }
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
            return loginError(request, model, username, "Tên đăng nhập hoặc mật khẩu không đúng.");
        } catch (AccountDisabledException ex) {
            return loginError(request, model, username, "Tài khoản hiện không hoạt động.");
        } catch (IdentityProviderUnavailableException ex) {
            return loginError(request, model, username, "Dịch vụ xác thực tạm thời không khả dụng. Vui lòng thử lại sau.");
        } catch (IllegalStateException ex) {
            return loginError(request, model, username, "Không thể hoàn tất đăng nhập. Vui lòng thử lại sau.");
        } catch (DomainException ex) {
            return loginError(request, model, username, ex.getMessage() != null ? ex.getMessage() : "Thông tin đăng nhập không hợp lệ.");
        } catch (Exception ex) {
            return loginError(request, model, username, "Đã xảy ra lỗi trong quá trình đăng nhập. Vui lòng thử lại.");
        }
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage(Model model) {
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String handleForgotPassword(
        @RequestParam(defaultValue = "") String identifier,
        Model model
    ) {
        String cleanIdentifier = identifier.trim();
        if (cleanIdentifier.isBlank()) {
            model.addAttribute("errorMessage", "Vui lòng nhập tên đăng nhập hoặc email.");
            return "auth/forgot-password";
        }

        Optional<User> userOpt = cleanIdentifier.contains("@")
            ? userRepository.findByEmail(cleanIdentifier.toLowerCase())
            : userRepository.findByUsername(cleanIdentifier.toLowerCase());

        if (userOpt.isEmpty()) {
            model.addAttribute("errorMessage", "Không tìm thấy tài khoản tương ứng với thông tin đã nhập.");
            model.addAttribute("identifier", cleanIdentifier);
            return "auth/forgot-password";
        }

        User user = userOpt.get();
        String message;
        if (user.role() == UserRole.ADMIN) {
            message = "Tài khoản <strong>" + user.username() + "</strong> có vai trò Quản trị viên (ADMIN). Vui lòng sử dụng tài khoản Supabase Auth / Render Dashboard hoặc liên hệ chủ sở hữu hệ thống để thiết lập lại mật khẩu.";
        } else {
            String roleText = UiText.INSTANCE.role(user.role());
            message = "Đã tìm thấy tài khoản <strong>" + user.username() + "</strong> (" + roleText + "). Đối với vai trò này, vui lòng liên hệ trực tiếp <strong>Quản trị viên (Admin)</strong> để được cấp lại mật khẩu tạm thời mới.";
        }

        model.addAttribute("successMessage", message);
        return "auth/forgot-password";
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

    private String loginError(HttpServletRequest request, Model model, String username, String message) {
        model.addAttribute("errorMessage", message);
        if (username != null && !username.isBlank()) {
            model.addAttribute("username", username.trim());
        }

        HttpSession session = request.getSession(true);
        Integer attempts = (Integer) session.getAttribute(SESSION_LOGIN_FAILED_ATTEMPTS);
        int currentAttempts = (attempts == null ? 0 : attempts) + 1;
        session.setAttribute(SESSION_LOGIN_FAILED_ATTEMPTS, currentAttempts);

        if (currentAttempts >= FAILED_ATTEMPTS_THRESHOLD_FOR_SUGGESTION) {
            model.addAttribute("suggestForgotPassword", true);
            model.addAttribute("failedAttempts", currentAttempts);
        }
        return "auth/login";
    }

    private String passwordError(Model model, String message) {
        model.addAttribute("currentUser", currentUser());
        model.addAttribute("errorMessage", message != null ? message : "Mật khẩu mới không hợp lệ.");
        return "auth/password";
    }
}
