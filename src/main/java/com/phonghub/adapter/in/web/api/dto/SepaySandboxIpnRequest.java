package com.phonghub.adapter.in.web.api.dto;

import com.phonghub.application.port.in.SepayWebhookUseCase.SandboxIpnCommand;
import com.phonghub.application.port.in.SepayWebhookUseCase.SandboxOrder;
import com.phonghub.application.port.in.SepayWebhookUseCase.SandboxTransaction;
import com.fasterxml.jackson.annotation.JsonProperty;

public record SepaySandboxIpnRequest(
    @JsonProperty("notification_type") String notificationType,
    Order order,
    Transaction transaction
) {
    public record Order(
        @JsonProperty("order_status") String orderStatus,
        @JsonProperty("order_currency") String orderCurrency,
        @JsonProperty("order_amount") String orderAmount,
        @JsonProperty("order_invoice_number") String orderInvoiceNumber
    ) {}

    public record Transaction(
        @JsonProperty("transaction_type") String transactionType,
        @JsonProperty("transaction_status") String transactionStatus,
        @JsonProperty("transaction_currency") String transactionCurrency,
        @JsonProperty("id") String id,
        @JsonProperty("transaction_amount") String transactionAmount
    ) {}

    public SandboxIpnCommand toCommand() {
        return new SandboxIpnCommand(
            notificationType,
            order == null ? null : new SandboxOrder(
                order.orderStatus(), order.orderCurrency(), order.orderAmount(), order.orderInvoiceNumber()
            ),
            transaction == null ? null : new SandboxTransaction(
                transaction.transactionType(), transaction.transactionStatus(), transaction.transactionCurrency(),
                transaction.id(), transaction.transactionAmount()
            )
        );
    }
}
