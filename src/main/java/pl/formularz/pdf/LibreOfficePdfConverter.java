package pl.formularz.pdf;

import java.nio.file.Path;

import org.jodconverter.core.office.OfficeException;
import org.jodconverter.core.office.OfficeManager;
import org.jodconverter.core.office.OfficeUtils;
import org.jodconverter.local.LocalConverter;
import org.jodconverter.local.office.LocalOfficeManager;
import org.jodconverter.local.office.LocalOfficeUtils;

/**
 * Converts documents with a headless LibreOffice process that is started once and reused
 * for every conversion, so a batch of documents does not pay the start-up cost each time.
 */
public final class LibreOfficePdfConverter implements PdfConverter, AutoCloseable {

    private final OfficeManager officeManager;

    private LibreOfficePdfConverter(OfficeManager officeManager) {
        this.officeManager = officeManager;
    }

    public static boolean isAvailable() {
        return LocalOfficeUtils.getDefaultOfficeHome() != null;
    }

    public static LibreOfficePdfConverter start() {
        OfficeManager officeManager = LocalOfficeManager.builder().install().build();
        try {
            officeManager.start();
        } catch (OfficeException e) {
            throw new PdfConversionException("Cannot start LibreOffice", e);
        }
        return new LibreOfficePdfConverter(officeManager);
    }

    @Override
    public void convert(Path document, Path pdf) {
        try {
            LocalConverter.make(officeManager).convert(document.toFile()).to(pdf.toFile()).execute();
        } catch (OfficeException e) {
            throw new PdfConversionException("Cannot convert " + document + " to PDF", e);
        }
    }

    @Override
    public void close() {
        OfficeUtils.stopQuietly(officeManager);
    }
}
