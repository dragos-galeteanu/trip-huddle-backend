package com.triphuddle.api.destination.visa;

import com.triphuddle.api.destination.content.PlacePrompts;
import com.triphuddle.api.destination.entity.VisaRequirement;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Builds the prompts for visa guidance: one passport, one destination, and the residence permits that may matter.
 */
final class VisaPrompts {

    private VisaPrompts() {
    }

    static String system(String languageCode) {
        return """
                You explain entry requirements to travellers in a trip-planning app, writing in %s.
                Cover an ordinary tourist's short stay. Check the current rules on the web, preferring the
                destination's government and embassies; put the official pages you used in officialLinks.
                The dataset's requirement may be out of date: if official sources differ, say so in warnings and
                give the requirement they state. Never invent fees, durations or forms; when unsure, say it varies
                and point to the official page. Explain how the residence permits listed change things, if they do.
                You are not giving legal advice. Do not mention that you are an AI. Answer only with JSON matching
                the schema.
                """.formatted(PlacePrompts.languageName(languageCode));
    }

    static String user(String passportCode, String destinationCode, VisaRequirement requirement,
                       Integer allowedDays, List<String> permitCodes) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Passport: ").append(country(passportCode)).append(" (").append(passportCode).append(")\n");
        prompt.append("Destination: ").append(country(destinationCode)).append(" (").append(destinationCode)
                .append(")\n");
        prompt.append("The dataset says: ").append(requirement == null ? "no rule known" : describe(requirement,
                allowedDays)).append('\n');
        if (permitCodes.isEmpty()) {
            prompt.append("Residence permits: none\n");
        } else {
            prompt.append("Residence permits held: ").append(permitCodes.stream()
                    .map(code -> country(code) + " (" + code + ")").collect(Collectors.joining(", "))).append('\n');
        }
        return prompt.append("""

                Explain what this traveller must do before and on arrival: the steps in order, the documents to
                carry, the costs, how long it takes, how long they may stay, and any warnings.""").toString();
    }

    private static String describe(VisaRequirement requirement, Integer allowedDays) {
        String name = requirement.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return allowedDays == null ? name : "%s for up to %d days".formatted(name, allowedDays);
    }

    static String country(String code) {
        String name = new Locale("", code).getDisplayCountry(Locale.ENGLISH);
        return name.isBlank() || name.equals(code) ? code : name;
    }
}
