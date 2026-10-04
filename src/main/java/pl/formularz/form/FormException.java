package pl.formularz.form;

import java.util.List;

/**
 * Form values cannot be used: the message lists every problem, one per line, in Polish for the user.
 */
public class FormException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FormException(List<String> problems) {
        super(String.join("\n", problems));
    }
}
