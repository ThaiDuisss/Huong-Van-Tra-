interface BreadcrumbsProps {
    items: string[];
}

export function Breadcrumbs({ items }: BreadcrumbsProps) {
    return (
        <nav
            className="flex items-center gap-2 text-sm text-pos-muted"
            aria-label="Breadcrumb"
        >
            {items.map((item, index) => (
                <span
                    key={item}
                    className="flex items-center gap-2"
                >
                    {index > 0 ? (
                        <span aria-hidden="true">/</span>
                    ) : null}
                    <span
                        className={
                            index === items.length - 1
                                ? "font-bold text-pos-ink"
                                : ""
                        }
                    >
                        {item}
                    </span>
                </span>
            ))}
        </nav>
    );
}
