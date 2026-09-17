package com.phonghub.adapter.in.security;

import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityCurrentUserAdapter implements CurrentUserPort {

    @Override
    public CurrentUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof DomainAuthenticationToken domainAuth) {
            return domainAuth.getCurrentUser();
        }
        throw new UnauthorizedPropertyAccessException("Authentication required: no verified identity found in security context");
    }
}
