package github.luckygc.am.test;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

public abstract class PostgreSqlContainerTest {

    protected static final String TEST_SCHEMA = "am_test";

    @Container
    protected static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18")
                    .withDatabaseName("archive_management_test")
                    .withUsername("postgres")
                    .withPassword("postgres");

    @DynamicPropertySource
    protected static void registerPostgreSqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgreSqlContainerTest::jdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.default-schema", () -> TEST_SCHEMA);
        registry.add("spring.flyway.schemas", () -> TEST_SCHEMA);
    }

    private static String jdbcUrl() {
        return POSTGRES.getJdbcUrl() + "&currentSchema=" + TEST_SCHEMA;
    }
}
