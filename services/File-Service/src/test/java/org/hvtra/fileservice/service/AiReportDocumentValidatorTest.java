package org.hvtra.fileservice.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class AiReportDocumentValidatorTest {
    private final AiReportDocumentValidator validator = new AiReportDocumentValidator();

    byte[] workbook(Map<String, String> replacements) throws Exception {
        Map<String, String> contents = new HashMap<>(Map.of(
            "[Content_Types].xml", "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/></Types>",
            "_rels/.rels", "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>",
            "xl/workbook.xml", "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheets/></workbook>"));
        contents.putAll(replacements);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (var entry : contents.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    @Test void pdfHeaderAccepted() {
        assertDoesNotThrow(() -> validator.validate("%PDF-1.7\nreport".getBytes(StandardCharsets.UTF_8), "PDF"));
    }
    @Test void fakePdfRejected() {
        assertThrows(ResponseStatusException.class, () -> validator.validate("not a PDF".getBytes(StandardCharsets.UTF_8), "PDF"));
    }
    @Test void validOpenXmlAccepted() throws Exception {
        assertDoesNotThrow(() -> validator.validate(workbook(Map.of()), "XLSX"));
    }
    @Test void plainZipIsNotXlsx() {
        assertThrows(ResponseStatusException.class, () -> validator.validate(new byte[]{80, 75, 3, 4}, "XLSX"));
    }
    @Test void zipBombRejected() {
        assertThrows(ResponseStatusException.class, () -> validator.validate(workbook(Map.of("bomb.bin", "a".repeat(9 * 1024 * 1024))), "XLSX"));
    }
    @Test void traversalRejected() {
        assertThrows(ResponseStatusException.class, () -> validator.validate(workbook(Map.of("../outside", "bad")), "XLSX"));
    }
    @Test void xmlExternalEntityRejected() {
        assertThrows(ResponseStatusException.class, () -> validator.validate(workbook(Map.of("xl/workbook.xml",
                "<!DOCTYPE workbook [<!ENTITY x SYSTEM 'file:///secret'>]><workbook>&x;</workbook>")), "XLSX"));
    }
    @Test void macroWorkbookRejected() {
        assertThrows(ResponseStatusException.class, () -> validator.validate(workbook(Map.of("xl/vbaProject.bin", "macro")), "XLSX"));
    }
    @Test void wrongWorkbookTypeRejected() {
        assertThrows(ResponseStatusException.class, () -> validator.validate(workbook(Map.of("[Content_Types].xml",
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>")), "XLSX"));
    }
    @Test void emptyAndOversizedRejected() {
        assertThrows(ResponseStatusException.class, () -> validator.validate(new byte[0], "PDF"));
        assertThrows(ResponseStatusException.class, () -> validator.validate(new byte[AiReportDocumentValidator.MAX_BYTES + 1], "PDF"));
    }
    @Test void truncatedArchiveRejected() throws Exception {
        byte[] bytes = workbook(Map.of());
        assertThrows(ResponseStatusException.class, () -> validator.validate(Arrays.copyOf(bytes, bytes.length - 22), "XLSX"));
    }
    @Test void excessiveEntryCountRejected() {
        Map<String, String> entries = new HashMap<>();
        for (int i = 0; i < 513; i++) entries.put("entry" + i, "data");
        assertThrows(ResponseStatusException.class, () -> validator.validate(workbook(entries), "XLSX"));
    }
}
