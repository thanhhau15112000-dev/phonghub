package com.phonghub.config;

import com.phonghub.application.port.in.AuthTokenResponse;
import com.phonghub.application.port.in.AuthUseCase;
import com.phonghub.domain.model.User;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Wraps local account/profile writes in one database transaction; AuthService compensates external identities. */
public class TransactionalAuthUseCase implements AuthUseCase {

    private final AuthUseCase delegate;

    public TransactionalAuthUseCase(AuthUseCase delegate) {
        this.delegate = delegate;
    }

    @Override public AuthTokenResponse login(String username, String password) { return delegate.login(username, password); }
    @Override public AuthTokenResponse refreshToken(String refreshToken) { return delegate.refreshToken(refreshToken); }
    @Override public void logout(String accessToken) { delegate.logout(accessToken); }
    @Override public void changePassword(String newPassword) { delegate.changePassword(newPassword); }
    @Override public PasswordResetResult adminResetPassword(UUID targetUserId) {
        return delegate.adminResetPassword(targetUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminCreateUserResult adminCreateUser(CreateUserCommand command) {
        return delegate.adminCreateUser(command);
    }

    @Override public List<User> listTenantAccountsMissingProfiles() {
        return delegate.listTenantAccountsMissingProfiles();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminAttachTenantProfile(UUID targetUserId, String identityCardNumber, String phone) {
        delegate.adminAttachTenantProfile(targetUserId, identityCardNumber, phone);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminDeleteUser(UUID targetUserId) {
        delegate.adminDeleteUser(targetUserId);
    }
}
