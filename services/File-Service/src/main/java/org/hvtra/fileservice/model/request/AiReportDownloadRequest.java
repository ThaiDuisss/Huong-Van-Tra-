package org.hvtra.fileservice.model.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AiReportDownloadRequest(@NotNull UUID fileId) { }
