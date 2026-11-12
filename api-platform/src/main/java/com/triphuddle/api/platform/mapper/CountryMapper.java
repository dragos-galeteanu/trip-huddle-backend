package com.triphuddle.api.platform.mapper;

import com.triphuddle.common.mapper.BaseMapper;
import com.triphuddle.api.platform.dto.CreateCountryRequest;
import com.triphuddle.api.platform.dto.CountryResponse;
import com.triphuddle.api.platform.dto.UpdateCountryRequest;
import com.triphuddle.api.platform.entity.Country;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CountryMapper extends BaseMapper<Country, CreateCountryRequest, UpdateCountryRequest, CountryResponse> {

    @Override
    @Mapping(target = "defaultCurrencyId", source = "defaultCurrency.id")
    @Mapping(target = "defaultLanguageId", source = "defaultLanguage.id")
    @Mapping(target = "isEuVatMember", source = "euVatMember")
    @Mapping(target = "isEuMember", source = "euMember")
    @Mapping(target = "isEeaMember", source = "eeaMember")
    @Mapping(target = "isSchengenMember", source = "schengenMember")
    CountryResponse toResponse(Country country);

    @Override
    @Mapping(target = "defaultCurrency", ignore = true)
    @Mapping(target = "defaultLanguage", ignore = true)
    @Mapping(target = "euVatMember", source = "isEuVatMember")
    @Mapping(target = "euMember", source = "isEuMember")
    @Mapping(target = "eeaMember", source = "isEeaMember")
    @Mapping(target = "schengenMember", source = "isSchengenMember")
    void createEntityFromRequest(@MappingTarget Country country, CreateCountryRequest request);

    @Override
    @Mapping(target = "defaultCurrency", ignore = true)
    @Mapping(target = "defaultLanguage", ignore = true)
    @Mapping(target = "euVatMember", source = "isEuVatMember")
    @Mapping(target = "euMember", source = "isEuMember")
    @Mapping(target = "eeaMember", source = "isEeaMember")
    @Mapping(target = "schengenMember", source = "isSchengenMember")
    void updateEntityFromRequest(@MappingTarget Country country, UpdateCountryRequest request);
}
