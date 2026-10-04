package pl.formularz.pdf;

public class PdfConversionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PdfConversionException(String message, Throwable cause) {
        super(message, cause);
    }
}
