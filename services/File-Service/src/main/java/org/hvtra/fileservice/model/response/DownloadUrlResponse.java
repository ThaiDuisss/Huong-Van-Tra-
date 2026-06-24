package org.hvtra.fileservice.model.response;

import org.hvtra.fileservice.model.enums.FileVisibility;

public record DownloadUrlResponse(
        String url,
        Integer expiresInSeconds,
        FileVisibility visibility
) {
}