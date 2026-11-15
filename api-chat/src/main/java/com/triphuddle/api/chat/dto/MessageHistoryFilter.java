package com.triphuddle.api.chat.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cursor parameters for message history: older than {@code before}, or newer than {@code after}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageHistoryFilter {

    // Scrolling back: messages older than this ID, newest first. Omit both for the latest messages.
    private String before;

    // Catching up after a reconnect: messages newer than this ID, oldest first.
    private String after;

    @Min(value = 1, message = "Limit must be at least 1")
    @Max(value = 100, message = "Limit cannot exceed 100")
    private int limit = 50;
}
