package com.triphuddle.api.destination.config;

import com.triphuddle.api.destination.visa.VisaDatasetClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triphuddle.api.destination.opendata.CommonsClient;
import com.triphuddle.api.destination.opendata.WikidataClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * The Wikidata and Wikimedia Commons clients, sending the User-Agent Wikimedia's policy asks for.
 */
@Configuration
@EnableConfigurationProperties(ImportProperties.class)
public class OpenDataConfig {

    /**
     * The Wikidata SPARQL client.
     *
     * @param builder the HTTP client builder
     * @param objectMapper the JSON mapper
     * @param properties the import settings
     * @return the client
     */
    @Bean
    public WikidataClient wikidataClient(RestClient.Builder builder, ObjectMapper objectMapper,
                                         ImportProperties properties) {
        return new WikidataClient(clientFor(builder, properties.sparqlUrl(), properties), objectMapper, properties,
                duration -> Thread.sleep(duration.toMillis()));
    }

    /**
     * The Wikimedia Commons client.
     *
     * @param builder the HTTP client builder
     * @param objectMapper the JSON mapper
     * @param properties the import settings
     * @return the client
     */
    @Bean
    public CommonsClient commonsClient(RestClient.Builder builder, ObjectMapper objectMapper,
                                       ImportProperties properties) {
        return new CommonsClient(clientFor(builder, properties.commonsApiUrl(), properties), objectMapper);
    }

    /**
     * The client that downloads the passport-index visa dataset.
     *
     * @param builder the HTTP client builder
     * @param properties the import settings, for the User-Agent and timeouts
     * @return the client
     */
    @Bean
    public VisaDatasetClient visaDatasetClient(RestClient.Builder builder, ImportProperties properties) {
        return new VisaDatasetClient(clientFor(builder, null, properties));
    }

    private static RestClient clientFor(RestClient.Builder builder, String baseUrl, ImportProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.readTimeoutSeconds()));
        RestClient.Builder configured = builder.clone();
        if (baseUrl != null) {
            configured.baseUrl(baseUrl);
        }
        return configured
                .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
                .requestFactory(requestFactory)
                .build();
    }
}
