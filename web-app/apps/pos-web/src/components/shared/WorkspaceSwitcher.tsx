import {
    useEffect,
    useRef,
    useState
} from "react";
import {
    useLocation,
    useNavigate
} from "react-router-dom";

import { authService } from "../../auth/auth-service";
import { routePaths } from "../../routes/path";
import { UserMenu, type WorkspaceKey } from "./UserMenu";
import { UserProfile } from "./UserProfile";

function getWorkspaceFromPathname(
    pathname: string
): WorkspaceKey {
    if (pathname.startsWith("/system")) {
        return "system";
    }

    return "pos";
}

export function WorkspaceSwitcher({
    compact = false
}: {
    compact?: boolean;
}) {
    const [openPathname, setOpenPathname] =
        useState<string | null>(null);
    const containerRef = useRef<HTMLDivElement>(null);
    const location = useLocation();
    const navigate = useNavigate();
    const activeWorkspace = getWorkspaceFromPathname(
        location.pathname
    );
    const open = openPathname === location.pathname;

    useEffect(() => {
        if (!open) {
            return undefined;
        }

        function handlePointerDown(event: PointerEvent) {
            const target = event.target;

            if (
                target instanceof Node &&
                !containerRef.current?.contains(target)
            ) {
                setOpenPathname(null);
            }
        }

        function handleKeyDown(event: KeyboardEvent) {
            if (event.key === "Escape") {
                setOpenPathname(null);
            }
        }

        document.addEventListener(
            "pointerdown",
            handlePointerDown
        );
        document.addEventListener("keydown", handleKeyDown);

        return () => {
            document.removeEventListener(
                "pointerdown",
                handlePointerDown
            );
            document.removeEventListener(
                "keydown",
                handleKeyDown
            );
        };
    }, [open]);

    function selectWorkspace(workspace: WorkspaceKey) {
        navigate(
            workspace === "system"
                ? routePaths.customers
                : routePaths.posSales
        );
        setOpenPathname(null);
    }

    function logout() {
        setOpenPathname(null);
        void authService.logout();
    }

    return (
        <div
            ref={containerRef}
            className="relative"
        >
            {open ? (
                <UserMenu
                    activeWorkspace={activeWorkspace}
                    onSelectWorkspace={selectWorkspace}
                    onLogout={logout}
                />
            ) : null}

            <UserProfile
                expanded={open}
                compact={compact}
                onToggle={() =>
                    setOpenPathname((current) =>
                        current === location.pathname
                            ? null
                            : location.pathname
                    )
                }
            />
        </div>
    );
}
