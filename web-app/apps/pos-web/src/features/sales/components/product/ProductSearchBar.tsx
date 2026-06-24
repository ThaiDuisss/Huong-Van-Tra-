import { Plus, ScanLine, Search, X } from "lucide-react";

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
  const visibleResults = results.slice(0, 5);
  const getStockLabel = (product: SaleProductPreview) => {
    if (product.availableQuantity === null) {
      return "Tồn: Không rõ";
    }

    return product.availableQuantity <= 0
      ? "Hết hàng"
      : `Tồn: ${product.availableQuantity}`;
  };

  return (
    <div className="relative">
      <div className="flex min-w-0 items-center gap-4 rounded-xl border border-pos-border bg-pos-surface px-4 shadow-pos-panel focus-within:border-hvt-green-400">
        <Search
          aria-hidden="true"
          className="size-7 shrink-0 text-hvt-green-700"
          strokeWidth={1.9}
        />
        <input
          className="h-[58px] min-w-0 flex-1 bg-transparent text-[15px] outline-none placeholder:text-[#8b95b1]"
          aria-label="Tìm sản phẩm"
          placeholder="Tìm hàng hóa (F3)"
          type="search"
          value={value}
          onChange={(event) => onChange(event.currentTarget.value)}
        />
        {value ? (
          <button
            type="button"
            onClick={onClear}
            className="grid size-9 place-items-center rounded-lg text-neutral-500 hover:bg-neutral-100 hover:text-gray-500"
            aria-label="Xóa từ khóa tìm kiếm"
          >
            <X size={22} aria-hidden="true" />
          </button>
        ) : null}
        <div className="h-8 w-px bg-pos-border" />
        <button
          type="button"
          className="grid size-10 place-items-center rounded-lg text-hvt-green-700 hover:bg-hvt-green-50"
          aria-label="Quét mã vạch"
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

                  <span
                    className={`rounded-md px-2 py-1 text-xs font-semibold ${
                      product.availableQuantity !== null &&
                      product.availableQuantity <= 0
                        ? "bg-red-50 text-red-700"
                        : "bg-hvt-green-50 text-hvt-green-800"
                    }`}
                  >
                    {getStockLabel(product)}
                  </span>

                  <div className="grid justify-items-end gap-2">
                    <strong className="text-base text-hvt-green-900">
                      {formatCurrency(product.price)}
                    </strong>
                    <button
                      type="button"
                      disabled={
                        addingProductId === product.id ||
                        (product.availableQuantity !== null &&
                          product.availableQuantity <= 0)
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
            <button
              type="button"
              className="mt-3 h-10 w-full rounded-xl border-t border-pos-border text-sm font-semibold text-hvt-green-800"
            >
              Xem tất cả {results.length} kết quả
            </button>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
