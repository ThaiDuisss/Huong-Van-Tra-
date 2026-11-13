import { X, ShieldCheck } from "lucide-react";

import { Button, IconButton } from "../../../components/ui/Button";
import { ModalShell } from "../../../components/ui/Overlay";
import styles from "../pages/CreateReturnPage.module.css";

interface ReturnPolicyModalProps {
    isOpen: boolean;
    onClose: () => void;
    policies?: string[];
}

const DEFAULT_POLICIES = [
    "Hàng hoàn phải đúng phân loại, còn nguyên tem/seal hoặc đúng tình trạng khai báo ban đầu.",
    "Hàng hoàn sẽ được bộ phận kiểm định (QC) đánh giá chất lượng trước khi xử lý nhập kho.",
    "Số lượng hoàn không được vượt quá số lượng còn lại có thể hoàn của đơn hàng.",
    "Tiền hoàn (nếu có) sẽ được kích hoạt sau khi kiểm kho hoàn tất theo quy định của cửa hàng."
];

export function ReturnPolicyModal({
    isOpen,
    onClose,
    policies = []
}: ReturnPolicyModalProps) {
    if (!isOpen) return null;

    const displayPolicies = policies.length > 0 ? policies : DEFAULT_POLICIES;

    return (
        <ModalShell
            className={styles.modalDialog}
            labelledBy="return-policy-modal-title"
            onClose={onClose}
        >
                <div className={styles.modalHeader}>
                    <div className={styles.modalTitleWrap}>
                        <ShieldCheck size={20} className={styles.modalIcon} aria-hidden="true" />
                        <h2 id="return-policy-modal-title">Chính sách hoàn hàng chi tiết</h2>
                    </div>
                    <IconButton aria-label="Đóng chính sách" onClick={onClose}>
                        <X size={18} aria-hidden="true" />
                    </IconButton>
                </div>

                <div className={styles.modalBody}>
                    <p className={styles.modalIntro}>
                        Các điều khoản và quy định kiểm định khi tiếp nhận hàng hoàn từ khách hàng:
                    </p>
                    <ul className={styles.policyList}>
                        {displayPolicies.map((item, index) => (
                            <li key={index}>{item}</li>
                        ))}
                    </ul>
                </div>

                <div className={styles.modalFooter}>
                    <Button onClick={onClose} variant="primary">
                        Đã hiểu
                    </Button>
                </div>
        </ModalShell>
    );
}

