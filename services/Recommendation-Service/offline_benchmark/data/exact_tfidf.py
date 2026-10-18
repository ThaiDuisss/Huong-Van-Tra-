"""Explicit read-only acquisition or deterministic local snapshot replay."""
from dataclasses import asdict
from datetime import datetime
import json

from app.recommendation.v1_model import CatalogProduct, catalog_fingerprint
from offline_benchmark.common.manifest import digest
from offline_benchmark.common.types import DatabaseSnapshot, PurchaseEvent


def payload(snapshot, products, audit):
    return {'format': 'exact-tfidf-input-v1', 'catalog': [asdict(p) for p in products],
            'purchases': [asdict(e) for e in snapshot.purchases], 'audit': audit}


def replay(path):
    from offline_benchmark.common.types import CatalogProduct as BinaryProduct
    data = json.loads(path.read_text(encoding='utf-8'))
    if data['format'] != 'exact-tfidf-input-v1':
        raise ValueError('Unknown snapshot format')
    products = tuple(CatalogProduct(**{**r, 'ingredient_codes': tuple(r['ingredient_codes'])}) for r in data['catalog'])
    events = tuple(PurchaseEvent(**{**r, 'event_time': datetime.fromisoformat(r['event_time'])}) for r in data['purchases'])
    snapshot = DatabaseSnapshot(events, tuple(BinaryProduct(p.product_id, p.category_code, p.ingredient_codes) for p in products))
    if catalog_fingerprint(products) != data['audit']['catalog_sha256'] or digest([asdict(e) for e in events]) != data['audit']['purchases_sha256']:
        raise ValueError('Snapshot input fingerprints differ from recorded manifest')
    return snapshot, products, data['audit']


def acquire(env_file):
    from sqlalchemy import create_engine
    from sqlalchemy.orm import Session
    from app.core.settings import Settings
    from app.repositories.v1_recommendation_repository import V1RecommendationRepository
    from offline_benchmark.data.db_loader import read_snapshot
    from offline_benchmark.data.prefix_history import fingerprint_tables
    if not env_file.is_file():
        raise ValueError('Explicit existing environment file required')
    settings = Settings(_env_file=env_file)
    engine = create_engine(settings.sqlalchemy_database_url, echo=False, connect_args={'connect_timeout': 10})
    try:
        with engine.connect() as connection:
            connection.exec_driver_sql('SET TRANSACTION ISOLATION LEVEL REPEATABLE READ')
            connection.exec_driver_sql('SET SESSION TRANSACTION READ ONLY')
            connection.exec_driver_sql('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY')
            try:
                if int(connection.exec_driver_sql('SELECT @@transaction_read_only').scalar()) != 1:
                    raise ValueError('Read-only session confirmation failed')
                with Session(bind=connection, autoflush=False) as db:
                    before_counts, before_hashes = fingerprint_tables(db)
                    snapshot = read_snapshot(db)
                    products = V1RecommendationRepository().active_catalog(db)
                    before = [(p.product_id, p.category_code, p.ingredient_codes) for p in snapshot.products]
                    exact = [(p.product_id, p.category_code, p.ingredient_codes) for p in products]
                    if before != exact:
                        raise ValueError('Binary loader and serving catalog projection differ')
                    after_counts, after_hashes = fingerprint_tables(db)
                    if (before_counts, before_hashes) != (after_counts, after_hashes):
                        raise ValueError('Read-only consistent snapshot changed')
                    return snapshot, products, {'database': settings.mysql_database,
                        'server_confirmed_read_only': True, 'table_counts': before_counts,
                        'table_sha256': before_hashes, 'snapshot_stable': True,
                        'catalog_sha256': catalog_fingerprint(products),
                        'purchases_sha256': digest([asdict(e) for e in snapshot.purchases]),
                        'catalog_availability': 'retrospective current catalog, no historical snapshots',
                        'ingestion_availability': 'event_time only, no observed-at reconstruction',
                        'provenance': 'Unverified; seed staging exists, production-real status not established'}
            finally:
                connection.rollback()
    finally:
        engine.dispose()
