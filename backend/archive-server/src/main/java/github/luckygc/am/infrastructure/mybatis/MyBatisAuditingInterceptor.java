package github.luckygc.am.infrastructure.mybatis;

import java.util.Map;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Component;

import github.luckygc.am.infrastructure.audit.AuditContextProvider;

@Component
@Intercepts({
    @Signature(
            type = Executor.class,
            method = "update",
            args = {MappedStatement.class, Object.class}),
    @Signature(
            type = Executor.class,
            method = "query",
            args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
    @Signature(
            type = Executor.class,
            method = "query",
            args = {
                MappedStatement.class,
                Object.class,
                RowBounds.class,
                ResultHandler.class,
                CacheKey.class,
                BoundSql.class
            })
})
public class MyBatisAuditingInterceptor implements Interceptor {

    private static final String AUDIT_PARAMETER = "_audit";

    private final AuditContextProvider auditContextProvider;

    public MyBatisAuditingInterceptor(AuditContextProvider auditContextProvider) {
        this.auditContextProvider = auditContextProvider;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object parameter = invocation.getArgs()[1];
        if (parameter instanceof Map<?, ?> parameters) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> mutableParameters = (Map<Object, Object>) parameters;
            mutableParameters.put(AUDIT_PARAMETER, auditContextProvider.current());
        }
        return invocation.proceed();
    }
}
