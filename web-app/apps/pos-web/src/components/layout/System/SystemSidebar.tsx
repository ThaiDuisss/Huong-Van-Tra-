import { ChevronDown, MapPin } from "lucide-react";
import { NavLink, useLocation, useNavigate } from "react-router-dom";
import { useState } from "react";

import { HvtLogo } from "../../shared/HvtLogo";
import { systemNavigationItems } from "./system-navigation";
import { routePaths } from "../../../routes/path";

export function SystemSidebar() {
    const location = useLocation();
    const navigate = useNavigate();
    const activeGroup =
        systemNavigationItems.find((item) =>
            item.children?.some(
                (child) =>
                    location.pathname === child.to ||
                    location.pathname.startsWith(`${child.to}/`)
            )
        )?.label ?? null;
    const [openGroup, setOpenGroup] = useState<string | null>(
        activeGroup ?? "Khách hàng"
    );

    return (
        <aside className="flex h-dvh min-h-0 w-[178px] shrink-0 flex-col overflow-hidden border-r border-[#e8eef0] bg-white px-3 py-4 text-[#159734] shadow-[8px_0_30px_rgba(25,70,45,0.05)] xl:w-[188px]">
            <div className="mb-5 shrink-0 px-1">
                <HvtLogo compact={false} />
            </div>

            <nav
                className="min-h-0 flex-1 overflow-hidden"
                aria-label="Điều hướng quản lý hệ thống"
            >
                <ul className="grid gap-2">
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
                        const firstChildTo = item.children?.[0]?.to;

                        return (
                            <li key={item.label}>
                                <button
                                    type="button"
                                    onClick={() => {
                                        if (item.children) {
                                            setOpenGroup((current) =>
                                                current === item.label
                                                    ? null
                                                    : item.label
                                            );

                                            if (firstChildTo) {
                                                navigate(firstChildTo);
                                            }
                                        }
                                    }}
                                    className={`flex h-9 w-full items-center gap-2.5 rounded-lg px-2 text-left text-[13px] font-semibold ${
                                        childActive || item.label === activeGroup
                                            ? "bg-[#edf8ef] text-[#159734]"
                                            : "text-[#159734] hover:bg-[#f4faf5]"
                                    }`}
                                >
                                    <Icon
                                        size={17}
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
                                            <ChevronDown
                                                className="rotate-180"
                                                size={14}
                                                aria-hidden="true"
                                            />
                                        ) : (
                                            <ChevronDown
                                                size={14}
                                                aria-hidden="true"
                                            />
                                        )
                                    ) : null}
                                </button>

                                {item.children && isOpen ? (
                                    <ul className="ml-4 mt-1 grid gap-1 border-l border-[#d8eadc] pl-3">
                                        {item.children.map(
                                            (child) => {
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
                                                                `flex h-8 items-center gap-2 rounded-md px-2 text-[12px] font-semibold transition ${isActive
                                                                    ? "bg-[#f1fbf3] text-[#159734]"
                                                                    : "text-[#315f3c] hover:bg-[#f4faf5] hover:text-[#159734]"
                                                                }`
                                                            }
                                                        >
                                                            <span
                                                                className="size-1.5 rounded-full bg-current"
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

            <div className="mt-3 shrink-0 border-t border-[#edf0f4] pt-3">
                <button
                    type="button"
                    onClick={() => navigate(routePaths.posSales)}
                    className="flex w-full items-center gap-2.5 rounded-lg px-1.5 py-2 text-left hover:bg-[#f4faf5]"
                    aria-label="Chuyển về POS bán hàng"
                >
                    <span className="block size-8 shrink-0 overflow-hidden rounded-full bg-[linear-gradient(135deg,#f8c9b5,#182438)]" />
                    <span className="min-w-0 flex-1">
                        <strong className="block truncate text-[12px] text-[#315f3c]">
                            Phạm Thu Hương
                        </strong>
                        <span className="block truncate text-[11px] text-[#5f7a66]">
                            Quản trị viên
                        </span>
                    </span>
                    <ChevronDown size={13} aria-hidden="true" />
                </button>

                <div className="mt-2 flex items-center gap-2 rounded-lg px-1.5 py-1.5 text-[11px] font-semibold text-[#315f3c]">
                    <MapPin size={14} aria-hidden="true" />
                    <span className="min-w-0 flex-1 truncate">
                        Cửa hàng trung tâm
                    </span>
                </div>
            </div>
        </aside>
    );
}
