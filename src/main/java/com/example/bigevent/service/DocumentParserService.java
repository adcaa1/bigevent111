package com.example.bigevent.service;

import com.example.bigevent.domain.rag.DocBlock;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 结构化文档解析服务。
 * <p>
 * 把各类文档解析成语义块（标题、正文、表格行、Excel 记录等），供后续分块和 Embedding 使用。
 * 图片/扫描件暂时不处理。
 */
@Slf4j
@Service
public class DocumentParserService {

    /**
     * 中文/数字编号标题正则，例如：第1章、1. 、一、
     */
    private static final Pattern NUMBERING_HEADING_PATTERN = Pattern.compile(
            "^\\s*(?:第[一二三四五六七八九十\\d]+(?:章|节)|[一二三四五六七八九十\\d]+[.．、\\)])\\s+"
    );

    private static final Pattern HEADING_STYLE_PATTERN = Pattern.compile(
            "(?i).*?(?:heading|标题)\\s*(\\d+).*"
    );

    /**
     * 根据文件类型解析成结构化块。
     */
    public List<DocBlock> parse(String fileType, InputStream is) throws IOException {
        return switch (fileType.toLowerCase()) {
            case "docx" -> parseDocx(is);
            case "xlsx", "xls" -> parseExcel(is);
            case "pdf" -> parsePdf(is);
            case "txt", "md" -> parsePlainText(new String(is.readAllBytes(), StandardCharsets.UTF_8));
            default -> throw new IllegalArgumentException("不支持的文件类型: " + fileType);
        };
    }

    /**
     * 把纯文本按段落解析成块。
     */
    public List<DocBlock> parsePlainText(String text) {
        List<DocBlock> blocks = new ArrayList<>();
        String[] paragraphs = text.split("\\n{2,}|\\r\\n\\r\\n");
        for (String para : paragraphs) {
            String trimmed = para.trim().replaceAll("\\s+", " ");
            if (trimmed.isEmpty()) {
                continue;
            }
            blocks.add(new DocBlock(DocBlock.Type.PLAIN_TEXT, trimmed));
        }
        return blocks;
    }

    /**
     * 解析 Word 文档：识别标题、正文、表格。
     * <p>
     * 表格行只使用“文档级标题 + 紧邻的表格题注”作为上下文，避免多个表格的题注互相污染。
     */
    private List<DocBlock> parseDocx(InputStream is) throws IOException {
        List<DocBlock> blocks = new ArrayList<>();
        try (XWPFDocument document = new XWPFDocument(is)) {
            List<String> headingContext = new ArrayList<>();
            String pendingTableCaption = null;

            List<IBodyElement> elements = document.getBodyElements();
            for (int i = 0; i < elements.size(); i++) {
                IBodyElement element = elements.get(i);
                if (element instanceof XWPFParagraph paragraph) {
                    String text = paragraph.getText().trim();
                    if (text.isEmpty()) {
                        continue;
                    }

                    if (isHeadingParagraph(paragraph) && !isTableCaption(text)) {
                        int level = extractHeadingLevel(paragraph);
                        updateHeadingContext(headingContext, text, level);
                        pendingTableCaption = null; // 进入新章节，清理旧表格题注
                        blocks.add(new DocBlock(DocBlock.Type.HEADING, text, level, null));
                    } else if (isTableCaption(text)) {
                        // 表格题注只作为后续表格的上下文，不单独生成片段
                        pendingTableCaption = text;
                    } else {
                        pendingTableCaption = null;
                        blocks.add(new DocBlock(DocBlock.Type.PARAGRAPH,
                                withHeadingContext(headingContext, text), null, null));
                    }
                } else if (element instanceof XWPFTable table) {
                    List<String> tableContext = new ArrayList<>(headingContext);
                    if (pendingTableCaption != null) {
                        tableContext.add(pendingTableCaption);
                    }
                    blocks.addAll(parseDocxTable(table, tableContext));
                    pendingTableCaption = null;
                }
            }
        }
        return blocks;
    }

    /**
     * 判断文本是否是表格题注（表一、表1、Table 1 等）。
     */
    private boolean isTableCaption(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return text.matches("(?i)^(?:表[一二三四五六七八九十\\d]+|table\\s*\\d+)[、：:.\\s].*");
    }

    private boolean isHeadingParagraph(XWPFParagraph paragraph) {
        String styleId = paragraph.getStyle();
        if (styleId == null || styleId.isBlank()) {
            return false;
        }
        // Word 默认标题样式 ID 是 Heading1 ~ Heading9
        if (styleId.matches("(?i)Heading\\d+")) {
            return true;
        }
        // 中文 Word 中样式名可能叫 "标题 1"、"标题 2"
        XWPFStyle style = paragraph.getDocument().getStyles().getStyle(styleId);
        if (style != null && style.getName() != null) {
            String name = style.getName().toLowerCase();
            return name.contains("heading") || name.contains("标题");
        }
        return false;
    }

    private int extractHeadingLevel(XWPFParagraph paragraph) {
        String styleId = paragraph.getStyle();
        if (styleId != null) {
            Matcher m = Pattern.compile("(?i).*?(\\d+)").matcher(styleId);
            if (m.find()) {
                return Math.max(1, Math.min(9, Integer.parseInt(m.group(1))));
            }
        }
        XWPFStyle style = paragraph.getDocument().getStyles().getStyle(paragraph.getStyle());
        if (style != null && style.getName() != null) {
            Matcher m = HEADING_STYLE_PATTERN.matcher(style.getName());
            if (m.find()) {
                return Math.max(1, Math.min(9, Integer.parseInt(m.group(1))));
            }
        }
        return 1;
    }

    private void updateHeadingContext(List<String> headingContext, String headingText, int level) {
        // 只保留比当前层级更高的标题
        if (headingContext.size() >= level) {
            headingContext.subList(level - 1, headingContext.size()).clear();
        }
        headingContext.add(headingText);
    }

    private List<DocBlock> parseDocxTable(XWPFTable table, List<String> baseContext) {
        List<DocBlock> blocks = new ArrayList<>();
        List<XWPFTableRow> rows = table.getRows();
        if (rows == null || rows.isEmpty()) {
            return blocks;
        }

        int rowIdx = 0;
        List<String> headers = cellTexts(rows.get(rowIdx));
        List<String> context = new ArrayList<>(baseContext);

        // 跳过表格顶部的“表格/Table/图”等说明行，把它当作表格上下文
        while (rowIdx < rows.size() && isTableCaptionRow(headers)) {
            headers.stream().filter(s -> !s.isEmpty()).findFirst()
                    .ifPresent(context::add);
            rowIdx++;
            if (rowIdx < rows.size()) {
                headers = cellTexts(rows.get(rowIdx));
            } else {
                return blocks;
            }
        }

        if (rowIdx >= rows.size()) {
            return blocks;
        }
        rowIdx++; // 表头行

        for (int i = rowIdx; i < rows.size(); i++) {
            List<String> cells = cellTexts(rows.get(i));
            if (cells.stream().allMatch(String::isEmpty)) {
                continue;
            }
            String rowText = formatRowAsNaturalLanguage(headers, cells);
            if (!rowText.isEmpty()) {
                blocks.add(new DocBlock(DocBlock.Type.TABLE_ROW,
                        withHeadingContext(context, rowText), null, null));
            }
        }
        return blocks;
    }

    private List<String> cellTexts(XWPFTableRow row) {
        return row.getTableCells().stream()
                .map(c -> c.getText().trim())
                .toList();
    }

    /**
     * 判断表格第一行是否是说明行（如“表格”、“Table”、“图 1”），而非真正的列头。
     */
    private boolean isTableCaptionRow(List<String> cells) {
        List<String> nonEmpty = cells.stream().filter(s -> !s.isEmpty()).toList();
        if (nonEmpty.size() != 1) {
            return false;
        }
        String text = nonEmpty.get(0);
        return text.matches("(?i)^(表格|table|图|figure|图片|附注).*");
    }

    /**
     * 解析 Excel：每个有效行作为一条独立记录。
     */
    private List<DocBlock> parseExcel(InputStream is) throws IOException {
        List<DocBlock> blocks = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(is)) {
            for (Sheet sheet : workbook) {
                List<String> headers = null;
                for (Row row : sheet) {
                    if (row == null) {
                        continue;
                    }
                    List<String> cells = new ArrayList<>();
                    boolean allEmpty = true;
                    for (Cell cell : row) {
                        String value = getCellStringValue(cell).trim();
                        cells.add(value);
                        if (!value.isEmpty()) {
                            allEmpty = false;
                        }
                    }
                    if (allEmpty) {
                        continue;
                    }
                    if (headers == null) {
                        headers = cells;
                    } else {
                        String rowText = formatRowAsNaturalLanguage(headers, cells);
                        if (!rowText.isEmpty()) {
                            blocks.add(new DocBlock(DocBlock.Type.SPREADSHEET_ROW, rowText));
                        }
                    }
                }
            }
        }
        return blocks;
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toString();
                }
                double d = cell.getNumericCellValue();
                yield d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    /**
     * 解析 PDF：按页提取文本，基于字体大小和启发式规则识别标题，正文按段落合并。
     * 表格会作为普通文本段落处理，不做版面分析。
     */
    private List<DocBlock> parsePdf(InputStream is) throws IOException {
        List<DocBlock> blocks = new ArrayList<>();
        try (PDDocument document = PDDocument.load(is)) {
            int pageCount = document.getNumberOfPages();
            List<String> headingContext = new ArrayList<>();
            StringBuilder paragraph = new StringBuilder();
            LineInfo previousLine = null;

            for (int pageNum = 1; pageNum <= pageCount; pageNum++) {
                PdfLineExtractor extractor = new PdfLineExtractor(pageNum);
                extractor.setStartPage(pageNum);
                extractor.setEndPage(pageNum);
                extractor.getText(document);

                for (LineInfo line : extractor.lines) {
                    if (isPdfHeading(line, extractor.averageBodyFontSize)) {
                        // 先刷新之前的段落
                        flushParagraph(blocks, headingContext, paragraph,
                                previousLine != null ? previousLine.pageNum : pageNum);

                        int level = estimatePdfHeadingLevel(line, headingContext);
                        updateHeadingContext(headingContext, line.text, level);
                        blocks.add(new DocBlock(DocBlock.Type.HEADING, line.text, level, pageNum));
                        previousLine = null;
                        continue;
                    }

                    // 合并同一页连续正文行成一个段落
                    if (previousLine != null && previousLine.pageNum == pageNum) {
                        paragraph.append(" ").append(line.text);
                    } else {
                        flushParagraph(blocks, headingContext, paragraph,
                                previousLine != null ? previousLine.pageNum : pageNum);
                        paragraph.append(line.text);
                    }
                    previousLine = line;
                }
            }
            flushParagraph(blocks, headingContext, paragraph,
                    previousLine != null ? previousLine.pageNum : null);
        }
        return blocks;
    }

    private boolean isPdfHeading(LineInfo line, float averageBodyFontSize) {
        String text = line.text.trim();
        if (text.isEmpty() || text.length() > 120) {
            return false;
        }
        // 1. 字体明显大于正文
        if (averageBodyFontSize > 0 && line.avgFontSize > averageBodyFontSize + 2.0f) {
            return true;
        }
        // 2. 字体加粗/黑体
        if (line.fontName != null && (line.fontName.toLowerCase().contains("bold")
                || line.fontName.contains("黑体")
                || line.fontName.contains("Bold"))) {
            return true;
        }
        // 3. 编号式标题
        return NUMBERING_HEADING_PATTERN.matcher(text).find();
    }

    private int estimatePdfHeadingLevel(LineInfo line, List<String> headingContext) {
        if (line.fontName != null && line.fontName.toLowerCase().contains("heading")) {
            Matcher m = Pattern.compile("(?i).*?(\\d+)").matcher(line.fontName);
            if (m.find()) {
                return Math.max(1, Math.min(9, Integer.parseInt(m.group(1))));
            }
        }
        // 根据字号相对正文粗略推断层级
        if (line.avgFontSize > 0 && headingContext.isEmpty()) {
            return 1;
        }
        return Math.min(headingContext.size() + 1, 9);
    }

    private void flushParagraph(List<DocBlock> blocks, List<String> headingContext,
                                StringBuilder paragraph, Integer pageNum) {
        if (paragraph.isEmpty()) {
            return;
        }
        String text = paragraph.toString().trim().replaceAll("\\s+", " ");
        paragraph.setLength(0);
        if (text.isEmpty()) {
            return;
        }
        blocks.add(new DocBlock(DocBlock.Type.PARAGRAPH,
                withHeadingContext(headingContext, text), null, pageNum));
    }

    private String formatRowAsNaturalLanguage(List<String> headers, List<String> cells) {
        StringBuilder sb = new StringBuilder();
        int count = Math.min(headers.size(), cells.size());
        for (int i = 0; i < count; i++) {
            String header = headers.get(i).trim();
            String cell = cells.get(i).trim();
            if (cell.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append("，");
            }
            if (!header.isEmpty()) {
                sb.append(header).append("：");
            }
            sb.append(cell);
        }
        // 如果列数不一致，把多出来的单元格也拼上
        for (int i = count; i < cells.size(); i++) {
            String cell = cells.get(i).trim();
            if (!cell.isEmpty()) {
                if (!sb.isEmpty()) {
                    sb.append("，");
                }
                sb.append(cell);
            }
        }
        return sb.toString();
    }

    private String withHeadingContext(List<String> headingContext, String text) {
        if (headingContext == null || headingContext.isEmpty()) {
            return text;
        }
        return String.join(" > ", headingContext) + "\n" + text;
    }

    /**
     * PDF 行信息内部类。
     */
    private static class LineInfo {
        final String text;
        final float avgFontSize;
        final String fontName;
        final int pageNum;

        LineInfo(String text, float avgFontSize, String fontName, int pageNum) {
            this.text = text;
            this.avgFontSize = avgFontSize;
            this.fontName = fontName;
            this.pageNum = pageNum;
        }
    }

    /**
     * PDF 文本行提取器。
     * <p>
     * 收集每一行的文本、平均字号、字体名和页码，用于标题识别。
     */
    private static class PdfLineExtractor extends PDFTextStripper {

        final List<LineInfo> lines = new ArrayList<>();
        float averageBodyFontSize = 0;

        private final int pageNum;
        private final List<TextWord> currentWords = new ArrayList<>();
        private float currentY = -1;

        PdfLineExtractor(int pageNum) throws IOException {
            this.pageNum = pageNum;
            setSortByPosition(true);
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) {
            if (textPositions == null || textPositions.isEmpty()) {
                return;
            }
            TextPosition first = textPositions.get(0);
            float y = first.getY();
            if (currentY != -1 && Math.abs(y - currentY) > 3) {
                flushLine();
            }
            currentY = y;

            float totalSize = 0;
            for (TextPosition tp : textPositions) {
                totalSize += tp.getFontSizeInPt();
            }
            float avgSize = totalSize / textPositions.size();
            String fontName = first.getFont().getName();
            currentWords.add(new TextWord(text, avgSize, fontName));
        }

        @Override
        protected void writeLineSeparator() {
            flushLine();
        }

        private void flushLine() {
            if (currentWords.isEmpty()) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            float totalSize = 0;
            String representativeFont = null;
            for (TextWord word : currentWords) {
                sb.append(word.text);
                totalSize += word.fontSize;
                if (representativeFont == null) {
                    representativeFont = word.fontName;
                }
            }
            String lineText = sb.toString().trim();
            if (!lineText.isEmpty()) {
                float avgSize = totalSize / currentWords.size();
                lines.add(new LineInfo(lineText, avgSize, representativeFont, pageNum));
            }
            currentWords.clear();
            currentY = -1;
        }

        @Override
        public String getText(PDDocument doc) throws IOException {
            String result = super.getText(doc);
            // 计算正文平均字号（排除疑似标题的短行）
            float total = 0;
            int count = 0;
            for (LineInfo line : lines) {
                if (line.text.length() > 40) {
                    total += line.avgFontSize;
                    count++;
                }
            }
            averageBodyFontSize = count > 0 ? total / count : 0;
            return result;
        }
    }

    private static class TextWord {
        final String text;
        final float fontSize;
        final String fontName;

        TextWord(String text, float fontSize, String fontName) {
            this.text = text;
            this.fontSize = fontSize;
            this.fontName = fontName;
        }
    }
}
