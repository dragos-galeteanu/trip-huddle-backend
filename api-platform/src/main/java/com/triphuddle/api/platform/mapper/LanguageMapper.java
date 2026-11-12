package com.triphuddle.api.platform.mapper;

import com.triphuddle.common.mapper.BaseMapper;
import com.triphuddle.api.platform.dto.CreateLanguageRequest;
import com.triphuddle.api.platform.dto.LanguageResponse;
import com.triphuddle.api.platform.dto.UpdateLanguageRequest;
import com.triphuddle.api.platform.entity.Language;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface LanguageMapper extends BaseMapper<Language, CreateLanguageRequest, UpdateLanguageRequest, LanguageResponse> {

    @Override
    @Mapping(target = "isRightToLeft", source = "rightToLeft")
    LanguageResponse toResponse(Language language);

    @Override
    @Mapping(target = "rightToLeft", source = "isRightToLeft")
    void createEntityFromRequest(@MappingTarget Language language, CreateLanguageRequest request);

    @Override
    @Mapping(target = "rightToLeft", source = "isRightToLeft")
    void updateEntityFromRequest(@MappingTarget Language language, UpdateLanguageRequest request);
}
