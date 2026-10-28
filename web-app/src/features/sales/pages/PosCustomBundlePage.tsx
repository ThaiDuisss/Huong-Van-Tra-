import { Leaf } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { routePaths } from "../../../routes/path";
import { CustomBundleBuilder, type CustomBundleInput } from "../components/custom-bundle/CustomBundleBuilder";

/** State gửi kèm khi quay về POS: PosSalePage thêm bundle vào đơn đang bán. */
export interface PosSaleNavigationState {
    pendingCustomBundle?: CustomBundleInput;
}

/** Màn đóng trà theo yêu cầu: chọn nguyên liệu, rồi đưa bundle vào đơn đang bán trên POS. */
export function PosCustomBundlePage() {
    const navigate = useNavigate();

    return (
        <section className="grid h-full min-h-0 grid-rows-[auto_minmax(0,1fr)] gap-4 overflow-hidden p-4">
            <header className="grid gap-1">
                <h1 className="flex items-center gap-2 text-2xl font-black text-[#172317]">
                    <Leaf aria-hidden="true" className="text-[#159734]" size={22} />
                    Đóng trà theo yêu cầu
                </h1>
                <p className="text-sm font-semibold text-[#697684]">
                    Chọn nguyên liệu và khối lượng khách muốn. Bấm "Thêm vào đơn" để quay lại POS với bundle trong đơn
                    đang bán; kho đóng gói sau khi thu tiền.
                </p>
            </header>

            <CustomBundleBuilder
                branchId={import.meta.env.VITE_POS_BRANCH_ID ?? null}
                submitLoading={false}
                onCancel={() => navigate(routePaths.posSales)}
                onSubmit={(input) => {
                    const state: PosSaleNavigationState = { pendingCustomBundle: input };
                    navigate(routePaths.posSales, { state });
                }}
            />
        </section>
    );
}
