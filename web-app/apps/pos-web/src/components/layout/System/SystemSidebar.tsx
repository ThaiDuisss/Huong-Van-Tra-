import { ChevronDown, Leaf, MapPin } from "lucide-react";
import { NavLink, useLocation } from "react-router-dom";

import { WorkspaceSwitcher } from "../../shared/WorkspaceSwitcher";
import { systemNavigationItems } from "./system-navigation";

export function SystemSidebar() {
    const location = useLocation();

    return (
        <aside className="flex h-dvh min-h-0 w-[260px] shrink-0 flex-col bg-[radial-gradient(circle_at_42%_12%,rgba(53,141,98,0.36),transparent_35%),linear-gradient(180deg,#0a402d_0%,#06351f_100%)] px-4 py-6 text-white shadow-[inset_-1px_0_0_rgba(255,255,255,0.08)]">
            <div className="mb-8 grid place-items-center px-2 text-center">
                <div
                    className="mb-3 grid size-16 place-items-center rounded-[26px] border border-pos-gold/90 text-pos-gold shadow-[0_0_28px_rgba(191,138,45,0.22)]"
                    aria-hidden="true"
                >
                    <Leaf size={36} strokeWidth={1.8} />
                </div>
                <strong className="text-[17px] leading-tight tracking-[0.08em] text-pos-gold drop-shadow-sm">
                    HƯƠNG VÂN TRÀ
                </strong>
                <span className="mt-1 text-[10px] uppercase tracking-[0.16em] text-pos-gold-light">
                    Premium tea house
                </span>
            </div>

            <nav
                className="min-h-0 flex-1 overflow-y-auto pr-1"
                aria-label="Điều hướng quản lý hệ thống"
            >
                <ul className="grid gap-1.5">
                    {systemNavigationItems.map((item) => {
                        const Icon = item.icon;
                        const childActive = item.children?.some(
                            (child) =>
                                location.pathname === child.to ||
                                location.pathname.startsWith(
                                    `${child.to}/`
                                )
                        );

                        return (
                            <li key={item.label}>
                                <div
                                    className={`flex min-h-11 items-center gap-3 rounded-xl px-3 text-sm font-semibold ${
                                        childActive
                                            ? "bg-white/12 text-white"
                                            : "text-white/86"
                                    }`}
                                >
                                    <Icon
                                        size={21}
                                        strokeWidth={1.9}
                                        aria-hidden="true"
                                    />
                                    <span className="min-w-0 flex-1 truncate">
                                        {item.label}
                                    </span>
                                    {item.badge ? (
                                        <span className="grid min-w-6 place-items-center rounded-full bg-emerald-500 px-1.5 text-xs font-bold text-white">
                                            {item.badge}
                                        </span>
                                    ) : null}
                                    {item.children ? (
                                        <ChevronDown
                                            size={16}
                                            aria-hidden="true"
                                        />
                                    ) : null}
                                </div>

                                {item.children ? (
                                    <ul className="mt-1 grid gap-1 rounded-xl border border-white/14 bg-black/12 p-1">
                                        {item.children.map(
                                            (child) => {
                                                const ChildIcon =
                                                    child.icon;

                                                return (
                                                    <li
                                                        key={
                                                            child.to
                                                        }
                                                    >
                                                        <NavLink
                                                            to={
                                                                child.to
                                                            }
                                                            className={({
                                                                isActive
                                                            }) =>
                                                                `flex min-h-10 items-center gap-3 rounded-lg px-3 text-xs font-bold transition ${
                                                                    isActive
                                                                        ? "bg-white/16 text-white shadow-[inset_3px_0_0_#d6a443]"
                                                                        : "text-white/78 hover:bg-white/10 hover:text-white"
                                                                }`
                                                            }
                                                        >
                                                            <ChildIcon
                                                                size={
                                                                    17
                                                                }
                                                                aria-hidden="true"
                                                            />
                                                            {
                                                                child.label
                                                            }
                                                        </NavLink>
                                                    </li>
                                                );
                                            }
                                        )}
                                    </ul>
                                ) : null}
                            </li>
                        );
                    })}
                </ul>
            </nav>

            <div className="mt-5 grid gap-5">
                <button
                    type="button"
                    className="flex min-h-11 items-center gap-3 rounded-xl border border-white/12 bg-white/5 px-3 text-left text-xs font-semibold text-white/88 hover:bg-white/10"
                >
                    <MapPin size={17} aria-hidden="true" />
                    <span className="min-w-0 flex-1 truncate">
                        Chi nhánh trung tâm
                    </span>
                    <ChevronDown size={15} aria-hidden="true" />
                </button>

                <WorkspaceSwitcher />

                <div className="flex items-center gap-2 text-sm font-semibold text-white/88">
                    <span
                        className="size-3 rounded-full bg-emerald-400"
                        aria-hidden="true"
                    />
                    Trực tuyến
                </div>
            </div>
        </aside>
    );
}
