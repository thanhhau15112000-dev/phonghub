package com.phonghub.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CachingUserRepositoryPortTest {

    @Test
    void cachesFindByIdAndRolesAndInvalidatesOnSaveOrDelete() {
        UserRepositoryPort delegate = mock(UserRepositoryPort.class);
        CachingUserRepositoryPort cached = new CachingUserRepositoryPort(delegate, true);

        UUID id = UUID.randomUUID();
        User active = new User(id, "u1", "u1@x", "User 1", "0900000001", UserRole.TENANT,
            User.UserStatus.ACTIVE, false, Instant.now());
        when(delegate.findById(id)).thenReturn(Optional.of(active));
        when(delegate.findAllIdRoles()).thenReturn(Map.of(id, UserRole.TENANT));

        assertTrue(cached.findById(id).isPresent());
        assertTrue(cached.findById(id).isPresent());
        assertEquals(Map.of(id, UserRole.TENANT), cached.findAllIdRoles());
        assertEquals(Map.of(id, UserRole.TENANT), cached.findAllIdRoles());
        verify(delegate, times(1)).findById(id);
        verify(delegate, times(1)).findAllIdRoles();

        User suspended = new User(id, "u1", "u1@x", "User 1", "0900000001", UserRole.TENANT,
            User.UserStatus.SUSPENDED, false, active.createdAt());
        when(delegate.save(suspended)).thenReturn(suspended);
        when(delegate.findById(id)).thenReturn(Optional.of(suspended));
        cached.save(suspended);

        assertEquals(User.UserStatus.SUSPENDED, cached.findById(id).orElseThrow().status());
        verify(delegate, times(2)).findById(id);

        cached.deleteById(id);
        cached.findAllIdRoles();
        verify(delegate, times(2)).findAllIdRoles();
    }
}
