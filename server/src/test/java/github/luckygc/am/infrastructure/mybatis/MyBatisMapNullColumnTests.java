package github.luckygc.am.infrastructure.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MyBatis Map 空列处理")
class MyBatisMapNullColumnTests {

    @Test
    @DisplayName("开启 callSettersOnNulls 后 Map 结果保留 null 列 key")
    void mapResultKeepsNullColumnKeyWhenCallSettersOnNullsEnabled() {
        Configuration configuration = new Configuration();
        configuration.setCallSettersOnNulls(true);
        configuration.setReturnInstanceForEmptyRow(true);
        configuration.setEnvironment(
                new Environment("test", new JdbcTransactionFactory(), fakeDataSource()));
        configuration.addMapper(NullColumnMapper.class);
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);

        try (SqlSession session = sqlSessionFactory.openSession()) {
            Map<String, Object> row =
                    session.getMapper(NullColumnMapper.class).selectRows().getFirst();

            assertThat(row).containsEntry("id", 1);
            assertThat(row).containsKey("f_empty");
            assertThat(row.get("f_empty")).isNull();
        }
    }

    private DataSource fakeDataSource() {
        return (DataSource)
                Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[] {DataSource.class},
                        (proxy, method, args) -> {
                            if ("getConnection".equals(method.getName())) {
                                return fakeConnection();
                            }
                            return defaultValue(method.getReturnType());
                        });
    }

    private Connection fakeConnection() {
        return proxy(
                Connection.class,
                (proxy, method, args) ->
                        switch (method.getName()) {
                            case "prepareStatement" -> fakePreparedStatement();
                            case "getAutoCommit" -> true;
                            default -> defaultValue(method.getReturnType());
                        });
    }

    private PreparedStatement fakePreparedStatement() {
        return proxy(
                PreparedStatement.class,
                (proxy, method, args) ->
                        switch (method.getName()) {
                            case "execute" -> true;
                            case "getResultSet" -> fakeResultSet();
                            case "getUpdateCount" -> -1;
                            default -> defaultValue(method.getReturnType());
                        });
    }

    private ResultSet fakeResultSet() {
        return proxy(
                ResultSet.class,
                new InvocationHandler() {
                    private int cursor;
                    private boolean lastWasNull;

                    @Override
                    public Object invoke(
                            Object proxy, java.lang.reflect.Method method, Object[] args) {
                        return switch (method.getName()) {
                            case "next" -> ++cursor == 1;
                            case "getMetaData" -> fakeMetaData();
                            case "getObject" -> objectValue(args);
                            case "getInt" -> intValue(args);
                            case "getLong" -> longValue(args);
                            case "getString" -> stringValue(args);
                            case "wasNull" -> lastWasNull;
                            default -> defaultValue(method.getReturnType());
                        };
                    }

                    private Object objectValue(Object[] args) {
                        Object value = columnValue(args);
                        lastWasNull = value == null;
                        return value;
                    }

                    private int intValue(Object[] args) {
                        Object value = columnValue(args);
                        lastWasNull = value == null;
                        return value instanceof Number number ? number.intValue() : 0;
                    }

                    private long longValue(Object[] args) {
                        Object value = columnValue(args);
                        lastWasNull = value == null;
                        return value instanceof Number number ? number.longValue() : 0L;
                    }

                    private String stringValue(Object[] args) {
                        Object value = columnValue(args);
                        lastWasNull = value == null;
                        return value == null ? null : value.toString();
                    }
                });
    }

    private Object columnValue(Object[] args) {
        if (args != null && args.length > 0 && args[0] instanceof Integer columnIndex) {
            return columnIndex == 1 ? 1 : null;
        }
        if (args != null && args.length > 0 && "id".equals(args[0])) {
            return 1;
        }
        return null;
    }

    private ResultSetMetaData fakeMetaData() {
        return proxy(
                ResultSetMetaData.class,
                (proxy, method, args) ->
                        switch (method.getName()) {
                            case "getColumnCount" -> 2;
                            case "getColumnLabel", "getColumnName" ->
                                    ((Integer) args[0]) == 1 ? "id" : "f_empty";
                            case "getColumnType" -> java.sql.Types.INTEGER;
                            case "getColumnClassName" -> Integer.class.getName();
                            default -> defaultValue(method.getReturnType());
                        });
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T)
                Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {type}, handler);
    }

    private Object defaultValue(Class<?> returnType) {
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == void.class) {
            return null;
        }
        return null;
    }

    interface NullColumnMapper {
        @Select("select id, f_empty from am_archive_record_item_demo")
        List<Map<String, Object>> selectRows();
    }
}
