import { ChevronDown, ChevronUp, Leaf, MapPin } from "lucide-react";
import { NavLink, useLocation } from "react-router-dom";
import { useState } from "react";

import { WorkspaceSwitcher } from "../../shared/WorkspaceSwitcher";
import { systemNavigationItems } from "./system-navigation";

export function SystemSidebar() {
    const location = useLocation();
    const activeGroup =
        systemNavigationItems.find((item) =>
            item.children?.some(
                (child) =>
                    location.pathname === child.to ||
                    location.pathname.startsWith(`${child.to}/`)
            )
        )?.label ?? null;
    const [openGroup, setOpenGroup] = useState<string | null>(
        activeGroup
    );

    return (
        <aside className="flex h-dvh min-h-0 w-[210px] shrink-0 flex-col overflow-hidden bg-[radial-gradient(circle_at_42%_12%,rgba(53,141,98,0.36),transparent_35%),linear-gradient(180deg,#0a402d_0%,#06351f_100%)] px-3 py-3 text-white shadow-[inset_-1px_0_0_rgba(255,255,255,0.08)] lg:w-[230px] lg:px-4 xl:w-[260px]">
            <div className="mb-2 flex shrink-0 items-center gap-3 px-2">
                <div
                    className="grid size-12 shrink-0 place-items-center rounded-2xl border border-pos-gold/90 text-pos-gold shadow-[0_0_20px_rgba(191,138,45,0.18)]"
                    aria-hidden="true"
                >
                    <Leaf size={29} strokeWidth={1.8} />
                </div>
                <div className="min-w-0 text-left">
                    <strong className="block truncate text-base leading-tight tracking-[0.06em] text-pos-gold drop-shadow-sm">
                        HƯƠNG VÂN TRÀ
                    </strong>
                    <span className="block truncate text-[11px] uppercase tracking-[0.12em] text-pos-gold-light">
                        Premium tea house
                    </span>
                </div>
            </div>

            <nav
                className="min-h-0 flex-1 overflow-hidden"
                aria-label="Điều hướng quản lý hệ thống"
            >
                <ul className="grid gap-0.5">
                    {systemNavigationItems.map((item) => {
                        const Icon = item.icon;
                        const childActive = item.children?.some(
                            (child) =>
                                location.pathname === child.to ||
                                location.pathname.startsWith(
                                    `${child.to}/`
                                )
                        );
                        const isOpen =
                            openGroup === item.label ||
                            (openGroup === null && childActive);

                        return (
                            <li key={item.label}>
                                <button
                                    type="button"
                                    onClick={() =>
                                        item.children
                                            ? setOpenGroup((current) =>
                                                  current === item.label
                                                      ? null
                                                      : item.label
                                              )
                                            : undefined
                                    }
                                    className={`flex h-9 w-full items-center gap-3 rounded-lg px-2.5 text-left text-sm font-semibold ${
                                        childActive
                                            ? "bg-white/12 text-white"
                                            : "text-white/86 hover:bg-white/8"
                                    }`}
                                >
                                    <Icon
                                        size={20}
                                        strokeWidth={1.9}
                                        aria-hidden="true"
                                    />
                                    <span className="min-w-0 flex-1 truncate">
                                        {item.label}
                                    </span>
                                    {item.badge ? (
                                        <span className="grid min-w-5 place-items-center rounded-full bg-emerald-500 px-1 text-[10px] font-bold text-white">
                                            {item.badge}
                                        </span>
                                    ) : null}
                                    {item.children ? (
                                        isOpen ? (
                                            <ChevronUp
                                                size={16}
                                                aria-hidden="true"
                                            />
                                        ) : (
                                            <ChevronDown
                                                size={16}
                                                aria-hidden="true"
                                            />
                                        )
                                    ) : null}
                                </button>

                                {item.children && isOpen ? (
                                    <ul className="my-0.5 grid gap-0.5 rounded-lg border border-white/14 bg-black/12 p-0.5">
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
                                                                `flex h-7 items-center gap-2.5 rounded-md px-2.5 text-[13px] font-bold transition ${isActive
                                                                    ? "bg-white/16 text-white shadow-[inset_2px_0_0_#d6a443]"
                                                                    : "text-white/78 hover:bg-white/10 hover:text-white"
                                                                }`
                                                            }
                                                        >
                                                            <ChildIcon
                                                                size={17}
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

            <div className="mt-2 grid shrink-0 gap-1.5 border-t border-white/10 pt-2">
                <button
                    type="button"
                    className="flex h-9 items-center gap-2.5 rounded-lg border border-white/12 bg-white/5 px-2.5 text-left text-[13px] font-semibold text-white/88 hover:bg-white/10"
                >
                    <MapPin size={17} aria-hidden="true" />
                    <span className="min-w-0 flex-1 truncate">
                        Chi nhánh trung tâm
                    </span>
                    <ChevronDown size={14} aria-hidden="true" />
                </button>

                <div className="[&_button]:rounded-lg [&_button]:text-[13px]">
                    <WorkspaceSwitcher compact />
                </div>

                <div className="flex items-center gap-2 px-2 text-[13px] font-semibold text-white/88">
                    <span
                        className="size-2 rounded-full bg-emerald-400"
                        aria-hidden="true"
                    />
                    Trực tuyến
                </div>
            </div>
        </aside>
    );
}
