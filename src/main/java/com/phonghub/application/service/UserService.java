package com.phonghub.application.service;

import com.phonghub.application.port.in.UserUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.StaffPropertyAssignmentPort;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.exception.UserNotFoundException;
import com.phonghub.domain.model.StaffPropertyAssignment;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class UserService implements UserUseCase {

    private final UserRepositoryPort userRepository;
    private final StaffPropertyAssignmentPort assignmentPort;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;

    public UserService(
        UserRepositoryPort userRepository,
        StaffPropertyAssignmentPort assignmentPort,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService
    ) {
        this.userRepository = userRepository;
        this.assignmentPort = assignmentPort;
        this.currentUserPort = currentUserPort;
        this.authorizationService = authorizationService;
    }

    @Override
    public List<User> listUsers() {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        authorizationService.assertAdmin(currentUser);
        return userRepository.findAll();
    }

    @Override
    public User getUser(UUID userId) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        if (currentUser.role() != UserRole.ADMIN && !currentUser.id().equals(userId)) {
            throw new UnauthorizedPropertyAccessException("Cannot access other user's profile");
        }
        return userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + userId));
    }

    @Override
    public StaffPropertyAssignment assignStaffToProperty(AssignStaffCommand command) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        authorizationService.assertAdmin(currentUser);

        StaffPropertyAssignment assignment = new StaffPropertyAssignment(
            UUID.randomUUID(),
            command.staffUserId(),
            command.propertyId(),
            command.canCollectPayment(),
            command.canManageContracts(),
            Instant.now()
        );

        return assignmentPort.save(assignment);
    }

    @Override
    public List<StaffPropertyAssignment> getStaffAssignments(UUID staffUserId) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        if (currentUser.role() != UserRole.ADMIN && !currentUser.id().equals(staffUserId)) {
            throw new UnauthorizedPropertyAccessException("Cannot access other user's assignments");
        }
        return assignmentPort.findByUserId(staffUserId);
    }

    @Override
    public Set<UUID> getAssignedPropertyIds(UUID userId) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        if (currentUser.role() != UserRole.ADMIN && !currentUser.id().equals(userId)) {
            throw new UnauthorizedPropertyAccessException("Cannot access other user's assignments");
        }
        return assignmentPort.findPropertyIdsByUserId(userId);
    }
}
