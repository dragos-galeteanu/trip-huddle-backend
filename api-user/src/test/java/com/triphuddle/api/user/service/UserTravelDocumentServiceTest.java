package com.triphuddle.api.user.service;

import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.amqp.user.ResidencePermitKind;
import com.triphuddle.amqp.user.UserTravelDocumentsUpdatedContract;
import com.triphuddle.amqp.user.UserTravelDocumentsUpdatedEvent;
import com.triphuddle.api.user.dto.ResidencePermitRequest;
import com.triphuddle.api.user.dto.TravelDocumentsRequest;
import com.triphuddle.api.user.dto.TravelDocumentsResponse;
import com.triphuddle.api.user.entity.UserTravelDocument;
import com.triphuddle.api.user.repository.UserTravelDocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * USR-6: the rules for nationalities and residence permits.
 */
@ExtendWith(MockitoExtension.class)
class UserTravelDocumentServiceTest {

    @Mock
    UserTravelDocumentRepository repository;

    @Mock
    RabbitMQMessageProducer messageProducer;

    @InjectMocks
    UserTravelDocumentService service;

    private final UUID userId = UUID.randomUUID();
    private final List<UserTravelDocument> stored = new ArrayList<>();

    @Test
    void replacesEverythingWithUpperCasedCodesAndAnnouncesTheResult() {
        givenRepositoryStores();
        LocalDate nextYear = LocalDate.now(ZoneOffset.UTC).plusYears(1);

        TravelDocumentsResponse response = service.replace(userId, request(List.of(" ro", "md"),
                permit("de", ResidencePermitKind.TEMPORARY, nextYear)));

        then(repository).should().deleteByUserId(userId);
        assertThat(response.nationalities()).containsExactly("RO", "MD");
        assertThat(response.residencePermits()).singleElement().satisfies(p -> {
            assertThat(p.countryCode()).isEqualTo("DE");
            assertThat(p.kind()).isEqualTo(ResidencePermitKind.TEMPORARY);
            assertThat(p.validUntil()).isEqualTo(nextYear);
        });
        ArgumentCaptor<UserTravelDocumentsUpdatedEvent> event = ArgumentCaptor.forClass(
                UserTravelDocumentsUpdatedEvent.class);
        then(messageProducer).should().publishAfterCommit(event.capture(), eq(""),
                eq(UserTravelDocumentsUpdatedContract.USER_TRAVEL_DOCUMENTS_UPDATED_QUEUE_NAME));
        assertThat(event.getValue().userId()).isEqualTo(userId);
        assertThat(event.getValue().nationalities()).containsExactly("RO", "MD");
    }

    @Test
    void takesKosovoThoughItHasNoIsoCodeAndAPermitThatEndsToday() {
        givenRepositoryStores();

        TravelDocumentsResponse response = service.replace(userId, request(List.of("XK"),
                permit("AT", ResidencePermitKind.PERMANENT, LocalDate.now(ZoneOffset.UTC))));

        assertThat(response.nationalities()).containsExactly("XK");
        assertThat(response.residencePermits()).hasSize(1);
    }

    @Test
    void countsADuplicateNationalityOnce() {
        givenRepositoryStores();

        assertThat(service.replace(userId, request(List.of("RO", "ro"))).nationalities()).containsExactly("RO");
        then(repository).should(times(1)).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ZZ", "UK", "EU"})
    void refusesACodeThatIsNoCountry(String code) {
        assertThatThrownBy(() -> service.replace(userId, request(List.of(code))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Unknown country code");
        then(repository).should(never()).deleteByUserId(any());
    }

    @Test
    void refusesAPermitForACountryOfNationality() {
        assertThatThrownBy(() -> service.replace(userId, request(List.of("RO"),
                permit("ro", ResidencePermitKind.PERMANENT, null))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("needs no residence permit");
    }

    @Test
    void refusesTwoPermitsForOneCountry() {
        assertThatThrownBy(() -> service.replace(userId, request(List.of(),
                permit("DE", ResidencePermitKind.PERMANENT, null), permit("de", ResidencePermitKind.TEMPORARY, null))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("one residence permit per country");
    }

    @Test
    void refusesAnExpiredPermit() {
        assertThatThrownBy(() -> service.replace(userId, request(List.of(),
                permit("DE", ResidencePermitKind.TEMPORARY, LocalDate.now(ZoneOffset.UTC).minusDays(1)))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("expired");
        then(messageProducer).shouldHaveNoInteractions();
    }

    @Test
    void readsAndDeletesAUsersDocuments() {
        given(repository.findByUserIdOrderByTypeAscCountryCodeAsc(userId)).willReturn(List.of());
        given(repository.deleteByUserId(userId)).willReturn(3, 0);

        assertThat(service.get(userId).nationalities()).isEmpty();
        service.deleteForUser(userId);
        service.deleteForUser(userId);

        then(repository).should(times(2)).deleteByUserId(userId);
    }

    private void givenRepositoryStores() {
        given(repository.save(any())).willAnswer(invocation -> {
            stored.add(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
        given(repository.findByUserIdOrderByTypeAscCountryCodeAsc(userId)).willAnswer(invocation -> stored);
    }

    private static TravelDocumentsRequest request(List<String> nationalities, ResidencePermitRequest... permits) {
        TravelDocumentsRequest request = new TravelDocumentsRequest();
        request.setNationalities(new ArrayList<>(nationalities));
        request.setResidencePermits(new ArrayList<>(List.of(permits)));
        return request;
    }

    private static ResidencePermitRequest permit(String country, ResidencePermitKind kind, LocalDate validUntil) {
        ResidencePermitRequest permit = new ResidencePermitRequest();
        permit.setCountryCode(country);
        permit.setKind(kind);
        permit.setValidUntil(validUntil);
        return permit;
    }
}
