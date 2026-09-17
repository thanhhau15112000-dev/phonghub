package com.phonghub.adapter.in.security;

import com.phonghub.application.port.out.CurrentUser;
import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public class DomainAuthenticationToken extends AbstractAuthenticationToken {

    private final CurrentUser currentUser;
    private final Jwt jwt;
    private final String accessToken;
    private final boolean mustChangePassword;

    public DomainAuthenticationToken(
        CurrentUser currentUser,
        Jwt jwt,
        Collection<? extends GrantedAuthority> authorities,
        boolean mustChangePassword
    ) {
        this(
            currentUser,
            jwt,
            jwt != null ? jwt.getTokenValue() : null,
            authorities,
            mustChangePassword
        );
    }

    public DomainAuthenticationToken(
        CurrentUser currentUser,
        String accessToken,
        Collection<? extends GrantedAuthority> authorities,
        boolean mustChangePassword
    ) {
        this(currentUser, null, accessToken, authorities, mustChangePassword);
    }

    private DomainAuthenticationToken(
        CurrentUser currentUser,
        Jwt jwt,
        String accessToken,
        Collection<? extends GrantedAuthority> authorities,
        boolean mustChangePassword
    ) {
        super(authorities);
        this.currentUser = currentUser;
        this.jwt = jwt;
        this.accessToken = accessToken;
        this.mustChangePassword = mustChangePassword;
        setAuthenticated(true);
    }

    public CurrentUser getCurrentUser() {
        return currentUser;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    @Override
    public Object getCredentials() {
        return accessToken;
    }

    @Override
    public Object getPrincipal() {
        return currentUser;
    }
}
