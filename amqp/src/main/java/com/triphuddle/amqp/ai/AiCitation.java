package com.triphuddle.amqp.ai;

/**
 * A web page an AI answer drew on.
 *
 * @param url the page's address
 * @param title the page's title, or null
 */
public record AiCitation(String url, String title) {
}
