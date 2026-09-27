/**
 * 档案全文检索能力的跨模块抽象。
 *
 * <p>管理列表和后台筛选继续以数据库语义为准；这里的接口只描述普通用户发现型全文检索 provider
 * 的最小合同，避免 archive 模块直接绑定 PostgreSQL、Elasticsearch 或其他检索中间件。
 */
@NullMarked
package github.luckygc.am.common.search;

import org.jspecify.annotations.NullMarked;
