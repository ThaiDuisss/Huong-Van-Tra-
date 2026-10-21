package org.hvtra.fileservice.model.response;

import java.util.UUID;

public record AiReportFileResponse(UUID fileId, UUID ownerId, String fileName,
        String fileType, String contentType, long sizeBytes, String status,
        String visibility, boolean deleted) { }
