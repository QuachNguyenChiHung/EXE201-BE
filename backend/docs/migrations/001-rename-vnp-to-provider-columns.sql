ALTER TABLE transactions RENAME COLUMN vnp_txn_ref TO provider_txn_ref;
ALTER TABLE transactions RENAME COLUMN vnp_transaction_no TO provider_transaction_no;
ALTER TABLE transactions RENAME COLUMN vnp_pay_date TO provider_pay_date;
