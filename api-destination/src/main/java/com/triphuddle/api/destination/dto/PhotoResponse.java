package com.triphuddle.api.destination.dto;

import java.util.UUID;

/**
 * A place's photo, with what its licence asks to be shown next to it.
 *
 * @param id the ID of the photo
 * @param url where to load it from; a signed URL that expires when copied to the bucket
 * @param width its width in pixels
 * @param height its height in pixels
 * @param author who made it, or null
 * @param licenseName the licence, e.g. CC BY-SA 4.0
 * @param licenseUrl the licence's page, or null
 * @param sourcePageUrl the photo's page on Wikimedia Commons
 * @param isAttributionRequired whether the author and licence must be shown with it
 * @param isPrimary whether it is the place's main photo
 */
public record PhotoResponse(
        UUID id,
        String url,
        int width,
        int height,
        String author,
        String licenseName,
        String licenseUrl,
        String sourcePageUrl,
        boolean isAttributionRequired,
        boolean isPrimary
) {
}
