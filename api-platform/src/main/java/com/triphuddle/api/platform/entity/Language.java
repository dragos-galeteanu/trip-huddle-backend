package com.triphuddle.api.platform.entity;

import com.triphuddle.common.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "languages")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class Language extends BaseJpaEntity {

    @Column(name = "code", nullable = false, length = 8)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "native_name", nullable = false)
    private String nativeName;

    @Column(name = "is_right_to_left", nullable = false)
    private boolean isRightToLeft;
}
