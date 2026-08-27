import { LoaderCircle } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, Navigate, useNavigate, useParams } from "react-router-dom";

import { stocktakeApi } from "../services/stocktake.api";

export function StocktakeAdjustmentDetailRedirectPage() {
  const { adjustmentId } = useParams();
  const navigate = useNavigate();
  const [error, setError] = useState("");

  useEffect(() => {
    if (!adjustmentId) return;
    let active = true;
    void stocktakeApi
      .getAdjustment(adjustmentId)
      .then((adjustment) => {
        if (!active) return;
        if (adjustment.referenceType === "STOCK_COUNT" && adjustment.referenceId) {
          navigate(`/management/inventory/stocktakes/${adjustment.referenceId}/adjustment`, {
            replace: true,
          });
          return;
        }
        navigate(`/management/inventory/documents/${adjustment.id}`, { replace: true });
      })
      .catch((loadError: unknown) => {
        if (active)
          setError(loadError instanceof Error ? loadError.message : "Không thể mở phiếu.");
      });
    return () => {
      active = false;
    };
  }, [adjustmentId, navigate]);

  if (!adjustmentId) return <Navigate replace to="/management/inventory/stocktake-adjustments" />;

  return (
    <section className="grid min-h-72 place-items-center rounded-xl border border-[#dce5e1] bg-white p-6 text-center">
      {error ? (
        <div>
          <p className="font-bold text-red-700">{error}</p>
          <Link
            className="mt-3 inline-flex text-sm font-bold text-[#159734]"
            to="/management/inventory/stocktake-adjustments"
          >
            Quay lại danh sách
          </Link>
        </div>
      ) : (
        <div className="flex items-center gap-2 text-sm font-bold text-[#536171]">
          <LoaderCircle className="animate-spin" size={18} /> Đang mở phiếu...
        </div>
      )}
    </section>
  );
}
