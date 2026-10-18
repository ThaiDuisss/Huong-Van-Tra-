package org.hvtra.fileservice.model.response;

public record AiReportDownloadResponse(String url, int expiresInSeconds,
        String fileName, String contentType, long sizeBytes, String etag) { }
