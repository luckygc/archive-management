import { createHashRouter } from "react-router";

import { MobileSessionGate } from "@/shared/authentication/MobileSessionGate";
import { HomePage } from "@/pages/home/HomePage";
import { LoginPage } from "@/pages/login/LoginPage";
import { PlaceholderPage } from "@/pages/home/PlaceholderPage";

export function createMobileRouter() {
    return createHashRouter([
        {
            path: "/login",
            element: <LoginPage />,
        },
        {
            path: "/",
            element: <MobileSessionGate />,
            children: [
                {
                    index: true,
                    element: <HomePage />,
                },
                {
                    path: "approval/tasks",
                    element: <PlaceholderPage title="我的待办" />,
                },
                {
                    path: "intake",
                    element: <PlaceholderPage title="归档接收" />,
                },
                {
                    path: "archive/search",
                    element: <PlaceholderPage title="档案查询" />,
                },
            ],
        },
    ]);
}
