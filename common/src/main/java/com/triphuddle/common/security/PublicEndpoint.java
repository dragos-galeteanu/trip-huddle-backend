package com.triphuddle.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an endpoint that is open on purpose, in place of {@code @PreAuthorize}; its route must also be permitted in
 * the security config. Method security refuses a call without authentication before any expression runs, so an
 * open route cannot be expressed as {@code @PreAuthorize("permitAll()")}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PublicEndpoint {

    /**
     * Why the endpoint needs no caller, e.g. "the invitation token in the body is the credential".
     */
    String value();
}
