package org.hvtra.fileservice.config;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;

class HistoricalMigrationIntegrityTest {
    @Test
    void appliedV1ThroughV6RetainTheirVerifiedFlywayChecksums() throws Exception {
        Map<String, Integer> historical = Map.of(
                "V1__init_schema.sql", 1563888655,
                "V2__create_file_references_if_missing.sql", 1876724228,
                "V3__fix_audit_column_types.sql", -1382391432,
                "V4__update_files_table_schema.sql", 1408072548,
                "V5__delete_column_refType.sql", -93814498,
                "V6__create_business_sequences.sql", -478525518);
        for (Map.Entry<String, Integer> migration : historical.entrySet()) {
            String source = Files.readString(Path.of("src/main/resources/migration", migration.getKey()));
            if (source.startsWith("\uFEFF")) {
                source = source.substring(1);
            }
            CRC32 checksum = new CRC32();
            source.lines().forEach(line -> checksum.update(line.getBytes(StandardCharsets.UTF_8)));
            assertThat((int) checksum.getValue()).as(migration.getKey()).isEqualTo(migration.getValue());
        }
    }

    @Test
    void restoredV6RetainsTheExactVerifiedGitBlobBytes() throws Exception {
        byte[] source = Files.readAllBytes(Path.of("src/main/resources/migration/V6__create_business_sequences.sql"));
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source));
        assertThat(sha256).isEqualTo("3a158eba8385819faf2ad208247b38d26f38635dc786afa0a0be11d0cbb5490b");
    }
}
