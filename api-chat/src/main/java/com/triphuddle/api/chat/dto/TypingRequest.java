package com.triphuddle.api.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * STOMP frame body a client sends to say it started or stopped typing.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TypingRequest {

    private Boolean isTyping = true;
}
