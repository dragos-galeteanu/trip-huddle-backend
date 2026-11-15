package com.triphuddle.api.destination.service;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Makes URL slugs: lower-case ASCII letters and digits joined by single hyphens.
 */
public final class Slugs {

    private static final int MAX_LENGTH = 200;

    private Slugs() {
    }

    /**
     * Make a slug from text, dropping accents and anything that is not a letter or digit.
     *
     * @param text the text, e.g. "Lisboa – Belém"
     * @return the slug, e.g. "lisboa-belem", or "place" if nothing is left
     */
    public static String of(String text) {
        String ascii = Normalizer.normalize(text, Normalizer.Form.NFKD).replaceAll("\\p{M}+", "");
        String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        if (slug.length() > MAX_LENGTH) {
            slug = slug.substring(0, MAX_LENGTH).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? "place" : slug;
    }
}
