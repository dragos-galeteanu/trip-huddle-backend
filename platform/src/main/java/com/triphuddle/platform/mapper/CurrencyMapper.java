package com.triphuddle.platform.mapper;

import com.triphuddle.common.mapper.BaseMapper;
import com.triphuddle.platform.dto.CreateCurrencyRequest;
import com.triphuddle.platform.dto.CurrencyResponse;
import com.triphuddle.platform.dto.UpdateCurrencyRequest;
import com.triphuddle.platform.entity.Currency;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CurrencyMapper extends BaseMapper<Currency, CreateCurrencyRequest, UpdateCurrencyRequest, CurrencyResponse> {

    @Override
    CurrencyResponse toResponse(Currency currency);

    @Override
    void createEntityFromRequest(@MappingTarget Currency currency, CreateCurrencyRequest request);

    @Override
    void updateEntityFromRequest(@MappingTarget Currency currency, UpdateCurrencyRequest request);
}
