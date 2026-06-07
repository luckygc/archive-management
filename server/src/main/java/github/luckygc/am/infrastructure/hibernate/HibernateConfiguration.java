package github.luckygc.am.infrastructure.hibernate;

import javax.sql.DataSource;

import org.hibernate.SessionFactory;
import org.hibernate.StatelessSession;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.PostgreSQLDialect;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.orm.jpa.hibernate.SpringSessionContext;

import github.luckygc.am.module.auth.AuthRole;

@Configuration
public class HibernateConfiguration {

    @Bean
    SessionFactory hibernateSessionFactory(DataSource dataSource) {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting(AvailableSettings.JAKARTA_NON_JTA_DATASOURCE, dataSource)
                .applySetting(AvailableSettings.DIALECT, PostgreSQLDialect.class)
                .applySetting(AvailableSettings.HBM2DDL_AUTO, "none")
                .build();

        return new MetadataSources(registry)
                .addAnnotatedClass(AuthRole.class)
                .buildMetadata()
                .buildSessionFactory();
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    StatelessSession hibernateStatelessSession(SessionFactory sessionFactory) {
        return SpringSessionContext.currentStatelessSession(sessionFactory);
    }
}
