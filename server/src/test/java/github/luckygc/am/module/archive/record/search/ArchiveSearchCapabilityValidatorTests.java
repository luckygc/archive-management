package github.luckygc.am.module.archive.record.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.common.search.ArchiveSearchConfigurationException;
import github.luckygc.am.common.search.FullTextSearchAdapter;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;

@DisplayName("档案全文检索能力校验")
class ArchiveSearchCapabilityValidatorTests {

    private final ArchiveMapper archiveMapper = mock(ArchiveMapper.class);

    @Test
    @DisplayName("默认搜索配置允许后台数据库查询并禁用全文检索")
    void defaultSearchConfigurationAllowsDatabaseManagementAndDisabledFullText() {
        ArchiveSearchProperties properties = new ArchiveSearchProperties();
        ArchiveSearchCapabilityValidator validator =
                new ArchiveSearchCapabilityValidator(properties, archiveMapper, List.of());

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
        verify(archiveMapper, never()).databaseFullTextSearchAvailable();
    }

    @Test
    @DisplayName("PostgreSQL 全文检索缺少数据库能力时拒绝启动")
    void rejectDatabaseFullTextSearchWhenDatabaseCapabilityIsMissing() {
        ArchiveSearchProperties properties = new ArchiveSearchProperties();
        properties.getFullText().setAdapter("postgresql");
        when(archiveMapper.databaseFullTextSearchAvailable()).thenReturn(false);
        ArchiveSearchCapabilityValidator validator =
                new ArchiveSearchCapabilityValidator(
                        properties,
                        archiveMapper,
                        List.of(new StubFullTextSearchAdapter("postgresql")));

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(ArchiveSearchConfigurationException.class)
                .hasMessageContaining("未检测到 pg_trgm 扩展或全文检索索引")
                .satisfies(
                        exception ->
                                assertThat(action(exception))
                                        .contains("archive.search.full-text.adapter"));
    }

    @Test
    @DisplayName("PostgreSQL 全文检索数据库能力存在时允许启动")
    void allowDatabaseFullTextSearchWhenDatabaseCapabilityExists() {
        ArchiveSearchProperties properties = new ArchiveSearchProperties();
        properties.getFullText().setAdapter("postgresql");
        when(archiveMapper.databaseFullTextSearchAvailable()).thenReturn(true);
        ArchiveSearchCapabilityValidator validator =
                new ArchiveSearchCapabilityValidator(
                        properties,
                        archiveMapper,
                        List.of(new StubFullTextSearchAdapter("postgresql")));

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("全文检索 adapter 为空时拒绝启动")
    void rejectMissingFullTextSearchAdapter() {
        ArchiveSearchProperties properties = new ArchiveSearchProperties();
        properties.getFullText().setAdapter(null);
        ArchiveSearchCapabilityValidator validator =
                new ArchiveSearchCapabilityValidator(properties, archiveMapper, List.of());

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(ArchiveSearchConfigurationException.class)
                .hasMessageContaining("全文检索 adapter 不能为空")
                .satisfies(
                        exception -> assertThat(action(exception)).contains("disabled、postgresql"));
    }

    @Test
    @DisplayName("全文检索结果上限必须为正数")
    void rejectNonPositiveResultLimit() {
        ArchiveSearchProperties properties = new ArchiveSearchProperties();
        properties.getFullText().setResultLimit(0);
        ArchiveSearchCapabilityValidator validator =
                new ArchiveSearchCapabilityValidator(properties, archiveMapper, List.of());

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
                new ArchiveSearchCapabilityValidator(properties, archiveMapper, List.of());

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
                        properties,
                        archiveMapper,
                        List.of(new StubFullTextSearchAdapter("custom-search")));

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("禁用全文检索时不要求注册 adapter")
    void disabledFullTextSearchDoesNotRequireRegisteredAdapter() {
        ArchiveSearchProperties properties = new ArchiveSearchProperties();
        ArchiveSearchCapabilityValidator validator =
                new ArchiveSearchCapabilityValidator(properties, archiveMapper, List.of());

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    private static String action(Throwable exception) {
        return ((ArchiveSearchConfigurationException) exception).action();
    }

    private record StubFullTextSearchAdapter(String adapter) implements FullTextSearchAdapter {}
}
