package com.phonghub.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.phonghub.application.port.in.UserUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.StaffPropertyAssignmentPort;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.application.service.UserService;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.exception.UserNotFoundException;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserServiceUnitTest {

    private UserRepositoryPort userRepository;
    private StaffPropertyAssignmentPort assignmentPort;
    private CurrentUserPort currentUserPort;
    private AuthorizationService authorizationService;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepositoryPort.class);
        assignmentPort = mock(StaffPropertyAssignmentPort.class);
        currentUserPort = mock(CurrentUserPort.class);
        authorizationService = mock(AuthorizationService.class);
        userService = new UserService(userRepository, assignmentPort, currentUserPort, authorizationService);
    }

    @Test
    void userCanUpdateOwnProfile() {
        UUID userId = UUID.randomUUID();
        CurrentUser actor = new CurrentUser(userId, "staff@phonghub.local", "Staff Old", UserRole.STAFF);
        when(currentUserPort.getCurrentUser()).thenReturn(actor);

        User existingUser = new User(
            userId, "staff1", "staff@phonghub.local", "Staff Old", "0900000001",
            UserRole.STAFF, User.UserStatus.ACTIVE, false, Instant.now()
        );
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User updated = userService.updateProfile(new UserUseCase.UpdateProfileCommand(
            userId, "Staff New Name", "0912345678"
        ));

        assertNotNull(updated);
        assertEquals("Staff New Name", updated.fullName());
        assertEquals("0912345678", updated.phone());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void nonAdminCannotUpdateOtherUserProfile() {
        UUID actorId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        CurrentUser actor = new CurrentUser(actorId, "tenant@phonghub.local", "Tenant User", UserRole.TENANT);
        when(currentUserPort.getCurrentUser()).thenReturn(actor);

        assertThrows(UnauthorizedPropertyAccessException.class, () ->
            userService.updateProfile(new UserUseCase.UpdateProfileCommand(
                otherUserId, "Malicious Name", "0912345678"
            ))
        );

        verify(userRepository, never()).save(any());
    }

    @Test
    void adminCanUpdateAnyUserProfile() {
        UUID adminId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        CurrentUser admin = new CurrentUser(adminId, "admin@phonghub.local", "Admin User", UserRole.ADMIN);
        when(currentUserPort.getCurrentUser()).thenReturn(admin);

        User targetUser = new User(
            targetUserId, "user2", "user2@phonghub.local", "Old Name", null,
            UserRole.TECHNICIAN, User.UserStatus.ACTIVE, false, Instant.now()
        );
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User updated = userService.updateProfile(new UserUseCase.UpdateProfileCommand(
            targetUserId, "Updated Technician", "0987654321"
        ));

        assertEquals("Updated Technician", updated.fullName());
        assertEquals("0987654321", updated.phone());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void updateProfileThrowsWhenUserNotFound() {
        UUID userId = UUID.randomUUID();
        CurrentUser actor = new CurrentUser(userId, "user@phonghub.local", "User", UserRole.TENANT);
        when(currentUserPort.getCurrentUser()).thenReturn(actor);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () ->
            userService.updateProfile(new UserUseCase.UpdateProfileCommand(
                userId, "New Name", null
            ))
        );
    }

    @Test
    void updateProfileValidatesBlankFullName() {
        UUID userId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () ->
            new UserUseCase.UpdateProfileCommand(userId, "   ", "0912345678")
        );
    }

    @Test
    void updateProfileValidatesInvalidPhoneNumber() {
        UUID userId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () ->
            new UserUseCase.UpdateProfileCommand(userId, "Valid Name", "12345")
        );
    }

    @Test
    void updateProfileRejectsDuplicatePhone() {
        UUID userId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        CurrentUser actor = new CurrentUser(userId, "staff@phonghub.local", "Staff", UserRole.STAFF);
        when(currentUserPort.getCurrentUser()).thenReturn(actor);

        User existingUser = new User(
            userId, "staff1", "staff@phonghub.local", "Staff", "0900000001",
            UserRole.STAFF, User.UserStatus.ACTIVE, false, Instant.now()
        );
        User otherUser = new User(
            otherId, "staff2", "staff2@phonghub.local", "Other", "0900000002",
            UserRole.STAFF, User.UserStatus.ACTIVE, false, Instant.now()
        );
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepository.findAll()).thenReturn(java.util.List.of(existingUser, otherUser));

        assertThrows(IllegalArgumentException.class, () ->
            userService.updateProfile(new UserUseCase.UpdateProfileCommand(
                userId, "New Name", "0900000002"
            ))
        );
    }
}
