package com.phonghub.application.port.out;

import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface UserRepositoryPort {
    User save(User user);
    Optional<User> findById(UUID id);
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByPhone(String phone);
    List<User> findByRole(UserRole role);
    List<User> findAll();
    void deleteById(UUID id);

    /** Đọc nhanh id và role để lọc phạm vi chat mà không giải mã các cột PII. */
    default Map<UUID, UserRole> findAllIdRoles() {
        Map<UUID, UserRole> roles = new java.util.LinkedHashMap<>();
        for (User user : findAll()) {
            roles.put(user.id(), user.role());
        }
        return roles;
    }
}
