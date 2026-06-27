import { ConfigProvider } from "antd-mobile";
import zhCN from "antd-mobile/es/locales/zh-CN";
import type { ReactNode } from "react";

export function AppProviders({ children }: { children: ReactNode }) {
    return <ConfigProvider locale={zhCN}>{children}</ConfigProvider>;
}
