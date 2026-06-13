import { Link } from "react-router-dom";

import { routePaths } from "../routes/path";

export function NotFoundPage() {
    return (
        <main className="grid min-h-screen place-items-center bg-pos-background px-6 text-center text-pos-ink">
            <section className="grid max-w-md gap-4 rounded-3xl bg-pos-surface p-8 shadow-pos-panel">
                <p className="text-sm font-semibold uppercase tracking-[0.14em] text-hvt-green-800">
                    404
                </p>
                <h1 className="text-3xl font-bold">
                    Không tìm thấy trang
                </h1>
                <p className="text-pos-muted">
                    Đường dẫn này không tồn tại trong hệ thống POS.
                </p>
                <Link
                    to={routePaths.posSales}
                    className="mx-auto inline-flex h-11 items-center justify-center rounded-xl bg-hvt-green-900 px-5 text-sm font-bold text-white"
                >
                    Quay lại bán hàng
                </Link>
            </section>
        </main>
    );
}
