package com.phonghub.config;

import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;

/**
 * Xóa cache (#88) sau mỗi transaction ghi commit thành công: hợp đồng, hóa đơn, thanh toán, bảo trì, bàn giao, job
 * định kỳ... đều đi qua transaction nên dữ liệu đổi ở đâu cache cũng không trả bản cũ. Transaction chỉ đọc bỏ qua.
 */
public class CacheEvictingTransactionListener implements TransactionExecutionListener {

    private final PhongHubCaches caches;

    public CacheEvictingTransactionListener(PhongHubCaches caches) {
        this.caches = caches;
    }

    @Override
    public void afterCommit(TransactionExecution transaction, Throwable commitFailure) {
        if (commitFailure == null && transaction.isNewTransaction() && !transaction.isReadOnly()) {
            caches.evictAll();
        }
    }
}
