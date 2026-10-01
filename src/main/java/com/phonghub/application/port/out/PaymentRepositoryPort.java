package com.phonghub.application.port.out;

import com.phonghub.domain.model.PaymentTransaction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepositoryPort {
    PaymentTransaction save(PaymentTransaction transaction);
    Optional<PaymentTransaction> findById(UUID id);
    Optional<PaymentTransaction> findBySepayId(Long sepayId);
    boolean existsBySepayId(Long sepayId);
    List<PaymentTransaction> findAll();
}
