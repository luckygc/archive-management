package github.luckygc.am.common.runtime;

/** 当前应用实际使用的数据库运行时能力。 */
public interface RuntimeDatabase {

    /** 返回稳定的 adapter 名称，例如 {@code postgresql}。 */
    String adapter();
}
