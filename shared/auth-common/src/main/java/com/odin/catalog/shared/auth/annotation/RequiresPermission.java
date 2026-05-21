package com.odin.catalog.shared.auth.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the permission(s) required to invoke the annotated controller method.
 * Enforced by {@code PermissionCheckAspect} in each service.
 *
 * Example:
 *   @RequiresPermission("catalog:datasets:write")
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {
    /** One or more permission strings. All must be present (AND semantics). */
    String[] value();

    /** When true, any one permission is sufficient (OR semantics). */
    boolean anyOf() default false;
}
