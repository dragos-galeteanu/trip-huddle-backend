package com.triphuddle.api.platform.repository;

import com.triphuddle.api.platform.entity.Language;
import com.triphuddle.common.repository.BaseJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Data access for {@link Language}.
 */
@Repository
public interface LanguageRepository extends BaseJpaRepository<Language, UUID> {

    /**
     * Check if a language has the given code.
     *
     * @param code the code to look for
     * @return true if a language has the code, false otherwise
     */
    boolean existsByCode(String code);

    /**
     * Check if another language has the given code.
     *
     * @param code the code to look for
     * @param id the ID of the language to leave out
     * @return true if another language has the code, false otherwise
     */
    boolean existsByCodeAndIdNot(String code, UUID id);
}
