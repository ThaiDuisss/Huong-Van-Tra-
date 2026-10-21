package org.hvtra.fileservice.migration;

import org.hvtra.fileservice.migration.FkRecoveryPlan.Column;
import org.hvtra.fileservice.migration.FkRecoveryPlan.Decision;
import org.hvtra.fileservice.migration.FkRecoveryPlan.ForeignKey;
import org.hvtra.fileservice.migration.FkRecoveryPlan.Index;
import org.hvtra.fileservice.migration.FkRecoveryPlan.Snapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hvtra.fileservice.migration.FkRecoveryPlan.assess;

class FkRecoveryPlanTest {
    private static final String SCHEMA = "isolated_test_db";
    private static final Column BINARY_ID = new Column("binary(16)", false);
    private static final Index CHILD_INDEX = new Index(List.of("file_id"), false, true, true, "BTREE");
    private static final Index PARENT_INDEX = new Index(List.of("id"), true, true, true, "BTREE");

    @Test
    void compatibleExistingSchemaWithoutForeignKeyPlansAnAdd() {
        assertThat(assess(snapshot(List.of(), 0))).isEqualTo(Decision.ADD);
    }

    @Test
    void freshSchemaWithCanonicalForeignKeyPlansNoChange() {
        assertThat(assess(snapshot(List.of(key("fk_file_references_file", "RESTRICT", "RESTRICT")), 0)))
                .isEqualTo(Decision.NO_OP);
    }

    @Test
    void equivalentForeignKeyWithAnotherNamePlansNoRenameOrDrop() {
        assertThat(assess(snapshot(List.of(key("legacy_reference_fk", "NO ACTION", "RESTRICT")), 0)))
                .isEqualTo(Decision.NO_OP);
    }

    @Test
    void multipleEquivalentForeignKeysArePreserved() {
        assertThat(assess(snapshot(List.of(key("first_fk", "RESTRICT", "RESTRICT"),
                key("second_fk", "NO ACTION", "NO ACTION")), 0))).isEqualTo(Decision.NO_OP);
    }

    @Test
    void orphanReferencesStopRatherThanDeleteData() {
        assertThat(assess(snapshot(List.of(), 1))).isEqualTo(Decision.STOP);
    }

    @Test
    void differentReferentialActionsStopRatherThanReplaceAnExistingForeignKey() {
        for (String rule : List.of("CASCADE", "SET NULL", "SET DEFAULT")) {
            assertThat(assess(snapshot(List.of(key("legacy_fk", "RESTRICT", rule)), 0)))
                    .as(rule).isEqualTo(Decision.STOP);
            assertThat(assess(snapshot(List.of(key("legacy_fk", rule, "RESTRICT")), 0)))
                    .as(rule).isEqualTo(Decision.STOP);
        }
    }

    @Test
    void wrongParentAndCrossSchemaBindingsStop() {
        for (ForeignKey key : List.of(
                new ForeignKey("legacy_fk", "file_references", List.of("file_id"), SCHEMA,
                        "other_files", List.of("id"), "RESTRICT", "RESTRICT"),
                new ForeignKey("legacy_fk", "file_references", List.of("file_id"), "another_schema",
                        "files", List.of("id"), "RESTRICT", "RESTRICT"))) {
            assertThat(assess(snapshot(List.of(key), 0))).isEqualTo(Decision.STOP);
        }
    }

    @Test
    void compositeForeignKeyStopsEvenWhenItsNameMatches() {
        ForeignKey composite = new ForeignKey("fk_file_references_file", "file_references",
                List.of("file_id", "ref_id"), SCHEMA, "files", List.of("id", "ref_id"), "RESTRICT", "RESTRICT");
        assertThat(assess(snapshot(List.of(composite), 0))).isEqualTo(Decision.STOP);
    }

    @Test
    void canonicalNameUsedByAnotherTableStopsAnAdd() {
        ForeignKey collision = new ForeignKey("fk_file_references_file", "other_table",
                List.of("file_id"), SCHEMA, "files", List.of("id"), "RESTRICT", "RESTRICT");
        assertThat(assess(snapshot(List.of(collision), 0))).isEqualTo(Decision.STOP);
    }

    @Test
    void compatibleAliasNeedsNoNewNameEvenIfCanonicalNameIsUsedElsewhere() {
        ForeignKey collision = new ForeignKey("fk_file_references_file", "other_table",
                List.of("file_id"), SCHEMA, "files", List.of("id"), "RESTRICT", "RESTRICT");
        assertThat(assess(snapshot(List.of(collision, key("legacy_fk", "RESTRICT", "RESTRICT")), 0)))
                .isEqualTo(Decision.NO_OP);
    }

    @Test
    void mismatchedTypesAndNullableColumnsStop() {
        for (Column column : List.of(new Column("varbinary(16)", false), new Column("binary(32)", false),
                new Column("binary(16)", true))) {
            assertThat(assess(new Snapshot(SCHEMA, column, BINARY_ID, "InnoDB", "InnoDB",
                    List.of(CHILD_INDEX), List.of(PARENT_INDEX), List.of(), 0, 0, true))).isEqualTo(Decision.STOP);
            assertThat(assess(new Snapshot(SCHEMA, BINARY_ID, column, "InnoDB", "InnoDB",
                    List.of(CHILD_INDEX), List.of(PARENT_INDEX), List.of(), 0, 0, true))).isEqualTo(Decision.STOP);
        }
    }

    @Test
    void nonInnoDbEngineStops() {
        assertThat(assess(new Snapshot(SCHEMA, BINARY_ID, BINARY_ID, "MyISAM", "InnoDB",
                List.of(CHILD_INDEX), List.of(PARENT_INDEX), List.of(), 0, 0, true))).isEqualTo(Decision.STOP);
        assertThat(assess(new Snapshot(SCHEMA, BINARY_ID, BINARY_ID, "InnoDB", "MyISAM",
                List.of(CHILD_INDEX), List.of(PARENT_INDEX), List.of(), 0, 0, true))).isEqualTo(Decision.STOP);
    }

    @Test
    void missingRequiredColumnsAndIndexesStop() {
        assertThat(assess(new Snapshot(SCHEMA, null, BINARY_ID, "InnoDB", "InnoDB",
                List.of(CHILD_INDEX), List.of(PARENT_INDEX), List.of(), 0, 0, true))).isEqualTo(Decision.STOP);
        assertThat(assess(new Snapshot(SCHEMA, BINARY_ID, null, "InnoDB", "InnoDB",
                List.of(CHILD_INDEX), List.of(PARENT_INDEX), List.of(), 0, 0, true))).isEqualTo(Decision.STOP);
        assertThat(assess(new Snapshot(SCHEMA, BINARY_ID, BINARY_ID, "InnoDB", "InnoDB",
                List.of(), List.of(PARENT_INDEX), List.of(), 0, 0, true))).isEqualTo(Decision.STOP);
        assertThat(assess(new Snapshot(SCHEMA, BINARY_ID, BINARY_ID, "InnoDB", "InnoDB",
                List.of(CHILD_INDEX), List.of(), List.of(), 0, 0, true))).isEqualTo(Decision.STOP);
    }

    @Test
    void fullLengthCompositeChildIndexWithLeadingFileIdIsUsable() {
        Index composite = new Index(List.of("file_id", "ref_type", "ref_id"), true, true, true, "BTREE");
        assertThat(assess(new Snapshot(SCHEMA, BINARY_ID, BINARY_ID, "InnoDB", "InnoDB",
                List.of(composite), List.of(PARENT_INDEX), List.of(), 0, 0, true))).isEqualTo(Decision.ADD);
    }

    @Test
    void invalidChildIndexesStop() {
        for (Index index : List.of(new Index(List.of("ref_id", "file_id"), false, true, true, "BTREE"),
                new Index(List.of("file_id"), false, false, true, "BTREE"),
                new Index(List.of("file_id"), false, true, false, "BTREE"))) {
            assertThat(assess(new Snapshot(SCHEMA, BINARY_ID, BINARY_ID, "InnoDB", "InnoDB",
                    List.of(index), List.of(PARENT_INDEX), List.of(), 0, 0, true))).isEqualTo(Decision.STOP);
        }
    }

    @Test
    void parentMustHaveAFullUniqueSingleColumnIndex() {
        for (Index index : List.of(new Index(List.of("id"), false, true, true, "BTREE"),
                new Index(List.of("id", "ref_id"), true, true, true, "BTREE"),
                new Index(List.of("id"), true, false, true, "BTREE"))) {
            assertThat(assess(new Snapshot(SCHEMA, BINARY_ID, BINARY_ID, "InnoDB", "InnoDB",
                    List.of(CHILD_INDEX), List.of(index), List.of(), 0, 0, true))).isEqualTo(Decision.STOP);
        }
    }

    @Test
    void disabledForeignKeyChecksAndNullFileIdsStop() {
        assertThat(assess(new Snapshot(SCHEMA, BINARY_ID, BINARY_ID, "InnoDB", "InnoDB",
                List.of(CHILD_INDEX), List.of(PARENT_INDEX), List.of(), 0, 0, false))).isEqualTo(Decision.STOP);
        assertThat(assess(new Snapshot(SCHEMA, BINARY_ID, BINARY_ID, "InnoDB", "InnoDB",
                List.of(CHILD_INDEX), List.of(PARENT_INDEX), List.of(), 0, 1, true))).isEqualTo(Decision.STOP);
    }

    private static Snapshot snapshot(List<ForeignKey> keys, long orphans) {
        return new Snapshot(SCHEMA, BINARY_ID, BINARY_ID, "InnoDB", "InnoDB",
                List.of(CHILD_INDEX), List.of(PARENT_INDEX), keys, orphans, 0, true);
    }

    private static ForeignKey key(String name, String updateRule, String deleteRule) {
        return new ForeignKey(name, "file_references", List.of("file_id"), SCHEMA, "files",
                List.of("id"), updateRule, deleteRule);
    }
}
