"""Optional bounded SELECT-only reconciliation. Never infers cups from variant units."""
from dataclasses import asdict
from decimal import Decimal
from pathlib import Path
from uuid import UUID

from offline_benchmark.common.manifest import digest
from offline_benchmark.common.types import PurchaseEvent


def probe_quantity(dataset, env_file, *, connect=None):
    """Missing authoritative UOM evidence always leaves workbook quantity N/A."""
    connection = None
    try:
        from app.core.settings import Settings
        import pymysql
        if not Path(env_file).is_file():
            raise ValueError("missing_explicit_environment_file")
        settings = Settings(_env_file=Path(env_file))
        if settings.mysql_database != "recommend_app":
            raise ValueError("unexpected_database_configuration")
        connection = (connect or pymysql.connect)(
            host=settings.mysql_host, port=settings.mysql_port, user=settings.mysql_user,
            password=settings.mysql_password.get_secret_value(), database=settings.mysql_database,
            charset="utf8mb4", connect_timeout=5, read_timeout=10, write_timeout=5,
            autocommit=False, cursorclass=pymysql.cursors.DictCursor)
        with connection.cursor() as cursor:
            cursor.execute("SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ")
            cursor.execute("SET SESSION TRANSACTION READ ONLY")
            cursor.execute("SET SESSION MAX_EXECUTION_TIME=8000")
            cursor.execute("START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY")
            cursor.execute("SELECT DATABASE() AS db,@@session.transaction_read_only AS ro")
            identity = cursor.fetchone()
            if identity != {"db": "recommend_app", "ro": 1}:
                raise ValueError("read_only_identity_not_confirmed")
            cursor.execute("SELECT TABLE_NAME AS name,ENGINE AS engine FROM information_schema.TABLES "
                           "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('interaction_events','product_variants')")
            if {r['name'] for r in cursor.fetchall() if r['engine']=='InnoDB'} != {'interaction_events','product_variants'}:
                raise ValueError("nontransactional_source_tables")
            cursor.execute("SELECT id,customer_id,product_id,event_time,order_id,event_type,"
                           "product_variant_id,quantity,source_event_id,order_item_id FROM interaction_events "
                           "WHERE event_type='PURCHASE' AND customer_id IS NOT NULL AND TRIM(customer_id)<>'' "
                           "ORDER BY id LIMIT 100001")
            rows = cursor.fetchall()
            if len(rows) > 100000:
                raise ValueError("quantity_source_row_bound_exceeded")
            events = [PurchaseEvent(r['id'],r['customer_id'],r['product_id'],r['event_time'],
                                    r['order_id'],r['event_type']) for r in rows]
            if digest([asdict(e) for e in events]) != dataset.manifest['history_sha256']:
                raise ValueError("live_history_differs_from_frozen_archive")
            cursor.execute("SELECT id,product_id FROM product_variants ORDER BY id LIMIT 100001")
            variants = cursor.fetchall()
            if len(variants)>100000 or len({v['id'] for v in variants})!=len(variants):
                raise ValueError("invalid_variant_projection")
            mapping = {v['id']:v['product_id'] for v in variants}
            event_ids, lines, source_orders = set(), set(), {}
            for row in rows:
                if row['id'] in event_ids or row['order_item_id'] in lines:
                    raise ValueError("duplicate_source_identity")
                event_ids.add(row['id']); lines.add(row['order_item_id'])
                for key in ('id','product_variant_id','product_id','source_event_id','order_item_id','order_id'):
                    if str(UUID(row[key])) != row[key]:
                        raise ValueError("invalid_source_identity")
                if source_orders.setdefault(row['source_event_id'],row['order_id']) != row['order_id']:
                    raise ValueError("source_event_reused_across_orders")
                value=Decimal(row['quantity'])
                if not value.is_finite() or value<=0 or mapping.get(row['product_variant_id'])!=row['product_id']:
                    raise ValueError("invalid_quantity_or_variant_mapping")
            test_ids={e.event_id for s in dataset.splits for e in s.test}
            if not test_ids<=event_ids:
                raise ValueError("incomplete_test_quantity_projection")
            projection=[{k:str(row[k]) for k in ('id','product_variant_id','product_id','quantity',
                                                'source_event_id','order_item_id','order_id')}
                        for row in rows if row['id'] in test_ids]
            return {"status":"NOT_VERIFIED", "reason":"authoritative_unit_provenance_unavailable",
                    "mapping_one_to_one":True, "test_events":len(projection),
                    "projection_sha256":digest(projection), "unit_status":"NOT_VERIFIED",
                    "workbook_quantity":"N/A", "transaction_read_only":True}
    except Exception as exc:
        code = exc.args[0] if exc.args and isinstance(exc.args[0],int) else None
        reason = str(exc) if type(exc) is ValueError else "mysql_"+str(code) if code else type(exc).__name__
        return {"status":"NOT_VERIFIED", "reason":reason, "workbook_quantity":"N/A"}
    finally:
        if connection is not None:
            try:
                connection.rollback()
            finally:
                connection.close()
