import { shallowRef } from "vue";
import type { CapWidget } from "cap-widget";
import { describe, expect, it, vi } from "vite-plus/test";
import { useCapVerification } from "./capVerification";

describe("useCapVerification", () => {
  it("只从 solve 事件提交 token，不主动调用 widget.solve", () => {
    const widget = createWidget();
    const verification = useCapVerification(shallowRef(widget));

    verification.handleCapSolve(createEvent({ token: "event-token" }));

    expect(verification.powToken.value).toBe("event-token");
    expect(verification.securityMessage.value).toBe("安全验证已完成");
    expect(widget.solve).not.toHaveBeenCalled();
  });

  it("登录失败重置时清空 token 并重置 widget UI", () => {
    const widget = createWidget();
    const verification = useCapVerification(shallowRef(widget));
    verification.handleCapSolve(createEvent({ token: "event-token" }));

    verification.resetCapWidget("请重新完成安全验证");

    expect(verification.powToken.value).toBe("");
    expect(verification.securityMessage.value).toBe("请重新完成安全验证");
    expect(widget.reset).toHaveBeenCalledTimes(1);
    expect(widget.solve).not.toHaveBeenCalled();
  });

  it("验证错误时清空已保存 token 并展示错误", () => {
    const widget = createWidget();
    const verification = useCapVerification(shallowRef(widget));
    verification.handleCapSolve(createEvent({ token: "event-token" }));

    verification.handleCapError(createEvent({ message: "Challenge invalid or expired" }));

    expect(verification.powToken.value).toBe("");
    expect(verification.securityMessage.value).toBe("安全验证失败：Challenge invalid or expired");
    expect(widget.solve).not.toHaveBeenCalled();
  });
});

function createWidget() {
  return {
    reset: vi.fn(),
    solve: vi.fn(),
  } as unknown as CapWidget & {
    reset: ReturnType<typeof vi.fn>;
    solve: ReturnType<typeof vi.fn>;
  };
}

function createEvent(detail: unknown) {
  return { detail } as unknown as Event;
}
