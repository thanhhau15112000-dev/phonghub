package com.phonghub.adapter.out.identity;

import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.domain.exception.DomainException;
import java.util.UUID;

/**
 * ============================================================================
 * SUPABASE AUTHENTICATION INTEGRATION SEAM (PORTS & ADAPTERS)
 * ============================================================================
 * Production Specification:
 * 1. The client browser authenticates with the Spring backend using username & password.
 * 2. The Spring backend verifies the credentials with Supabase Auth (server-side).
 * 3. Or, for bearer token flows, client requests pass Authorization: Bearer <JWT>.
 * 4. Spring Security Resource Server validates the JWT signature against Supabase JWKS:
 *    https://<project-ref>.supabase.co/auth/v1/.well-known/jwks.json
 * 5. The subject claim ('sub') maps to public.users.id.
 * 6. Business authorization (roles, property assignment scopes) is always evaluated
 *    by the Spring application use cases, never delegated to client-supplied role claims.
 * 7. Browser NEVER connects to Supabase business tables directly.
 * ============================================================================
 */
public class SupabaseAuthenticationAdapter implements CurrentUserPort {

    private final String supabaseUrl;
    private final String supabaseAnonKey;

    public SupabaseAuthenticationAdapter(String supabaseUrl, String supabaseAnonKey) {
        this.supabaseUrl = supabaseUrl;
        this.supabaseAnonKey = supabaseAnonKey;
    }

    @Override
    public CurrentUser getCurrentUser() {
        throw new UnsupportedOperationException(
            "Production SupabaseAuthenticationAdapter is not active in this MVP slice. " +
            "Use LocalDemoAuthenticationAdapter for local tests and demo."
        );
    }

    @Override
    public void setCurrentUser(CurrentUser user) {
        throw new UnsupportedOperationException(
            "Cannot manually set user in production SupabaseAuthenticationAdapter."
        );
    }

    @Override
    public void clear() {
        // No-op for stateless JWT authentication
    }

    public String getSupabaseUrl() {
        return supabaseUrl;
    }
}
