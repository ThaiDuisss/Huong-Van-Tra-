interface BreadcrumbsProps {
    items: string[];
}

export function Breadcrumbs({ items }: BreadcrumbsProps) {
    return (
        <nav
            className="text-gray-600 text-sm text-gray-600"
            aria-label="Breadcrumb"
        >
            {items.map((item, index) => (
                <span
                    key={item}
                    className="text-gray-600"
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
