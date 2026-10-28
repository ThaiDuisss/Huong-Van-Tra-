"""Deterministic SQL rendering for review/tests; never opens a DB connection."""
from sqlalchemy.dialects import mysql
from sqlalchemy.schema import CreateIndex, CreateTable

from app.ingredient_poc.schema import metadata


def _trigger(name, table, operation, body):
    return (f"CREATE TRIGGER {name} BEFORE {operation} ON {table}\n"
            f"FOR EACH ROW\nBEGIN\n{body}\nEND$$")


def mysql_migration():
    # Plain Flyway SQL, without DBAPI pyformat percent escaping.
    dialect = mysql.dialect(paramstyle="named")
    statements = [
        "-- Ingredient research only. No V1 tables, data, grants or cross-service FKs.",
        "-- Requires MySQL >= 8.0.16 (enforced CHECK constraints).",
        "-- Never run against a shared database without a separate approved task.",
    ]
    for table in metadata.sorted_tables:
        statements.append(str(CreateTable(table).compile(dialect=dialect)).strip() + ";")
        statements.extend(str(CreateIndex(index).compile(dialect=dialect)) + ";"
                          for index in sorted(table.indexes, key=lambda x: x.name))
    triggers = []
    fail = "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = "
    triggers.append(_trigger("tr_ip_datasets_insert", "ingredient_poc_datasets", "INSERT",
                            "  IF NEW.status <> 'STAGED' THEN\n    " + fail
                            + "'dataset must start STAGED';\n  END IF;"))
    triggers.append(_trigger("tr_ip_datasets_update", "ingredient_poc_datasets", "UPDATE", """
  IF OLD.status IN ('PUBLISHED','REJECTED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'terminal dataset is immutable';
  END IF;
  IF NOT (NEW.id <=> OLD.id) OR NOT (NEW.source_kind <=> OLD.source_kind)
     OR NOT (NEW.source_system <=> OLD.source_system)
     OR NOT (NEW.schema_version <=> OLD.schema_version)
     OR NOT (NEW.dataset_name <=> OLD.dataset_name)
     OR NOT (NEW.source_snapshot_hash <=> OLD.source_snapshot_hash)
     OR NOT (NEW.created_at <=> OLD.created_at) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'dataset identity is immutable';
  END IF;
  IF NOT ((OLD.status = 'STAGED' AND NEW.status IN ('STAGED','VALIDATED','REJECTED'))
       OR (OLD.status = 'VALIDATED' AND NEW.status IN ('PUBLISHED','REJECTED'))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'invalid dataset transition';
  END IF;
  IF OLD.status = 'VALIDATED' AND (
       NOT (NEW.manifest_json <=> OLD.manifest_json)
       OR NOT (NEW.validation_policy_version <=> OLD.validation_policy_version)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'validated manifest is immutable';
  END IF;
  IF NEW.status IN ('VALIDATED','PUBLISHED') AND (
       COALESCE(JSON_TYPE(JSON_EXTRACT(NEW.manifest_json,'$.validation_complete')),'') <> 'BOOLEAN'
       OR COALESCE(JSON_UNQUOTE(JSON_EXTRACT(NEW.manifest_json,'$.validation_complete')),'false') <> 'true') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'validated manifest required';
  END IF;""".strip()))
    triggers.append(_trigger("tr_ip_datasets_delete", "ingredient_poc_datasets", "DELETE", """
  IF OLD.status IN ('PUBLISHED','VALIDATED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cannot delete frozen dataset';
  END IF;""".strip()))
    for table in metadata.sorted_tables:
        short = table.name.removeprefix("ingredient_poc_")
        if short == "datasets":
            continue
        for operation in ("INSERT", "UPDATE", "DELETE"):
            row = "OLD" if operation == "DELETE" else "NEW"
            allowed = "('STAGED','REJECTED')" if operation == "DELETE" else "('STAGED')"
            body = f"""  DECLARE parent_status VARCHAR(16);
  SELECT status INTO parent_status FROM ingredient_poc_datasets
    WHERE id = {row}.dataset_id FOR UPDATE;
  IF parent_status IS NULL OR parent_status NOT IN {allowed} THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'dataset is not writable';
  END IF;"""
            if operation == "UPDATE":
                body += """
  IF NOT (NEW.id <=> OLD.id) OR NOT (NEW.dataset_id <=> OLD.dataset_id)
     OR NOT (NEW.source_kind <=> OLD.source_kind) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'research identity is immutable';
  END IF;"""
            if short in ("recipes", "recipe_items") and operation != "INSERT":
                recipe = "OLD.id" if short == "recipes" else "OLD.recipe_id"
                body += f"""
  IF EXISTS (SELECT 1 FROM ingredient_poc_sale_items WHERE recipe_id = {recipe})
     OR EXISTS (SELECT 1 FROM ingredient_poc_recipe_snapshots WHERE recipe_id = {recipe}) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'referenced recipe snapshot is immutable';
  END IF;"""
            if short == "recipe_items" and operation != "DELETE":
                body += """
  IF EXISTS (SELECT 1 FROM ingredient_poc_sale_items WHERE recipe_id = NEW.recipe_id)
     OR EXISTS (SELECT 1 FROM ingredient_poc_recipe_snapshots WHERE recipe_id = NEW.recipe_id) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'referenced recipe snapshot is immutable';
  END IF;"""
            if short == "units" and operation == "UPDATE":
                body += """
  IF EXISTS (SELECT 1 FROM ingredient_poc_recipe_items WHERE unit_id = OLD.id)
     OR EXISTS (SELECT 1 FROM ingredient_poc_variants WHERE sale_unit_id = OLD.id)
     OR EXISTS (SELECT 1 FROM ingredient_poc_recipes WHERE portion_unit_id = OLD.id) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'referenced unit snapshot is immutable';
  END IF;"""
            if short == "recipe_snapshots" and operation == "UPDATE":
                body += "\n  " + fail + "'actual snapshot is append-only';"
            if short in ("sale_items", "recipe_snapshots") and operation != "DELETE":
                body += """
  IF NEW.recipe_id IS NOT NULL AND (
       NOT EXISTS (SELECT 1 FROM ingredient_poc_recipes
                   WHERE id = NEW.recipe_id AND validation_status = 'VALIDATED')
       OR NOT EXISTS (SELECT 1 FROM ingredient_poc_recipe_items WHERE recipe_id = NEW.recipe_id)
       OR EXISTS (SELECT 1 FROM ingredient_poc_recipe_items
                  WHERE recipe_id = NEW.recipe_id AND validation_status <> 'VALIDATED')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'validated nonempty recipe required';
  END IF;"""
            if short == "sale_items" and operation == "UPDATE":
                body += "\n  " + fail + "'purchase facts are append-only; use a new dataset';"
            triggers.append(_trigger("tr_ip_" + short + "_" + operation.lower(),
                                     table.name, operation, body))
    statements.extend(["DELIMITER $$", "\n\n".join(triggers), "DELIMITER ;"])
    return "\n\n".join(statements) + "\n"
