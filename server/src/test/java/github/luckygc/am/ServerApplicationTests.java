package github.luckygc.am;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;

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
    }
}
