-- Internal transfers are a fixed, non-deletable category so they filter like any other group.
alter table category add column system boolean not null default false;

-- Seed the reserved Transfer category once; it is referenced by nature=TRANSFER rows.
insert into category (name, color, sort_order, system)
select 'Transfer', '#78909C', 1000, true
where not exists (select 1 from category where name = 'Transfer');

-- Backfill existing and future transfer legs into that category.
update transaction
set category_id = (select id from category where name = 'Transfer')
where nature = 'TRANSFER' and category_id is null;
