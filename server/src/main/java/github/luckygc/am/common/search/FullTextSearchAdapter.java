package github.luckygc.am.common.search;

/** 普通用户发现型全文检索 adapter 的最小合同。 */
public interface FullTextSearchAdapter {

    /** 返回稳定的 adapter 名称，例如 {@code postgresql}。 */
    String adapter();
}
