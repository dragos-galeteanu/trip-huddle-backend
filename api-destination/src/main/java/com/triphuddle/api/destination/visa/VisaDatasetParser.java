package com.triphuddle.api.destination.visa;

import com.triphuddle.api.destination.entity.VisaRequirement;
import com.triphuddle.api.destination.repository.VisaRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads the passport-index "tidy" CSV: {@code Passport,Destination,Requirement}, with ISO 3166-1 alpha-2 codes.
 */
public final class VisaDatasetParser {

    private VisaDatasetParser() {
    }

    /**
     * The rules a dataset gives, and how many rows it had that aren't rules.
     *
     * @param rules the rules
     * @param skipped rows with an unknown value or bad codes
     */
    public record Parsed(List<VisaRule> rules, int skipped) {
    }

    /**
     * Parse a dataset.
     *
     * @param csv the file
     * @return the rules
     * @throws IllegalStateException if it isn't the tidy ISO2 file
     */
    public static Parsed parse(String csv) {
        String[] lines = csv.split("\\r?\\n");
        if (lines.length == 0 || !lines[0].strip().replace("﻿", "").equalsIgnoreCase(
                "Passport,Destination,Requirement")) {
            throw new IllegalStateException("The visa dataset doesn't start with Passport,Destination,Requirement");
        }
        List<VisaRule> rules = new ArrayList<>(lines.length);
        int skipped = 0;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty()) {
                continue;
            }
            String[] cells = line.split(",", -1);
            VisaRule rule = cells.length == 3 ? rule(cells[0], cells[1], cells[2]) : null;
            if (rule == null) {
                skipped++;
            } else {
                rules.add(rule);
            }
        }
        return new Parsed(rules, skipped);
    }

    private static VisaRule rule(String passport, String destination, String value) {
        String from = passport.strip().toUpperCase(Locale.ROOT);
        String to = destination.strip().toUpperCase(Locale.ROOT);
        if (!from.matches("^[A-Z]{2}$") || !to.matches("^[A-Z]{2}$")) {
            return null;
        }
        String text = value.strip().toLowerCase(Locale.ROOT);
        // A number is a visa-free stay of that many days; -1 is the passport's own country.
        if (text.matches("^-?\\d+$")) {
            int days = Integer.parseInt(text);
            if (days == -1) {
                return new VisaRule(from, to, VisaRequirement.CITIZEN, null);
            }
            return days > 0 ? new VisaRule(from, to, VisaRequirement.VISA_FREE, days) : null;
        }
        VisaRequirement requirement = switch (text) {
            case "visa free" -> VisaRequirement.VISA_FREE;
            case "visa on arrival" -> VisaRequirement.VISA_ON_ARRIVAL;
            case "eta" -> VisaRequirement.ETA;
            case "e-visa" -> VisaRequirement.E_VISA;
            case "visa required" -> VisaRequirement.VISA_REQUIRED;
            case "no admission" -> VisaRequirement.NO_ADMISSION;
            default -> null;
        };
        return requirement == null ? null : new VisaRule(from, to, requirement, null);
    }
}
