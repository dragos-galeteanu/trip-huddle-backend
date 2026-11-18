package com.triphuddle.smoke.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Integration tests that prove a requirement end to end can't observe, such as a retry or a lock. Each is
 * {@code "module:ClassName"}, e.g. {@code "api-chat:SupportTicketIT"}; the traceability check finds it in that
 * module's tests. Named, not imported: this module never depends on a service.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CoveredBy {

    String[] value();
}
