import type { ReactNode } from "react";

import { NotificationProvider } from "../features/notification/context/NotificationContext";

interface AppProvidersProps {
    children: ReactNode;
}

export function AppProviders({
    children
}: AppProvidersProps) {
    return (
        <NotificationProvider>{children}</NotificationProvider>
    );
}
