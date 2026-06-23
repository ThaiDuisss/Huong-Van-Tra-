import { ArrowRight, Box } from "lucide-react";

interface BomAttentionItem {
    id: string;
    title: string;
    subtitle: string;
    imageUrl: string;
}

interface BomAttentionListProps {
    items: readonly BomAttentionItem[];
}

export function BomAttentionList({
    items
}: BomAttentionListProps) {
    return (
        <section className="rounded-2xl border border-orange-100 bg-orange-50/40 p-4">
            <div className="mb-3 flex items-center justify-between">
                <h2 className="inline-flex items-center gap-2 text-lg font-bold text-gray-500">
                    <Box
                        size={18}
                        className="text-hvt-green-900"
                        aria-hidden="true"
                    />
                    BOM cần chú ý
                </h2>
                <button
                    type="button"
                    className="inline-flex h-9 items-center gap-2 rounded-lg bg-white px-3 text-sm font-bold text-gray-500 shadow-sm"
                >
                    Xem tất cả
                    <ArrowRight size={15} aria-hidden="true" />
                </button>
            </div>
            <div className="grid grid-cols-1 divide-y divide-pos-border rounded-xl bg-white/50 sm:grid-cols-2 sm:divide-x sm:divide-y-0 2xl:grid-cols-4">
                {items.map((item) => (
                    <article
                        key={item.id}
                        className="flex items-center gap-3 px-4 py-2"
                    >
                        <img
                            src={item.imageUrl}
                            alt={item.title}
                            className="size-12 rounded-lg object-cover"
                        />
                        <div className="min-w-0">
                            <h3 className="truncate text-sm font-bold text-gray-500">
                                {item.title}
                            </h3>
                            <p className="mt-1 text-xs font-semibold text-hvt-green-800">
                                {item.subtitle}
                            </p>
                        </div>
                    </article>
                ))}
            </div>
        </section>
    );
}
