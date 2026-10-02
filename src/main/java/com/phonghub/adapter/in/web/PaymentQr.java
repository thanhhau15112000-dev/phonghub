package com.phonghub.adapter.in.web;

import com.phonghub.config.SepayProperties;
import com.phonghub.domain.model.Invoice;
import java.math.RoundingMode;
import org.springframework.web.util.UriComponentsBuilder;

/** Link ảnh QR chuyển khoản SePay cho số tiền còn nợ của một hóa đơn. */
final class PaymentQr {

    private PaymentQr() {}

    static String url(SepayProperties properties, Invoice invoice) {
        return UriComponentsBuilder.fromUriString("https://qr.sepay.vn/img")
            .queryParam("acc", properties.getBankAccountNumber().trim())
            .queryParam("bank", properties.getBankCode().trim())
            .queryParam("amount", invoice.remainingAmount().setScale(0, RoundingMode.UP).toPlainString())
            .queryParam("des", invoice.getPaymentCode())
            .encode()
            .toUriString();
    }
}
