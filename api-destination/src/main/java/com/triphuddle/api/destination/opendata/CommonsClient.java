package com.triphuddle.api.destination.opendata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reads images and their licences from the Wikimedia Commons API, and downloads them.
 */
public class CommonsClient {

    private static final int BATCH = 50;
    private static final int MAX_DOWNLOAD_BYTES = 15 * 1024 * 1024;
    // Free licences only: what Commons hosts is meant to be free, but a file may carry other terms.
    private static final Pattern FREE_LICENSE = Pattern.compile(
            "^(CC0|Public domain|PD|CC BY|CC-BY|CC BY-SA|CC-BY-SA|GFDL|Attribution|FAL)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAGS = Pattern.compile("<[^>]+>");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    /**
     * @param restClient the HTTP client, pointed at the Commons API and sending Wikimedia's User-Agent
     * @param objectMapper the JSON mapper
     */
    public CommonsClient(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Read images at a width, keeping only freely licensed ones.
     *
     * @param fileNames the files' names
     * @param width the width to have them at
     * @return the images by the file name asked for; missing or non-free files are left out
     */
    public Map<String, CommonsImage> images(List<String> fileNames, int width) {
        Map<String, CommonsImage> images = new HashMap<>();
        for (int from = 0; from < fileNames.size(); from += BATCH) {
            List<String> batch = fileNames.subList(from, Math.min(from + BATCH, fileNames.size()));
            JsonNode root = query(batch, width);
            // Commons normalises titles, e.g. underscores to spaces; map them back to the names asked for.
            Map<String, String> askedByTitle = new LinkedHashMap<>();
            for (String fileName : batch) {
                askedByTitle.put("File:" + fileName.replace('_', ' '), fileName);
            }
            for (JsonNode normalized : root.path("query").path("normalized")) {
                String asked = askedByTitle.remove(normalized.path("from").asText());
                if (asked != null) {
                    askedByTitle.put(normalized.path("to").asText(), asked);
                }
            }
            for (JsonNode page : root.path("query").path("pages")) {
                String asked = askedByTitle.get(page.path("title").asText());
                JsonNode info = page.path("imageinfo").path(0);
                if (asked == null || info.isMissingNode() || page.has("missing")) {
                    continue;
                }
                CommonsImage image = imageOf(asked, info);
                if (image != null) {
                    images.put(asked, image);
                }
            }
        }
        return images;
    }

    /**
     * Download an image.
     *
     * @param url the image's URL, from {@link #images}
     * @return its bytes
     */
    public byte[] download(String url) {
        try {
            byte[] bytes = restClient.get().uri(URI.create(url)).retrieve().body(byte[].class);
            if (bytes == null || bytes.length == 0 || bytes.length > MAX_DOWNLOAD_BYTES) {
                throw new OpenDataException("Image %s is empty or too large".formatted(url));
            }
            return bytes;
        } catch (RestClientException e) {
            throw new OpenDataException("Downloading %s failed: %s".formatted(url, e.getMessage()), e);
        }
    }

    private JsonNode query(List<String> fileNames, int width) {
        String titles = fileNames.stream().map(name -> "File:" + name).collect(Collectors.joining("|"));
        try {
            String raw = restClient.get()
                    .uri(builder -> builder
                            .queryParam("action", "query")
                            .queryParam("format", "json")
                            .queryParam("formatversion", "2")
                            .queryParam("prop", "imageinfo")
                            .queryParam("iiprop", "url|size|mime|extmetadata")
                            .queryParam("iiurlwidth", width)
                            .queryParam("titles", "{titles}")
                            .build(titles))
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(raw == null ? "{}" : raw);
        } catch (RestClientException | IOException e) {
            throw new OpenDataException("Commons query failed: " + e.getMessage(), e);
        }
    }

    private static CommonsImage imageOf(String fileName, JsonNode info) {
        JsonNode meta = info.path("extmetadata");
        String license = plain(meta.path("LicenseShortName").path("value").asText(null));
        if (license == null || !FREE_LICENSE.matcher(license).find()) {
            return null;
        }
        boolean hasThumb = info.hasNonNull("thumburl");
        return new CommonsImage(fileName,
                hasThumb ? info.path("thumburl").asText() : info.path("url").asText(),
                hasThumb ? info.path("thumbwidth").asInt() : info.path("width").asInt(),
                hasThumb ? info.path("thumbheight").asInt() : info.path("height").asInt(),
                info.path("mime").asText(null),
                plain(meta.path("Artist").path("value").asText(null)),
                license,
                meta.path("LicenseUrl").path("value").asText(null),
                info.path("descriptionurl").asText(null),
                !"false".equalsIgnoreCase(meta.path("AttributionRequired").path("value").asText("true")));
    }

    // Commons gives authors as HTML, often a link to their user page.
    private static String plain(String html) {
        if (html == null) {
            return null;
        }
        String text = TAGS.matcher(html).replaceAll("").replace("&amp;", "&").replace("&nbsp;", " ")
                .replaceAll("\\s+", " ").strip();
        return text.isEmpty() ? null : text;
    }
}
