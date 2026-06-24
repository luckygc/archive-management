package github.luckygc.am.common.runtime;

/** 运行时锁 adapter 的注册接口。 */
public interface RuntimeLockAdapter extends RuntimeLockManager {

    /** 返回稳定的 adapter 名称，例如 {@code database} 或 {@code local}。 */
    String adapter();
}
