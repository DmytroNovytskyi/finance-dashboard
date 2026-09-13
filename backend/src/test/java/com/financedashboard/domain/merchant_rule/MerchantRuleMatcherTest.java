package com.financedashboard.domain.merchant_rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MerchantRuleMatcherTest {

    private static final long GROCERIES = 1L;
    private static final long ENTERTAINMENT = 2L;
    private static final long CLOTHING = 3L;

    private static MerchantRule rule(long id, String merchant, MatchType type, long categoryId) {
        return MerchantRule.builder()
                .id(id)
                .merchant(merchant)
                .matchType(type)
                .categoryId(categoryId)
                .build();
    }

    @Test
    void equalsClaimsOnlyTheSameCounterparty() {
        List<MerchantRule> rules = List.of(rule(1, "EXAMPLE SHOP", MatchType.EQUALS, ENTERTAINMENT));

        assertThat(MerchantRuleMatcher.categoryFor("EXAMPLE SHOP", rules)).isEqualTo(ENTERTAINMENT);
        assertThat(MerchantRuleMatcher.categoryFor("EXAMPLE SHOP SP. Z O.O.", rules)).isNull();
    }

    @Test
    void startsWithClaimsCounterpartiesThatBeginWithTheText() {
        List<MerchantRule> rules = List.of(rule(1, "EXAMPLE SHOP", MatchType.STARTS_WITH, ENTERTAINMENT));

        assertThat(MerchantRuleMatcher.categoryFor("EXAMPLE SHOP SP. Z O.O.", rules)).isEqualTo(ENTERTAINMENT);
        assertThat(MerchantRuleMatcher.categoryFor("CARD PAYMENT EXAMPLE SHOP", rules)).isNull();
    }

    @Test
    void containsClaimsACounterpartyThatWrapsTheText() {
        List<MerchantRule> rules = List.of(rule(1, "EXAMPLE SHOP", MatchType.CONTAINS, ENTERTAINMENT));

        assertThat(MerchantRuleMatcher.categoryFor("EXAMPLE SHOP SP. Z O.O.", rules)).isEqualTo(ENTERTAINMENT);
        assertThat(MerchantRuleMatcher.categoryFor("CARD PAYMENT EXAMPLE SHOP", rules)).isEqualTo(ENTERTAINMENT);
        assertThat(MerchantRuleMatcher.categoryFor("EXAMPLE MARKET", rules)).isNull();
    }

    @Test
    void noRuleClaimsACounterpartyWhenThereAreNoRules() {
        assertThat(MerchantRuleMatcher.categoryFor("EXAMPLE SHOP", List.of())).isNull();
    }

    @Test
    void theNarrowerRuleWinsOverTheBroaderOneThatAlsoMatches() {
        List<MerchantRule> rules = List.of(
                rule(1, "EXAMPLE SHOP", MatchType.CONTAINS, ENTERTAINMENT),
                rule(2, "EXAMPLE SHOP SP. Z O.O.", MatchType.EQUALS, CLOTHING));

        assertThat(MerchantRuleMatcher.categoryFor("EXAMPLE SHOP SP. Z O.O.", rules)).isEqualTo(CLOTHING);
    }

    @Test
    void amongEquallyNarrowRulesTheLongerTextWins() {
        List<MerchantRule> rules = List.of(
                rule(1, "EXAMPLE SHOP", MatchType.CONTAINS, ENTERTAINMENT),
                rule(2, "EXAMPLE SHOP SP", MatchType.CONTAINS, CLOTHING));

        assertThat(MerchantRuleMatcher.categoryFor("EXAMPLE SHOP SP. Z O.O.", rules)).isEqualTo(CLOTHING);
    }

    @Test
    void theOutcomeDoesNotDependOnTheOrderTheRulesArriveIn() {
        MerchantRule broad = rule(1, "EXAMPLE SHOP", MatchType.CONTAINS, ENTERTAINMENT);
        MerchantRule narrow = rule(2, "EXAMPLE SHOP SP", MatchType.CONTAINS, CLOTHING);

        assertThat(MerchantRuleMatcher.categoryFor("EXAMPLE SHOP SP. Z O.O.", List.of(broad, narrow)))
                .isEqualTo(MerchantRuleMatcher.categoryFor("EXAMPLE SHOP SP. Z O.O.", List.of(narrow, broad)))
                .isEqualTo(CLOTHING);
    }

    @Test
    void aBroaderRuleStillClaimsWhatTheNarrowerOneDoesNot() {
        List<MerchantRule> rules = List.of(
                rule(1, "EXAMPLE SHOP", MatchType.CONTAINS, ENTERTAINMENT),
                rule(2, "EXAMPLE SHOP SP. Z O.O.", MatchType.EQUALS, CLOTHING));

        assertThat(MerchantRuleMatcher.categoryFor("CARD PAYMENT EXAMPLE SHOP", rules))
                .isEqualTo(ENTERTAINMENT);
    }

    @Test
    void anEqualsRuleOutranksStartsWithAndContainsAfterIt() {
        List<MerchantRule> rules = List.of(
                rule(1, "SHOP", MatchType.CONTAINS, GROCERIES),
                rule(2, "SHOP", MatchType.STARTS_WITH, ENTERTAINMENT),
                rule(3, "SHOPPING", MatchType.EQUALS, CLOTHING));

        assertThat(MerchantRuleMatcher.categoryFor("SHOPPING", rules)).isEqualTo(CLOTHING);
        assertThat(MerchantRuleMatcher.categoryFor("SHOP AROUND", rules)).isEqualTo(ENTERTAINMENT);
        assertThat(MerchantRuleMatcher.categoryFor("MY SHOP", rules)).isEqualTo(GROCERIES);
    }
}
