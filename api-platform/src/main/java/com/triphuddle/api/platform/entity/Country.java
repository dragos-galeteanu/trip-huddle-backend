package com.triphuddle.api.platform.entity;

import com.triphuddle.common.entity.BaseJpaEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * A country, identified by its ISO 3166-1 codes, with its VAT and membership facts.
 */
@Entity
@Table(name = "countries")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class Country extends BaseJpaEntity {

    @Column(name = "alpha2", nullable = false, length = 2)
    private String alpha2;

    @Column(name = "alpha3", nullable = false, length = 3)
    private String alpha3;

    @Column(name = "numeric_code", nullable = false, length = 3)
    private String numericCode;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "calling_code", length = 8)
    private String callingCode;

    @Column(name = "standard_vat_rate", precision = 5, scale = 2)
    private BigDecimal standardVatRate;

    @Column(name = "is_eu_vat_member", nullable = false)
    private boolean isEuVatMember;

    @Column(name = "is_eu_member", nullable = false)
    private boolean isEuMember;

    @Column(name = "is_eea_member", nullable = false)
    private boolean isEeaMember;

    @Column(name = "is_schengen_member", nullable = false)
    private boolean isSchengenMember;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_currency_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Currency defaultCurrency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_language_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Language defaultLanguage;
}
