package com.triphuddle.api.destination.job;

import com.triphuddle.api.destination.content.ContentMaintenance;
import com.triphuddle.api.destination.semantic.SemanticSearchService;
import com.triphuddle.api.destination.service.ImportService;
import com.triphuddle.api.destination.social.QuestionService;
import com.triphuddle.api.destination.trip.ItineraryService;
import com.triphuddle.api.destination.visa.VisaGuidanceService;
import com.triphuddle.api.destination.visa.VisaRuleImportService;
import org.junit.jupiter.api.Test;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

/**
 * Each job runs its parts separately: one failing leaves the others to run, and the next run tries again.
 */
class DestinationJobsTest {

    @Test
    void aFailingPlaceImportStillLetsTheVisaImportRunAndAFailingVisaImportIsLogged() {
        ImportService imports = mock(ImportService.class);
        VisaRuleImportService visaImports = mock(VisaRuleImportService.class);
        given(imports.runNext()).willThrow(new IllegalStateException("database down"));
        given(visaImports.runNext()).willThrow(new IllegalStateException("database down"));

        new ImportJobRunner(imports, visaImports).runWaiting();

        then(visaImports).should().runNext();
    }

    @Test
    void failingContentMaintenanceStillLetsTheEmbeddingsRunAndAFailingEmbeddingIsLogged() {
        ContentMaintenance maintenance = mock(ContentMaintenance.class);
        SemanticSearchService semantic = mock(SemanticSearchService.class);
        given(maintenance.resendOverdue()).willThrow(new IllegalStateException("database down"));
        given(semantic.releaseOverdue()).willThrow(new IllegalStateException("database down"));

        new ContentJobs(maintenance, mock(ItineraryService.class), mock(VisaGuidanceService.class),
                mock(QuestionService.class), semantic).run();

        then(semantic).should().releaseOverdue();
    }

    @Test
    void aQuietRunDoesEachPartOnce() {
        ContentMaintenance maintenance = mock(ContentMaintenance.class);
        SemanticSearchService semantic = mock(SemanticSearchService.class);

        new ContentJobs(maintenance, mock(ItineraryService.class), mock(VisaGuidanceService.class),
                mock(QuestionService.class), semantic).run();

        then(maintenance).should().refreshDue();
        then(semantic).should().embedDue();
    }
}
