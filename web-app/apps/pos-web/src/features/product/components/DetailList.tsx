import type { ReactNode } from "react";

interface DetailListItem {
    label: string;
    value: ReactNode;
}

interface DetailListProps {
    items: DetailListItem[];
}

export function DetailList({
    items
}: DetailListProps) {
    return (
        <dl className="grid gap-4">
            {items.map((item) => (
                <div
                    key={item.label}
                    className="border-b border-pos-border pb-3 last:border-0"
                >
                    <dt className="text-xs font-semibold uppercase tracking-wide text-gray-600">
                        {item.label}
                    </dt>
                    <dd className="mt-1 font-semibold text-gray-600">
                        {item.value}
                    </dd>
                </div>
            ))}
        </dl>
    );
}
