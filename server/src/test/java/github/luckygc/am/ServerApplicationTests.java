package github.luckygc.am;

import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;

import static org.mockito.Mockito.mock;

@SpringBootTest(
        properties = {
            "spring.autoconfigure.exclude="
                    + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                    + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
                    + "org.springframework.boot.session.jdbc.autoconfigure.JdbcSessionAutoConfiguration,"
                    + "org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatAutoConfiguration"
        })
class ServerApplicationTests {

    @Test
    void contextLoads() {}

    @TestConfiguration
    static class TestJdbcConfiguration {

        @Bean
        JdbcClient jdbcClient() {
            return sql -> {
                throw new UnsupportedOperationException("空启动测试不访问数据库");
            };
        }

        @Bean
        SqlSessionFactory sqlSessionFactory() {
            SqlSessionFactory sqlSessionFactory = mock(SqlSessionFactory.class);
            Configuration configuration = new Configuration();
            configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), mock(DataSource.class)));
            org.mockito.Mockito.when(sqlSessionFactory.getConfiguration()).thenReturn(configuration);
            return sqlSessionFactory;
        }
    }
}
