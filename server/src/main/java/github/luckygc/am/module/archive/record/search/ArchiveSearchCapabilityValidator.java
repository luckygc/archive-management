package github.luckygc.am.module.archive.record.search;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;

import github.luckygc.am.common.search.ArchiveSearchConfigurationException;
import github.luckygc.am.common.search.FullTextSearchAdapter;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;

class ArchiveSearchCapabilityValidator implements InitializingBean {

    private static final String DISABLED_FULL_TEXT_ADAPTER = "disabled";

    private static final String POSTGRESQL_FULL_TEXT_ADAPTER = "postgresql";

    private final ArchiveSearchProperties properties;

    private final ArchiveMapper archiveMapper;

    private final List<FullTextSearchAdapter> fullTextSearchAdapters;

    ArchiveSearchCapabilityValidator(
            ArchiveSearchProperties properties,
            ArchiveMapper archiveMapper,
            List<FullTextSearchAdapter> fullTextSearchAdapters) {
        this.properties = properties;
        this.archiveMapper = archiveMapper;
        this.fullTextSearchAdapters = List.copyOf(fullTextSearchAdapters);
    }

    @Override
    public void afterPropertiesSet() {
        validateResultLimit();
        validateFullTextSearchAdapter();
    }

    private void validateResultLimit() {
        if (properties.getFullText().getResultLimit() > 0) {
            return;
        }
        throw new ArchiveSearchConfigurationException(
                "全文检索 result-limit 必须大于 0", "将 archive.search.full-text.result-limit 设置为大于 0 的整数");
    }

    private void validateFullTextSearchAdapter() {
        String adapter = normalize(properties.getFullText().getAdapter());
        if (StringUtils.isBlank(adapter)) {
            throw new ArchiveSearchConfigurationException(
                    "全文检索 adapter 不能为空",
                    "将 archive.search.full-text.adapter 设置为 disabled、postgresql 或已注册的全文检索 adapter 名称");
        }
        if (DISABLED_FULL_TEXT_ADAPTER.equals(adapter)) {
            return;
        }
        if (!hasFullTextAdapter(adapter)) {
            throw new ArchiveSearchConfigurationException(
                    "已启用全文检索，但当前应用未注册 adapter: " + adapter,
                    "增加实现该 adapter 的全文检索基础设施 Bean，或将 archive.search.full-text.adapter 改为 disabled");
        }
        if (POSTGRESQL_FULL_TEXT_ADAPTER.equals(adapter)) {
            validateDatabaseFullTextSearch();
        }
    }

    private void validateDatabaseFullTextSearch() {
        if (archiveMapper.databaseFullTextSearchAvailable()) {
            return;
        }
        throw new ArchiveSearchConfigurationException(
                "已启用 PostgreSQL 全文检索，但当前数据库未检测到 pg_trgm 扩展或全文检索索引",
                "确认 pg_trgm 扩展和 idx_am_archive_record_search_trgm 已创建；否则将"
                        + " archive.search.full-text.adapter 改为 disabled");
    }

    private boolean hasFullTextAdapter(String adapter) {
        return fullTextSearchAdapters.stream().anyMatch(client -> client.adapter().equals(adapter));
    }

    private String normalize(String adapter) {
        return StringUtils.defaultString(adapter).trim();
    }
}
