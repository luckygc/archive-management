package github.luckygc.am.infrastructure.hibernate;

import java.util.Properties;
import javax.sql.DataSource;

import jakarta.persistence.EntityAgent;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.BatchSettings;
import org.hibernate.cfg.CacheSettings;
import org.hibernate.dialect.PostgreSQLDialect;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.orm.jpa.hibernate.LocalSessionFactoryBuilder;

import github.luckygc.am.common.persistence.CosIdIdentifierGenerator;

import me.ahoo.cosid.provider.IdGeneratorProvider;

@Configuration
public class HibernateConfiguration {

    private static final int JDBC_BATCH_SIZE = 50;
    private static final String ENTITY_BASE_PACKAGE = "github.luckygc.am";

    @Bean
    SessionFactory hibernateSessionFactory(
            DataSource dataSource,
            ObjectProvider<FlywayMigrationInitializer> flywayInitializer,
            ObjectProvider<IdGeneratorProvider> idGeneratorProvider) {
        flywayInitializer.ifAvailable(initializer -> {});
        CosIdIdentifierGenerator.setIdGenerator(idGeneratorProvider.getObject().getShare());
        LocalSessionFactoryBuilder builder = new LocalSessionFactoryBuilder(dataSource);
        builder.scanPackages(ENTITY_BASE_PACKAGE);
        builder.addProperties(hibernateProperties());
        return builder.buildSessionFactory();
    }

    @Bean
    TransactionalStatelessSessionContext transactionalStatelessSessionContext(
            SessionFactory sessionFactory,
            DataSource dataSource,
            SecurityAuditingInterceptor securityAuditingInterceptor) {
        return new TransactionalStatelessSessionContext(
                sessionFactory, dataSource, securityAuditingInterceptor);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    EntityAgent entityAgent(TransactionalStatelessSessionContext context) {
        return context.currentSession();
    }

    private Properties hibernateProperties() {
        Properties properties = new Properties();
        properties.put(AvailableSettings.DIALECT, PostgreSQLDialect.class.getName());
        properties.put(AvailableSettings.HBM2DDL_AUTO, "none");
        properties.put(CacheSettings.USE_SECOND_LEVEL_CACHE, false);
        properties.put(CacheSettings.USE_QUERY_CACHE, false);
        properties.put(BatchSettings.STATEMENT_BATCH_SIZE, JDBC_BATCH_SIZE);
        return properties;
    }
}
