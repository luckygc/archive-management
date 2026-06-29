package github.luckygc.am.module.archive.item.search;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;

import github.luckygc.am.common.search.ArchiveSearchConfigurationException;
import github.luckygc.am.common.search.FullTextSearchProvider;

class ArchiveSearchCapabilityValidator implements InitializingBean {

    private final ArchiveSearchProperties properties;

    private final List<FullTextSearchProvider> fullTextSearchProviders;

    ArchiveSearchCapabilityValidator(
            ArchiveSearchProperties properties,
            List<FullTextSearchProvider> fullTextSearchProviders) {
        this.properties = properties;
        this.fullTextSearchProviders = List.copyOf(fullTextSearchProviders);
    }

    @Override
    public void afterPropertiesSet() {
        validateResultLimit();
        validateFullTextSearchProvider();
    }

    private void validateResultLimit() {
        if (properties.getFullText().getResultLimit() > 0) {
            return;
        }
        throw new ArchiveSearchConfigurationException(
                "全文检索 result-limit 必须大于 0", "将 archive.search.full-text.result-limit 设置为大于 0 的整数");
    }

    private void validateFullTextSearchProvider() {
        String provider = normalize(properties.getFullText().getProvider());
        if (StringUtils.isBlank(provider)) {
            provider = "postgresql";
            properties.getFullText().setProvider(provider);
        }
        if (!hasFullTextProvider(provider)) {
            throw new ArchiveSearchConfigurationException(
                    "已启用全文检索，但当前应用未注册 provider: " + provider,
                    "增加实现该 provider 的全文检索基础设施 Bean，或将 archive.search.full-text.provider 改为已注册的 provider 名称");
        }
    }

    private boolean hasFullTextProvider(String provider) {
        return fullTextSearchProviders.stream()
                .anyMatch(client -> client.provider().equals(provider));
    }

    private String normalize(String provider) {
        return StringUtils.defaultString(provider).trim();
    }
}
