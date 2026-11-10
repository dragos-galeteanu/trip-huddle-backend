package com.triphuddle.platform.entity;

import com.triphuddle.common.entity.BaseJpaEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "currencies")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class Currency extends BaseJpaEntity {

    @Column(name = "code", nullable = false, length = 3)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "symbol", nullable = false, length = 8)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "symbol_position", nullable = false, length = 8)
    private SymbolPosition symbolPosition;

    @Column(name = "minor_units", nullable = false)
    private int minorUnits;
}
