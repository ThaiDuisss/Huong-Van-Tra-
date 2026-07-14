import type { ReactNode } from "react";

interface DetailListItem {
    label: string;
    value: ReactNode;
    fullWidth?: boolean;
}

interface DetailListProps {
    items: DetailListItem[];
}

export function DetailList({
    items
}: DetailListProps) {
    return (
        <dl className="grid grid-cols-2 gap-x-6 gap-y-4">
            {items.map((item) => (
                <div
                    key={item.label}
                    className={`border-b border-pos-border pb-3 ${item.fullWidth ? "col-span-2" : ""}`}
                >
                    <dt className="text-xs font-semibold uppercase tracking-wide text-gray-500">
                        {item.label}
                    </dt>
                    <dd className="mt-1 font-semibold text-gray-700">
                        {item.value}
                    </dd>
                </div>
            ))}
        </dl>
    );
}
