export function BomListPage() {
    return (
        <section className="grid gap-6">
            <nav
                className="flex items-center gap-2 text-sm text-pos-muted"
                aria-label="Breadcrumb"
            >
                <span>BOM & Sản xuất</span>
                <span aria-hidden="true">/</span>
                <strong className="text-pos-ink">
                    Danh sách BOM
                </strong>
            </nav>

            <div className="grid gap-2">
                <h1 className="text-3xl font-bold text-pos-ink">
                    Danh sách BOM
                </h1>
                <p className="text-pos-muted">
                    Quản lý công thức sản xuất và định mức nguyên
                    liệu.
                </p>
            </div>

            <div className="rounded-3xl border border-dashed border-hvt-green-200 bg-pos-surface p-10 text-center shadow-pos-card">
                <h2 className="text-xl font-bold text-pos-ink">
                    Placeholder nội dung BOM
                </h2>
                <p className="mt-2 text-pos-muted">
                    Bảng, bộ lọc và chi tiết BOM sẽ được triển khai
                    trong task riêng.
                </p>
            </div>
        </section>
    );
}
