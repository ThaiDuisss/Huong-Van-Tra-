import type {
    SalesOrderDetail,
    SalesOrderItemDetail
} from "../types/sales-history.types";
import {
    formatCurrency,
    formatQuantity,
    formatSaleDateTime,
    getDetailCustomer,
    getDetailSaleTime,
    getItemUnitPrice,
    getSalesPaymentMethodLabel,
    toNumber
} from "./sales-history-formatters";

export interface ReceiptStoreInfo {
    name: string;
    address: string;
    hotline: string;
}

/** Thông tin cửa hàng in trên hóa đơn; có thể đổi qua biến môi trường VITE_STORE_*. */
export function getReceiptStoreInfo(): ReceiptStoreInfo {
    const env = import.meta.env as Record<string, string | undefined>;

    return {
        name: env.VITE_STORE_NAME?.trim() || "Hương Vân Trà",
        address: env.VITE_STORE_ADDRESS?.trim() || "",
        hotline: env.VITE_STORE_HOTLINE?.trim() || ""
    };
}

function escapeHtml(value: unknown) {
    return String(value ?? "")
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#39;");
}

function getReceiptItemName(item: SalesOrderItemDetail) {
    return (
        item.variantName?.trim() ||
        item.variantNameSnapshot?.trim() ||
        item.productName?.trim() ||
        item.productNameSnapshot?.trim() ||
        item.name?.trim() ||
        item.sku?.trim() ||
        item.skuSnapshot?.trim() ||
        "Sản phẩm"
    );
}

function getReceiptLineTotal(item: SalesOrderItemDetail) {
    if (item.lineTotal !== null && item.lineTotal !== undefined) {
        return item.lineTotal;
    }

    return toNumber(item.quantity) * toNumber(getItemUnitPrice(item));
}

function infoRow(label: string, value?: string | null) {
    if (!value?.trim()) {
        return "";
    }

    return `<div class="row"><span>${escapeHtml(label)}</span><span>${escapeHtml(value)}</span></div>`;
}

function totalRow(label: string, value: string, strong = false) {
    return `<div class="row${strong ? " strong" : ""}"><span>${escapeHtml(label)}</span><span>${escapeHtml(value)}</span></div>`;
}

/**
 * Dựng hóa đơn bán hàng khổ giấy in nhiệt 80 mm dưới dạng một trang HTML độc lập,
 * dùng chung cho xem trước và in.
 */
export function buildReceiptHtml(
    order: SalesOrderDetail,
    store: ReceiptStoreInfo = getReceiptStoreInfo()
) {
    const items = order.items ?? [];
    const subTotal = order.originalAmount ?? order.subTotal;
    const discount = toNumber(order.discountAmount);
    const change = toNumber(order.changeAmount);
    const debt = toNumber(order.debtAmount);

    const itemRows = items
        .map(
            (item) => `
        <div class="item">
            <div class="item-name">${escapeHtml(getReceiptItemName(item))}</div>
            <div class="row">
                <span>${escapeHtml(formatQuantity(item.quantity))} x ${escapeHtml(formatCurrency(getItemUnitPrice(item)))}</span>
                <span>${escapeHtml(formatCurrency(getReceiptLineTotal(item)))}</span>
            </div>
        </div>`
        )
        .join("");

    return `<!DOCTYPE html>
<html lang="vi">
<head>
<meta charset="UTF-8" />
<title>Hóa đơn ${escapeHtml(order.orderCode ?? "")}</title>
<style>
    @page { size: 80mm auto; margin: 4mm; }
    * { box-sizing: border-box; }
    body { margin: 0; padding: 8px; font-family: Arial, Helvetica, sans-serif; font-size: 12px; color: #000; background: #fff; }
    .receipt { width: 72mm; max-width: 100%; margin: 0 auto; }
    .center { text-align: center; }
    .store { font-size: 16px; font-weight: 700; text-transform: uppercase; }
    .title { margin: 8px 0 6px; font-size: 14px; font-weight: 700; }
    .muted { font-size: 11px; }
    .divider { border-top: 1px dashed #000; margin: 6px 0; }
    .row { display: flex; justify-content: space-between; gap: 8px; }
    .row span:last-child { text-align: right; white-space: nowrap; }
    .item { margin-bottom: 4px; }
    .item-name { font-weight: 700; }
    .strong { font-size: 14px; font-weight: 700; }
    .footer { margin-top: 10px; }
</style>
</head>
<body>
<div class="receipt">
    <div class="center store">${escapeHtml(store.name)}</div>
    ${store.address ? `<div class="center muted">${escapeHtml(store.address)}</div>` : ""}
    ${store.hotline ? `<div class="center muted">Hotline: ${escapeHtml(store.hotline)}</div>` : ""}
    <div class="center title">HÓA ĐƠN BÁN HÀNG</div>
    ${infoRow("Số HĐ", order.orderCode)}
    ${infoRow("Thời gian", formatSaleDateTime(getDetailSaleTime(order)))}
    ${infoRow("Thu ngân", order.cashierName)}
    ${infoRow("Khách hàng", getDetailCustomer(order))}
    ${infoRow("Điện thoại", order.customerPhone)}
    <div class="divider"></div>
    ${itemRows || `<div class="center muted">Không có sản phẩm</div>`}
    <div class="divider"></div>
    ${subTotal !== null && subTotal !== undefined ? totalRow("Tổng tiền hàng", formatCurrency(subTotal)) : ""}
    ${discount > 0 ? totalRow("Giảm giá", `-${formatCurrency(discount)}`) : ""}
    ${totalRow("Khách phải trả", formatCurrency(order.finalAmount), true)}
    ${totalRow("Khách thanh toán", formatCurrency(order.paidAmount))}
    ${change > 0 ? totalRow("Tiền thừa trả khách", formatCurrency(change)) : ""}
    ${debt > 0 ? totalRow("Còn nợ", formatCurrency(debt)) : ""}
    ${infoRow("Hình thức", getSalesPaymentMethodLabel(order.paymentMethod, order.paymentMethodLabel))}
    <div class="divider"></div>
    <div class="center footer">Cảm ơn quý khách và hẹn gặp lại!</div>
</div>
</body>
</html>`;
}

/**
 * In hóa đơn qua một iframe ẩn để hộp thoại in của trình duyệt chỉ chứa hóa đơn,
 * không lẫn giao diện POS.
 */
export function printReceipt(order: SalesOrderDetail) {
    const iframe = document.createElement("iframe");

    iframe.setAttribute("aria-hidden", "true");
    iframe.title = "Hóa đơn";
    iframe.style.position = "fixed";
    iframe.style.right = "0";
    iframe.style.bottom = "0";
    iframe.style.width = "0";
    iframe.style.height = "0";
    iframe.style.border = "0";
    document.body.appendChild(iframe);

    const frameWindow = iframe.contentWindow;
    const frameDocument = frameWindow?.document;

    if (!frameWindow || !frameDocument) {
        iframe.remove();
        throw new Error("Trình duyệt không cho phép mở khung in hóa đơn.");
    }

    frameDocument.open();
    frameDocument.write(buildReceiptHtml(order));
    frameDocument.close();

    const cleanup = () => {
        window.setTimeout(() => iframe.remove(), 500);
    };

    frameWindow.addEventListener("afterprint", cleanup);

    // Chờ trình duyệt dựng xong nội dung rồi mới gọi lệnh in.
    window.setTimeout(() => {
        frameWindow.focus();
        frameWindow.print();
        // Dự phòng cho trình duyệt không phát sự kiện afterprint.
        window.setTimeout(() => iframe.remove(), 60_000);
    }, 150);
}
