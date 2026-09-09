package com.financedashboard.domain.port;

import com.financedashboard.domain.merchant_rule.MerchantRule;
import java.util.List;
import java.util.Optional;

/** Outbound port for storing and reading {@link MerchantRule} aggregates. */
public interface MerchantRuleRepository {

    /** Returns all rules, in insertion order. */
    List<MerchantRule> findAll();

    /** Returns the rule with the given id, if present. */
    Optional<MerchantRule> findById(Long id);

    /** Saves the rule and returns its stored state (with the generated id). */
    MerchantRule save(MerchantRule rule);

    /** Returns whether a rule with exactly the given normalized merchant already exists. */
    boolean existsByMerchant(String merchant);

    /** Returns whether a rule with the given id exists. */
    boolean existsById(Long id);

    /** Deletes the rule with the given id. */
    void deleteById(Long id);
}
