package org.hvtra.commonservice.importing.handler;

import java.util.Map;

public record ParsedImportRow(
        int rowIndex,
        Map<String, Object> payload,
        int occurrenceCount
) {
    public ParsedImportRow(int rowIndex, Map<String, Object> payload) {
        this(rowIndex, payload, 1);
    }
}
