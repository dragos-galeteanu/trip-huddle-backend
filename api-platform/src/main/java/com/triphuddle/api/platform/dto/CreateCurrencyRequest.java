package com.triphuddle.api.platform.dto;

import com.triphuddle.api.platform.entity.SymbolPosition;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for creating a currency.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateCurrencyRequest {

    @NotBlank(message = "Code is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Code must be a three-letter uppercase ISO 4217 code")
    private String code;

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Symbol is required")
    @Size(max = 8, message = "Symbol must be at most 8 characters")
    private String symbol;

    @NotNull(message = "Symbol position is required")
    private SymbolPosition symbolPosition;

    @NotNull(message = "Minor units is required")
    @Min(value = 0, message = "Minor units must be at least 0")
    private Integer minorUnits;
}
