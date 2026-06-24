package github.luckygc.am.module.archive.record.search;

import github.luckygc.am.common.search.FullTextSearchAdapter;

class PostgreSqlArchiveRecordFullTextSearchAdapter implements FullTextSearchAdapter {

    @Override
    public String adapter() {
        return "postgresql";
    }
}
