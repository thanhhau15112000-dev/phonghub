package com.phonghub.adapter.in.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.phonghub.application.port.in.AuthUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;

/**
 * Spring Session Redis ghi attribute của session bằng JDK serialization.
 * Mọi object đặt vào session (SecurityContext, flash attribute) phải đi qua được serializer này.
 */
class SessionSerializationTest {

    private final JdkSerializationRedisSerializer serializer = new JdkSerializationRedisSerializer();

    private Object roundTrip(Object value) {
        return serializer.deserialize(serializer.serialize(value));
    }

    @Test
    void securityContextWithDomainTokenSurvivesRoundTrip() {
        CurrentUser user = new CurrentUser(UUID.randomUUID(), "owner@example.test", "Chủ trọ", UserRole.OWNER, true);
        DomainAuthenticationToken token = new DomainAuthenticationToken(
            user, "access-token", List.of(new SimpleGrantedAuthority("ROLE_OWNER")), true
        );

        Object restored = roundTrip(new SecurityContextImpl(token));

        DomainAuthenticationToken restoredToken = assertInstanceOf(
            DomainAuthenticationToken.class, assertInstanceOf(SecurityContextImpl.class, restored).getAuthentication()
        );
        assertEquals(user, restoredToken.getCurrentUser());
        assertEquals("access-token", restoredToken.getCredentials());
        assertEquals(List.of(new SimpleGrantedAuthority("ROLE_OWNER")), List.copyOf(restoredToken.getAuthorities()));
        assertEquals(true, restoredToken.isMustChangePassword());
        assertEquals(true, restoredToken.isAuthenticated());
    }

    @Test
    void flashAttributeResultsSurviveRoundTrip() {
        AuthUseCase.PasswordResetResult reset = new AuthUseCase.PasswordResetResult(
            UUID.randomUUID(), "user", "Temp#123", true, "ok"
        );
        AuthUseCase.AdminCreateUserResult created = new AuthUseCase.AdminCreateUserResult(
            UUID.randomUUID(), "user", "user@example.test", "Người dùng", "0900000000",
            UserRole.TENANT, User.UserStatus.ACTIVE, true, "Temp#123", Instant.now()
        );

        assertEquals(reset, roundTrip(reset));
        assertEquals(created, roundTrip(created));
    }
}
