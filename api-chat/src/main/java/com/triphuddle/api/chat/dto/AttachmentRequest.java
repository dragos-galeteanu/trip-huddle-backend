package com.triphuddle.api.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A file already uploaded through an upload URL, to attach to a message.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class AttachmentRequest {

    @NotBlank(message = "Object path is required")
    @Size(max = 512, message = "Object path cannot exceed 512 characters")
    private String objectPath;

    @NotBlank(message = "File name is required")
    @Size(max = 255, message = "File name cannot exceed 255 characters")
    private String fileName;
}
