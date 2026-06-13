import { ChevronDown } from "lucide-react";

interface UserProfileProps {
    expanded: boolean;
    onToggle(): void;
}

export function UserProfile({
    expanded,
    onToggle
}: UserProfileProps) {
    return (
        <section
            className="flex items-center gap-3 pb-6 text-sm"
            aria-label="Người dùng hiện tại"
        >
            <div
                className="grid size-10 shrink-0 place-items-center overflow-hidden rounded-full bg-white text-hvt-green-900 ring-2 ring-white/20"
                aria-hidden="true"
            >
                <span className="font-bold">Đ</span>
            </div>
            <div className="min-w-0">
                <strong className="block truncate">
                    Đỗ Xuân Thái
                </strong>
                <p className="mt-0.5 text-white/70">Ca sáng</p>
            </div>
            <button
                type="button"
                className="ml-auto grid size-7 shrink-0 place-items-center rounded-lg text-white/75 hover:bg-white/10 hover:text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-pos-gold"
                aria-label="Mở menu workspace"
                aria-haspopup="menu"
                aria-expanded={expanded}
                onClick={onToggle}
            >
                <ChevronDown
                    className={`transition ${
                        expanded ? "rotate-180" : ""
                    }`}
                    size={18}
                    aria-hidden="true"
                />
            </button>
        </section>
    );
}
