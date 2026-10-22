package com.phonghub.adapter.out.persistence.jpa;

import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import jakarta.persistence.EntityManagerFactory;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Hibernate trên DataSource dùng chung với các repository JDBC. Flyway là nguồn schema duy nhất: Hibernate chỉ
 * kiểm tra (validate) ánh xạ entity khớp bảng, không tự tạo/sửa bảng. Transaction manager (JpaTransactionManager)
 * do cấu hình persistence khai báo.
 *
 * <p>Không gắn @Configuration để component scan không nạp ở profile in-memory; chỉ vào context qua @Import của
 * cấu hình Postgres (prod/docker).
 */
@EnableTransactionManagement
@EnableJpaRepositories(basePackageClasses = JpaPersistenceConfiguration.class)
public class JpaPersistenceConfiguration {

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource, Environment environment) {
        // Thuộc tính Hibernate bổ sung theo quy ước spring.jpa.properties.*; mặc định chỉ validate schema.
        Map<String, String> properties = new HashMap<>(Binder.get(environment)
            .bind("spring.jpa.properties", Bindable.mapOf(String.class, String.class))
            .orElse(Map.of()));
        properties.putIfAbsent("hibernate.hbm2ddl.auto", "validate");

        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan(JpaPersistenceConfiguration.class.getPackageName());
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(properties);
        return factory;
    }

    @Bean
    public PropertyRepositoryPort propertyRepository(PropertyJpaRepository properties) {
        return new JpaPropertyRepository(properties);
    }

    @Bean
    public RoomRepositoryPort roomRepository(
        RoomJpaRepository rooms,
        PropertyJpaRepository properties,
        EntityManagerFactory entityManagerFactory
    ) {
        return new JpaRoomRepository(rooms, properties,
            SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory));
    }

    @Bean
    public ContractRepositoryPort contractRepository(
        ContractJpaRepository contracts,
        PropertyJpaRepository properties,
        RoomJpaRepository rooms,
        EntityManagerFactory entityManagerFactory
    ) {
        return new JpaContractRepository(contracts, properties, rooms,
            SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory));
    }
}
