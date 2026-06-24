package github.luckygc.am.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = "github.luckygc.am")
@ConfigurationPropertiesScan("github.luckygc.am")
public class ArchiveManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArchiveManagementApplication.class, args);
    }
}
