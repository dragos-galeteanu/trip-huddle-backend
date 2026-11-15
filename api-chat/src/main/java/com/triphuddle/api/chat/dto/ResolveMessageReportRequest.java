package com.triphuddle.api.chat.dto;

import com.triphuddle.api.chat.entity.ReportStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for resolving a report; {@code isMessageRemoved} also deletes the reported message.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResolveMessageReportRequest {

    @NotNull(message = "Status is required")
    private ReportStatus status;

    @Size(max = 1000, message = "Resolution note cannot exceed 1000 characters")
    private String resolutionNote;

    private Boolean isMessageRemoved = false;
}
