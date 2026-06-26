package github.luckygc.am.module.archive.record.search;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;

import github.luckygc.am.common.search.ArchiveSearchConfigurationException;
import github.luckygc.am.common.search.FullTextSearchAdapter;

class ArchiveSearchCapabilityValidator implements InitializingBean {

    private final ArchiveSearchProperties properties;

    private final List<FullTextSearchAdapter> fullTextSearchAdapters;

    ArchiveSearchCapabilityValidator(
            ArchiveSearchProperties properties,
            List<FullTextSearchAdapter> fullTextSearchAdapters) {
        this.properties = properties;
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
            adapter = "postgresql";
            properties.getFullText().setAdapter(adapter);
        }
        if (!hasFullTextAdapter(adapter)) {
            throw new ArchiveSearchConfigurationException(
                    "已启用全文检索，但当前应用未注册 adapter: " + adapter,
                    "增加实现该 adapter 的全文检索基础设施 Bean，或将 archive.search.full-text.adapter 改为已注册的 adapter 名称");
        }
    }

    private boolean hasFullTextAdapter(String adapter) {
        return fullTextSearchAdapters.stream().anyMatch(client -> client.adapter().equals(adapter));
    }

    private String normalize(String adapter) {
        return StringUtils.defaultString(adapter).trim();
    }
}
