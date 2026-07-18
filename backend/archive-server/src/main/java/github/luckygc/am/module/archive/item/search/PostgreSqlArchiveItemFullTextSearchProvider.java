package github.luckygc.am.module.archive.item.search;

import github.luckygc.am.common.search.FullTextSearchProvider;

class PostgreSqlArchiveItemFullTextSearchProvider implements FullTextSearchProvider {

    @Override
    public String provider() {
        return "postgresql";
    }
}
