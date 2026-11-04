import { QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";

import { NotificationProvider } from "../features/notification/context/NotificationContext";
import { ToastContainer } from "../components/ui/Toast";
import { queryClient } from "./query-client";

interface AppProvidersProps {
    children: ReactNode;
}

export function AppProviders({
    children
}: AppProvidersProps) {
    return (
        <QueryClientProvider client={queryClient}>
            <NotificationProvider>
                {children}
                <ToastContainer />
            </NotificationProvider>
        </QueryClientProvider>
    );
}
