package com.phonghub.application.port.in;

import com.phonghub.domain.model.UserRole;
import java.util.UUID;

public record ChatContactSummary(
    UUID userId,
    String fullName,
    String email,
    UserRole role
) {}
