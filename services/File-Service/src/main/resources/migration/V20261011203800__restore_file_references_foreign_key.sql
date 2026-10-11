-- Restore only the original restrictive relationship; never replace constraints or rows.
-- Requires CREATE ROUTINE, EXECUTE, ALTER ROUTINE, SELECT, ALTER and REFERENCES.
-- No DROP IF EXISTS: an existing helper name must fail without being overwritten.
-- On failure MySQL may retain this helper and a failed Flyway row. Stop and review;
-- do not repair history or automatically remove objects to hide that failure.
DELIMITER $$
CREATE PROCEDURE hvtra_restore_file_reference_fk()
SQL SECURITY INVOKER
BEGIN
    DECLARE binding_count BIGINT DEFAULT 0;

    IF DATABASE() IS NULL OR @@SESSION.foreign_key_checks <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FK recovery: schema/FK checks gate failed';
    END IF;

    IF (SELECT COUNT(*) FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('files', 'file_references')
          AND TABLE_TYPE = 'BASE TABLE' AND ENGINE = 'InnoDB') <> 2 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FK recovery: expected InnoDB tables missing';
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.PARTITIONS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('files', 'file_references')
          AND PARTITION_NAME IS NOT NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FK recovery: partitioned tables unsupported';
    END IF;

    IF (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND ((TABLE_NAME = 'files' AND COLUMN_NAME = 'id')
            OR (TABLE_NAME = 'file_references' AND COLUMN_NAME = 'file_id'))
          AND COLUMN_TYPE = 'binary(16)' AND IS_NULLABLE = 'NO'
          AND COALESCE(GENERATION_EXPRESSION, '') = '') <> 2 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FK recovery: incompatible key columns';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'file_references'
          AND COLUMN_NAME = 'file_id' AND SEQ_IN_INDEX = 1 AND SUB_PART IS NULL
          AND IS_VISIBLE = 'YES' AND INDEX_TYPE = 'BTREE') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FK recovery: child index missing/incompatible';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'files'
        GROUP BY INDEX_NAME
        HAVING COUNT(*) = 1 AND MIN(NON_UNIQUE) = 0 AND MIN(COLUMN_NAME) = 'id'
          AND MIN(SEQ_IN_INDEX) = 1 AND COUNT(SUB_PART) = 0
          AND MIN(IS_VISIBLE) = 'YES' AND MIN(INDEX_TYPE) = 'BTREE') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FK recovery: unique parent index missing';
    END IF;

    IF EXISTS (SELECT 1 FROM file_references r LEFT JOIN files f ON f.id = r.file_id
        WHERE r.file_id IS NULL OR f.id IS NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FK recovery: orphan/null references';
    END IF;

    -- Include every component of any FK containing file_id, not only the matching row.
    IF EXISTS (
        SELECT 1 FROM information_schema.KEY_COLUMN_USAGE k
        JOIN information_schema.REFERENTIAL_CONSTRAINTS rc
          ON rc.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA
          AND rc.TABLE_NAME = k.TABLE_NAME AND rc.CONSTRAINT_NAME = k.CONSTRAINT_NAME
        WHERE k.CONSTRAINT_SCHEMA = DATABASE() AND k.TABLE_NAME = 'file_references'
          AND EXISTS (SELECT 1 FROM information_schema.KEY_COLUMN_USAGE component
              WHERE component.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA
                AND component.TABLE_NAME = k.TABLE_NAME
                AND component.CONSTRAINT_NAME = k.CONSTRAINT_NAME
                AND component.COLUMN_NAME = 'file_id')
        GROUP BY k.CONSTRAINT_NAME
        HAVING COUNT(*) <> 1 OR MIN(k.COLUMN_NAME) <> 'file_id'
          OR MIN(k.REFERENCED_TABLE_SCHEMA) <> DATABASE()
          OR MIN(k.REFERENCED_TABLE_NAME) <> 'files'
          OR MIN(k.REFERENCED_COLUMN_NAME) <> 'id'
          OR MIN(rc.UPDATE_RULE) NOT IN ('RESTRICT', 'NO ACTION')
          OR MIN(rc.DELETE_RULE) NOT IN ('RESTRICT', 'NO ACTION')
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FK recovery: conflicting relationship/actions';
    END IF;

    SELECT COUNT(*) INTO binding_count FROM information_schema.KEY_COLUMN_USAGE
        WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'file_references'
          AND COLUMN_NAME = 'file_id' AND REFERENCED_TABLE_NAME IS NOT NULL;

    IF binding_count = 0 THEN
        IF EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = DATABASE()
              AND CONSTRAINT_NAME = 'fk_file_references_file') THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FK recovery: constraint name collision';
        END IF;

        ALTER TABLE file_references
            ADD CONSTRAINT fk_file_references_file
            FOREIGN KEY (file_id) REFERENCES files(id)
            ON UPDATE RESTRICT ON DELETE RESTRICT;
    END IF;
END$$
DELIMITER ;

CALL hvtra_restore_file_reference_fk();
-- This drops only the helper created above, after successful evaluation.
DROP PROCEDURE hvtra_restore_file_reference_fk;
