package com.odin.catalog.shared.auth.filter;

/**
 * Thread-local holder for the current tenant ID, populated from the JWT claim
 * or API key principal early in the filter chain. Services read this to scope
 * database queries without passing tenantId through every method signature.
 */
public final class TenantContextHolder {

    private static final ThreadLocal<String> TENANT = new ThreadLocal<>();

    private TenantContextHolder() {}

    public static void set(String tenantId) {
        TENANT.set(tenantId);
    }

    public static String get() {
        return TENANT.get();
    }

    public static void clear() {
        TENANT.remove();
    }
}
