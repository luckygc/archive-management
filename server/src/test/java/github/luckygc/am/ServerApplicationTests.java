package github.luckygc.am;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
                + "org.springframework.boot.session.jdbc.autoconfigure.JdbcSessionAutoConfiguration,"
                + "org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatAutoConfiguration"
})
class ServerApplicationTests {

    @Test
    void contextLoads() {
    }

}
