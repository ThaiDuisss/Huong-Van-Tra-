export interface RankListItem {
  id: string;
  label: string;
  sub?: string;
  value: number;
  valueText: string;
}

/** Horizontal bar compact cho Top SKU / cửa hàng / thanh toán (bảng chọn chart mục 9). */
export function RankList({ items }: { items: RankListItem[] }) {
  const max = Math.max(1, ...items.map((item) => item.value));

  return (
    <ol className="grid gap-3">
      {items.map((item, index) => (
        <li className="grid gap-1" key={item.id}>
          <div className="flex items-baseline justify-between gap-3 text-sm">
            <span className="flex min-w-0 items-baseline gap-2">
              <span className="w-4 shrink-0 text-xs font-black text-[#9aa7b5]">{index + 1}</span>
              <span className="min-w-0">
                <span className="block truncate font-black text-[#172317]">{item.label}</span>
                {item.sub ? (
                  <span className="block truncate text-xs font-semibold text-[#697684]">{item.sub}</span>
                ) : null}
              </span>
            </span>
            <span className="shrink-0 font-black tabular-nums text-[#172317]">{item.valueText}</span>
          </div>
          <div className="ml-6 h-1.5 overflow-hidden rounded-full bg-[#edf0f4]">
            <div
              className="h-full rounded-full bg-[#159734]"
              style={{ width: `${Math.max(3, (item.value / max) * 100)}%` }}
            />
          </div>
        </li>
      ))}
    </ol>
  );
}
