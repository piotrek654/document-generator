package pl.formularz.pdf;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * Analyzes rendered PDF documents to extract text and detect vector-based strikethrough lines.
 * Uses a geometric approach matching text character bounding boxes against horizontal vector paths.
 */
public final class PdfStrikeAnalyzer {

    /** Represents a character with its position and strikethrough status. */
    public record CharacterInfo(String unicode, float x, float yTop, float width, float height, boolean struck) {}

    /** Result of analyzing a single page of a PDF document. */
    public record PageAnalysis(int pageNumber, List<CharacterInfo> characters, List<LineSegment> strikeLines) {

        public String allText() {
            return characters.stream().map(CharacterInfo::unicode).collect(Collectors.joining());
        }

        public String struckText() {
            return characters.stream()
                .filter(CharacterInfo::struck)
                .map(CharacterInfo::unicode)
                .collect(Collectors.joining());
        }

        public String unstruckText() {
            return characters.stream()
                .filter(c -> !c.struck())
                .map(CharacterInfo::unicode)
                .collect(Collectors.joining());
        }
    }

    /** A horizontal or near-horizontal 2D line segment in top-down coordinates. */
    public record LineSegment(float x1, float y1, float x2, float y2) {

        public float minX() {
            return Math.min(x1, x2);
        }

        public float maxX() {
            return Math.max(x1, x2);
        }

        public float minY() {
            return Math.min(y1, y2);
        }

        public float maxY() {
            return Math.max(y1, y2);
        }

        public boolean isHorizontal(float maxSlopeAngleDegrees) {
            float dx = Math.abs(x2 - x1);
            float dy = Math.abs(y2 - y1);
            if (dx < 0.001f) {
                return false;
            }
            double angle = Math.toDegrees(Math.atan2(dy, dx));
            return angle <= maxSlopeAngleDegrees;
        }
    }

    private final List<PageAnalysis> pages;

    private PdfStrikeAnalyzer(List<PageAnalysis> pages) {
        this.pages = pages;
    }

    public static PdfStrikeAnalyzer analyze(Path pdfPath) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdfPath.toFile())) {
            return analyze(doc);
        }
    }

    public static PdfStrikeAnalyzer analyze(byte[] pdfBytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            return analyze(doc);
        }
    }

    public static PdfStrikeAnalyzer analyze(File file) throws IOException {
        try (PDDocument doc = Loader.loadPDF(file)) {
            return analyze(doc);
        }
    }

    public static PdfStrikeAnalyzer analyze(PDDocument document) throws IOException {
        List<PageAnalysis> pages = new ArrayList<>();
        int pageCount = document.getNumberOfPages();

        for (int i = 0; i < pageCount; i++) {
            PDPage page = document.getPage(i);
            int pageNumber = i + 1;

            // 1. Extract vector graphics / line segments on this page
            List<LineSegment> lines = extractVectorLines(page);

            // 2. Extract characters with positions on this page and match with lines
            List<CharacterInfo> characters = extractCharacters(document, pageNumber, lines);

            pages.add(new PageAnalysis(pageNumber, characters, lines));
        }

        return new PdfStrikeAnalyzer(Collections.unmodifiableList(pages));
    }

    public List<PageAnalysis> pages() {
        return pages;
    }

    public PageAnalysis page(int pageNumber) {
        if (pageNumber < 1 || pageNumber > pages.size()) {
            throw new IndexOutOfBoundsException("Page " + pageNumber + " out of bounds (1.." + pages.size() + ")");
        }
        return pages.get(pageNumber - 1);
    }

    /** Returns all text in the document. */
    public String allText() {
        return pages.stream().map(PageAnalysis::allText).collect(Collectors.joining("\n"));
    }

    /** Returns all struck characters in the document joined together. */
    public String struckText() {
        return pages.stream().map(PageAnalysis::struckText).collect(Collectors.joining());
    }

    /** Returns all unstruck characters in the document joined together. */
    public String unstruckText() {
        return pages.stream().map(PageAnalysis::unstruckText).collect(Collectors.joining());
    }

    /**
     * Checks whether the exact phrase appears in the document and all of its non-whitespace characters are struck through.
     */
    public boolean isPhraseStruck(String phrase) {
        return phraseMatchStatus(phrase) == StrikeStatus.ALL_STRUCK;
    }

    /**
     * Checks whether the exact phrase appears in the document and none of its characters are struck through.
     */
    public boolean isPhraseUnstruck(String phrase) {
        return phraseMatchStatus(phrase) == StrikeStatus.NONE_STRUCK;
    }

    public enum StrikeStatus {
        NOT_FOUND,
        ALL_STRUCK,
        NONE_STRUCK,
        PARTIALLY_STRUCK
    }

    /**
     * Determines whether occurrences of a phrase in the document are struck through.
     */
    public StrikeStatus phraseMatchStatus(String phrase) {
        Objects.requireNonNull(phrase, "phrase must not be null");
        String normalizedPhrase = phrase.replaceAll("\\s+", " ").trim();
        if (normalizedPhrase.isEmpty()) {
            return StrikeStatus.NOT_FOUND;
        }

        for (PageAnalysis page : pages) {
            StrikeStatus status = matchPhraseOnPage(page, normalizedPhrase);
            if (status != StrikeStatus.NOT_FOUND) {
                return status;
            }
        }
        return StrikeStatus.NOT_FOUND;
    }

    private static StrikeStatus matchPhraseOnPage(PageAnalysis page, String normalizedPhrase) {
        List<CharacterInfo> chars = page.characters();
        StringBuilder sb = new StringBuilder();
        List<Integer> charIndices = new ArrayList<>();

        for (int i = 0; i < chars.size(); i++) {
            String u = chars.get(i).unicode();
            if (u != null && !u.isEmpty()) {
                sb.append(u);
                for (int k = 0; k < u.length(); k++) {
                    charIndices.add(i);
                }
            }
        }

        String fullPageText = sb.toString();
        // Try exact match first
        int idx = fullPageText.indexOf(normalizedPhrase);
        if (idx == -1) {
            // Try normalized spaces match
            String collapsed = fullPageText.replaceAll("\\s+", " ");
            idx = collapsed.indexOf(normalizedPhrase);
            if (idx == -1) {
                return StrikeStatus.NOT_FOUND;
            }
            // If matched on collapsed, find matching characters
            return evaluateMatchOnPage(chars, fullPageText, normalizedPhrase);
        }

        int startCharIdx = charIndices.get(idx);
        int endCharIdx = charIndices.get(Math.min(idx + normalizedPhrase.length() - 1, charIndices.size() - 1));

        int nonWhitespaceCount = 0;
        int struckCount = 0;

        for (int i = startCharIdx; i <= endCharIdx; i++) {
            CharacterInfo ci = chars.get(i);
            if (!Character.isWhitespace(ci.unicode().charAt(0))) {
                nonWhitespaceCount++;
                if (ci.struck()) {
                    struckCount++;
                }
            }
        }

        if (nonWhitespaceCount == 0) {
            return StrikeStatus.NOT_FOUND;
        }
        if (struckCount == nonWhitespaceCount) {
            return StrikeStatus.ALL_STRUCK;
        }
        if (struckCount == 0) {
            return StrikeStatus.NONE_STRUCK;
        }
        return StrikeStatus.PARTIALLY_STRUCK;
    }

    private static StrikeStatus evaluateMatchOnPage(List<CharacterInfo> chars, String pageText, String phrase) {
        // Simplified fallback substring search ignoring multiple whitespaces
        String sanitizedPage = pageText.replace('\n', ' ').replace('\r', ' ');
        int found = sanitizedPage.indexOf(phrase);
        if (found == -1) {
            return StrikeStatus.NOT_FOUND;
        }
        return StrikeStatus.NOT_FOUND;
    }

    private static List<LineSegment> extractVectorLines(PDPage page) throws IOException {
        PDRectangle cropBox = page.getCropBox() != null ? page.getCropBox() : page.getMediaBox();
        float pageHeight = cropBox.getHeight();

        List<LineSegment> segments = new ArrayList<>();

        PDFGraphicsStreamEngine engine = new PDFGraphicsStreamEngine(page) {
            private Point2D currentPoint = new Point2D.Float(0, 0);
            private Point2D subpathStart = new Point2D.Float(0, 0);
            private final List<Point2D> currentSubpath = new ArrayList<>();

            @Override
            public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
                // p0..p3 in PDF user space coordinates
                float minX = (float) Math.min(Math.min(p0.getX(), p1.getX()), Math.min(p2.getX(), p3.getX()));
                float maxX = (float) Math.max(Math.max(p0.getX(), p1.getX()), Math.max(p2.getX(), p3.getX()));
                float minY = (float) Math.min(Math.min(p0.getY(), p1.getY()), Math.min(p2.getY(), p3.getY()));
                float maxY = (float) Math.max(Math.max(p0.getY(), p1.getY()), Math.max(p2.getY(), p3.getY()));

                float width = maxX - minX;
                float height = maxY - minY;

                // If it's a thin horizontal rectangle (e.g., strikethrough bar)
                if (width > 2.0f && height < 5.0f) {
                    float midY = (minY + maxY) / 2.0f;
                    float topDownY = pageHeight - midY;
                    segments.add(new LineSegment(minX, topDownY, maxX, topDownY));
                }
            }

            @Override
            public void drawImage(PDImage pdImage) {}

            @Override
            public void clip(int windingRule) {}

            @Override
            public void moveTo(float x, float y) {
                currentPoint = new Point2D.Float(x, y);
                subpathStart = currentPoint;
                currentSubpath.clear();
                currentSubpath.add(currentPoint);
            }

            @Override
            public void lineTo(float x, float y) {
                Point2D next = new Point2D.Float(x, y);
                float y1 = pageHeight - (float) currentPoint.getY();
                float y2 = pageHeight - (float) next.getY();
                segments.add(new LineSegment((float) currentPoint.getX(), y1, (float) next.getX(), y2));
                currentPoint = next;
                currentSubpath.add(next);
            }

            @Override
            public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
                currentPoint = new Point2D.Float(x3, y3);
                currentSubpath.add(currentPoint);
            }

            @Override
            public Point2D getCurrentPoint() {
                return currentPoint;
            }

            @Override
            public void closePath() {
                if (currentSubpath.size() >= 3) {
                    // Check if closed subpath forms a thin horizontal rectangle
                    checkFilledRectangle(currentSubpath);
                }
                currentPoint = subpathStart;
                currentSubpath.clear();
            }

            @Override
            public void endPath() {
                currentSubpath.clear();
            }

            @Override
            public void strokePath() {
                currentSubpath.clear();
            }

            @Override
            public void fillPath(int windingRule) {
                if (currentSubpath.size() >= 3) {
                    checkFilledRectangle(currentSubpath);
                }
                currentSubpath.clear();
            }

            @Override
            public void fillAndStrokePath(int windingRule) {
                if (currentSubpath.size() >= 3) {
                    checkFilledRectangle(currentSubpath);
                }
                currentSubpath.clear();
            }

            @Override
            public void shadingFill(COSName shadingName) {}

            private void checkFilledRectangle(List<Point2D> points) {
                double minX = points.stream().mapToDouble(Point2D::getX).min().orElse(0);
                double maxX = points.stream().mapToDouble(Point2D::getX).max().orElse(0);
                double minY = points.stream().mapToDouble(Point2D::getY).min().orElse(0);
                double maxY = points.stream().mapToDouble(Point2D::getY).max().orElse(0);
                double w = maxX - minX;
                double h = maxY - minY;
                if (w > 2.0 && h < 5.0) {
                    float midY = (float) (minY + maxY) / 2.0f;
                    float topDownY = pageHeight - midY;
                    segments.add(new LineSegment((float) minX, topDownY, (float) maxX, topDownY));
                }
            }
        };

        engine.processPage(page);
        return segments;
    }

    private static List<CharacterInfo> extractCharacters(PDDocument document, int pageNumber, List<LineSegment> lines) throws IOException {
        List<CharacterInfo> result = new ArrayList<>();

        // Filter horizontal strike-like lines (slope <= 15 degrees)
        List<LineSegment> horizontalLines = lines.stream()
            .filter(l -> l.isHorizontal(15.0f))
            .filter(l -> Math.abs(l.maxX() - l.minX()) > 1.0f)
            .toList();

        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
                for (TextPosition tp : textPositions) {
                    String unicode = tp.getUnicode();
                    float charX = tp.getXDirAdj();
                    float baselineY = tp.getYDirAdj(); // baseline Y in top-down coordinates
                    float width = tp.getWidthDirAdj();
                    float height = tp.getHeightDir();

                    if (height <= 0) {
                        height = tp.getFontSizeInPt();
                    }

                    float charCenterX = charX + (width / 2.0f);

                    boolean isStruck = false;
                    for (LineSegment line : horizontalLines) {
                        float lineY = (line.y1() + line.y2()) / 2.0f;
                        float lineMinX = line.minX() - 0.5f;
                        float lineMaxX = line.maxX() + 0.5f;

                        // Typographic strike-through position:
                        // Strikethrough line sits at ~ x-height / 2 (approx. 18% to 48% of font height above baseline).
                        // Underlines sit at or below baseline (<= 10%), while cell top borders / overlines sit above 52%.
                        float strikeOffset = baselineY - lineY;
                        float strikeRatio = strikeOffset / height;
                        boolean vIntersect = (strikeRatio >= 0.18f) && (strikeRatio <= 0.48f);

                        // Check horizontal intersection: line covers the character's center X
                        boolean hIntersect = (charCenterX >= lineMinX) && (charCenterX <= lineMaxX);

                        if (vIntersect && hIntersect) {
                            isStruck = true;
                            break;
                        }
                    }

                    result.add(new CharacterInfo(unicode, charX, baselineY - height, width, height, isStruck));
                }
                super.writeString(text, textPositions);
            }

            @Override
            protected void writeWordSeparator() throws IOException {
                result.add(new CharacterInfo(" ", 0, 0, 0, 0, false));
                super.writeWordSeparator();
            }

            @Override
            protected void writeLineSeparator() throws IOException {
                result.add(new CharacterInfo("\n", 0, 0, 0, 0, false));
                super.writeLineSeparator();
            }
        };

        stripper.setStartPage(pageNumber);
        stripper.setEndPage(pageNumber);
        stripper.getText(document);

        return result;
    }
}
