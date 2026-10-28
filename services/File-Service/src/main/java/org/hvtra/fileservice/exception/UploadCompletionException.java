package org.hvtra.fileservice.exception;

import org.hvtra.commonservice.common_core.exception.AppException;

/** Completion rejected after persisting FAILED; other business errors still roll back. */
public class UploadCompletionException extends AppException {
    public UploadCompletionException(int code, String message) {
        super(code, message);
    }
}
