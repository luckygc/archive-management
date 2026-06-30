import { Button, List, NavBar, Space } from "antd-mobile";
import { useSessionStore } from "@archive-management/frontend-core/authentication";
import { useNavigate } from "react-router";

const entries = [
    { title: "我的待办", description: "审批任务和处理入口", path: "/approval/tasks" },
    { title: "归档接收", description: "外部数据接入和处理入口", path: "/intake" },
    { title: "档案查询", description: "移动端轻量检索入口", path: "/archive/search" },
];

export function HomePage() {
    const navigate = useNavigate();
    const currentUser = useSessionStore((state) => state.currentUser);
    const logoutCurrentUser = useSessionStore((state) => state.logoutCurrentUser);

    async function logout() {
        await logoutCurrentUser();
        await navigate("/login", { replace: true });
    }

    return (
        <main className="am-mobile-page">
            <NavBar
                back={null}
                right={
                    <Button fill="none" size="small" onClick={() => void logout()}>
                        退出
                    </Button>
                }
            >
                移动门户
            </NavBar>
            <section className="am-mobile-content">
                <Space block direction="vertical">
                    <List header={currentUser?.displayName ?? currentUser?.username}>
                        {entries.map((entry) => (
                            <List.Item
                                clickable
                                description={entry.description}
                                key={entry.path}
                                onClick={() => void navigate(entry.path)}
                            >
                                {entry.title}
                            </List.Item>
                        ))}
                    </List>
                    <div className="am-mobile-summary" aria-label="移动门户概览">
                        <div className="am-mobile-summary__item">
                            <span className="am-mobile-summary__value">0</span>
                            <span className="am-mobile-summary__label">待办</span>
                        </div>
                        <div className="am-mobile-summary__item">
                            <span className="am-mobile-summary__value">0</span>
                            <span className="am-mobile-summary__label">接收任务</span>
                        </div>
                        <div className="am-mobile-summary__item">
                            <span className="am-mobile-summary__value">0</span>
                            <span className="am-mobile-summary__label">异常</span>
                        </div>
                    </div>
                </Space>
            </section>
        </main>
    );
}
