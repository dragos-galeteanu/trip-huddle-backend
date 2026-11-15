package com.triphuddle.api.destination.messaging;

import com.triphuddle.amqp.platform.CountryCreatedContract;
import com.triphuddle.amqp.platform.CountryCreatedEvent;
import com.triphuddle.amqp.platform.CountryDeletedContract;
import com.triphuddle.amqp.platform.CountryDeletedEvent;
import com.triphuddle.amqp.platform.CountryDisabledContract;
import com.triphuddle.amqp.platform.CountryDisabledEvent;
import com.triphuddle.api.destination.service.CopyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Follows api-platform's countries, under which places are added.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CountryEventListener {

    private final CopyService copyService;

    /**
     * Record a new country.
     *
     * @param event the event
     */
    @RabbitListener(queues = CountryCreatedContract.COUNTRY_CREATED_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onCountryCreated(CountryCreatedEvent event) {
        log.info("Received country created [alpha2={}]", event.alpha2());
        copyService.onCountryCreated(event.countryId(), event.alpha2());
    }

    /**
     * Mark a country disabled.
     *
     * @param event the event
     */
    @RabbitListener(queues = CountryDisabledContract.COUNTRY_DISABLED_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onCountryDisabled(CountryDisabledEvent event) {
        log.info("Received country disabled [alpha2={}]", event.alpha2());
        copyService.onCountryRemoved(event.countryId(), event.alpha2(), false);
    }

    /**
     * Mark a country deleted.
     *
     * @param event the event
     */
    @RabbitListener(queues = CountryDeletedContract.COUNTRY_DELETED_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onCountryDeleted(CountryDeletedEvent event) {
        log.info("Received country deleted [alpha2={}]", event.alpha2());
        copyService.onCountryRemoved(event.countryId(), event.alpha2(), true);
    }
}
