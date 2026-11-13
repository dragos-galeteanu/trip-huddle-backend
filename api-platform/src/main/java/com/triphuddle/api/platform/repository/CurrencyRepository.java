package com.triphuddle.api.platform.repository;

import com.triphuddle.api.platform.entity.Currency;
import com.triphuddle.common.repository.BaseJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CurrencyRepository extends BaseJpaRepository<Currency, UUID> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID id);
}
