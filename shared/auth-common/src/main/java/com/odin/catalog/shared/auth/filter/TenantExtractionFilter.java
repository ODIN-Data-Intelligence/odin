package com.odin.catalog.shared.auth.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads the tenant_id claim from the JWT (or from ApiKeyPrincipal) and
 * stores it in {@link TenantContextHolder} for the duration of the request.
 * Must run after both the JWT filter and ApiKeyAuthenticationFilter.
 */
public class TenantExtractionFilter extends OncePerRequestFilter {

    private static final String TENANT_CLAIM = "tenant_id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                Object principal = auth.getPrincipal();
                if (principal instanceof Jwt jwt) {
                    String tenantId = jwt.getClaimAsString(TENANT_CLAIM);
                    if (tenantId != null) {
                        TenantContextHolder.set(tenantId);
                    }
                } else if (principal instanceof ApiKeyAuthenticationFilter.ApiKeyPrincipal apiKey) {
                    TenantContextHolder.set(apiKey.tenantId());
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }
}
