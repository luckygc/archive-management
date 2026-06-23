package github.luckygc.am.test;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public abstract class PostgreSqlContainerTest {

    private static final ImageFromDockerfile POSTGRES_IMAGE =
            new ImageFromDockerfile("archive-management-postgres-pg-textsearch:18", false)
                    .withFileFromClasspath(
                            "Dockerfile", "testcontainers/postgres-pg-textsearch/Dockerfile");

    @Container
    protected static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(postgresImageName())
                    .withDatabaseName("archive_management_test")
                    .withUsername("postgres")
                    .withPassword("postgres")
                    .withCommand("postgres", "-c", "shared_preload_libraries=pg_textsearch");

    @DynamicPropertySource
    protected static void registerPostgreSqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    private static DockerImageName postgresImageName() {
        return DockerImageName.parse(POSTGRES_IMAGE.get()).asCompatibleSubstituteFor("postgres");
    }
}
