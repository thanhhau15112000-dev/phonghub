package com.phonghub.migration;

import com.phonghub.adapter.out.persistence.jpa.JpaPersistenceConfiguration;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Dựng các repository JPA bằng đúng {@link JpaPersistenceConfiguration} của prod trên DataSource đã migrate, kèm
 * JpaTransactionManager để repository JDBC trong test ghi chung transaction như khi chạy thật.
 */
final class JpaTestContext {

    private final AnnotationConfigApplicationContext context;

    private JpaTestContext(AnnotationConfigApplicationContext context) {
        this.context = context;
    }

    static JpaTestContext over(DataSource dataSource) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, () -> dataSource);
        context.register(JpaPersistenceConfiguration.class);
        context.registerBean("transactionManager", PlatformTransactionManager.class,
            () -> new JpaTransactionManager(context.getBean(EntityManagerFactory.class)));
        context.refresh();
        context.registerShutdownHook();
        return new JpaTestContext(context);
    }

    PropertyRepositoryPort properties() {
        return context.getBean(PropertyRepositoryPort.class);
    }

    RoomRepositoryPort rooms() {
        return context.getBean(RoomRepositoryPort.class);
    }

    ContractRepositoryPort contracts() {
        return context.getBean(ContractRepositoryPort.class);
    }

    PlatformTransactionManager transactionManager() {
        return context.getBean(PlatformTransactionManager.class);
    }

    TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager());
    }
}
