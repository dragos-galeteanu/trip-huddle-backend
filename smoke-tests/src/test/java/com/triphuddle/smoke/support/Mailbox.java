package com.triphuddle.smoke.support;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The emails the system sent, read from Mailpit, which notification sends to in the smoke stack. */
public final class Mailbox {

    private static final Pattern LINK = Pattern.compile("href=\"(https?://[^\"]+)\"");

    private Mailbox() {
    }

    /** An email as the recipient sees it. */
    public record Email(String subject, String html) {

        /** The first link whose address contains this text. */
        public String link(String containing) {
            Matcher matcher = LINK.matcher(html);
            while (matcher.find()) {
                if (matcher.group(1).contains(containing)) {
                    return matcher.group(1).replace("&amp;", "&");
                }
            }
            throw new AssertionError("No link with " + containing + " in " + subject);
        }
    }

    /** Waits for an email to this address whose subject contains this text. */
    public static Email await(String to, String subjectContaining) {
        String query = URLEncoder.encode("to:\"" + to + "\" subject:\"" + subjectContaining + "\"",
                StandardCharsets.UTF_8);
        JsonNode[] found = new JsonNode[1];
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofSeconds(1)).until(() -> {
            JsonNode messages = Http.send("GET", SmokeStack.mailpitUrl() + "/api/v1/search?query=" + query, null,
                    null).body().path("messages");
            found[0] = messages.isEmpty() ? null : messages.get(0);
            return found[0] != null;
        });
        JsonNode message = Http.send("GET", SmokeStack.mailpitUrl() + "/api/v1/message/" + found[0].get("ID")
                .asText(), null, null).body();
        return new Email(message.get("Subject").asText(), message.get("HTML").asText());
    }
}
