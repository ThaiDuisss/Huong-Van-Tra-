export function PosHeader() {
  return (
    <header className="flex min-w-0 items-center justify-between gap-4 px-8">
      <div>
        <p className="text-xs font-bold uppercase tracking-[0.16em] text-hvt-green-700">
          POS
        </p>
        <h1 className="text-2xl font-bold text-gray-500">
          Bán hàng tại quầy
        </h1>
      </div>

      <div className="flex items-center gap-3 rounded-full border border-pos-border bg-pos-surface px-4 py-2 text-sm shadow-pos-card">
        <span className="text-neutral-500">Chi nhánh trung tâm</span>
        <strong className="text-hvt-green-800">Ca sáng</strong>
      </div>
    </header>
  );
}
