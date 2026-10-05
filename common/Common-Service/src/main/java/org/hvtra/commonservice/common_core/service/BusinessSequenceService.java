package org.hvtra.commonservice.common_core.service;

import org.hvtra.commonservice.common_core.entity.BusinessSequence;
import org.hvtra.commonservice.common_core.repository.BusinessSequenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service quản lý bộ đếm dùng chung (business_sequences).
 * Cung cấp các method sinh mã tăng dần an toàn trong môi trường đa luồng.
 */
@Service
public class BusinessSequenceService {

    private final BusinessSequenceRepository repository;

    public BusinessSequenceService(BusinessSequenceRepository repository) {
        this.repository = repository;
    }

    /**
     * Lấy giá trị kế tiếp của bộ đếm {@code key} (bắt đầu từ 1). Dòng bộ đếm bị khóa (SELECT ... FOR UPDATE)
     * đến hết transaction của nơi gọi, nên các request đồng thời không bao giờ nhận trùng số.
     * Nếu transaction của nơi gọi rollback thì số cũng được trả lại (không nhảy số).
     */
    @Transactional
    public long next(String key) {
        repository.insertIfAbsent(key);
        BusinessSequence sequence = repository.findBySequenceKeyWithLock(key)
                .orElseThrow(() -> new IllegalStateException("Không khởi tạo được bộ đếm: " + key));
        long nextValue = sequence.getCurrentValue() + 1;
        sequence.setCurrentValue(nextValue);
        repository.save(sequence);
        return nextValue;
    }

    /** Sinh mã dạng {@code KEY-00001} với số chữ số tối thiểu {@code digits}. */
    public String nextCode(String key, int digits) {
        return String.format("%s-%0" + digits + "d", key, next(key));
    }
}
