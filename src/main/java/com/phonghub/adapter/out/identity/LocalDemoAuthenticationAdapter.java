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
        ADMIN_ID, "admin@phonghub.local", "Quan Tri Vien (Admin)", UserRole.ADMIN, false
    );
    public static final CurrentUser DEMO_STAFF_1 = new CurrentUser(
        STAFF_1_ID, "staff1@phonghub.local", "Nhan Vien Q7 (Staff 1)", UserRole.STAFF, false
    );
    public static final CurrentUser DEMO_STAFF_2 = new CurrentUser(
        STAFF_2_ID, "staff2@phonghub.local", "Nhan Vien TB (Staff 2)", UserRole.STAFF, false
    );
    public static final CurrentUser DEMO_TECH_1 = new CurrentUser(
        TECH_1_ID, "tech1@phonghub.local", "Ky Thuat Vien (Tech 1)", UserRole.TECHNICIAN, false
    );
    public static final CurrentUser DEMO_TENANT_1 = new CurrentUser(
        TENANT_1_ID, "tenant1@phonghub.local", "Nguyen Van A (Tenant)", UserRole.TENANT, true
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
        return fallbackUser;
    }

    @Override
    public void setCurrentUser(CurrentUser user) {
        if (user == null) {
            currentUserHolder.remove();
        } else {
            currentUserHolder.set(user);
        }
    }

    public void setGlobalDefaultUser(CurrentUser user) {
        this.fallbackUser = user != null ? user : DEMO_ADMIN;
    }

    @Override
    public void switchActor(UUID userId) {
        CurrentUser user = DEMO_USERS.get(userId);
        if (user != null) {
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
