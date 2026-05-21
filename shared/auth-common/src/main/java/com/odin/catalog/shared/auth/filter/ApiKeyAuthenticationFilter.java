package com.odin.catalog.shared.auth.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;

/**
 * Checks for the {@code X-API-Key} header and authenticates the request
 * when a valid key is found. Runs before Spring Security's JWT filter.
 *
 * Services inject their own {@code ApiKeyLookup} function (e.g., querying
 * identity-service or a local cache) to resolve and validate the key.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);
    private static final String API_KEY_HEADER = "X-API-Key";

    private final Function<String, ApiKeyPrincipal> apiKeyLookup;

    public ApiKeyAuthenticationFilter(Function<String, ApiKeyPrincipal> apiKeyLookup) {
        this.apiKeyLookup = apiKeyLookup;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String apiKey = request.getHeader(API_KEY_HEADER);
        if (!StringUtils.hasText(apiKey)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            ApiKeyPrincipal principal = apiKeyLookup.apply(apiKey);
            if (principal != null && principal.active()) {
                List<SimpleGrantedAuthority> authorities = principal.scopes().stream()
                    .map(s -> new SimpleGrantedAuthority("SCOPE_" + s))
                    .toList();
                UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        } catch (Exception e) {
            log.warn("API key validation failed: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    public record ApiKeyPrincipal(
        String keyId,
        String tenantId,
        String ownerId,
        List<String> scopes,
        boolean active
    ) {}
}
