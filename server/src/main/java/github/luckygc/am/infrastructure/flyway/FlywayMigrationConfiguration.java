package github.luckygc.am.infrastructure.flyway;

import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayMigrationConfiguration {

    @Bean
    FlywayMigrationStrategy cleanBeforeMigrateStrategy(FlywayResetProperties properties) {
        return flyway -> {
            if (properties.isCleanBeforeMigrate()) {
                flyway.clean();
            }
            flyway.migrate();
        };
    }
}
