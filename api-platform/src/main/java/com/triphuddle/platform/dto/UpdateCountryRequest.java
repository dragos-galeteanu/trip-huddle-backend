package com.triphuddle.platform.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCountryRequest {

    @NotNull(message = "ID is required")
    private UUID id;

    @NotBlank(message = "Alpha-2 code is required")
    @Pattern(regexp = "^[A-Z]{2}$", message = "Alpha-2 code must be a two-letter uppercase ISO 3166-1 code")
    private String alpha2;

    @NotBlank(message = "Alpha-3 code is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Alpha-3 code must be a three-letter uppercase ISO 3166-1 code")
    private String alpha3;

    @NotBlank(message = "Numeric code is required")
    @Pattern(regexp = "^[0-9]{3}$", message = "Numeric code must be a three-digit ISO 3166-1 code")
    private String numericCode;

    @NotBlank(message = "Name is required")
    private String name;

    @Size(max = 8, message = "Calling code must be at most 8 characters")
    private String callingCode;

    @DecimalMin(value = "0.00", message = "Standard VAT rate must be at least 0")
    @Digits(integer = 3, fraction = 2, message = "Standard VAT rate must have at most 3 integer and 2 fraction digits")
    private BigDecimal standardVatRate;

    @NotNull(message = "EU VAT member is required")
    private Boolean isEuVatMember;

    @NotNull(message = "EU member is required")
    private Boolean isEuMember;

    @NotNull(message = "EEA member is required")
    private Boolean isEeaMember;

    @NotNull(message = "Schengen member is required")
    private Boolean isSchengenMember;

    private UUID defaultCurrencyId;

    private UUID defaultLanguageId;
}
