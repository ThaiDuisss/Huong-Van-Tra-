import { ChevronDown } from "lucide-react";

interface UserProfileProps {
    expanded: boolean;
    compact?: boolean;
    onToggle(): void;
}

export function UserProfile({
    expanded,
    compact = false,
    onToggle
}: UserProfileProps) {
    return (
        <section
            className={`flex items-center text-sm ${
                compact ? "gap-2 pb-0" : "gap-3 pb-6"
            }`}
            aria-label="Người dùng hiện tại"
        >
            <div
                className={`grid shrink-0 place-items-center overflow-hidden rounded-full bg-white text-hvt-green-900 ring-2 ring-white/20 ${
                    compact ? "size-8" : "size-10"
                }`}
                aria-hidden="true"
            >
                <span className="font-bold">Đ</span>
            </div>
            <div className="min-w-0">
                <strong
                    className={`block truncate ${
                        compact ? "text-sm" : ""
                    }`}
                >
                    Đỗ Xuân Thái
                </strong>
                {compact ? null : (
                    <p className="mt-0.5 text-white/70">
                        Ca sáng
                    </p>
                )}
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
