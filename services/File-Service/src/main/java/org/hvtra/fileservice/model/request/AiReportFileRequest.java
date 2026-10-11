package org.hvtra.fileservice.model.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AiReportFileRequest(@NotNull UUID fileId, @NotNull UUID ownerId) { }
