package com.triphuddle.platform.service;

import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.amqp.platform.LanguageCreatedContract;
import com.triphuddle.amqp.platform.LanguageCreatedEvent;
import com.triphuddle.amqp.platform.LanguageDeletedContract;
import com.triphuddle.amqp.platform.LanguageDeletedEvent;
import com.triphuddle.amqp.platform.LanguageDisabledContract;
import com.triphuddle.amqp.platform.LanguageDisabledEvent;
import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.dto.PaginationMetaData;
import com.triphuddle.common.exception.ResourceNotFoundException;
import com.triphuddle.common.filter.PaginationFilter;
import com.triphuddle.common.service.BaseJpaService;
import com.triphuddle.platform.dto.CreateLanguageRequest;
import com.triphuddle.platform.dto.LanguageResponse;
import com.triphuddle.platform.dto.UpdateLanguageRequest;
import com.triphuddle.platform.entity.Language;
import com.triphuddle.platform.mapper.LanguageMapper;
import com.triphuddle.platform.repository.LanguageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class LanguageService implements BaseJpaService<Language, CreateLanguageRequest, UpdateLanguageRequest, LanguageResponse> {

    private final LanguageRepository languageRepository;
    private final LanguageMapper languageMapper;
    private final RabbitMQMessageProducer messageProducer;

    @Override
    public LanguageResponse create(CreateLanguageRequest createLanguageRequest) {
        String code = createLanguageRequest.getCode();

        if (languageRepository.existsByCode(code)) {
            throw new IllegalStateException("Language with code=%s already exists".formatted(code));
        }

        Language language = new Language();
        languageMapper.createEntityFromRequest(language, createLanguageRequest);

        Language saved = languageRepository.save(language);
        log.info("Language created [id={}]", saved.getId());

        messageProducer.publish(
                new LanguageCreatedEvent(saved.getId(), saved.getCode()),
                "",
                LanguageCreatedContract.LANGUAGE_CREATED_QUEUE_NAME
        );
        log.info("Event published for language creation [languageId={}]", saved.getId());
        return languageMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Language getEntityById(UUID id) {
        return languageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Language with id=%s not found".formatted(id)));
    }

    @Override
    @Transactional(readOnly = true)
    public LanguageResponse getById(UUID id) {
        return languageMapper.toResponse(getEntityById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Paginated<LanguageResponse> getAll(PaginationFilter filter) {
        Pageable pageable = PageRequest.of(filter.getPage() - 1, filter.getPageSize());
        Page<Language> languagePage = languageRepository.findAll(pageable);

        PaginationMetaData paginationMetaData = new PaginationMetaData(
                languagePage.getTotalElements(),
                filter.getPageSize(),
                filter.getPage(),
                languagePage.getTotalPages()
        );

        return new Paginated<>(
                languageMapper.toResponseList(languagePage.getContent()),
                paginationMetaData
        );
    }

    @Override
    public LanguageResponse update(UpdateLanguageRequest updateLanguageRequest) {
        Language language = getEntityById(updateLanguageRequest.getId());
        String code = updateLanguageRequest.getCode();

        if (languageRepository.existsByCodeAndIdNot(code, language.getId())) {
            throw new IllegalStateException("Language with code=%s already exists".formatted(code));
        }

        languageMapper.updateEntityFromRequest(language, updateLanguageRequest);

        Language saved = languageRepository.save(language);
        log.info("Language updated [id={}]", saved.getId());
        return languageMapper.toResponse(saved);
    }

    @Override
    public LanguageResponse disable(UUID id) {
        Language language = getEntityById(id);
        language.setDisabledAt(Instant.now());

        Language saved = languageRepository.save(language);
        log.info("Language disabled [id={}]", saved.getId());

        messageProducer.publish(
                new LanguageDisabledEvent(saved.getId(), saved.getCode()),
                "",
                LanguageDisabledContract.LANGUAGE_DISABLED_QUEUE_NAME
        );
        log.info("Event published for language disabling [languageId={}]", saved.getId());
        return languageMapper.toResponse(saved);
    }

    @Override
    public LanguageResponse delete(UUID id) {
        Language language = getEntityById(id);
        language.setDeletedAt(Instant.now());

        Language saved = languageRepository.save(language);
        log.info("Language deleted [id={}]", saved.getId());

        messageProducer.publish(
                new LanguageDeletedEvent(saved.getId(), saved.getCode()),
                "",
                LanguageDeletedContract.LANGUAGE_DELETED_QUEUE_NAME
        );
        log.info("Event published for language deletion [languageId={}]", saved.getId());
        return languageMapper.toResponse(saved);
    }
}
