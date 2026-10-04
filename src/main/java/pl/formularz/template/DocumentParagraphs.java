package pl.formularz.template;

import java.util.ArrayList;
import java.util.List;

import org.apache.poi.xwpf.usermodel.IBody;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

/**
 * All paragraphs of a document: body, tables (including nested ones), headers and footers.
 */
final class DocumentParagraphs {

    private DocumentParagraphs() {
    }

    static List<XWPFParagraph> of(XWPFDocument document) {
        List<XWPFParagraph> paragraphs = new ArrayList<>();
        collect(document, paragraphs);
        document.getHeaderList().forEach(header -> collect(header, paragraphs));
        document.getFooterList().forEach(footer -> collect(footer, paragraphs));
        return paragraphs;
    }

    private static void collect(IBody body, List<XWPFParagraph> paragraphs) {
        for (IBodyElement element : body.getBodyElements()) {
            if (element instanceof XWPFParagraph paragraph) {
                paragraphs.add(paragraph);
            } else if (element instanceof XWPFTable table) {
                collect(table, paragraphs);
            }
        }
    }

    private static void collect(XWPFTable table, List<XWPFParagraph> paragraphs) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                collect(cell, paragraphs);
            }
        }
    }
}
