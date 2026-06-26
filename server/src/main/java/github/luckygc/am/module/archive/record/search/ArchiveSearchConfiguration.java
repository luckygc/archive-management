package github.luckygc.am.module.archive.record.search;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import github.luckygc.am.common.search.FullTextSearchAdapter;

@Configuration
class ArchiveSearchConfiguration {

    @Bean
    FullTextSearchAdapter postgreSqlArchiveRecordFullTextSearchAdapter() {
        return new PostgreSqlArchiveRecordFullTextSearchAdapter();
    }

    @Bean
    ArchiveSearchCapabilityValidator archiveSearchCapabilityValidator(
            ArchiveSearchProperties properties,
            List<FullTextSearchAdapter> fullTextSearchAdapters) {
        return new ArchiveSearchCapabilityValidator(properties, fullTextSearchAdapters);
    }
}
