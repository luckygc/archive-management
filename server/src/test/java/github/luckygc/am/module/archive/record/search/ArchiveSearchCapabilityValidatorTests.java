package github.luckygc.am.module.archive.record.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.common.search.ArchiveSearchConfigurationException;
import github.luckygc.am.common.search.FullTextSearchAdapter;

@DisplayName("档案全文检索能力校验")
class ArchiveSearchCapabilityValidatorTests {

    @Test
    @DisplayName("默认搜索配置使用 PostgreSQL 全文检索 adapter")
    void defaultSearchConfigurationUsesPostgreSqlFullTextAdapter() {
        ArchiveSearchProperties properties = new ArchiveSearchProperties();
        ArchiveSearchCapabilityValidator validator =
                new ArchiveSearchCapabilityValidator(
                        properties, List.of(new StubFullTextSearchAdapter("postgresql")));

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("全文检索 adapter 为空时使用默认 PostgreSQL adapter")
    void blankFullTextSearchAdapterUsesDefaultPostgreSqlAdapter() {
        ArchiveSearchProperties properties = new ArchiveSearchProperties();
        properties.getFullText().setAdapter(null);
        ArchiveSearchCapabilityValidator validator =
                new ArchiveSearchCapabilityValidator(
                        properties, List.of(new StubFullTextSearchAdapter("postgresql")));

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
        assertThat(properties.getFullText().getAdapter()).isEqualTo("postgresql");
    }

    @Test
    @DisplayName("全文检索结果上限必须为正数")
    void rejectNonPositiveResultLimit() {
        ArchiveSearchProperties properties = new ArchiveSearchProperties();
        properties.getFullText().setResultLimit(0);
        ArchiveSearchCapabilityValidator validator =
                new ArchiveSearchCapabilityValidator(
                        properties, List.of(new StubFullTextSearchAdapter("postgresql")));

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(ArchiveSearchConfigurationException.class)
                .hasMessageContaining("result-limit 必须大于 0")
                .satisfies(
                        exception ->
                                assertThat(action(exception))
                                        .contains("archive.search.full-text.result-limit"));
    }

    @Test
    @DisplayName("全文检索 adapter Bean 未注册时拒绝启动")
    void rejectFullTextSearchWhenAdapterBeanIsNotRegistered() {
        ArchiveSearchProperties properties = new ArchiveSearchProperties();
        properties.getFullText().setAdapter("custom-search");
        ArchiveSearchCapabilityValidator validator =
                new ArchiveSearchCapabilityValidator(properties, List.of());

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(ArchiveSearchConfigurationException.class)
                .hasMessageContaining("未注册 adapter: custom-search")
                .satisfies(exception -> assertThat(action(exception)).contains("增加实现该 adapter"));
    }

    @Test
    @DisplayName("全文检索 adapter Bean 已注册时允许启动")
    void allowFullTextSearchWhenAdapterBeanIsRegistered() {
        ArchiveSearchProperties properties = new ArchiveSearchProperties();
        properties.getFullText().setAdapter("custom-search");
        ArchiveSearchCapabilityValidator validator =
                new ArchiveSearchCapabilityValidator(
                        properties, List.of(new StubFullTextSearchAdapter("custom-search")));

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    private static String action(Throwable exception) {
        return ((ArchiveSearchConfigurationException) exception).action();
    }

    private record StubFullTextSearchAdapter(String adapter) implements FullTextSearchAdapter {}
}
