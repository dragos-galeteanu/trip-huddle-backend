package com.triphuddle.api.destination.visa;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Downloads the passport-index dataset, a CSV of a few hundred kilobytes.
 */
public class VisaDatasetClient {

    private final RestClient restClient;

    /**
     * Create the client.
     *
     * @param restClient the HTTP client, with the User-Agent and timeouts set
     */
    public VisaDatasetClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Download the dataset.
     *
     * @param url where it is
     * @return the CSV
     * @throws IllegalStateException if the download fails or is empty
     */
    public String download(String url) {
        try {
            String body = restClient.get().uri(url).retrieve().body(String.class);
            if (body == null || body.isBlank()) {
                throw new IllegalStateException("The visa dataset at %s is empty".formatted(url));
            }
            return body;
        } catch (RestClientException e) {
            throw new IllegalStateException("Could not download the visa dataset: " + e.getMessage(), e);
        }
    }
}
