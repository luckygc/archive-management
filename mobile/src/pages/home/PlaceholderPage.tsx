import { ErrorBlock, NavBar } from "antd-mobile";
import { useNavigate } from "react-router";

export function PlaceholderPage({ title }: { title: string }) {
    const navigate = useNavigate();

    return (
        <main className="am-mobile-page">
            <NavBar onBack={() => void navigate(-1)}>{title}</NavBar>
            <section className="am-mobile-content">
                <ErrorBlock
                    description="入口已预留，后续按业务流程接入"
                    status="empty"
                    title={title}
                />
            </section>
        </main>
    );
}
