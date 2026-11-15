package com.triphuddle.api.destination.job;

import com.triphuddle.api.destination.visa.VisaRuleImportService;
import com.triphuddle.api.destination.service.ImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that runs waiting place and visa rule imports, one at a time per instance.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImportJobRunner {

    private final ImportService importService;
    private final VisaRuleImportService visaRuleImportService;

    /**
     * Run waiting imports until none is left.
     */
    @Scheduled(fixedDelayString = "${app.destination.import.interval-ms:30000}",
            initialDelayString = "${app.destination.import.interval-ms:30000}")
    public void runWaiting() {
        try {
            while (importService.runNext() != null) {
                log.info("Import run finished, looking for the next one");
            }
        } catch (RuntimeException e) {
            log.error("Import runner failed", e);
        }
        try {
            visaRuleImportService.runNext();
        } catch (RuntimeException e) {
            log.error("Visa rule import runner failed", e);
        }
    }
}
