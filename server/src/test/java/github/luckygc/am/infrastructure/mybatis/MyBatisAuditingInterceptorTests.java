package github.luckygc.am.infrastructure.mybatis;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.stereotype.Component;

import github.luckygc.am.infrastructure.audit.AuditContext;
import github.luckygc.am.infrastructure.audit.AuditContextProvider;

@ExtendWith(MockitoExtension.class)
@DisplayName("MyBatis 统一审计参数插件")
class MyBatisAuditingInterceptorTests {

    private static final String AUDIT_PARAMETER = "_audit";
    private static final String INTERCEPTOR_CLASS_NAME =
            "github.luckygc.am.infrastructure.mybatis.MyBatisAuditingInterceptor";
    private static final AuditContext AUDIT_CONTEXT =
            new AuditContext(LocalDateTime.of(2026, 7, 16, 11, 30), 42L);
    private static final Method UPDATE_METHOD =
            executorMethod("update", MappedStatement.class, Object.class);
    private static final Method QUERY_METHOD =
            executorMethod(
                    "query",
                    MappedStatement.class,
                    Object.class,
                    RowBounds.class,
                    ResultHandler.class);
    private static final Method QUERY_WITH_BOUND_SQL_METHOD =
            executorMethod(
                    "query",
                    MappedStatement.class,
                    Object.class,
                    RowBounds.class,
                    ResultHandler.class,
                    CacheKey.class,
                    BoundSql.class);

    @Mock private AuditContextProvider auditContextProvider;

    @InjectMocks private MyBatisAuditingInterceptor interceptor;

    @Test
    @DisplayName("声明 Spring 组件、构造器依赖与三个精确 Executor 签名")
    void declaresComponentConstructorAndExactExecutorSignatures() {
        Class<?> interceptorClass = assertDoesNotThrow(() -> Class.forName(INTERCEPTOR_CLASS_NAME));

        assertTrue(Interceptor.class.isAssignableFrom(interceptorClass));
        assertNotNull(interceptorClass.getAnnotation(Component.class));
        assertDoesNotThrow(
                () -> interceptorClass.getDeclaredConstructor(AuditContextProvider.class));

        Intercepts intercepts = interceptorClass.getAnnotation(Intercepts.class);
        assertNotNull(intercepts);
        Set<SignatureContract> actual =
                Arrays.stream(intercepts.value())
                        .map(SignatureContract::from)
                        .collect(Collectors.toUnmodifiableSet());

        assertEquals(
                Set.of(
                        new SignatureContract(
                                Executor.class, "update", MappedStatement.class, Object.class),
                        new SignatureContract(
                                Executor.class,
                                "query",
                                MappedStatement.class,
                                Object.class,
                                RowBounds.class,
                                ResultHandler.class),
                        new SignatureContract(
                                Executor.class,
                                "query",
                                MappedStatement.class,
                                Object.class,
                                RowBounds.class,
                                ResultHandler.class,
                                CacheKey.class,
                                BoundSql.class)),
                actual);
    }

    @Test
    @DisplayName("update 在执行前用一次当前上下文覆盖伪造审计参数并原样返回结果")
    void updateInjectsAuditContextBeforeProceedAndReturnsResult() throws Throwable {
        Executor executor = mock(Executor.class);
        MappedStatement mappedStatement = mock(MappedStatement.class);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(AUDIT_PARAMETER, "forged");
        AtomicReference<Object> auditAtProceed = new AtomicReference<>();
        AtomicReference<Object> parameterAtProceed = new AtomicReference<>();
        when(auditContextProvider.current()).thenReturn(AUDIT_CONTEXT);
        when(executor.update(same(mappedStatement), same(parameters)))
                .thenAnswer(
                        called -> {
                            parameterAtProceed.set(called.getArgument(1));
                            auditAtProceed.set(parameters.get(AUDIT_PARAMETER));
                            return 7;
                        });

        Object result =
                interceptor.intercept(
                        new Invocation(
                                executor,
                                UPDATE_METHOD,
                                new Object[] {mappedStatement, parameters}));

        assertEquals(7, result);
        assertSame(parameters, parameterAtProceed.get());
        assertSame(AUDIT_CONTEXT, auditAtProceed.get());
        assertSame(AUDIT_CONTEXT, parameters.get(AUDIT_PARAMETER));
        verify(auditContextProvider).current();
        verify(mappedStatement, never()).getBoundSql(any());
    }

    @Test
    @DisplayName("四参数 query 在执行前注入同一个审计上下文并原样返回结果")
    void fourArgumentQueryInjectsAuditContextBeforeProceedAndReturnsResult() throws Throwable {
        Executor executor = mock(Executor.class);
        MappedStatement mappedStatement = mock(MappedStatement.class);
        Map<String, Object> parameters = new HashMap<>();
        ResultHandler<Object> resultHandler = ignored -> {};
        List<String> expected = List.of("result");
        AtomicReference<Object> auditAtProceed = new AtomicReference<>();
        when(auditContextProvider.current()).thenReturn(AUDIT_CONTEXT);
        when(executor.query(
                        same(mappedStatement),
                        same(parameters),
                        same(RowBounds.DEFAULT),
                        same(resultHandler)))
                .thenAnswer(
                        called -> {
                            auditAtProceed.set(parameters.get(AUDIT_PARAMETER));
                            return expected;
                        });

        Object result =
                interceptor.intercept(
                        new Invocation(
                                executor,
                                QUERY_METHOD,
                                new Object[] {
                                    mappedStatement, parameters, RowBounds.DEFAULT, resultHandler
                                }));

        assertSame(expected, result);
        assertSame(AUDIT_CONTEXT, auditAtProceed.get());
        assertSame(AUDIT_CONTEXT, parameters.get(AUDIT_PARAMETER));
        verify(auditContextProvider).current();
        verify(mappedStatement, never()).getBoundSql(any());
    }

    @Test
    @DisplayName("六参数 query 只修改原参数 Map 且保持原 BoundSql 与 SQL")
    void sixArgumentQueryKeepsOriginalBoundSqlAndSql() throws Throwable {
        Executor executor = mock(Executor.class);
        MappedStatement mappedStatement = mock(MappedStatement.class);
        Map<String, Object> parameters = new HashMap<>();
        ResultHandler<Object> resultHandler = ignored -> {};
        CacheKey cacheKey = new CacheKey();
        BoundSql boundSql =
                new BoundSql(new Configuration(), "select * from am_demo", List.of(), parameters);
        List<String> expected = List.of("result");
        AtomicReference<Object> parameterAtProceed = new AtomicReference<>();
        AtomicReference<Object> auditAtProceed = new AtomicReference<>();
        AtomicReference<BoundSql> boundSqlAtProceed = new AtomicReference<>();
        when(auditContextProvider.current()).thenReturn(AUDIT_CONTEXT);
        when(executor.query(
                        same(mappedStatement),
                        same(parameters),
                        same(RowBounds.DEFAULT),
                        same(resultHandler),
                        same(cacheKey),
                        same(boundSql)))
                .thenAnswer(
                        called -> {
                            parameterAtProceed.set(called.getArgument(1));
                            auditAtProceed.set(parameters.get(AUDIT_PARAMETER));
                            boundSqlAtProceed.set(called.getArgument(5));
                            return expected;
                        });

        Object result =
                interceptor.intercept(
                        new Invocation(
                                executor,
                                QUERY_WITH_BOUND_SQL_METHOD,
                                new Object[] {
                                    mappedStatement,
                                    parameters,
                                    RowBounds.DEFAULT,
                                    resultHandler,
                                    cacheKey,
                                    boundSql
                                }));

        assertSame(expected, result);
        assertSame(parameters, parameterAtProceed.get());
        assertSame(AUDIT_CONTEXT, auditAtProceed.get());
        assertSame(boundSql, boundSqlAtProceed.get());
        assertSame(parameters, boundSql.getParameterObject());
        assertEquals("select * from am_demo", boundSql.getSql());
        verify(auditContextProvider).current();
        verify(mappedStatement, never()).getBoundSql(any());
    }

    @Test
    @DisplayName("null 参数不读取上下文并继续执行")
    void nullParameterSkipsAuditContextAndStillProceeds() throws Throwable {
        Executor executor = mock(Executor.class);
        MappedStatement mappedStatement = mock(MappedStatement.class);
        when(executor.update(same(mappedStatement), isNull())).thenReturn(11);

        Object result =
                interceptor.intercept(
                        new Invocation(
                                executor, UPDATE_METHOD, new Object[] {mappedStatement, null}));

        assertEquals(11, result);
        verifyNoInteractions(auditContextProvider);
    }

    @Test
    @DisplayName("非 Map 参数不读取上下文也不替换参数并继续执行")
    void nonMapParameterSkipsAuditContextAndStillProceeds() throws Throwable {
        Executor executor = mock(Executor.class);
        MappedStatement mappedStatement = mock(MappedStatement.class);
        Object parameter = new Object();
        AtomicReference<Object> parameterAtProceed = new AtomicReference<>();
        when(executor.update(same(mappedStatement), same(parameter)))
                .thenAnswer(
                        called -> {
                            parameterAtProceed.set(called.getArgument(1));
                            return 13;
                        });

        Object result =
                interceptor.intercept(
                        new Invocation(
                                executor,
                                UPDATE_METHOD,
                                new Object[] {mappedStatement, parameter}));

        assertEquals(13, result);
        assertSame(parameter, parameterAtProceed.get());
        verifyNoInteractions(auditContextProvider);
    }

    @Test
    @DisplayName("不可变 Map 保留 put 的明确异常且不执行 Executor")
    void immutableMapKeepsPutFailureAndDoesNotProceed() {
        Executor executor = mock(Executor.class);
        MappedStatement mappedStatement = mock(MappedStatement.class);
        Map<String, Object> parameters = Map.of("name", "demo");
        when(auditContextProvider.current()).thenReturn(AUDIT_CONTEXT);
        Invocation invocation =
                new Invocation(executor, UPDATE_METHOD, new Object[] {mappedStatement, parameters});

        assertThrows(UnsupportedOperationException.class, () -> interceptor.intercept(invocation));

        verify(auditContextProvider).current();
        verifyNoInteractions(executor);
    }

    private static Method executorMethod(String name, Class<?>... parameterTypes) {
        return assertDoesNotThrow(() -> Executor.class.getMethod(name, parameterTypes));
    }

    private record SignatureContract(Class<?> type, String method, List<Class<?>> args) {

        private SignatureContract(Class<?> type, String method, Class<?>... args) {
            this(type, method, List.of(args));
        }

        private static SignatureContract from(Signature signature) {
            return new SignatureContract(signature.type(), signature.method(), signature.args());
        }
    }
}
