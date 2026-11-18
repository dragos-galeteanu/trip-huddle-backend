package com.triphuddle.api.platform.mapper;

import com.triphuddle.common.mapper.BaseMapper;
import com.triphuddle.api.platform.dto.CreateCurrencyRequest;
import com.triphuddle.api.platform.dto.CurrencyResponse;
import com.triphuddle.api.platform.dto.UpdateCurrencyRequest;
import com.triphuddle.api.platform.entity.Currency;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Maps {@link Currency} to and from its request and response DTOs.
 */
@Mapper(componentModel = "spring")
public interface CurrencyMapper extends BaseMapper<Currency, CreateCurrencyRequest, UpdateCurrencyRequest, CurrencyResponse> {

    @Override
    CurrencyResponse toResponse(Currency currency);

    @Override
    void createEntityFromRequest(@MappingTarget Currency currency, CreateCurrencyRequest request);

    @Override
    // The entity was loaded by this ID; copying it again could only ever re-key the row.
    @Mapping(target = "id", ignore = true)
    void updateEntityFromRequest(@MappingTarget Currency currency, UpdateCurrencyRequest request);
}
