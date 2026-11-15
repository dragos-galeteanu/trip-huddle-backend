package com.triphuddle.api.chat.entity;

/**
 * A report is {@code OPEN} until staff review it and either dismiss it or act on it.
 */
public enum ReportStatus {
    OPEN,
    DISMISSED,
    ACTION_TAKEN
}
