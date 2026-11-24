package org.hvtra.catalog.taste;

import org.hvtra.commonservice.common_core.exception.BaseException;
import org.springframework.http.HttpStatus;

final class TasteSnapshotFailure extends BaseException {
    private TasteSnapshotFailure(String code, String message, HttpStatus status) {
        super(code, message, status);
    }

    static TasteSnapshotFailure capacity() {
        return new TasteSnapshotFailure("TASTE_SNAPSHOT_CAPACITY_ERROR", "Taste snapshot exceeds export capacity",
                HttpStatus.PAYLOAD_TOO_LARGE);
    }

    static TasteSnapshotFailure invalid() {
        return new TasteSnapshotFailure("TASTE_SNAPSHOT_INVALID", "Catalog taste snapshot is inconsistent",
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    static TasteSnapshotFailure unavailable() {
        return new TasteSnapshotFailure("TASTE_SNAPSHOT_AUTH_UNAVAILABLE", "Taste snapshot credentials unavailable",
                HttpStatus.SERVICE_UNAVAILABLE);
    }
}
