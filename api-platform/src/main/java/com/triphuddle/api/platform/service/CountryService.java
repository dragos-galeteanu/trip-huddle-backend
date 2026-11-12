package com.triphuddle.api.platform.service;

import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.amqp.platform.CountryCreatedContract;
import com.triphuddle.amqp.platform.CountryCreatedEvent;
import com.triphuddle.amqp.platform.CountryDeletedContract;
import com.triphuddle.amqp.platform.CountryDeletedEvent;
import com.triphuddle.amqp.platform.CountryDisabledContract;
import com.triphuddle.amqp.platform.CountryDisabledEvent;
import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.dto.PaginationMetaData;
import com.triphuddle.common.exception.ResourceNotFoundException;
import com.triphuddle.common.filter.PaginationFilter;
import com.triphuddle.common.service.BaseJpaService;
import com.triphuddle.api.platform.dto.CountryResponse;
import com.triphuddle.api.platform.dto.CreateCountryRequest;
import com.triphuddle.api.platform.dto.UpdateCountryRequest;
import com.triphuddle.api.platform.entity.Country;
import com.triphuddle.api.platform.mapper.CountryMapper;
import com.triphuddle.api.platform.repository.CountryRepository;
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
public class CountryService implements BaseJpaService<Country, CreateCountryRequest, UpdateCountryRequest, CountryResponse> {

    private final CountryRepository countryRepository;
    private final CountryMapper countryMapper;
    private final CurrencyService currencyService;
    private final LanguageService languageService;
    private final RabbitMQMessageProducer messageProducer;

    @Override
    public CountryResponse create(CreateCountryRequest createCountryRequest) {
        if (countryRepository.existsByAlpha2(createCountryRequest.getAlpha2())) {
            throw new IllegalStateException("Country with alpha2=%s already exists"
                    .formatted(createCountryRequest.getAlpha2()));
        }
        if (countryRepository.existsByAlpha3(createCountryRequest.getAlpha3())) {
            throw new IllegalStateException("Country with alpha3=%s already exists"
                    .formatted(createCountryRequest.getAlpha3()));
        }
        if (countryRepository.existsByNumericCode(createCountryRequest.getNumericCode())) {
            throw new IllegalStateException("Country with numericCode=%s already exists"
                    .formatted(createCountryRequest.getNumericCode()));
        }

        Country country = new Country();
        countryMapper.createEntityFromRequest(country, createCountryRequest);
        setDefaults(country, createCountryRequest.getDefaultCurrencyId(), createCountryRequest.getDefaultLanguageId());

        Country saved = countryRepository.save(country);
        log.info("Country created [id={}]", saved.getId());

        messageProducer.publish(
                new CountryCreatedEvent(saved.getId(), saved.getAlpha2()),
                "",
                CountryCreatedContract.COUNTRY_CREATED_QUEUE_NAME
        );
        log.info("Event published for country creation [countryId={}]", saved.getId());
        return countryMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Country getEntityById(UUID id) {
        return countryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Country with id=%s not found".formatted(id)));
    }

    @Override
    @Transactional(readOnly = true)
    public CountryResponse getById(UUID id) {
        return countryMapper.toResponse(getEntityById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Paginated<CountryResponse> getAll(PaginationFilter filter) {
        Pageable pageable = PageRequest.of(filter.getPage() - 1, filter.getPageSize());
        Page<Country> countryPage = countryRepository.findAll(pageable);

        PaginationMetaData paginationMetaData = new PaginationMetaData(
                countryPage.getTotalElements(),
                filter.getPageSize(),
                filter.getPage(),
                countryPage.getTotalPages()
        );

        return new Paginated<>(
                countryMapper.toResponseList(countryPage.getContent()),
                paginationMetaData
        );
    }

    @Override
    public CountryResponse update(UpdateCountryRequest updateCountryRequest) {
        Country country = getEntityById(updateCountryRequest.getId());
        UUID id = country.getId();

        if (countryRepository.existsByAlpha2AndIdNot(updateCountryRequest.getAlpha2(), id)) {
            throw new IllegalStateException("Country with alpha2=%s already exists"
                    .formatted(updateCountryRequest.getAlpha2()));
        }
        if (countryRepository.existsByAlpha3AndIdNot(updateCountryRequest.getAlpha3(), id)) {
            throw new IllegalStateException("Country with alpha3=%s already exists"
                    .formatted(updateCountryRequest.getAlpha3()));
        }
        if (countryRepository.existsByNumericCodeAndIdNot(updateCountryRequest.getNumericCode(), id)) {
            throw new IllegalStateException("Country with numericCode=%s already exists"
                    .formatted(updateCountryRequest.getNumericCode()));
        }

        countryMapper.updateEntityFromRequest(country, updateCountryRequest);
        setDefaults(country, updateCountryRequest.getDefaultCurrencyId(), updateCountryRequest.getDefaultLanguageId());

        Country saved = countryRepository.save(country);
        log.info("Country updated [id={}]", saved.getId());
        return countryMapper.toResponse(saved);
    }

    /**
     * Set the default currency and language of a country from their IDs; a null ID clears the default.
     *
     * @param country the country to set the defaults on
     * @param defaultCurrencyId the ID of the default currency, or null for none
     * @param defaultLanguageId the ID of the default language, or null for none
     * @throws ResourceNotFoundException if a given ID matches no currency or language
     */
    private void setDefaults(Country country, UUID defaultCurrencyId, UUID defaultLanguageId) {
        country.setDefaultCurrency(defaultCurrencyId == null ? null : currencyService.getEntityById(defaultCurrencyId));
        country.setDefaultLanguage(defaultLanguageId == null ? null : languageService.getEntityById(defaultLanguageId));
    }

    @Override
    public CountryResponse disable(UUID id) {
        Country country = getEntityById(id);
        country.setDisabledAt(Instant.now());

        Country saved = countryRepository.save(country);
        log.info("Country disabled [id={}]", saved.getId());

        messageProducer.publish(
                new CountryDisabledEvent(saved.getId(), saved.getAlpha2()),
                "",
                CountryDisabledContract.COUNTRY_DISABLED_QUEUE_NAME
        );
        log.info("Event published for country disabling [countryId={}]", saved.getId());
        return countryMapper.toResponse(saved);
    }

    @Override
    public CountryResponse delete(UUID id) {
        Country country = getEntityById(id);
        country.setDeletedAt(Instant.now());

        Country saved = countryRepository.save(country);
        log.info("Country deleted [id={}]", saved.getId());

        messageProducer.publish(
                new CountryDeletedEvent(saved.getId(), saved.getAlpha2()),
                "",
                CountryDeletedContract.COUNTRY_DELETED_QUEUE_NAME
        );
        log.info("Event published for country deletion [countryId={}]", saved.getId());
        return countryMapper.toResponse(saved);
    }
}
