package github.luckygc.am.module.archive.record.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/** 档案记录搜索配置。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "archive.search")
public class ArchiveSearchProperties {

    /** 普通用户全文检索配置；后台管理查询不受该开关影响。 */
    private FullText fullText = new FullText();

    /** 普通用户全文检索配置。 */
    @Getter
    @Setter
    public static class FullText {

        /** 全文检索 provider 名称，默认使用 PostgreSQL 实现。 */
        private String provider = "postgresql";

        /** provider 内部使用的索引名或逻辑索引标识。 */
        private String indexName = "archive_records";

        /** 单次全文检索结果上限。 */
        private int resultLimit = 1000;
    }
}
