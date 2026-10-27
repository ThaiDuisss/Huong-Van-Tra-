import type { GoodsReceiptDetail, GoodsReceiptLine } from "../types/goods-receipt.types";
import {
    EXCESS_RESOLUTION_LABELS,
    SHORTAGE_RESOLUTION_LABELS,
    getLineException,
    hasLineException
} from "./goodsReceiptException";
import { formatVietnamDateTime, formatVietnamNumber } from "./inventory-format";

function escapeHtml(value: unknown) {
    return String(value ?? "")
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#39;");
}

function resolutionText(line: GoodsReceiptLine) {
    const { damaged } = getLineException(line);
    const parts: string[] = [];
    if (line.excessResolution) parts.push(`Thừa: ${EXCESS_RESOLUTION_LABELS[line.excessResolution]}`);
    if (line.shortageResolution) parts.push(`Thiếu: ${SHORTAGE_RESOLUTION_LABELS[line.shortageResolution]}`);
    if (damaged > 0) parts.push("Không đạt QC: Hủy");
    return parts.join("; ");
}

/** Biên bản xử lý ngoại lệ nhập kho (A4): phần thừa/từ chối trả NCC, phần hỏng hủy, kèm người và ghi chú duyệt. */
export function buildExceptionReportHtml(receipt: GoodsReceiptDetail) {
    const lines = receipt.lines.filter(hasLineException);
    const rows = lines
        .map((line, index) => {
            const posted = line.postedQuantity ?? line.qc.passedQuantity;
            return `
        <tr>
            <td class="center">${index + 1}</td>
            <td>${escapeHtml(line.materialName)}<div class="muted">${escapeHtml(line.skuCode)}</div></td>
            <td class="center">${escapeHtml(line.unit)}</td>
            <td class="num">${escapeHtml(formatVietnamNumber(line.orderedQuantity ?? 0))}</td>
            <td class="num">${escapeHtml(formatVietnamNumber(line.receivedQuantity))}</td>
            <td class="num">${escapeHtml(formatVietnamNumber(line.qc.passedQuantity))}</td>
            <td class="num">${escapeHtml(formatVietnamNumber(posted))}</td>
            <td class="num">${escapeHtml(formatVietnamNumber(line.returnedQuantity))}</td>
            <td class="num">${escapeHtml(formatVietnamNumber(line.qc.failedQuantity))}</td>
            <td>${escapeHtml(resolutionText(line))}</td>
            <td>${escapeHtml(line.resolutionNote ?? "")}</td>
        </tr>`;
        })
        .join("");

    const approvalNote = receipt.exceptionApprovalNote
        ? `<p><strong>Ghi chú duyệt:</strong> ${escapeHtml(receipt.exceptionApprovalNote)}</p>`
        : "";

    return `<!DOCTYPE html>
<html lang="vi">
<head>
<meta charset="UTF-8" />
<title>Biên bản xử lý ngoại lệ ${escapeHtml(receipt.code)}</title>
<style>
    @page { size: A4 landscape; margin: 12mm; }
    * { box-sizing: border-box; }
    body { margin: 0; font-family: Arial, Helvetica, sans-serif; font-size: 12px; color: #000; background: #fff; }
    h1 { margin: 0 0 4px; font-size: 18px; text-align: center; text-transform: uppercase; }
    .sub { text-align: center; margin-bottom: 12px; }
    .info { display: grid; grid-template-columns: 1fr 1fr; gap: 2px 24px; margin-bottom: 12px; }
    .info p { margin: 0; }
    table { width: 100%; border-collapse: collapse; }
    th, td { border: 1px solid #000; padding: 4px 6px; vertical-align: top; }
    th { background: #f0f0f0; font-size: 11px; }
    .num { text-align: right; white-space: nowrap; }
    .center { text-align: center; }
    .muted { font-size: 10px; color: #444; }
    .signs { display: grid; grid-template-columns: repeat(4, 1fr); margin-top: 28px; text-align: center; }
    .signs div { min-height: 90px; }
</style>
</head>
<body>
    <h1>Biên bản xử lý ngoại lệ nhập kho</h1>
    <div class="sub">Phiếu nhập: <strong>${escapeHtml(receipt.code)}</strong></div>
    <div class="info">
        <p><strong>Nhà cung cấp:</strong> ${escapeHtml(receipt.supplier?.name)}</p>
        <p><strong>Kho nhận:</strong> ${escapeHtml(receipt.receivingLocation?.name)}</p>
        <p><strong>Người duyệt ngoại lệ:</strong> ${escapeHtml(receipt.exceptionApprovedByName ?? "")}</p>
        <p><strong>Thời gian duyệt:</strong> ${escapeHtml(receipt.exceptionApprovedAt ? formatVietnamDateTime(receipt.exceptionApprovedAt) : "")}</p>
        <p><strong>Người ghi sổ:</strong> ${escapeHtml(receipt.postedByName ?? "")}</p>
        <p><strong>Ngày ghi sổ:</strong> ${escapeHtml(receipt.postedAt ? formatVietnamDateTime(receipt.postedAt) : "")}</p>
        ${approvalNote}
    </div>
    <table>
        <thead>
            <tr>
                <th>STT</th>
                <th>Nguyên liệu</th>
                <th>ĐVT</th>
                <th>Số đặt</th>
                <th>Thực nhận</th>
                <th>Đạt QC</th>
                <th>Nhập kho</th>
                <th>Trả NCC</th>
                <th>Hủy (không đạt QC)</th>
                <th>Cách xử lý</th>
                <th>Ghi chú</th>
            </tr>
        </thead>
        <tbody>
            ${rows || `<tr><td class="center" colspan="11">Phiếu không có ngoại lệ</td></tr>`}
        </tbody>
    </table>
    <div class="signs">
        <div><strong>Đại diện nhà cung cấp</strong><div class="muted">(Ký, ghi rõ họ tên)</div></div>
        <div><strong>Nhân viên QC</strong><div class="muted">(Ký, ghi rõ họ tên)</div></div>
        <div><strong>Thủ kho</strong><div class="muted">(Ký, ghi rõ họ tên)</div></div>
        <div><strong>Quản lý duyệt</strong><div class="muted">(Ký, ghi rõ họ tên)</div></div>
    </div>
</body>
</html>`;
}

/** In biên bản qua iframe ẩn để hộp thoại in chỉ chứa biên bản, không lẫn giao diện quản trị. */
export function printExceptionReport(receipt: GoodsReceiptDetail) {
    const iframe = document.createElement("iframe");
    iframe.setAttribute("aria-hidden", "true");
    iframe.title = "Biên bản xử lý ngoại lệ";
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
        throw new Error("Trình duyệt không cho phép mở khung in biên bản.");
    }

    frameDocument.open();
    frameDocument.write(buildExceptionReportHtml(receipt));
    frameDocument.close();

    frameWindow.addEventListener("afterprint", () => window.setTimeout(() => iframe.remove(), 500));
    window.setTimeout(() => {
        frameWindow.focus();
        frameWindow.print();
        window.setTimeout(() => iframe.remove(), 60_000);
    }, 150);
}
