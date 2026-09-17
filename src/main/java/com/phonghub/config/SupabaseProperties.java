package com.phonghub.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "supabase")
public class SupabaseProperties {

    @NotBlank(message = "supabase.url is required")
    private String url;

    @NotBlank(message = "supabase.anon-key is required")
    private String anonKey;

    @NotBlank(message = "supabase.service-role-key is required")
    private String serviceRoleKey;

    @NotBlank(message = "supabase.jwks-uri is required")
    private String jwksUri;

    @NotBlank(message = "supabase.jwt-issuer is required")
    private String jwtIssuer;

    @NotBlank(message = "supabase.jwt-audience is required")
    private String jwtAudience = "authenticated";

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getAnonKey() {
        return anonKey;
    }

    public void setAnonKey(String anonKey) {
        this.anonKey = anonKey;
    }

    public String getServiceRoleKey() {
        return serviceRoleKey;
    }

    public void setServiceRoleKey(String serviceRoleKey) {
        this.serviceRoleKey = serviceRoleKey;
    }

    public String getJwksUri() {
        return jwksUri;
    }

    public void setJwksUri(String jwksUri) {
        this.jwksUri = jwksUri;
    }

    public String getJwtIssuer() {
        return jwtIssuer;
    }

    public void setJwtIssuer(String jwtIssuer) {
        this.jwtIssuer = jwtIssuer;
    }

    public String getJwtAudience() {
        return jwtAudience;
    }

    public void setJwtAudience(String jwtAudience) {
        this.jwtAudience = jwtAudience;
    }
}
