"""Bounded MySQL SELECT reader. No ORM, initialization or write transaction."""
from dataclasses import dataclass, field


class SourceReadError(ValueError):
    """Sanitized error: never include SQL results, credentials or driver messages."""

    def __init__(self, code):
        self.code = code
        super().__init__(code)


@dataclass(repr=False)
class HistoricalSource:
    tables: dict = field(default_factory=dict)
    metadata: dict = field(default_factory=dict)


# Statements are fixed; a caller cannot provide SQL or an arbitrary table name.
PROJECTIONS = {
    "lfm_seed_ord": "ord_no,HEX(id) AS id_hex,cust_no,event_time,status,payment_status,n_lines",
    "lfm_seed_ord_line": "ord_no,variant_no,qty",
    "lfm_seed_cust": "cust_no,HEX(id) AS id_hex",
    "lfm_seed_variant": "variant_no,HEX(id) AS id_hex,HEX(product_id) AS product_hex,pack_key",
    "interaction_events": "event_type,order_id,source_event_id,order_item_id,customer_id,product_id,product_variant_id,quantity,event_time,created_at",
    "products": "id",
    "product_variants": "id,product_id,variant_name",
    "report_purchase_orders": "id,source_event_id,evidence_hash,`lines`",
}
ORDER_KEYS = dict(lfm_seed_ord="ord_no", lfm_seed_ord_line="ord_no,variant_no",
                  lfm_seed_cust="cust_no", lfm_seed_variant="variant_no",
                  interaction_events="id", products="id", product_variants="id",
                  report_purchase_orders="id")


def read_historical_source(settings, *, connect=None, max_rows=100000, timeout_ms=8000):
    if settings.mysql_database != "recommend_app":
        raise SourceReadError("unexpected_database_configuration")
    if not 1 <= max_rows <= 300000 or not 1 <= timeout_ms <= 30000:
        raise SourceReadError("invalid_read_bound")
    if connect is None:
        import pymysql
        connect = pymysql.connect
    import pymysql
    connection = None
    try:
        connection = connect(host=settings.mysql_host, port=settings.mysql_port,
            user=settings.mysql_user, password=settings.mysql_password.get_secret_value(),
            database=settings.mysql_database, charset="utf8mb4", connect_timeout=5,
            read_timeout=max(10, timeout_ms // 1000 + 2), write_timeout=5,
            autocommit=False, cursorclass=pymysql.cursors.DictCursor)
        with connection.cursor() as cursor:
            cursor.execute("SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ")
            cursor.execute("SET SESSION TRANSACTION READ ONLY")
            cursor.execute("SET SESSION MAX_EXECUTION_TIME=%s", (timeout_ms,))
            cursor.execute("START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY")
            queries = []

            def select(sql, parameters=None, bound=max_rows):
                # Defense in depth around fixed queries, not a general SQL parser.
                if not sql.startswith("SELECT ") or any(word in sql.upper() for word in
                        (" FOR UPDATE", " INTO OUTFILE", " INTO DUMPFILE", ";")):
                    raise SourceReadError("non_read_query_rejected")
                cursor.execute(sql, parameters)
                rows = cursor.fetchall()
                if len(rows) > bound:
                    raise SourceReadError("source_row_bound_exceeded")
                queries.append(dict(sql=sql, rows_read=len(rows)))
                return rows

            identity = select("SELECT DATABASE() AS db,VERSION() AS version,"
                "@@session.time_zone AS session_timezone,@@system_time_zone AS system_timezone,"
                "@@session.transaction_read_only AS read_only,UTC_TIMESTAMP(6) AS audit_utc", bound=1)[0]
            if identity["db"] != "recommend_app" or identity["read_only"] != 1:
                raise SourceReadError("database_identity_or_read_only_mismatch")
            engines = select("SELECT TABLE_NAME AS name,ENGINE AS engine FROM information_schema.TABLES "
                "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ("
                + ",".join("%s" for _ in PROJECTIONS) + ")", tuple(PROJECTIONS))
            if {row["name"] for row in engines if row["engine"] == "InnoDB"} != set(PROJECTIONS):
                raise SourceReadError("consistent_snapshot_requires_innodb_tables")
            column_metadata = select("SELECT TABLE_NAME AS table_name,COLUMN_NAME AS column_name,"
                "COLUMN_TYPE AS column_type,DATETIME_PRECISION AS datetime_precision,"
                "COLUMN_COMMENT AS comment FROM information_schema.COLUMNS "
                "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ("
                + ",".join("%s" for _ in PROJECTIONS) + ") ORDER BY TABLE_NAME,ORDINAL_POSITION", tuple(PROJECTIONS))
            tables, counts = {}, {}
            for table, columns in PROJECTIONS.items():
                count = select(f"SELECT COUNT(*) AS n FROM {table}", bound=1)[0]["n"]
                if count > max_rows:
                    raise SourceReadError("source_row_bound_exceeded")
                rows = select(f"SELECT {columns} FROM {table} ORDER BY {ORDER_KEYS[table]} LIMIT %s",
                              (max_rows + 1,))
                if len(rows) != count:
                    raise SourceReadError("incomplete_source_snapshot")
                counts[table], tables[table] = count, rows
            for table in ("recommendation_snapshots", "customer_interactions"):
                counts[table] = select(f"SELECT COUNT(*) AS n FROM {table}", bound=1)[0]["n"]
            # Only target line identities are needed for collision checks. Bound JSON
            # cardinality too: a bounded number of orders can still contain many lines.
            import json
            target_lines = 0
            for row in tables["report_purchase_orders"]:
                value = json.loads(row["lines"]) if isinstance(row["lines"], str) else row["lines"]
                if not isinstance(value, list):
                    raise SourceReadError("invalid_existing_projection_lines")
                target_lines += len(value)
                if target_lines > max_rows * 5:
                    raise SourceReadError("target_line_bound_exceeded")
                row["lines"] = value
            return HistoricalSource(tables, dict(identity=identity, counts=counts,
                columns=column_metadata, engines=engines, queries=queries, max_rows=max_rows, timeout_ms=timeout_ms))
    except SourceReadError:
        raise
    except Exception as error:
        # MySQL error numbers are safe diagnostics; never print driver messages.
        number = error.args[0] if error.args and isinstance(error.args[0], int) else None
        code = "historical_source_read_failed" + ("_mysql_" + str(number) if number else "")
        raise SourceReadError(code) from None
    finally:
        if connection is not None:
            try:
                connection.rollback()
            except Exception:
                raise SourceReadError("read_only_rollback_failed") from None
            finally:
                try:
                    connection.close()
                except Exception:
                    raise SourceReadError("read_only_close_failed") from None
