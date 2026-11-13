package com.triphuddle.common.repository;

import com.triphuddle.common.entity.BaseJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.Optional;

/**
 * Adds lookups that leave out soft-deleted and/or disabled records.
 */
@NoRepositoryBean
public interface BaseJpaRepository<T extends BaseJpaEntity, ID> extends JpaRepository<T, ID> {

    Optional<T> findByIdAndDeletedAtIsNullAndDisabledAtIsNull(ID id);

    Optional<T> findByIdAndDeletedAtIsNull(ID id);

    Optional<T> findByIdAndDisabledAtIsNull(ID id);

    Page<T> findAllByDeletedAtIsNullAndDisabledAtIsNull(Pageable pageable);

    Page<T> findAllByDeletedAtIsNull(Pageable pageable);

    Page<T> findAllByDisabledAtIsNull(Pageable pageable);
}
