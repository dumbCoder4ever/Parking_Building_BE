package fpt.swp391.parkingmanagement.service;

import fpt.swp391.parkingmanagement.config.PayOSConfig;
import org.springframework.stereotype.Service;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLinkItem;
import vn.payos.model.webhooks.ConfirmWebhookResponse;
import vn.payos.model.webhooks.WebhookData;

import java.math.BigDecimal;

@Service
public class PayOSService {

    private final PayOS payOS;
    private final PayOSConfig config;

    public PayOSService(PayOS payOS, PayOSConfig config) {
        this.payOS = payOS;
        this.config = config;
    }

    /**
     * Tạo payment link PayOS.
     *
     * @param paymentId  ID payment trong DB (dùng trong description)
     * @param amount     số tiền VND (PayOS dùng số nguyên)
     * @return response chứa checkoutUrl và orderCode
     */
    public CreatePaymentLinkResponse createPaymentLink(String paymentId, BigDecimal amount) {
        long orderCode = System.currentTimeMillis() / 1000;
        long amountVnd = amount.longValue();

        CreatePaymentLinkRequest paymentData = CreatePaymentLinkRequest.builder()
                .orderCode(orderCode)
                .amount(amountVnd)
                .description("Thanh toan phi do xe: " + paymentId)
                .returnUrl(config.getReturnUrl())
                .cancelUrl(config.getCancelUrl())
                .item(PaymentLinkItem.builder()
                        .name("Phi do xe")
                        .quantity(1)
                        .price(amountVnd)
                        .build())
                .build();

        return payOS.paymentRequests().create(paymentData);
    }

    public WebhookData verifyWebhook(Object body) {
        return payOS.webhooks().verify(body);
    }

    public ConfirmWebhookResponse confirmWebhook(String webhookUrl) {
        return payOS.webhooks().confirm(webhookUrl);
    }
}
