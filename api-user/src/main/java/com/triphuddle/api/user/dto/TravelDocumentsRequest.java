package com.triphuddle.api.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Every nationality and residence permit of the caller, replacing those on file.
 */
@Data
@NoArgsConstructor
public class TravelDocumentsRequest {

    @NotNull
    @Size(max = 4)
    private List<@Pattern(regexp = "^[A-Za-z]{2}$", message = "Country code must be ISO 3166-1 alpha-2") String>
            nationalities = new ArrayList<>();

    @NotNull
    @Size(max = 10)
    private List<@Valid @NotNull ResidencePermitRequest> residencePermits = new ArrayList<>();
}
