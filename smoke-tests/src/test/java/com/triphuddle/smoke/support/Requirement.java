package com.triphuddle.smoke.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** The README requirement IDs a smoke test proves, such as {@code FR-6} or {@code GRP-2}. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Requirement {

    String[] value();
}
