package com.phonghub.adapter.out.identity;

import com.phonghub.application.port.in.DemoActorPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.DemoFixturePort;
import com.phonghub.domain.model.UserRole;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Deterministic local/demo authentication adapter.
 * Supports switching the current actor during tests and local demo execution
 * without calling external Supabase authentication services.
 */
public class LocalDemoAuthenticationAdapter implements CurrentUserPort, DemoActorPort, DemoFixturePort {

    public static final CurrentUser DEMO_ADMIN = new CurrentUser(
        ADMIN_ID, "admin@phonghub.local", "Quản trị viên", UserRole.ADMIN, false
    );
    public static final CurrentUser DEMO_STAFF_1 = new CurrentUser(
        STAFF_1_ID, "staff1@phonghub.local", "Nhân viên Quận 7", UserRole.STAFF, false
    );
    public static final CurrentUser DEMO_STAFF_2 = new CurrentUser(
        STAFF_2_ID, "staff2@phonghub.local", "Nhân viên Tân Bình", UserRole.STAFF, false
    );
    public static final CurrentUser DEMO_TECH_1 = new CurrentUser(
        TECH_1_ID, "tech1@phonghub.local", "Kỹ thuật viên", UserRole.TECHNICIAN, false
    );
    public static final CurrentUser DEMO_TENANT_1 = new CurrentUser(
        TENANT_1_ID, "tenant1@phonghub.local", "Nguyễn Văn A", UserRole.TENANT, true
    );

    private static final Map<UUID, CurrentUser> DEMO_USERS = new LinkedHashMap<>();
    static {
        DEMO_USERS.put(ADMIN_ID, DEMO_ADMIN);
        DEMO_USERS.put(STAFF_1_ID, DEMO_STAFF_1);
        DEMO_USERS.put(STAFF_2_ID, DEMO_STAFF_2);
        DEMO_USERS.put(TECH_1_ID, DEMO_TECH_1);
        DEMO_USERS.put(TENANT_1_ID, DEMO_TENANT_1);
    }

    private final ThreadLocal<CurrentUser> currentUserHolder = new ThreadLocal<>();
    private volatile CurrentUser fallbackUser = DEMO_ADMIN;

    @Override
    public CurrentUser getCurrentUser() {
        CurrentUser user = currentUserHolder.get();
        if (user != null) {
            return user;
        }
        try {
            org.springframework.web.context.request.RequestAttributes attrs =
                org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes servletAttrs) {
                jakarta.servlet.http.HttpSession session = servletAttrs.getRequest().getSession(false);
                if (session != null) {
                    Object sessionUserId = session.getAttribute("currentUserId");
                    if (sessionUserId instanceof UUID uid && DEMO_USERS.containsKey(uid)) {
                        return DEMO_USERS.get(uid);
                    }
                }
            }
        } catch (Exception ignored) {}
        return fallbackUser;
    }

    @Override
    public void setCurrentUser(CurrentUser user) {
        if (user == null) {
            currentUserHolder.remove();
        } else {
            currentUserHolder.set(user);
            DEMO_USERS.put(user.id(), user);
            this.fallbackUser = user;
        }
    }

    public void setGlobalDefaultUser(CurrentUser user) {
        this.fallbackUser = user != null ? user : DEMO_ADMIN;
    }

    @Override
    public void switchActor(UUID userId) {
        CurrentUser user = DEMO_USERS.get(userId);
        if (user != null) {
            this.fallbackUser = user;
            setCurrentUser(user);
        }
    }

    @Override
    public Optional<CurrentUser> findDemoUser(UUID userId) {
        return Optional.ofNullable(DEMO_USERS.get(userId));
    }

    @Override
    public Map<UUID, CurrentUser> getAllDemoUsers() {
        return DEMO_USERS;
    }

    @Override
    public void clear() {
        currentUserHolder.remove();
    }
}
