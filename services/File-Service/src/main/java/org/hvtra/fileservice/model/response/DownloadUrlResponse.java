package org.hvtra.fileservice.model.response;

public record DownloadUrlResponse(
        String downloadUrl,
        Integer expiresInSeconds
) {}