package github.luckygc.am.common.search;

/** 普通用户发现型全文检索 provider 的最小合同。 */
public interface FullTextSearchProvider {

    /** 返回稳定的 provider 名称，例如 {@code postgresql}。 */
    String provider();
}
