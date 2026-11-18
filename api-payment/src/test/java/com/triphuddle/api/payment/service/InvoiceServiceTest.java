package com.triphuddle.api.payment.service;

import com.triphuddle.api.payment.dto.RefundRequest;
import com.triphuddle.api.payment.entity.BillingSubscription;
import com.triphuddle.api.payment.entity.PaymentInvoice;
import com.triphuddle.api.payment.repository.BillingSubscriptionRepository;
import com.triphuddle.api.payment.repository.PaymentInvoiceRepository;
import com.triphuddle.api.payment.repository.PaymentRefundRepository;
import com.triphuddle.common.exception.ResourceNotFoundException;
import com.triphuddle.stripe.invoice.StripeInvoiceClient;
import com.triphuddle.stripe.refund.StripeRefund;
import com.triphuddle.stripe.refund.StripeRefundClient;
import com.triphuddle.stripe.subscription.StripeSubscriptionClient;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class InvoiceServiceTest {

    private final PaymentInvoiceRepository invoices = mock(PaymentInvoiceRepository.class);
    private final PaymentRefundRepository refunds = mock(PaymentRefundRepository.class);
    private final BillingSubscriptionRepository subscriptions = mock(BillingSubscriptionRepository.class);
    private final StripeInvoiceClient stripeInvoices = mock(StripeInvoiceClient.class);
    private final StripeRefundClient stripeRefunds = mock(StripeRefundClient.class);
    private final StripeSubscriptionClient stripeSubscriptions = mock(StripeSubscriptionClient.class);
    private final InvoiceService service = new InvoiceService(invoices, refunds, subscriptions, stripeInvoices,
            stripeRefunds, stripeSubscriptions);

    private final BillingSubscription subscription = PaymentFixtures.active(UUID.randomUUID(), "sub_1");

    @Test
    void refusesAnInvoiceWithNothingToRefundOrAnAmountOutOfRange() {
        PaymentInvoice unpaid = PaymentFixtures.invoice(subscription, 999);
        unpaid.setStripePaymentIntentId(null);
        PaymentInvoice paid = PaymentFixtures.invoice(subscription, 999);
        given(invoices.lockById(unpaid.getId())).willReturn(Optional.of(unpaid));
        given(invoices.lockById(paid.getId())).willReturn(Optional.of(paid));

        assertThatThrownBy(() -> service.refund(unpaid.getId(), new RefundRequest(), UUID.randomUUID()))
                .hasMessageContaining("nothing to refund");
        RefundRequest zero = new RefundRequest();
        zero.setAmountMinor(0L);
        assertThatThrownBy(() -> service.refund(paid.getId(), zero, UUID.randomUUID()))
                .hasMessageContaining("between 1 and 999");
        assertThatThrownBy(() -> service.refund(UUID.randomUUID(), new RefundRequest(), UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
        then(stripeRefunds).shouldHaveNoInteractions();
    }

    @Test
    void aStripeRefundIsMatchedByItsInvoiceIdOrByThePaymentAndOtherwiseLeftAlone() {
        PaymentInvoice invoice = PaymentFixtures.invoice(subscription, 999);
        given(invoices.lockById(invoice.getId())).willReturn(Optional.empty());
        given(invoices.lockByStripePaymentIntentId("pi_1")).willReturn(Optional.of(invoice));
        given(refunds.findByStripeRefundId(any())).willReturn(Optional.empty());
        given(refunds.findByInvoiceIdOrderByStripeCreatedAtAsc(invoice.getId())).willReturn(List.of());

        service.onRefund(refund(Map.of("invoiceId", invoice.getId().toString()), "pi_1"));
        service.onRefund(refund(Map.of(), null));

        then(refunds).should(org.mockito.Mockito.times(1)).saveAndFlush(any());
    }

    @Test
    void recordingAFreeInvoiceAsksStripeForNoPayment() {
        given(invoices.lockByStripeInvoiceId("in_free")).willReturn(Optional.empty());

        service.record(new StripeInvoice("in_free", null, "sub_1", "subscription_create", "paid", 0, 0, 0, 0, "eur",
                null, null, Instant.now(), Instant.now(), null), subscription);

        then(stripeInvoices).should(never()).paidPaymentIntentId(any());
        then(invoices).should().save(org.mockito.ArgumentMatchers.argThat(i -> "EUR".equals(i.getCurrency())));
    }

    private static StripeRefund refund(Map<String, String> metadata, String paymentIntent) {
        return new StripeRefund("re_" + UUID.randomUUID(), paymentIntent, 100, "eur", "succeeded", null, Instant.now(),
                metadata);
    }
}
