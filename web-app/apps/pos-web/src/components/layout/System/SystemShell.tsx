import { Outlet } from "react-router-dom";

import { SystemSidebar } from "./SystemSidebar";
import { SystemTopbar } from "./SystemTopbar";

export function SystemShell() {
    return (
        <div className="flex h-dvh min-h-0 overflow-hidden bg-[#f7f8fb] text-pos-ink">
            <SystemSidebar />
            <div className="flex min-w-0 flex-1 flex-col">
                <SystemTopbar />
                <main className="min-h-0 flex-1 overflow-y-auto px-3 py-3 sm:px-4 lg:px-4 lg:py-4">
                    <Outlet />
                </main>
            </div>
        </div>
    );
}
