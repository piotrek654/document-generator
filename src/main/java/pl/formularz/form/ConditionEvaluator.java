package pl.formularz.form;

import java.util.Map;

/**
 * Evaluates conditional dependency expressions (e.g. "student_checkbox", "!bezrobotny_checkbox").
 */
public final class ConditionEvaluator {

    private ConditionEvaluator() {
    }

    public static boolean isEnabled(String expression, Map<String, Boolean> checkboxStates) {
        if (expression == null || expression.isBlank()) {
            return true;
        }
        String expr = expression.trim();
        boolean negate = isNegated(expr);
        String fieldName = getSourceCheckbox(expr);

        boolean fieldState = checkboxStates != null && Boolean.TRUE.equals(checkboxStates.get(fieldName));
        return negate ? !fieldState : fieldState;
    }

    public static String getSourceCheckbox(String expression) {
        if (expression == null) {
            return "";
        }
        String expr = expression.trim();
        return expr.startsWith("!") ? expr.substring(1).trim() : expr;
    }

    public static boolean isNegated(String expression) {
        return expression != null && expression.trim().startsWith("!");
    }
}
