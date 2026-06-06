package github.luckygc.am;

import github.luckygc.am.common.bootstrap.RuntimeDirectoryInitializer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ArchiveManagementApplication {

    static void main(String[] args) {
        if (RuntimeDirectoryInitializer.initializeIfRequested(args)) {
            return;
        }
        SpringApplication.run(ArchiveManagementApplication.class, args);
    }
}
