import { Outlet } from "react-router-dom";

import { SystemSidebar } from "./SystemSidebar";
import { SystemTopbar } from "./SystemTopbar";

export function SystemShell() {
    return (
        <div className="flex h-dvh min-h-0 overflow-hidden bg-pos-background text-pos-ink">
            <SystemSidebar />
            <div className="flex min-w-0 flex-1 flex-col">
                <SystemTopbar />
                <main className="min-h-0 flex-1 overflow-y-auto px-4 py-4 sm:px-5 lg:px-6 lg:py-5 xl:px-8 xl:py-6">
                    <Outlet />
                </main>
            </div>
        </div>
    );
}
