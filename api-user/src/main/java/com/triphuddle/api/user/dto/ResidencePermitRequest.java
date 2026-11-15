package com.triphuddle.api.user.dto;

import com.triphuddle.amqp.user.ResidencePermitKind;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * A residence permit, as the user enters it.
 */
@Data
@NoArgsConstructor
public class ResidencePermitRequest {

    @NotNull
    @Pattern(regexp = "^[A-Za-z]{2}$", message = "Country code must be ISO 3166-1 alpha-2")
    private String countryCode;

    @NotNull
    private ResidencePermitKind kind;

    private LocalDate validUntil;
}
