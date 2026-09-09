-- Per-currency transaction values, baked at import at each transaction's own date.
-- One row per supported currency per transaction; the native currency row equals transaction.amount.
create table transaction_amount (
    transaction_id bigint        not null references transaction (id) on delete cascade,
    currency       varchar(3)    not null,
    amount         numeric(19,4) not null,
    primary key (transaction_id, currency)
);

create index idx_transaction_amount_currency on transaction_amount (currency);

-- The import-time PLN snapshot is replaced by transaction_amount rows.
alter table transaction drop column base_amount;
alter table transaction drop column fx_rate;
alter table transaction drop column fx_rate_date;
