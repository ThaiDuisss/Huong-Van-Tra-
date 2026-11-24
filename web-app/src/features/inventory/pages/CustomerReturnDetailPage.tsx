import { useEffect, useState } from "react";
import { Link, Navigate, useNavigate, useParams } from "react-router-dom";

import { ApiError } from "@/lib/api-client";
import { routePaths } from "@/routes/path";

import { CustomerReturnWarehouseInspectionModal } from "../components/CustomerReturnWarehouseInspectionModal";
import { customerReturnApi } from "../services/customerReturn.api";

interface InspectionRouteTarget {
    returnId: string;
    returnCode?: string | null;
    inspectionId?: string;
}

const UUID = /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i;

async function resolveTarget(identifier: string): Promise<InspectionRouteTarget> {
    if (UUID.test(identifier)) {
        try {
            const inspection = await customerReturnApi.getInspection(identifier);
            return { returnId: inspection.returnId, returnCode: inspection.returnCode, inspectionId: inspection.id };
        } catch (error: unknown) {
            if (!(error instanceof ApiError) || error.status !== 404) throw error;
        }
        const summary = await customerReturnApi.getQcSummary(identifier);
        return { returnId: summary.returnId, returnCode: summary.returnCode };
    }
    const result = await customerReturnApi.listInspections({ returnCode: identifier, page: 0, size: 10 });
    const inspection = result.content.find((item) => item.returnCode?.toLowerCase() === identifier.toLowerCase());
    if (!inspection) throw new Error("Không tìm thấy phiếu kiểm định.");
    return { returnId: inspection.returnId, returnCode: inspection.returnCode };
}

function InspectionRoute({ identifier }: { identifier: string }) {
    const navigate = useNavigate();
    const [target, setTarget] = useState<InspectionRouteTarget | null>(null);
    const [error, setError] = useState(false);
    useEffect(() => {
        let active = true;
        void resolveTarget(identifier)
            .then((result) => { if (active) setTarget(result); })
            .catch(() => { if (active) setError(true); });
        return () => { active = false; };
    }, [identifier]);

    if (!target) return <section className="space-y-3">
        <p role={error ? "alert" : "status"}>{error ? "Không tải được phiếu kiểm định." : "Đang tải phiếu kiểm định…"}</p>
        <Link to={routePaths.inventoryCustomerReturnList}>Quay lại Khách trả hàng</Link>
    </section>;
    return <CustomerReturnWarehouseInspectionModal
        isOpen
        returnId={target.returnId}
        returnCode={target.returnCode}
        inspectionId={target.inspectionId}
        onClose={() => navigate(routePaths.inventoryCustomerReturnList)}
    />;
}

export function CustomerReturnDetailPage() {
    const { returnId } = useParams();
    if (!returnId) return <Navigate replace to={routePaths.inventoryCustomerReturnList} />;
    return <InspectionRoute identifier={returnId} key={returnId} />;
}
