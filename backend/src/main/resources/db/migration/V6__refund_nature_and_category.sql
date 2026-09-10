-- A refund pairs a purchase with the money coming back, so neither leg is real spending or real
-- income. Both legs become nature=REFUND and leave every statistic, exactly as internal transfers
-- do. Statistics already filter by nature, so the concept only needs a nature value, a reserved
-- category and a group column.

-- Reserved categories are looked up by an explicit key rather than by name: the user may rename
-- one, and "the first system category" stops being unique once there are two of them.
alter table category add column system_key varchar(32);

update category set system_key = 'TRANSFER' where system = true;

create unique index idx_category_system_key on category (system_key) where system_key is not null;

-- The nature check was declared inline in V1, so Postgres named it after the column. It has to be
-- dropped and recreated to admit the new value.
alter table transaction drop constraint transaction_nature_check;

alter table transaction add constraint transaction_nature_check
    check (nature in ('INCOME', 'EXPENSE', 'TRANSFER', 'REFUND'));

-- Seed the reserved Refund category once; it is referenced by nature=REFUND rows.
insert into category (name, color, sort_order, system, system_key)
select 'Refund', '#8D6E63', 1001, true, 'REFUND'
where not exists (select 1 from category where system_key = 'REFUND');

-- The two legs of one refund share this id, mirroring transfer_group_id.
alter table transaction add column refund_group_id uuid;

-- Both pairing lookups resolve legs by group, and neither column was indexed.
create index idx_transaction_refund_group_id on transaction (refund_group_id);
create index idx_transaction_transfer_group_id on transaction (transfer_group_id);
