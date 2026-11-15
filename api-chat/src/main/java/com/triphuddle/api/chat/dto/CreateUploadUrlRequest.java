package com.triphuddle.api.chat.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for an upload URL for one attachment; any file type is accepted, up to 20 MB.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateUploadUrlRequest {

    @NotBlank(message = "File name is required")
    @Size(max = 255, message = "File name cannot exceed 255 characters")
    private String fileName;

    @NotBlank(message = "Content type is required")
    @Size(max = 255, message = "Content type cannot exceed 255 characters")
    private String contentType;

    @NotNull(message = "Size is required")
    @Min(value = 1, message = "Size must be at least 1 byte")
    @Max(value = 20971520, message = "Size cannot exceed 20 MB")
    private Long sizeBytes;
}
