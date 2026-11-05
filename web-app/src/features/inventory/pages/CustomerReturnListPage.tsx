import { RefreshCw } from "lucide-react";
import { useState } from "react";

import { ListPageHeader } from "@/components/shared/list/ListPageHeader";
import { LIST_SECONDARY_BUTTON } from "@/components/shared/list/list-ui.constants";
import { useListUrlState } from "@/components/shared/list/useListUrlState";

import { CustomerReturnQcQueue } from "../components/CustomerReturnQcQueue";

export function CustomerReturnListPage() {
    const listState = useListUrlState();
    const [reloadSignal, setReloadSignal] = useState(0);

    return (
        <section className="flex h-full min-h-0 flex-col gap-4 text-[#172317]">
            <ListPageHeader
                title="Khách trả hàng"
                description="Tiếp nhận, kiểm hàng và xử lý kho cho hàng khách trả."
                actions={(
                    <button
                        className={LIST_SECONDARY_BUTTON}
                        onClick={() => setReloadSignal((value) => value + 1)}
                        type="button"
                    >
                        <RefreshCw size={17} aria-hidden="true" />
                        Làm mới
                    </button>
                )}
            />
            <CustomerReturnQcQueue listState={listState} reloadSignal={reloadSignal} />
        </section>
    );
}
