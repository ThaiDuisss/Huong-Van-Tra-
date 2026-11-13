import { Copy } from "lucide-react";
import { useState } from "react";
import { Button } from "../../../components/ui/Button";
import { StatusBadge } from "../../../components/ui/StatusBadge";
import { Table, TableCell, TableHead, TableHeaderCell, TableRow } from "../../../components/ui/Table";
import type { IngredientRecommendation } from "../types/ingredientDemo.types";
import { shortResearchId } from "../utils/ingredientDemo.display";

export function ResearchId({ label, value }: { label: string; value: string }) {
  const [message, setMessage] = useState("");
  async function copy() {
    try {
      await navigator.clipboard.writeText(value);
      setMessage("Đã sao chép ID.");
    } catch {
      setMessage("Không sao chép được. Mở ID đầy đủ để chọn và sao chép thủ công.");
    }
  }
  return <div className="space-y-1 text-xs text-slate-600">
    <div className="flex flex-wrap items-center gap-2">
      <span className="font-semibold">{label}:</span><code>{shortResearchId(value)}</code>
      <Button aria-label={`Sao chép ${label}`} onClick={() => void copy()} size="sm" variant="ghost">
        <Copy size={13} /> Sao chép
      </Button>
    </div>
    <details><summary className="cursor-pointer">ID đầy đủ</summary><code className="block break-all py-1">{value}</code></details>
    {message && <p role="status">{message}</p>}
  </div>;
}

export function IngredientResultCard({ item }: { item: IngredientRecommendation }) {
  const product = item.recommendation_type === "PRODUCT";
  return <article className="space-y-4 rounded-xl border border-slate-200 bg-white p-4 shadow-sm sm:p-5">
    <div className="flex flex-wrap items-center justify-between gap-2">
      <h3 className="font-bold text-slate-900">#{item.rank} · {product ? "Sản phẩm nghiên cứu" : "Công thức nghiên cứu"}</h3>
      <StatusBadge tone="success">{item.recommendation_type}</StatusBadge>
    </div>
    <div className="flex flex-wrap gap-x-5 gap-y-2 text-sm">
      <p>Điểm: <strong className="text-slate-900">{item.score.toFixed(4)}</strong></p>
      <p>Chiến lược: <strong>{item.strategy}</strong></p>
      <p>Nhóm công thức: <strong>{item.recipe_family}</strong></p>
    </div>
    <p className="rounded-lg bg-slate-50 p-3 text-sm leading-6 text-slate-700">{item.reason}</p>
    <div className="space-y-2">
      <ResearchId label={product ? "Product ID" : "Recipe ID"} value={item.item_id} />
      {product && item.variant_id && <ResearchId label="Variant ID" value={item.variant_id} />}
      {product && <ResearchId label="Recipe ID liên kết (suy diễn)" value={item.recipe_id} />}
    </div>
    <div className="overflow-x-auto">
      <Table>
        <caption className="mb-2 text-left font-semibold text-slate-800">Nguyên liệu và định lượng mô phỏng</caption>
        <TableHead><TableRow><TableHeaderCell>Nguyên liệu</TableHeaderCell><TableHeaderCell>Vai trò</TableHeaderCell><TableHeaderCell className="text-right">Định lượng</TableHeaderCell></TableRow></TableHead>
        <tbody>{item.ingredient_summary.map((ingredient, index) => <TableRow key={`${ingredient.ingredient}-${index}`}>
          <TableCell className="font-medium">{ingredient.ingredient}</TableCell><TableCell>{ingredient.role}</TableCell>
          <TableCell className="whitespace-nowrap text-right">{ingredient.quantity} {ingredient.unit}</TableCell>
        </TableRow>)}</tbody>
      </Table>
    </div>
    {item.profile_signals && <p className="text-xs leading-5 text-slate-500">
      Nguồn tín hiệu: {item.profile_signals.product_inferred_orders} đơn có Product suy diễn; {item.profile_signals.custom_synthetic_orders} đơn có BOM mô phỏng; {item.profile_signals.orders} đơn trong hồ sơ. Một đơn có thể có cả hai nguồn.
    </p>}
  </article>;
}
