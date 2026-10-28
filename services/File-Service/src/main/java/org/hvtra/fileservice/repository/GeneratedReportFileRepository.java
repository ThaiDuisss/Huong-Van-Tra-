package org.hvtra.fileservice.repository;

import java.util.UUID;
import org.hvtra.fileservice.model.entity.GeneratedReportFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeneratedReportFileRepository extends JpaRepository<GeneratedReportFile, UUID> { }
