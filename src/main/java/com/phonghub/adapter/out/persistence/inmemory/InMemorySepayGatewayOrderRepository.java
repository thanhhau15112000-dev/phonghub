package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.SepayGatewayOrderRepositoryPort;
import com.phonghub.domain.model.SepayGatewayOrder;
import com.phonghub.domain.model.SepayGatewayOrder.Status;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemorySepayGatewayOrderRepository implements SepayGatewayOrderRepositoryPort {

    private final Map<String, SepayGatewayOrder> orders = new ConcurrentHashMap<>();

    @Override
    public Optional<SepayGatewayOrder> findPendingByInvoiceIdForUpdate(UUID invoiceId) {
        return orders.values().stream()
            .filter(order -> order.invoiceId().equals(invoiceId) && order.status() == Status.PENDING)
            .max(Comparator.comparing(SepayGatewayOrder::createdAt));
    }

    @Override
    public Optional<SepayGatewayOrder> findByInvoiceNumberForUpdate(String orderInvoiceNumber) {
        return Optional.ofNullable(orders.get(orderInvoiceNumber));
    }

    @Override
    public Optional<SepayGatewayOrder> findByTransactionId(String transactionId) {
        if (transactionId == null || transactionId.isBlank()) {
            return Optional.empty();
        }
        return orders.values().stream().filter(order -> transactionId.equals(order.transactionId())).findFirst();
    }

    @Override
    public synchronized void savePending(SepayGatewayOrder order) {
        if (orders.containsKey(order.orderInvoiceNumber())
            || findPendingByInvoiceIdForUpdate(order.invoiceId()).isPresent()) {
            throw new IllegalStateException("A pending SePay Sandbox order already exists");
        }
        orders.put(order.orderInvoiceNumber(), order);
    }

    @Override
    public synchronized boolean markPaid(String orderInvoiceNumber, String transactionId, Instant paidAt) {
        SepayGatewayOrder order = orders.get(orderInvoiceNumber);
        if (order == null || order.status() != Status.PENDING
            || findByTransactionId(transactionId).filter(existing -> !existing.orderInvoiceNumber().equals(orderInvoiceNumber)).isPresent()) {
            return false;
        }
        orders.put(orderInvoiceNumber, new SepayGatewayOrder(
            order.orderInvoiceNumber(), order.invoiceId(), order.amount(), Status.PAID,
            transactionId, order.createdAt(), paidAt
        ));
        return true;
    }

    @Override
    public synchronized boolean expire(String orderInvoiceNumber) {
        SepayGatewayOrder order = orders.get(orderInvoiceNumber);
        if (order == null || order.status() != Status.PENDING) {
            return false;
        }
        orders.put(orderInvoiceNumber, new SepayGatewayOrder(
            order.orderInvoiceNumber(), order.invoiceId(), order.amount(), Status.EXPIRED,
            null, order.createdAt(), null
        ));
        return true;
    }
}
