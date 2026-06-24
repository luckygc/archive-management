package github.luckygc.am.common.runtime;

/** 运行时队列 adapter 的注册接口。 */
public interface RuntimeQueueAdapter extends RuntimeQueue {

    /** 返回稳定的 adapter 名称，例如 {@code database}。 */
    String adapter();
}
