package github.luckygc.am.module.archive.record.search;

import github.luckygc.am.common.search.FullTextSearchProvider;

class PostgreSqlArchiveRecordFullTextSearchProvider implements FullTextSearchProvider {

    @Override
    public String provider() {
        return "postgresql";
    }
}
