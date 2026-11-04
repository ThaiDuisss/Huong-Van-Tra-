"""Read-only, explicitly scoped access and reimport decisions; not an importer."""
from sqlalchemy import select

from app.ingredient_poc.schema import datasets, sale_items


class ImportConflict(ValueError):
    pass


class PocRepository:
    def __init__(self, connection):
        self.connection = connection

    def sales_for_customer(self, dataset_id, customer_id, *, source_kind):
        return list(self.connection.execute(
            select(sale_items).join(datasets, sale_items.c.dataset_id == datasets.c.id)
            .where(sale_items.c.dataset_id == str(dataset_id),
                   sale_items.c.customer_id == str(customer_id),
                   datasets.c.source_kind == source_kind,
                   datasets.c.status == "PUBLISHED")
            .order_by(sale_items.c.purchased_at, sale_items.c.order_item_id)
        ).mappings())

    def reimport_decision(self, contract):
        """No writes. Revision changes never add another copy of the business line."""
        row = self.connection.execute(select(
            sale_items.c.payload_hash, sale_items.c.source_revision,
        ).where(sale_items.c.dataset_id == str(contract.dataset_id),
                sale_items.c.order_id == str(contract.order_id),
                sale_items.c.order_item_id == str(contract.order_item_id))).mappings().first()
        if row is None:
            return "NEW"
        if row["source_revision"] == contract.source_revision and row["payload_hash"] == contract.payload_hash():
            return "UNCHANGED"
        raise ImportConflict("existing source line differs; quarantine or use a new dataset")
