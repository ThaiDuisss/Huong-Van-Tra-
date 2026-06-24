alter table customer_tiers
    change created_by_id created_by varchar(36) null;

alter table customer_tiers
    change updated_by_id updated_by varchar(36) null;

alter table customer_addresses
    change created_by_id created_by varchar(36) null;

alter table customer_addresses
    change updated_by_id updated_by varchar(36) null;

alter table loyalty_accounts
    change created_by_id created_by varchar(36) null;

alter table loyalty_accounts
    change updated_by_id updated_by varchar(36) null;

alter table loyalty_transactions
    change created_by_id created_by varchar(36) null;

alter table loyalty_transactions
    change updated_by_id updated_by varchar(36) null;