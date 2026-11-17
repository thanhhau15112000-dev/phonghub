package com.phonghub.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bảo vệ endpoint /actuator/prometheus bằng secret token.
 * Tránh mở số liệu nội bộ ra ngoài công khai khi deploy lên môi trường staging/production.
 */
@Configuration
public class ActuatorSecurityConfig {

    private final String metricsToken;

    public ActuatorSecurityConfig(
        @Value("${phonghub.management.metrics-token:phonghub-metrics-secret-token}") String metricsToken
    ) {
        this.metricsToken = metricsToken;
    }

    @Bean
    @Order(0)
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/actuator/**")
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/actuator/prometheus").authenticated()
                .anyRequest().authenticated()
            )
            .addFilterBefore(new MetricsTokenAuthenticationFilter(metricsToken), UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Missing or invalid metrics token\"}");
                })
            );
        return http.build();
    }

    static class MetricsTokenAuthenticationFilter extends OncePerRequestFilter {
        private final String expectedToken;

        MetricsTokenAuthenticationFilter(String expectedToken) {
            this.expectedToken = expectedToken != null ? expectedToken.trim() : "";
        }

        @Override
        protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
        ) throws ServletException, IOException {
            String token = extractToken(request);
            if (!expectedToken.isEmpty() && expectedToken.equals(token)) {
                var auth = new UsernamePasswordAuthenticationToken(
                    "metrics-scraper",
                    token,
                    List.of(new SimpleGrantedAuthority("ROLE_METRICS_SCRAPER"))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
            filterChain.doFilter(request, response);
        }

        private String extractToken(HttpServletRequest request) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
                return authHeader.substring(7).trim();
            }
            String param = request.getParameter("token");
            if (param != null && !param.isBlank()) {
                return param.trim();
            }
            String customHeader = request.getHeader("X-Metrics-Token");
            if (customHeader != null && !customHeader.isBlank()) {
                return customHeader.trim();
            }
            return null;
        }
    }
}

