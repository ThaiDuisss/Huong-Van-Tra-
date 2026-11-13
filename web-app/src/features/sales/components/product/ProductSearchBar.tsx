import { useEffect, useRef } from "react";
import { Plus, ScanLine, Search } from "lucide-react";

import { Button } from "@/components/ui/Button";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { useBarcodeScanner } from "@/hooks/useBarcodeScanner";
import type { SaleProductPreview } from "../../types/sale.types";

interface ProductSearchBarProps {
  value: string;
  results: SaleProductPreview[];
  loading?: boolean;
  error?: string;
  addingProductId?: string | null;
  formatCurrency(value: number): string;
  onChange(value: string): void;
  onClear(): void;
  onAdd(product: SaleProductPreview): void;
}

export function ProductSearchBar({
  value,
  results,
  loading = false,
  error = "",
  addingProductId = null,
  formatCurrency,
  onChange,
  onClear,
  onAdd
}: ProductSearchBarProps) {
  const searchInputRef = useRef<HTMLInputElement | null>(null);
  const visibleResults = results.slice(0, 5);

  // Lắng nghe máy quét mã vạch toàn cục (Global Keydown Listener)
  useBarcodeScanner({
    onScan: (barcode) => {
      onChange(barcode);
    },
    targetInputRef: searchInputRef
  });

  // Hỗ trợ phím tắt F3 để đưa con trỏ vào ô tìm kiếm
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "F3") {
        e.preventDefault();
        searchInputRef.current?.focus();
        searchInputRef.current?.select();
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, []);

  const stockTone = (quantity: number) => (quantity <= 0 ? "neutral" : "success");

  return (
    <div className="relative">
      <div className="flex min-w-0 items-center gap-4 rounded-xl border border-pos-border bg-pos-surface px-4 shadow-pos-panel focus-within:border-hvt-green-400">
        <Search
          aria-hidden="true"
          className="size-7 shrink-0 text-hvt-green-700"
          strokeWidth={1.9}
        />
        <input
          ref={searchInputRef}
          className="h-[58px] min-w-0 flex-1 bg-transparent text-[15px] outline-none placeholder:text-[#8b95b1]"
          aria-label="Tìm sản phẩm"
          placeholder="Tìm hàng hóa (F3) hoặc quét mã vạch"
          type="search"
          value={value}
          onChange={(event) => onChange(event.currentTarget.value)}
          onInput={(event) => {
            if (event.currentTarget.value === "") {
              onClear();
            }
          }}
          onKeyDown={(event) => {
            if (event.key === "Escape") {
              onClear();
            } else if (event.key === "Enter" && visibleResults.length > 0) {
              event.preventDefault();
              onAdd(visibleResults[0]);
            }
          }}
        />
        <div className="h-8 w-px bg-pos-border" />
        <button
          type="button"
          onClick={() => {
            searchInputRef.current?.focus();
            searchInputRef.current?.select();
          }}
          className="grid size-10 place-items-center rounded-lg text-hvt-green-700 hover:bg-hvt-green-50 cursor-pointer"
          aria-label="Quét mã vạch"
          title="Sẵn sàng quét mã vạch (hoặc bấm F3)"
        >
          <ScanLine size={28} aria-hidden="true" />
        </button>
      </div>

      {value ? (
        <div className="absolute left-0 right-0 top-[calc(100%+8px)] z-20 rounded-2xl border border-hvt-green-200 bg-white p-4 text-sm text-neutral-600 shadow-pos-panel">
          <div className="mb-3 flex items-center justify-between gap-4">
            <div className="flex items-center gap-3">
              <Search
                size={22}
                className="text-hvt-green-900"
                aria-hidden="true"
              />
              <strong className="text-gray-500">Kết quả tìm kiếm</strong>
              <span>{results.length} sản phẩm phù hợp</span>
            </div>
            <span className="text-xs text-neutral-500">
              {loading ? "Đang tìm..." : "Nhấn + để thêm sản phẩm vào đơn"}
            </span>
          </div>

          <div className="max-h-[420px] overflow-y-auto">
            {error ? (
              <div className="rounded-xl border border-dashed border-red-200 bg-red-50 px-4 py-3 text-center text-red-700">
                {error}
              </div>
            ) : loading && visibleResults.length === 0 ? (
              <div className="rounded-xl border border-dashed border-pos-border py-8 text-center">
                Đang tìm sản phẩm...
              </div>
            ) : visibleResults.length > 0 ? (
              visibleResults.map((product, index) => (
                <article
                  className={`grid cursor-pointer grid-cols-[64px_minmax(0,1fr)_auto_auto] items-center gap-4 border-b border-pos-border px-2 py-3 last:border-b-0 ${
                    index === 0
                      ? "rounded-xl border border-hvt-green-300 bg-hvt-green-50/70"
                      : ""
                  }`}
                  key={product.id}
                  onClick={() => onAdd(product)}
                >
                  <img
                    className="size-16 rounded-xl object-cover"
                    src={product.imageUrl}
                    alt={product.name}
                  />

                  <div className="min-w-0">
                    <h3 className="truncate text-base font-bold text-gray-500">
                      {product.name}
                    </h3>
                    <p className="mt-1 text-sm text-neutral-600">
                      {product.variant ? `${product.variant} · ` : null}
                      SKU: {product.sku}
                      {product.barcode ? ` · ${product.barcode}` : ""}
                    </p>
                    <p className="text-sm text-neutral-600">
                      Danh mục: {product.category}
                    </p>
                  </div>

                  {product.sellableQuantity <= 0 ? (
                    <StatusBadge shape="tag" tone="danger">
                      Hết hàng · có thể đặt trước
                    </StatusBadge>
                  ) : (
                    <div className="grid justify-items-end gap-1">
                      <StatusBadge shape="tag" tone={stockTone(product.shelfQuantity)}>
                        Trên kệ: {product.shelfQuantity}
                      </StatusBadge>
                      <StatusBadge shape="tag" tone={stockTone(product.warehouseQuantity)}>
                        Trong kho: {product.warehouseQuantity}
                      </StatusBadge>
                    </div>
                  )}

                  <div className="grid justify-items-end gap-2">
                    <strong className="text-base text-hvt-green-900">
                      {formatCurrency(product.price)}
                    </strong>
                    <button
                      type="button"
                      disabled={addingProductId === product.id}
                      title={
                        product.sellableQuantity <= 0
                          ? "Hết hàng: xem ngày dự kiến có hàng và đặt trước"
                          : undefined
                      }
                      onClick={(event) => {
                        event.stopPropagation();
                        onAdd(product);
                      }}
                      className="grid size-9 place-items-center rounded-full border border-pos-border bg-white text-gray-500 shadow-[0_2px_8px_rgba(0,0,0,0.05)] hover:border-hvt-green-700 hover:bg-hvt-green-50"
                      aria-label={`Thêm ${product.name} vào giỏ`}
                    >
                      {addingProductId === product.id ? (
                        <span className="text-xs font-bold">...</span>
                      ) : (
                        <Plus size={20} aria-hidden="true" />
                      )}
                    </button>
                  </div>
                </article>
              ))
            ) : (
              <div className="rounded-xl border border-dashed border-pos-border py-8 text-center">
                Không tìm thấy sản phẩm phù hợp.
              </div>
            )}
          </div>

          {results.length > visibleResults.length ? (
            <div className="mt-3 border-t border-pos-border">
              <Button className="w-full" variant="link">
                Xem tất cả {results.length} kết quả
              </Button>
            </div>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
