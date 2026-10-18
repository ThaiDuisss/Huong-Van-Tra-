package org.hvtra.paymentservice.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Quản lý đã đối soát tay giao dịch đang NEED_REVIEW / chờ: ghi PAID, không phát event (Order-Service tự cập nhật). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ManualPaymentConfirmRequest {
    private String paymentCode;
    private String note;
}
