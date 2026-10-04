package pl.formularz.template;

/**
 * The template cannot be filled: unknown placeholder, missing value or malformed block.
 */
public class TemplateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TemplateException(String message) {
        super(message);
    }

    public TemplateException(String message, Throwable cause) {
        super(message, cause);
    }
}
