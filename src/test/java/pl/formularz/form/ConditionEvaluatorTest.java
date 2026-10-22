package pl.formularz.form;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConditionEvaluatorTest {

    @Test
    void evaluatesEmptyOrNullExpressionAsAlwaysTrue() {
        assertThat(ConditionEvaluator.isEnabled(null, Map.of())).isTrue();
        assertThat(ConditionEvaluator.isEnabled("", Map.of())).isTrue();
        assertThat(ConditionEvaluator.isEnabled("   ", Map.of())).isTrue();
    }

    @Test
    void evaluatesPositiveCondition() {
        Map<String, Boolean> states = Map.of("student", true, "zatrudniony", false);

        assertThat(ConditionEvaluator.isEnabled("student", states)).isTrue();
        assertThat(ConditionEvaluator.isEnabled("zatrudniony", states)).isFalse();
        assertThat(ConditionEvaluator.isEnabled("nieznany", states)).isFalse();
    }

    @Test
    void evaluatesNegatedCondition() {
        Map<String, Boolean> states = Map.of("bezrobotny", true, "student", false);

        assertThat(ConditionEvaluator.isEnabled("!bezrobotny", states)).isFalse();
        assertThat(ConditionEvaluator.isEnabled("!student", states)).isTrue();
        assertThat(ConditionEvaluator.isEnabled("!nieznany", states)).isTrue();
    }

    @Test
    void extractsSourceAndNegation() {
        assertThat(ConditionEvaluator.getSourceCheckbox("!bezrobotny")).isEqualTo("bezrobotny");
        assertThat(ConditionEvaluator.getSourceCheckbox("  !student  ")).isEqualTo("student");
        assertThat(ConditionEvaluator.getSourceCheckbox("zatrudniony")).isEqualTo("zatrudniony");

        assertThat(ConditionEvaluator.isNegated("!bezrobotny")).isTrue();
        assertThat(ConditionEvaluator.isNegated("  !student")).isTrue();
        assertThat(ConditionEvaluator.isNegated("student")).isFalse();
        assertThat(ConditionEvaluator.isNegated(null)).isFalse();
    }
}
