package com.triphuddle.api.destination.opendata;

/**
 * A Wikimedia Commons image at the width asked for, with what its licence asks to be shown.
 *
 * @param fileName the file's name, e.g. {@code Porto_Ribeira.jpg}
 * @param url the image at the asked width
 * @param width its width in pixels
 * @param height its height in pixels
 * @param mimeType e.g. image/jpeg
 * @param author who made it, as plain text, or null
 * @param licenseName the licence's short name, e.g. CC BY-SA 4.0
 * @param licenseUrl the licence's page, or null for public domain
 * @param sourcePageUrl the file's page on Commons
 * @param isAttributionRequired whether the licence asks for the author to be named
 */
public record CommonsImage(
        String fileName,
        String url,
        int width,
        int height,
        String mimeType,
        String author,
        String licenseName,
        String licenseUrl,
        String sourcePageUrl,
        boolean isAttributionRequired
) {
}
