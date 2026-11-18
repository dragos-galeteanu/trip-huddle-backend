package com.triphuddle.api.platform.service;

import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.amqp.platform.LanguageCreatedContract;
import com.triphuddle.amqp.platform.LanguageCreatedEvent;
import com.triphuddle.amqp.platform.LanguageDeletedContract;
import com.triphuddle.amqp.platform.LanguageDeletedEvent;
import com.triphuddle.amqp.platform.LanguageDisabledContract;
import com.triphuddle.amqp.platform.LanguageDisabledEvent;
import com.triphuddle.api.platform.dto.CreateLanguageRequest;
import com.triphuddle.api.platform.dto.LanguageResponse;
import com.triphuddle.api.platform.dto.UpdateLanguageRequest;
import com.triphuddle.api.platform.entity.Language;
import com.triphuddle.api.platform.fixtures.LanguageFixtures;
import com.triphuddle.api.platform.mapper.LanguageMapper;
import com.triphuddle.api.platform.repository.LanguageRepository;
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
class LanguageServiceTest {

    @Mock
    LanguageRepository languageRepository;

    @Mock
    RabbitMQMessageProducer messageProducer;

    @Spy
    LanguageMapper languageMapper = Mappers.getMapper(LanguageMapper.class);

    @InjectMocks
    LanguageService languageService;

    @Nested
    class Create {

        @Test
        void savesTheLanguageAndPublishesCreated() {
            CreateLanguageRequest request = LanguageFixtures.createRequest().build();
            given(languageRepository.saveAndFlush(any())).willAnswer(invocation -> {
                Language language = invocation.getArgument(0);
                language.setId(UUID.randomUUID());
                return language;
            });

            LanguageResponse response = languageService.create(request);

            assertThat(response.id()).isNotNull();
            assertThat(response.code()).isEqualTo(request.getCode());
            assertThat(response.name()).isEqualTo(request.getName());
            then(messageProducer).should().publishAfterCommit(new LanguageCreatedEvent(response.id(), request.getCode()),
                    "", LanguageCreatedContract.LANGUAGE_CREATED_QUEUE_NAME);
        }

        @Test
        void refusesACodeAnotherLanguageHas() {
            CreateLanguageRequest request = LanguageFixtures.createRequest().build();
            given(languageRepository.existsByCode(request.getCode())).willReturn(true);

            assertThatThrownBy(() -> languageService.create(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(request.getCode());
            then(languageRepository).should(never()).saveAndFlush(any());
            then(messageProducer).shouldHaveNoInteractions();
        }
    }

    @Nested
    class Read {

        @Test
        void findsALanguageThatIsNotDeleted() {
            Language language = givenExisting();

            assertThat(languageService.getById(language.getId()).code()).isEqualTo(language.getCode());
        }

        @Test
        void treatsAMissingOrDeletedLanguageAsNotFound() {
            UUID id = UUID.randomUUID();
            given(languageRepository.findByIdAndDeletedAtIsNull(id)).willReturn(Optional.empty());

            assertThatThrownBy(() -> languageService.getEntityById(id))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(id.toString());
        }

        @Test
        void listsTheRequestedPageOfCurrenciesNotDeleted() {
            Language language = LanguageFixtures.entity();
            given(languageRepository.findAllByDeletedAtIsNull(PageRequest.of(0, 5)))
                    .willReturn(new PageImpl<>(List.of(language), PageRequest.of(0, 5), 1));

            Paginated<LanguageResponse> page = languageService.getAll(new PaginationFilter(1, 5));

            assertThat(page.data()).extracting(LanguageResponse::code).containsExactly(language.getCode());
            assertThat(page.metaData().totalCount()).isEqualTo(1);
        }
    }

    @Nested
    class Update {

        @Test
        void appliesTheChangesAndPublishesNothing() {
            Language language = givenExisting();
            UpdateLanguageRequest request = LanguageFixtures.updateRequest(language.getId()).build();
            given(languageRepository.saveAndFlush(language)).willReturn(language);

            LanguageResponse response = languageService.update(request);

            assertThat(response.code()).isEqualTo(request.getCode());
            assertThat(response.name()).isEqualTo(request.getName());
            then(languageRepository).should().existsByCodeAndIdNot(request.getCode(), language.getId());
            then(messageProducer).shouldHaveNoInteractions();
        }

        @Test
        void refusesACodeAnotherLanguageHas() {
            Language language = givenExisting();
            UpdateLanguageRequest request = LanguageFixtures.updateRequest(language.getId()).build();
            given(languageRepository.existsByCodeAndIdNot(request.getCode(), language.getId())).willReturn(true);

            assertThatThrownBy(() -> languageService.update(request)).isInstanceOf(IllegalStateException.class);
            then(languageRepository).should(never()).saveAndFlush(any());
        }
    }

    @Nested
    class DisableAndDelete {

        @Test
        void disablingStampsDisabledAtAndPublishesDisabled() {
            Language language = givenExisting();
            given(languageRepository.saveAndFlush(language)).willReturn(language);

            assertThat(languageService.disable(language.getId()).disabledAt()).isNotNull();
            then(messageProducer).should().publishAfterCommit(new LanguageDisabledEvent(language.getId(), language.getCode()),
                    "", LanguageDisabledContract.LANGUAGE_DISABLED_QUEUE_NAME);
        }

        @Test
        void deletingStampsDeletedAtAndPublishesDeleted() {
            Language language = givenExisting();
            given(languageRepository.saveAndFlush(language)).willReturn(language);

            assertThat(languageService.delete(language.getId()).deletedAt()).isNotNull();
            then(messageProducer).should().publishAfterCommit(new LanguageDeletedEvent(language.getId(), language.getCode()),
                    "", LanguageDeletedContract.LANGUAGE_DELETED_QUEUE_NAME);
        }

        @Test
        void neitherTouchesAMissingOrDeletedLanguage() {
            UUID id = UUID.randomUUID();
            given(languageRepository.findByIdAndDeletedAtIsNull(id)).willReturn(Optional.empty());

            assertThatThrownBy(() -> languageService.disable(id)).isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> languageService.delete(id)).isInstanceOf(ResourceNotFoundException.class);
            then(languageRepository).should(never()).saveAndFlush(any());
            then(messageProducer).shouldHaveNoInteractions();
        }
    }

    private Language givenExisting() {
        Language language = LanguageFixtures.entity();
        given(languageRepository.findByIdAndDeletedAtIsNull(language.getId())).willReturn(Optional.of(language));
        return language;
    }
}
