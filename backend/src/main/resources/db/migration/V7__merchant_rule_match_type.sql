-- Adds how a merchant default compares its text with a counterparty: EQUALS (the only behaviour
-- that existed until now), STARTS_WITH or CONTAINS.
--
-- NOT NULL DEFAULT 'EQUALS' is deliberate and load-bearing. Every rule already stored was written
-- when matching could only be exact, so the default is what makes this migration a no-op for them:
-- they read back as EQUALS, MerchantRuleMatcher resolves them exactly as before, and no existing
-- default changes which transactions it claims.
ALTER TABLE merchant_rule
    ADD COLUMN match_type VARCHAR(16) NOT NULL DEFAULT 'EQUALS';
