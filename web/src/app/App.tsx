import { RouterProvider } from "react-router";
import { useState } from "react";

import { AppProviders } from "./AppProviders";
import { createAppRouter } from "./routes";

import "./styles.css";

export function App() {
    const [router] = useState(() => createAppRouter());

    return (
        <AppProviders>
            <RouterProvider router={router} />
        </AppProviders>
    );
}
