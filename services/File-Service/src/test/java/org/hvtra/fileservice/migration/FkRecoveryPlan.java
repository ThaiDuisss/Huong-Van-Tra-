package org.hvtra.fileservice.migration;

import java.util.List;

/**
 * Test-scope model of the proposed metadata gates. Does not execute SQL or connect
 * to a database; passing these checks does not prove a MySQL migration works.
 */
final class FkRecoveryPlan {
    private static final String EXPECTED_NAME = "fk_file_references_file";

    enum Decision { ADD, NO_OP, STOP }

    record Column(String type, boolean nullable) { }

    record Index(List<String> columns, boolean unique, boolean fullLength, boolean visible, String type) { }

    record ForeignKey(String name, String childTable, List<String> columns,
                      String parentSchema, String parentTable, List<String> parentColumns,
                      String updateRule, String deleteRule) { }

    record Snapshot(String schema, Column child, Column parent, String childEngine, String parentEngine,
                    List<Index> childIndexes, List<Index> parentIndexes, List<ForeignKey> foreignKeys,
                    long orphanCount, long nullFileIds, boolean foreignKeyChecks) { }

    static Decision assess(Snapshot schema) {
        if (!schema.foreignKeyChecks() || schema.orphanCount() != 0 || schema.nullFileIds() != 0
                || !validColumn(schema.child()) || !validColumn(schema.parent())
                || !"InnoDB".equalsIgnoreCase(schema.childEngine())
                || !"InnoDB".equalsIgnoreCase(schema.parentEngine())
                || schema.childIndexes().stream().noneMatch(index -> usableIndex(index)
                    && !index.columns().isEmpty() && "file_id".equals(index.columns().getFirst()))
                || schema.parentIndexes().stream().noneMatch(index -> usableIndex(index)
                    && index.unique() && index.columns().equals(List.of("id")))) {
            return Decision.STOP;
        }

        List<ForeignKey> bindings = schema.foreignKeys().stream()
                .filter(key -> "file_references".equals(key.childTable()) && key.columns().contains("file_id"))
                .toList();
        if (bindings.stream().anyMatch(key -> !equivalent(key, schema.schema()))) {
            return Decision.STOP;
        }
        if (!bindings.isEmpty()) {
            return Decision.NO_OP;
        }
        if (schema.foreignKeys().stream().anyMatch(key -> EXPECTED_NAME.equalsIgnoreCase(key.name()))) {
            return Decision.STOP;
        }
        return Decision.ADD;
    }

    private static boolean validColumn(Column column) {
        return column != null && "binary(16)".equalsIgnoreCase(column.type()) && !column.nullable();
    }

    private static boolean usableIndex(Index index) {
        return index.fullLength() && index.visible() && "BTREE".equalsIgnoreCase(index.type());
    }

    private static boolean equivalent(ForeignKey key, String schema) {
        return key.columns().equals(List.of("file_id"))
                && schema.equals(key.parentSchema()) && "files".equals(key.parentTable())
                && key.parentColumns().equals(List.of("id"))
                && restrictive(key.updateRule()) && restrictive(key.deleteRule());
    }

    private static boolean restrictive(String rule) {
        return "RESTRICT".equalsIgnoreCase(rule) || "NO ACTION".equalsIgnoreCase(rule);
    }
}
