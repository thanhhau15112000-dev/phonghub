package com.phonghub.adapter.in.security;

import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.domain.model.User;
import java.util.List;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public class SupabaseJwtAuthenticationConverter implements Converter<Jwt, DomainAuthenticationToken> {

    private final UserRepositoryPort userRepository;

    public SupabaseJwtAuthenticationConverter(UserRepositoryPort userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public DomainAuthenticationToken convert(Jwt jwt) {
        if (jwt == null) {
            throw new BadCredentialsException("JWT token is null");
        }
        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new BadCredentialsException("JWT subject ('sub') claim is missing");
        }

        UUID userId;
        try {
            userId = UUID.fromString(sub.trim());
        } catch (IllegalArgumentException ex) {
            throw new BadCredentialsException("Invalid UUID format in JWT subject: " + sub);
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BadCredentialsException("Domain user profile not found for authenticated identity: " + userId));

        if (user.status() != User.UserStatus.ACTIVE) {
            throw new DisabledException("User account is " + user.status() + " (not ACTIVE)");
        }

        CurrentUser currentUser = new CurrentUser(
            user.id(),
            user.email(),
            user.fullName(),
            user.role(),
            user.mustChangePassword()
        );

        List<SimpleGrantedAuthority> authorities = List.of(
            new SimpleGrantedAuthority("ROLE_" + user.role().name())
        );

        return new DomainAuthenticationToken(currentUser, jwt, authorities, user.mustChangePassword());
    }
}
