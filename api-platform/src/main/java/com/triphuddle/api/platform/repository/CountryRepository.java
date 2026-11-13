package com.triphuddle.api.platform.repository;

import com.triphuddle.api.platform.entity.Country;
import com.triphuddle.common.repository.BaseJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CountryRepository extends BaseJpaRepository<Country, UUID> {

    boolean existsByAlpha2(String alpha2);

    boolean existsByAlpha2AndIdNot(String alpha2, UUID id);

    boolean existsByAlpha3(String alpha3);

    boolean existsByAlpha3AndIdNot(String alpha3, UUID id);

    boolean existsByNumericCode(String numericCode);

    boolean existsByNumericCodeAndIdNot(String numericCode, UUID id);
}
