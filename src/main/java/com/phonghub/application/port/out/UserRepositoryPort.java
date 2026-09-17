package com.phonghub.application.port.out;

import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepositoryPort {
    User save(User user);
    Optional<User> findById(UUID id);
    Optional<User> findByEmail(String email);
    List<User> findByRole(UserRole role);
    List<User> findAll();
}
