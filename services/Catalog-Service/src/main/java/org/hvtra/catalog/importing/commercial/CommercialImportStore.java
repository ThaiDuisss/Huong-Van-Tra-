package org.hvtra.catalog.importing.commercial;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.hvtra.catalog.importing.commercial.CommercialImportCodec.bytes;
import static org.hvtra.catalog.importing.commercial.CommercialImportCodec.normalize;
import static org.hvtra.catalog.importing.commercial.CommercialImportCodec.uuid;
import static org.hvtra.catalog.importing.commercial.CommercialImportPlan.Kind;
import static org.hvtra.catalog.importing.commercial.CommercialImportPlan.Row;
import static org.hvtra.catalog.importing.commercial.CommercialImportPlan.VariantInput;

/** Assigned UUID persistence using the same DataSource/transaction as Catalog JPA/outbox. */
@Repository
class CommercialImportStore {
    private static final String TABLES = "('categories','products','product_variants','units','outbox_events',"
            + "'catalog_import_approvals','catalog_import_runs','catalog_import_entries')";
    private final JdbcTemplate jdbc;
    private final CommercialImportCodec codec;

    CommercialImportStore(JdbcTemplate jdbc, CommercialImportCodec codec) {
        this.jdbc = jdbc;
        this.codec = codec;
    }

    boolean usesDataSource(DataSource source) { return source != null && source == jdbc.getDataSource(); }

    boolean hasSerializableWriteTransaction() {
        return TransactionSynchronizationManager.isActualTransactionActive()
                && !TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                && Integer.valueOf(Connection.TRANSACTION_SERIALIZABLE).equals(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel());
    }

    record Snapshot(Map<Kind, Map<UUID, Map<String, Object>>> rows,
                    Map<UUID, Map<String, Object>> units, String targetFingerprint,
                    String schemaFingerprint, Map<String, String> collations,
                    boolean infrastructureReady, List<String> blockers) { }

    Snapshot snapshot() {
        Map<Kind, Map<UUID, Map<String, Object>>> rows = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) rows.put(kind, tableRows(kind.table));
        Map<UUID, Map<String, Object>> units = tableRows("units");
        Map<String, Object> business = new LinkedHashMap<>();
        for (Kind kind : Kind.values()) business.put(kind.table, new ArrayList<>(rows.get(kind).values()));
        business.put("units", new ArrayList<>(units.values()));
        List<Map<String, Object>> tables = query("SELECT TABLE_NAME,ENGINE,TABLE_COLLATION FROM information_schema.TABLES "
                + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN " + TABLES + " ORDER BY TABLE_NAME");
        List<Map<String, Object>> columns = query("SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT,EXTRA,COLLATION_NAME "
                + "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN " + TABLES + " ORDER BY TABLE_NAME,ORDINAL_POSITION");
        List<Map<String, Object>> indexes = query("SELECT TABLE_NAME,INDEX_NAME,NON_UNIQUE,SEQ_IN_INDEX,COLUMN_NAME,SUB_PART "
                + "FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN " + TABLES + " ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX");
        List<Map<String, Object>> constraints = query("SELECT TABLE_NAME,CONSTRAINT_NAME,CONSTRAINT_TYPE FROM information_schema.TABLE_CONSTRAINTS "
                + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN " + TABLES + " ORDER BY TABLE_NAME,CONSTRAINT_NAME");
        List<Map<String, Object>> references = query("SELECT TABLE_NAME,CONSTRAINT_NAME,COLUMN_NAME,ORDINAL_POSITION,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME "
                + "FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN " + TABLES + " ORDER BY TABLE_NAME,CONSTRAINT_NAME,ORDINAL_POSITION");
        List<Map<String, Object>> referenceRules = query("SELECT TABLE_NAME,CONSTRAINT_NAME,UPDATE_RULE,DELETE_RULE,REFERENCED_TABLE_NAME "
                + "FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME IN " + TABLES + " ORDER BY TABLE_NAME,CONSTRAINT_NAME");
        List<Map<String, Object>> triggers = query("SELECT TRIGGER_NAME,EVENT_OBJECT_TABLE,ACTION_TIMING,EVENT_MANIPULATION,ACTION_STATEMENT "
                + "FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE() AND EVENT_OBJECT_TABLE IN " + TABLES + " ORDER BY TRIGGER_NAME");
        List<String> blockers = new ArrayList<>();
        Set<String> found = tables.stream().map(t -> (String) t.get("table_name")).collect(Collectors.toSet());
        boolean infrastructure = found.containsAll(Set.of("catalog_import_approvals", "catalog_import_runs", "catalog_import_entries", "outbox_events"));
        if (!infrastructure) blockers.add("IMPORT_INFRASTRUCTURE_MIGRATION_NOT_APPLIED");
        if (tables.stream().anyMatch(t -> !"InnoDB".equalsIgnoreCase(String.valueOf(t.get("engine"))))) blockers.add("NON_TRANSACTIONAL_ENGINE");
        if (!triggers.isEmpty()) blockers.add("TRIGGERS_REQUIRE_COMPATIBILITY_REVIEW");
        if (constraints.stream().anyMatch(c -> "CHECK".equals(c.get("constraint_type")))) blockers.add("CHECK_CONSTRAINTS_REQUIRE_REVIEW");
        if (!triggerVisibility()) blockers.add("TRIGGER_VISIBILITY_UNVERIFIED");
        String sqlMode = jdbc.queryForObject("SELECT @@SESSION.sql_mode", String.class);
        if (sqlMode == null || (!sqlMode.contains("STRICT_TRANS_TABLES") && !sqlMode.contains("STRICT_ALL_TABLES"))) blockers.add("STRICT_SQL_MODE_REQUIRED");
        Map<String, Map<String, Object>> byColumn = new LinkedHashMap<>();
        Map<String, String> collations = new LinkedHashMap<>();
        for (Map<String, Object> column : columns) {
            String key = column.get("table_name") + "." + column.get("column_name");
            byColumn.put(key, column);
            if (column.get("collation_name") != null) collations.put(key, column.get("collation_name").toString());
        }
        for (String table : List.of("categories", "products", "product_variants", "units")) {
            requiredType(byColumn, table + ".id", "binary(16)", blockers);
            for (String audit : List.of("created_by", "updated_by", "created_at", "updated_at", "version", "is_deleted")) {
                if (!byColumn.containsKey(table + "." + audit)) blockers.add("MISSING_AUDIT_COLUMN:" + table + "." + audit);
            }
        }
        for (String key : List.of("products.category_id", "product_variants.product_id", "product_variants.unit_id", "categories.parent_id")) {
            requiredType(byColumn, key, "binary(16)", blockers);
        }
        for (String key : List.of("quantity", "cost_price", "selling_price")) requiredType(byColumn, "product_variants." + key, "decimal(18,2)", blockers);
        Map<String, String> types = Map.ofEntries(
                Map.entry("categories.category_code", "varchar(50)"), Map.entry("categories.name", "varchar(255)"),
                Map.entry("categories.default_reorder_point", "int"), Map.entry("products.product_code", "varchar(50)"),
                Map.entry("products.name", "varchar(255)"), Map.entry("products.description", "longtext"),
                Map.entry("products.image_url", "varchar(500)"), Map.entry("product_variants.sku", "varchar(100)"),
                Map.entry("product_variants.variant_name", "varchar(255)"), Map.entry("product_variants.barcode", "varchar(100)"),
                Map.entry("product_variants.inventory_policy", "varchar(50)"));
        types.forEach((key, value) -> requiredType(byColumn, key, value, blockers));
        for (Kind kind : Kind.values()) requiredType(byColumn, kind.table + ".status", "varchar(50)", blockers);
        Map<String, List<Map<String, Object>>> uniqueIndexes = indexes.stream().filter(i -> ((Number) i.get("non_unique")).intValue() == 0)
                .collect(Collectors.groupingBy(i -> i.get("table_name") + "." + i.get("index_name")));
        for (Kind kind : Kind.values()) {
            if (!uniqueIndexes.values().stream().anyMatch(group -> group.size() == 1 && kind.table.equals(group.getFirst().get("table_name"))
                    && kind.codeColumn.equals(group.getFirst().get("column_name")) && group.getFirst().get("sub_part") == null)) blockers.add("MISSING_UNIQUE_KEY:" + kind.table);
        }
        requiredUnique("outbox_events", List.of("event_id"), uniqueIndexes, blockers);
        requiredUnique("outbox_events", List.of("event_type", "business_key"), uniqueIndexes, blockers);
        requiredType(byColumn, "outbox_events.id", "binary(16)", blockers);
        requiredType(byColumn, "outbox_events.payload", "json", blockers);
        if (infrastructure) {
            requiredUnique("catalog_import_approvals", List.of("id"), uniqueIndexes, blockers);
            requiredUnique("catalog_import_runs", List.of("plan_hash"), uniqueIndexes, blockers);
            requiredUnique("catalog_import_entries", List.of("source_system", "entity_type", "source_id"), uniqueIndexes, blockers);
            requiredUnique("catalog_import_entries", List.of("entity_type", "target_id"), uniqueIndexes, blockers);
            for (String key : List.of("catalog_import_approvals.id", "catalog_import_approvals.approved_by",
                    "catalog_import_runs.approval_id", "catalog_import_runs.executed_by", "catalog_import_entries.source_id",
                    "catalog_import_entries.target_id", "catalog_import_entries.approval_id", "catalog_import_entries.executed_by")) requiredType(byColumn, key, "binary(16)", blockers);
            for (String key : List.of("catalog_import_approvals.plan_hash", "catalog_import_approvals.actions_hash",
                    "catalog_import_approvals.target_fingerprint", "catalog_import_approvals.schema_fingerprint",
                    "catalog_import_runs.plan_hash", "catalog_import_runs.actions_hash", "catalog_import_runs.before_fingerprint",
                    "catalog_import_runs.after_fingerprint", "catalog_import_entries.payload_hash", "catalog_import_entries.plan_hash")) requiredType(byColumn, key, "char(64)", blockers);
            for (String key : List.of("commercial_decision_ref", "backup_recovery_ref", "downstream_verification_ref", "source_revalidation_ref")) requiredType(byColumn, "catalog_import_approvals." + key, "varchar(500)", blockers);
            for (String key : List.of("approved_at", "expires_at", "source_revalidated_at")) requiredType(byColumn, "catalog_import_approvals." + key, "datetime(6)", blockers);
            requiredType(byColumn, "catalog_import_entries.outbox_event_id", "varchar(36)", blockers);
        }
        // Omitted columns may use only the technical defaults attested by the audited profile.
        Map<String, String> defaults = Map.of("product_type", "FINISHED_PRODUCT", "pack_quantity", "1",
                "sellable_quantity", "0.00", "production_threshold", "0.00", "production_batch_quantity", "1.00", "custom_bundle", "0");
        defaults.forEach((name, expected) -> {
            Map<String, Object> c = byColumn.get("product_variants." + name);
            if (c == null && Set.of("product_type", "pack_quantity").contains(name)) return;
            if (c == null || !expected.equals(String.valueOf(c.get("column_default")))) blockers.add("TECHNICAL_DEFAULT_CHANGED:" + name);
        });
        for (String table : List.of("categories", "products", "product_variants")) {
            Map<String, Object> legacy = byColumn.get(table + ".update_at");
            if (legacy != null && (!"YES".equals(legacy.get("is_nullable")) || legacy.get("column_default") != null
                    || !"".equals(legacy.get("extra")))) blockers.add("LEGACY_TIMESTAMP_POLICY_CHANGED:" + table);
        }
        Map<String, Object> schema = Map.of("tables", tables, "columns", columns, "indexes", indexes, "constraints", constraints,
                "references", references, "referenceRules", referenceRules, "triggers", triggers, "sqlMode", sqlMode == null ? "UNKNOWN" : sqlMode);
        return new Snapshot(rows, units, codec.hash(normalize(business)), codec.hash(normalize(schema)), collations, infrastructure, List.copyOf(blockers));
    }

    private static void requiredType(Map<String, Map<String, Object>> columns, String key, String type, List<String> blockers) {
        if (!columns.containsKey(key) || !type.equalsIgnoreCase(String.valueOf(columns.get(key).get("column_type")))) blockers.add("UNSUPPORTED_COLUMN_TYPE:" + key);
    }

    private static void requiredUnique(String table, List<String> columns, Map<String, List<Map<String, Object>>> indexes, List<String> blockers) {
        boolean found = indexes.values().stream().anyMatch(group -> table.equals(group.getFirst().get("table_name"))
                && group.stream().map(i -> i.get("column_name")).toList().equals(columns)
                && group.stream().allMatch(i -> i.get("sub_part") == null));
        if (!found) blockers.add("MISSING_UNIQUE_KEY:" + table + "." + String.join("+", columns));
    }

    private boolean triggerVisibility() {
        String schema = jdbc.queryForObject("SELECT DATABASE()", String.class);
        // Raw SHOW GRANTS strings remain only in memory, never returned or logged.
        return jdbc.queryForList("SHOW GRANTS").stream().flatMap(m -> m.values().stream()).map(Object::toString).anyMatch(grant -> {
            String text = grant.replace("`", "").toUpperCase(Locale.ROOT);
            return (text.contains(" ON *.* ") || text.contains(" ON " + schema.toUpperCase(Locale.ROOT) + ".* "))
                    && (text.startsWith("GRANT ALL PRIVILEGES ") || text.substring(0, Math.max(0, text.indexOf(" ON "))).matches(".*\\bTRIGGER\\b.*"));
        });
    }

    private Map<UUID, Map<String, Object>> tableRows(String table) {
        Map<UUID, Map<String, Object>> rows = new LinkedHashMap<>();
        for (Map<String, Object> row : query("SELECT * FROM " + table + " ORDER BY id")) rows.put(uuid((byte[]) row.get("id")), row);
        return rows;
    }

    List<UUID> textMatches(Kind kind, String column, String value) {
        if (!List.of(kind.codeColumn, kind.nameColumn, "barcode").contains(column) || (column.equals("barcode") && kind != Kind.VARIANT)) throw new IllegalArgumentException("UNSUPPORTED_TEXT_LOOKUP");
        if (value == null) return List.of();
        return jdbc.query("SELECT id FROM " + kind.table + " WHERE " + column + "=? ORDER BY id",
                (rs, row) -> uuid(rs.getBytes(1)), value);
    }

    List<Map<String, Object>> sourceCodeCollisions(Kind kind, List<Row> rows, Snapshot snapshot) {
        return sourceTextCollisions(kind, rows, snapshot, kind.codeColumn);
    }

    List<Map<String, Object>> sourceBarcodeCollisions(List<Row> rows, Snapshot snapshot) {
        return sourceTextCollisions(Kind.VARIANT, rows, snapshot, "barcode");
    }

    private List<Map<String, Object>> sourceTextCollisions(Kind kind, List<Row> rows, Snapshot snapshot, String column) {
        List<Row> present = rows.stream().filter(r -> textValue(r, column) != null).toList();
        if (present.size() < 2) return List.of();
        String collation = snapshot.collations().get(kind.table + "." + column);
        if (collation == null || !collation.matches("[A-Za-z0-9_]+")) throw new IllegalStateException("TARGET_COLLATION_UNVERIFIED");
        String derived = present.stream().map(r -> "SELECT ? AS entity_id, CONVERT(? USING utf8mb4) COLLATE " + collation + " AS code_value").collect(Collectors.joining(" UNION ALL "));
        List<Object> params = new ArrayList<>();
        for (int copy = 0; copy < 2; copy++) for (Row row : present) {
            params.add(row.id() == null ? "MISSING" : row.id().toString()); params.add(textValue(row, column));
        }
        return query("SELECT a.entity_id AS left_id,b.entity_id AS right_id FROM (" + derived + ") a JOIN (" + derived
                + ") b ON a.code_value=b.code_value WHERE a.entity_id<b.entity_id ORDER BY a.entity_id,b.entity_id", params.toArray());
    }

    private String textValue(Row row, String column) {
        return column.equals("barcode") && row instanceof VariantInput variant ? variant.barcode() : row.code();
    }

    Optional<Map<String, Object>> entry(String source, Row row) {
        return query("SELECT * FROM catalog_import_entries WHERE source_system=? AND entity_type=? AND source_id=?",
                source, row.kind().name(), bytes(row.id())).stream().findFirst();
    }

    Optional<Map<String, Object>> run(String planHash) {
        return query("SELECT * FROM catalog_import_runs WHERE plan_hash=?", planHash).stream().findFirst();
    }

    Map<String, Object> lockApproval(UUID approvalId) {
        return query("SELECT a.*, (expires_at>CURRENT_TIMESTAMP(6)) AS approval_unexpired, "
                + "(source_revalidated_at<=CURRENT_TIMESTAMP(6) AND source_revalidated_at>DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 15 MINUTE)) AS source_preflight_fresh "
                + "FROM catalog_import_approvals a WHERE id=? FOR UPDATE", bytes(approvalId)).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("EXECUTION_APPROVAL_NOT_FOUND"));
    }

    boolean matches(Row row, Snapshot state) {
        Map<String, Object> actual = state.rows().get(row.kind()).get(row.id());
        if (actual == null || !active(actual)) return false;
        for (Map.Entry<String, Object> value : codec.payload(row).entrySet()) {
            if (!Objects.equals(normalize(value.getValue()), normalize(actual.get(value.getKey())))) return false;
        }
        return true;
    }

    static boolean active(Map<String, Object> row) {
        return row != null && "ACTIVE".equals(row.get("status")) && (Boolean.FALSE.equals(row.get("is_deleted"))
                || row.get("is_deleted") instanceof Number n && n.intValue() == 0);
    }

    boolean outboxExists(String eventId, UUID variantId) {
        if (eventId == null) return false;
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE event_id=? AND aggregate_id=? AND event_type='PRODUCT_VARIANT_CREATED'",
                Integer.class, eventId, variantId.toString());
        return Integer.valueOf(1).equals(count);
    }

    void insert(Row row, UUID actor, Instant now) {
        requireWriteTransaction();
        Map<String, Object> values = codec.payload(row);
        values.put("created_by", bytes(actor)); values.put("updated_by", bytes(actor));
        values.put("created_at", Timestamp.from(now)); values.put("updated_at", Timestamp.from(now));
        values.put("version", 0L); values.put("is_deleted", false);
        String columns = String.join(",", values.keySet());
        String placeholders = values.keySet().stream().map(k -> "?").collect(Collectors.joining(","));
        jdbc.update("INSERT INTO " + row.kind().table + " (" + columns + ") VALUES (" + placeholders + ")", values.values().toArray());
    }

    void saveEntry(String source, Row row, String rowHash, String planHash, UUID approvalId, UUID actor, String eventId, Instant now) {
        requireWriteTransaction();
        jdbc.update("INSERT INTO catalog_import_entries(source_system,entity_type,source_id,target_id,payload_hash,plan_hash,approval_id,executed_by,outbox_event_id,created_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                source, row.kind().name(), bytes(row.id()), bytes(row.id()), rowHash, planHash, bytes(approvalId), bytes(actor), eventId, Timestamp.from(now));
    }

    void saveApproval(UUID id, String planHash, String actionsHash, String beforeHash, String schemaHash, UUID actor,
                      CommercialImportAdapter.ApprovalEvidence evidence, Instant now) {
        requireWriteTransaction();
        jdbc.update("INSERT INTO catalog_import_approvals(id,plan_hash,actions_hash,target_fingerprint,schema_fingerprint,approved_by,approved_at,expires_at,commercial_decision_ref,backup_recovery_ref,downstream_verification_ref,source_revalidation_ref,source_revalidated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                bytes(id), planHash, actionsHash, beforeHash, schemaHash, bytes(actor), Timestamp.from(now), Timestamp.from(evidence.expiresAt()),
                evidence.commercialDecisionReference(), evidence.backupRecoveryReference(), evidence.downstreamVerificationReference(),
                evidence.sourceRevalidationReference(), Timestamp.from(evidence.sourceRevalidatedAt()));
    }

    void saveRun(String planHash, String source, String actionsHash, UUID approvalId, UUID actor, String before, String after, int count, Instant now) {
        requireWriteTransaction();
        jdbc.update("INSERT INTO catalog_import_runs(plan_hash,source_system,actions_hash,approval_id,executed_by,before_fingerprint,after_fingerprint,entity_count,completed_at) VALUES (?,?,?,?,?,?,?,?,?)",
                planHash, source, actionsHash, bytes(approvalId), bytes(actor), before, after, count, Timestamp.from(now));
    }

    private static void requireWriteTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) throw new IllegalStateException("IMPORT_WRITE_TRANSACTION_REQUIRED");
    }

    private List<Map<String, Object>> query(String sql, Object... params) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> raw : jdbc.queryForList(sql, params)) {
            Map<String, Object> row = new LinkedHashMap<>();
            raw.forEach((key, value) -> row.put(key.toLowerCase(Locale.ROOT), value)); result.add(row);
        }
        return result;
    }
}
