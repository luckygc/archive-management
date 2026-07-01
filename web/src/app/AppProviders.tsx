import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ConfigProvider } from "antd";
import zhCN from "antd/locale/zh_CN";
import type { ReactNode } from "react";

const queryClient = new QueryClient({
    defaultOptions: {
        queries: {
            gcTime: 0,
            refetchOnWindowFocus: false,
            retry: 1,
            staleTime: 0,
        },
        mutations: {
            gcTime: 0,
        },
    },
});

export function AppProviders({ children }: { children: ReactNode }) {
    return (
        <QueryClientProvider client={queryClient}>
            <ConfigProvider
                locale={zhCN}
                theme={{
                    cssVar: {
                        key: "am-react",
                    },
                    token: {
                        borderRadius: 6,
                        colorPrimary: "#1f5eff",
                        fontFamily:
                            'Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
                    },
                    components: {
                        Layout: {
                            bodyBg: "#f5f7fb",
                            headerBg: "#ffffff",
                            siderBg: "#ffffff",
                        },
                    },
                }}
            >
                {children}
            </ConfigProvider>
        </QueryClientProvider>
    );
}
