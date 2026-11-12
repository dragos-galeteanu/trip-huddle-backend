package com.triphuddle.api.platform.service;

import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.amqp.platform.CurrencyCreatedContract;
import com.triphuddle.amqp.platform.CurrencyCreatedEvent;
import com.triphuddle.amqp.platform.CurrencyDeletedContract;
import com.triphuddle.amqp.platform.CurrencyDeletedEvent;
import com.triphuddle.amqp.platform.CurrencyDisabledContract;
import com.triphuddle.amqp.platform.CurrencyDisabledEvent;
import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.dto.PaginationMetaData;
import com.triphuddle.common.exception.ResourceNotFoundException;
import com.triphuddle.common.filter.PaginationFilter;
import com.triphuddle.common.service.BaseJpaService;
import com.triphuddle.api.platform.dto.CreateCurrencyRequest;
import com.triphuddle.api.platform.dto.CurrencyResponse;
import com.triphuddle.api.platform.dto.UpdateCurrencyRequest;
import com.triphuddle.api.platform.entity.Currency;
import com.triphuddle.api.platform.mapper.CurrencyMapper;
import com.triphuddle.api.platform.repository.CurrencyRepository;
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
public class CurrencyService implements BaseJpaService<Currency, CreateCurrencyRequest, UpdateCurrencyRequest, CurrencyResponse> {

    private final CurrencyRepository currencyRepository;
    private final CurrencyMapper currencyMapper;
    private final RabbitMQMessageProducer messageProducer;

    @Override
    public CurrencyResponse create(CreateCurrencyRequest createCurrencyRequest) {
        String code = createCurrencyRequest.getCode();

        if (currencyRepository.existsByCode(code)) {
            throw new IllegalStateException("Currency with code=%s already exists".formatted(code));
        }

        Currency currency = new Currency();
        currencyMapper.createEntityFromRequest(currency, createCurrencyRequest);

        Currency saved = currencyRepository.save(currency);
        log.info("Currency created [id={}]", saved.getId());

        messageProducer.publish(
                new CurrencyCreatedEvent(saved.getId(), saved.getCode()),
                "",
                CurrencyCreatedContract.CURRENCY_CREATED_QUEUE_NAME
        );
        log.info("Event published for currency creation [currencyId={}]", saved.getId());
        return currencyMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Currency getEntityById(UUID id) {
        return currencyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Currency with id=%s not found".formatted(id)));
    }

    @Override
    @Transactional(readOnly = true)
    public CurrencyResponse getById(UUID id) {
        return currencyMapper.toResponse(getEntityById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Paginated<CurrencyResponse> getAll(PaginationFilter filter) {
        Pageable pageable = PageRequest.of(filter.getPage() - 1, filter.getPageSize());
        Page<Currency> currencyPage = currencyRepository.findAll(pageable);

        PaginationMetaData paginationMetaData = new PaginationMetaData(
                currencyPage.getTotalElements(),
                filter.getPageSize(),
                filter.getPage(),
                currencyPage.getTotalPages()
        );

        return new Paginated<>(
                currencyMapper.toResponseList(currencyPage.getContent()),
                paginationMetaData
        );
    }

    @Override
    public CurrencyResponse update(UpdateCurrencyRequest updateCurrencyRequest) {
        Currency currency = getEntityById(updateCurrencyRequest.getId());
        String code = updateCurrencyRequest.getCode();

        if (currencyRepository.existsByCodeAndIdNot(code, currency.getId())) {
            throw new IllegalStateException("Currency with code=%s already exists".formatted(code));
        }

        currencyMapper.updateEntityFromRequest(currency, updateCurrencyRequest);

        Currency saved = currencyRepository.save(currency);
        log.info("Currency updated [id={}]", saved.getId());
        return currencyMapper.toResponse(saved);
    }

    @Override
    public CurrencyResponse disable(UUID id) {
        Currency currency = getEntityById(id);
        currency.setDisabledAt(Instant.now());

        Currency saved = currencyRepository.save(currency);
        log.info("Currency disabled [id={}]", saved.getId());

        messageProducer.publish(
                new CurrencyDisabledEvent(saved.getId(), saved.getCode()),
                "",
                CurrencyDisabledContract.CURRENCY_DISABLED_QUEUE_NAME
        );
        log.info("Event published for currency disabling [currencyId={}]", saved.getId());
        return currencyMapper.toResponse(saved);
    }

    @Override
    public CurrencyResponse delete(UUID id) {
        Currency currency = getEntityById(id);
        currency.setDeletedAt(Instant.now());

        Currency saved = currencyRepository.save(currency);
        log.info("Currency deleted [id={}]", saved.getId());

        messageProducer.publish(
                new CurrencyDeletedEvent(saved.getId(), saved.getCode()),
                "",
                CurrencyDeletedContract.CURRENCY_DELETED_QUEUE_NAME
        );
        log.info("Event published for currency deletion [currencyId={}]", saved.getId());
        return currencyMapper.toResponse(saved);
    }
}
