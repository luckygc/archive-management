import { RouterProvider } from "react-router";
import { useState } from "react";

import { AppProviders } from "./AppProviders";
import { createMobileRouter } from "./routes";

export function App() {
    const [router] = useState(() => createMobileRouter());

    return (
        <AppProviders>
            <RouterProvider router={router} />
        </AppProviders>
    );
}
