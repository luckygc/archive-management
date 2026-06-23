package github.luckygc.am.infrastructure.hibernate;

import javax.sql.DataSource;

import org.hibernate.SessionFactory;
import org.hibernate.StatelessSession;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class TransactionalStatelessSessionContext {

    private final SessionFactory sessionFactory;
    private final DataSource dataSource;
    private final SecurityAuditingInterceptor securityAuditingInterceptor;

    public TransactionalStatelessSessionContext(
            SessionFactory sessionFactory,
            DataSource dataSource,
            SecurityAuditingInterceptor securityAuditingInterceptor) {
        this.sessionFactory = sessionFactory;
        this.dataSource = dataSource;
        this.securityAuditingInterceptor = securityAuditingInterceptor;
    }

    public StatelessSession currentSession() {
        Object resource = TransactionSynchronizationManager.getResource(sessionFactory);
        if (resource instanceof StatelessSession statelessSession) {
            return statelessSession;
        }
        if (resource != null) {
            throw new IllegalStateException(
                    "SessionFactory 已绑定非 StatelessSession 资源: " + resource.getClass().getName());
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("当前线程没有激活的 Spring 事务同步，无法获取事务内 StatelessSession");
        }

        StatelessSession statelessSession =
                sessionFactory
                        .withStatelessOptions()
                        .connection(DataSourceUtils.getConnection(dataSource))
                        .interceptor(securityAuditingInterceptor)
                        .openStatelessSession();
        TransactionSynchronizationManager.bindResource(sessionFactory, statelessSession);
        TransactionSynchronizationManager.registerSynchronization(
                new StatelessSessionSynchronization(sessionFactory, statelessSession));
        return statelessSession;
    }

    private static final class StatelessSessionSynchronization
            implements TransactionSynchronization {

        private final SessionFactory sessionFactory;
        private final StatelessSession statelessSession;
        private boolean holderActive = true;

        private StatelessSessionSynchronization(
                SessionFactory sessionFactory, StatelessSession statelessSession) {
            this.sessionFactory = sessionFactory;
            this.statelessSession = statelessSession;
        }

        @Override
        public int getOrder() {
            return DataSourceUtils.CONNECTION_SYNCHRONIZATION_ORDER - 100;
        }

        @Override
        public void suspend() {
            if (holderActive) {
                TransactionSynchronizationManager.unbindResource(sessionFactory);
            }
        }

        @Override
        public void resume() {
            if (holderActive) {
                TransactionSynchronizationManager.bindResource(sessionFactory, statelessSession);
            }
        }

        @Override
        public void beforeCompletion() {
            if (holderActive) {
                TransactionSynchronizationManager.unbindResourceIfPossible(sessionFactory);
                holderActive = false;
            }
        }

        @Override
        public void afterCompletion(int status) {
            if (holderActive) {
                TransactionSynchronizationManager.unbindResourceIfPossible(sessionFactory);
                holderActive = false;
            }
            statelessSession.close();
        }
    }
}
