import { CloudOff, CloudUpload } from "lucide-react";
import { useState } from "react";

import { useNetworkStatus } from "../../../../app/network/useNetworkStatus";
import { useOfflineOrderSync } from "../../offline/offline-order-sync.instance";
import { OfflineOrdersDialog } from "./OfflineOrdersDialog";

/** Nút ở thanh bên: số đơn offline chưa lên máy chủ, bấm mở danh sách. */
export function OfflineSyncButton() {
    const sync = useOfflineOrderSync();
    const { online } = useNetworkStatus();
    const [open, setOpen] = useState(false);
    const count = sync.pending + sync.failed;
    const label =
        count === 0
            ? "Đơn bán khi mất mạng"
            : `${count} đơn bán offline chưa đồng bộ`;
    const Icon = online ? CloudUpload : CloudOff;

    return (
        <>
            <button
                type="button"
                className={`relative grid size-9 place-items-center rounded-lg transition hover:bg-[#f4f5f3] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-hvt-green-600 ${
                    sync.failed > 0 ? "text-red-600" : "text-[#159734]"
                }`}
                aria-label={label}
                title={label}
                onClick={() => setOpen(true)}
            >
                <Icon
                    size={21}
                    strokeWidth={1.9}
                    aria-hidden="true"
                    className={sync.syncing ? "animate-pulse" : ""}
                />
                {count > 0 ? (
                    <span
                        className={`absolute -right-1 -top-1 grid min-w-[18px] place-items-center rounded-full px-1 text-[10px] font-black leading-[18px] text-white ${
                            sync.failed > 0 ? "bg-red-600" : "bg-amber-500"
                        }`}
                    >
                        {count > 99 ? "99+" : count}
                    </span>
                ) : null}
            </button>
            {open ? <OfflineOrdersDialog onClose={() => setOpen(false)} /> : null}
        </>
    );
}
