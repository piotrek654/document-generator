package pl.formularz.template;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Fills a document template with form data.
 */
public interface TemplateRenderer {

    void render(InputStream template, TemplateData data, OutputStream output) throws IOException;
}
