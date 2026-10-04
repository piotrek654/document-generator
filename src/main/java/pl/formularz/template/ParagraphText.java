package pl.formularz.template;

import java.util.List;

import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;

/**
 * Paragraph text seen as a single string, even though Word splits it into many runs
 * (e.g. "{imi" + "e" + "}"). Operation offsets refer to {@link #text()}.
 */
class ParagraphText {

    private final XWPFParagraph paragraph;

    ParagraphText(XWPFParagraph paragraph) {
        this.paragraph = paragraph;
    }

    String text() {
        StringBuilder text = new StringBuilder();
        for (XWPFRun run : paragraph.getRuns()) {
            text.append(run.text());
        }
        return text.toString();
    }

    /** Replaces characters [start, end); the new text takes the formatting of the run where the range starts. */
    void replace(int start, int end, String replacement) {
        int runStart = 0;
        boolean inserted = false;
        for (XWPFRun run : paragraph.getRuns()) {
            String runText = run.text();
            int runEnd = runStart + runText.length();
            if (runEnd > start && runStart < end) {
                int from = Math.max(start, runStart) - runStart;
                int to = Math.min(end, runEnd) - runStart;
                String inserting = inserted ? "" : replacement;
                setText(run, runText.substring(0, from) + inserting + runText.substring(to));
                inserted = true;
            }
            runStart = runEnd;
        }
    }

    /** Strikes through characters [start, end), splitting runs at the range boundaries. */
    void strike(int start, int end) {
        splitAt(end);
        splitAt(start);
        int runStart = 0;
        for (XWPFRun run : paragraph.getRuns()) {
            int runEnd = runStart + run.text().length();
            if (runStart >= start && runEnd <= end && runEnd > runStart) {
                run.setStrikeThrough(true);
            }
            runStart = runEnd;
        }
    }

    private void splitAt(int offset) {
        List<XWPFRun> runs = paragraph.getRuns();
        int runStart = 0;
        for (int i = 0; i < runs.size(); i++) {
            XWPFRun run = runs.get(i);
            String runText = run.text();
            int runEnd = runStart + runText.length();
            if (offset > runStart && offset < runEnd) {
                XWPFRun tail = paragraph.insertNewRun(i + 1);
                copyFormatting(run, tail);
                setText(run, runText.substring(0, offset - runStart));
                setText(tail, runText.substring(offset - runStart));
                return;
            }
            runStart = runEnd;
        }
    }

    private static void copyFormatting(XWPFRun source, XWPFRun target) {
        CTRPr properties = source.getCTR().getRPr();
        if (properties != null) {
            target.getCTR().setRPr((CTRPr) properties.copy());
        }
    }

    private static void setText(XWPFRun run, String text) {
        CTR ctr = run.getCTR();
        while (ctr.sizeOfTArray() > 1) {
            ctr.removeT(ctr.sizeOfTArray() - 1);
        }
        run.setText(text, 0);
    }
}
