package github.luckygc.am.module.archive.record.search;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import github.luckygc.am.common.search.FullTextSearchProvider;

@Configuration
class ArchiveSearchConfiguration {

    @Bean
    FullTextSearchProvider postgreSqlArchiveRecordFullTextSearchProvider() {
        return new PostgreSqlArchiveRecordFullTextSearchProvider();
    }

    @Bean
    ArchiveSearchCapabilityValidator archiveSearchCapabilityValidator(
            ArchiveSearchProperties properties,
            List<FullTextSearchProvider> fullTextSearchProviders) {
        return new ArchiveSearchCapabilityValidator(properties, fullTextSearchProviders);
    }
}
