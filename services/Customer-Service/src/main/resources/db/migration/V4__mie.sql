alter table customers
    change created_by_id created_by varchar(36) null;

alter table customers
    change updated_by_id updated_by varchar(36) null;