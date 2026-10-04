package pl.formularz.form;

import java.util.ArrayList;
import java.util.List;

/**
 * Form values cannot be used: the message lists every problem, one per line, in Polish for the user.
 */
public class FormException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ArrayList<String> problems;
    private final ArrayList<String> fieldNames;

    public FormException(List<String> problems) {
        this(problems, List.of());
    }

    public FormException(List<String> problems, List<String> fieldNames) {
        super(String.join("\n", problems));
        this.problems = new ArrayList<>(problems);
        this.fieldNames = new ArrayList<>(fieldNames);
    }

    public List<String> problems() {
        return List.copyOf(problems);
    }

    public List<String> fieldNames() {
        return List.copyOf(fieldNames);
    }
}
