package com.triphuddle.api.platform.service;

import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.amqp.platform.CurrencyCreatedContract;
import com.triphuddle.amqp.platform.CurrencyCreatedEvent;
import com.triphuddle.amqp.platform.CurrencyDeletedContract;
import com.triphuddle.amqp.platform.CurrencyDeletedEvent;
import com.triphuddle.amqp.platform.CurrencyDisabledContract;
import com.triphuddle.amqp.platform.CurrencyDisabledEvent;
import com.triphuddle.api.platform.dto.CreateCurrencyRequest;
import com.triphuddle.api.platform.dto.CurrencyResponse;
import com.triphuddle.api.platform.dto.UpdateCurrencyRequest;
import com.triphuddle.api.platform.entity.Currency;
import com.triphuddle.api.platform.fixtures.CurrencyFixtures;
import com.triphuddle.api.platform.mapper.CurrencyMapper;
import com.triphuddle.api.platform.repository.CurrencyRepository;
import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.exception.ResourceNotFoundException;
import com.triphuddle.common.filter.PaginationFilter;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class CurrencyServiceTest {

    @Mock
    CurrencyRepository currencyRepository;

    @Mock
    RabbitMQMessageProducer messageProducer;

    @Spy
    CurrencyMapper currencyMapper = Mappers.getMapper(CurrencyMapper.class);

    @InjectMocks
    CurrencyService currencyService;

    @Nested
    class Create {

        @Test
        void savesTheCurrencyAndPublishesCreated() {
            CreateCurrencyRequest request = CurrencyFixtures.createRequest().build();
            given(currencyRepository.saveAndFlush(any())).willAnswer(invocation -> {
                Currency currency = invocation.getArgument(0);
                currency.setId(UUID.randomUUID());
                return currency;
            });

            CurrencyResponse response = currencyService.create(request);

            assertThat(response.id()).isNotNull();
            assertThat(response.code()).isEqualTo(request.getCode());
            assertThat(response.name()).isEqualTo(request.getName());
            then(messageProducer).should().publishAfterCommit(new CurrencyCreatedEvent(response.id(), request.getCode()),
                    "", CurrencyCreatedContract.CURRENCY_CREATED_QUEUE_NAME);
        }

        @Test
        void refusesACodeAnotherCurrencyHas() {
            CreateCurrencyRequest request = CurrencyFixtures.createRequest().build();
            given(currencyRepository.existsByCode(request.getCode())).willReturn(true);

            assertThatThrownBy(() -> currencyService.create(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(request.getCode());
            then(currencyRepository).should(never()).saveAndFlush(any());
            then(messageProducer).shouldHaveNoInteractions();
        }
    }

    @Nested
    class Read {

        @Test
        void findsACurrencyThatIsNotDeleted() {
            Currency currency = givenExisting();

            assertThat(currencyService.getById(currency.getId()).code()).isEqualTo(currency.getCode());
        }

        @Test
        void treatsAMissingOrDeletedCurrencyAsNotFound() {
            UUID id = UUID.randomUUID();
            given(currencyRepository.findByIdAndDeletedAtIsNull(id)).willReturn(Optional.empty());

            assertThatThrownBy(() -> currencyService.getEntityById(id))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(id.toString());
        }

        @Test
        void listsTheRequestedPageOfCurrenciesNotDeleted() {
            Currency currency = CurrencyFixtures.entity();
            given(currencyRepository.findAllByDeletedAtIsNull(PageRequest.of(0, 5)))
                    .willReturn(new PageImpl<>(List.of(currency), PageRequest.of(0, 5), 1));

            Paginated<CurrencyResponse> page = currencyService.getAll(new PaginationFilter(1, 5));

            assertThat(page.data()).extracting(CurrencyResponse::code).containsExactly(currency.getCode());
            assertThat(page.metaData().totalCount()).isEqualTo(1);
        }
    }

    @Nested
    class Update {

        @Test
        void appliesTheChangesAndPublishesNothing() {
            Currency currency = givenExisting();
            UpdateCurrencyRequest request = CurrencyFixtures.updateRequest(currency.getId()).build();
            given(currencyRepository.saveAndFlush(currency)).willReturn(currency);

            CurrencyResponse response = currencyService.update(request);

            assertThat(response.code()).isEqualTo(request.getCode());
            assertThat(response.name()).isEqualTo(request.getName());
            then(currencyRepository).should().existsByCodeAndIdNot(request.getCode(), currency.getId());
            then(messageProducer).shouldHaveNoInteractions();
        }

        @Test
        void refusesACodeAnotherCurrencyHas() {
            Currency currency = givenExisting();
            UpdateCurrencyRequest request = CurrencyFixtures.updateRequest(currency.getId()).build();
            given(currencyRepository.existsByCodeAndIdNot(request.getCode(), currency.getId())).willReturn(true);

            assertThatThrownBy(() -> currencyService.update(request)).isInstanceOf(IllegalStateException.class);
            then(currencyRepository).should(never()).saveAndFlush(any());
        }
    }

    @Nested
    class DisableAndDelete {

        @Test
        void disablingStampsDisabledAtAndPublishesDisabled() {
            Currency currency = givenExisting();
            given(currencyRepository.saveAndFlush(currency)).willReturn(currency);

            assertThat(currencyService.disable(currency.getId()).disabledAt()).isNotNull();
            then(messageProducer).should().publishAfterCommit(new CurrencyDisabledEvent(currency.getId(), currency.getCode()),
                    "", CurrencyDisabledContract.CURRENCY_DISABLED_QUEUE_NAME);
        }

        @Test
        void deletingStampsDeletedAtAndPublishesDeleted() {
            Currency currency = givenExisting();
            given(currencyRepository.saveAndFlush(currency)).willReturn(currency);

            assertThat(currencyService.delete(currency.getId()).deletedAt()).isNotNull();
            then(messageProducer).should().publishAfterCommit(new CurrencyDeletedEvent(currency.getId(), currency.getCode()),
                    "", CurrencyDeletedContract.CURRENCY_DELETED_QUEUE_NAME);
        }

        @Test
        void neitherTouchesAMissingOrDeletedCurrency() {
            UUID id = UUID.randomUUID();
            given(currencyRepository.findByIdAndDeletedAtIsNull(id)).willReturn(Optional.empty());

            assertThatThrownBy(() -> currencyService.disable(id)).isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> currencyService.delete(id)).isInstanceOf(ResourceNotFoundException.class);
            then(currencyRepository).should(never()).saveAndFlush(any());
            then(messageProducer).shouldHaveNoInteractions();
        }
    }

    private Currency givenExisting() {
        Currency currency = CurrencyFixtures.entity();
        given(currencyRepository.findByIdAndDeletedAtIsNull(currency.getId())).willReturn(Optional.of(currency));
        return currency;
    }
}
