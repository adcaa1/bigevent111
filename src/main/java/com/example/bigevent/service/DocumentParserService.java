package com.example.bigevent.service;

import com.example.bigevent.domain.rag.DocBlock;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.Paragraph;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.hwpf.usermodel.Table;
import org.apache.poi.hwpf.usermodel.TableCell;
import org.apache.poi.hwpf.usermodel.TableRow;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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

    @Value("${rag.document.upload-path:uploads}")
    private String uploadPath;

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
     *
     * @param fileType 文件类型
     * @param is       文件输入流
     * @param docId    文档ID，用于保存提取的图片
     */
    public List<DocBlock> parse(String fileType, InputStream is, Long docId) throws IOException {
        return switch (fileType.toLowerCase()) {
            case "doc" -> parseDoc(is);
            case "docx" -> parseDocx(is, docId);
            case "xlsx", "xls" -> parseExcel(is);
            case "pdf" -> parsePdf(is, docId);
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
     * 解析旧版 Word 文档（.doc）：识别标题、正文、表格。
     * <p>
     * 表格按行解析为自然语言记录，与 docx 保持一致。
     */
    private List<DocBlock> parseDoc(InputStream is) throws IOException {
        List<DocBlock> blocks = new ArrayList<>();
        try (HWPFDocument document = new HWPFDocument(is)) {
            Range range = document.getRange();
            List<String> headingContext = new ArrayList<>();

            int paraIdx = 0;
            int numParagraphs = range.numParagraphs();
            while (paraIdx < numParagraphs) {
                Paragraph para = range.getParagraph(paraIdx);
                String text = para.text().trim();
                if (text.isEmpty()) {
                    paraIdx++;
                    continue;
                }

                if (para.isInTable()) {
                    Table table = range.getTable(para);
                    blocks.addAll(parseDocTable(document, table, headingContext));
                    paraIdx += table.numParagraphs();
                } else if (isDocHeadingParagraph(document, para)) {
                    int level = extractDocHeadingLevel(document, para);
                    updateHeadingContext(headingContext, text, level);
                    blocks.add(new DocBlock(DocBlock.Type.HEADING, text, level, null));
                    paraIdx++;
                } else {
                    blocks.add(new DocBlock(DocBlock.Type.PARAGRAPH,
                            withHeadingContext(headingContext, text), null, null));
                    paraIdx++;
                }
            }
        }
        return blocks;
    }

    /**
     * 解析旧版 Word 表格：第一行作为表头，后续每一行生成一条自然语言记录。
     */
    private List<DocBlock> parseDocTable(HWPFDocument document, Table table, List<String> headingContext) {
        List<DocBlock> blocks = new ArrayList<>();
        int rowCount = table.numRows();
        if (rowCount == 0) {
            return blocks;
        }

        List<String> context = new ArrayList<>(headingContext);
        TableRow headerRow = table.getRow(0);
        List<String> headers = docCellTexts(headerRow);

        // 如果第一行是说明行（非真正表头），则把它作为上下文，下一行作为表头
        int startRow = 1;
        if (isTableCaptionRow(headers) && rowCount > 1) {
            headers.stream().filter(s -> !s.isEmpty()).findFirst().ifPresent(context::add);
            headerRow = table.getRow(1);
            headers = docCellTexts(headerRow);
            startRow = 2;
        }

        for (int i = startRow; i < rowCount; i++) {
            List<String> cells = docCellTexts(table.getRow(i));
            String rowText = formatRowAsNaturalLanguage(headers, cells);
            if (!rowText.isEmpty()) {
                blocks.add(new DocBlock(DocBlock.Type.TABLE_ROW,
                        withHeadingContext(context, rowText), null, null));
            }
        }
        return blocks;
    }

    private List<String> docCellTexts(TableRow row) {
        List<String> cells = new ArrayList<>();
        for (int i = 0; i < row.numCells(); i++) {
            cells.add(docCellText(row.getCell(i)));
        }
        return cells;
    }

    private String docCellText(TableCell cell) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cell.numParagraphs(); i++) {
            String text = cell.getParagraph(i).text().trim();
            if (text.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(" ");
            }
            sb.append(text);
        }
        return sb.toString();
    }

    private boolean isDocHeadingParagraph(HWPFDocument document, Paragraph paragraph) {
        int styleIndex = paragraph.getStyleIndex();
        if (styleIndex < 0) {
            return false;
        }
        String name = document.getStyleSheet().getStyleDescription(styleIndex).getName();
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase();
        return lower.contains("heading") || lower.contains("标题");
    }

    private int extractDocHeadingLevel(HWPFDocument document, Paragraph paragraph) {
        int styleIndex = paragraph.getStyleIndex();
        if (styleIndex < 0) {
            return 1;
        }
        String name = document.getStyleSheet().getStyleDescription(styleIndex).getName();
        if (name == null) {
            return 1;
        }
        Matcher m = HEADING_STYLE_PATTERN.matcher(name);
        if (m.find()) {
            return Math.max(1, Math.min(9, Integer.parseInt(m.group(1))));
        }
        return 1;
    }

    /**
     * 解析 Word 文档：识别标题、正文、表格、图片。
     * <p>
     * 表格行只使用“文档级标题 + 紧邻的表格题注”作为上下文，避免多个表格的题注互相污染。
     * 图片按正文顺序提取并保存到本地，生成 IMAGE 类型的块。
     */
    private List<DocBlock> parseDocx(InputStream is, Long docId) throws IOException {
        List<DocBlock> blocks = new ArrayList<>();
        try (XWPFDocument document = new XWPFDocument(is)) {
            List<String> headingContext = new ArrayList<>();
            String pendingTableCaption = null;
            AtomicInteger imageIndex = new AtomicInteger(1);

            List<IBodyElement> elements = document.getBodyElements();
            for (int i = 0; i < elements.size(); i++) {
                IBodyElement element = elements.get(i);
                if (element instanceof XWPFParagraph paragraph) {
                    String text = paragraph.getText().trim();

                    // 先提取段落中的图片，保持图文顺序
                    List<DocBlock> imageBlocks = extractImagesFromParagraph(paragraph, docId, imageIndex);
                    blocks.addAll(imageBlocks);

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
                    blocks.addAll(parseDocxTable(table, tableContext, docId, imageIndex));
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

    private List<DocBlock> parseDocxTable(XWPFTable table, List<String> baseContext,
                                          Long docId, AtomicInteger imageIndex) throws IOException {
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
            XWPFTableRow row = rows.get(i);

            // 提取单元格中的图片，保持图文顺序
            for (XWPFTableCell cell : row.getTableCells()) {
                for (XWPFParagraph paragraph : cell.getParagraphs()) {
                    blocks.addAll(extractImagesFromParagraph(paragraph, docId, imageIndex));
                }
            }

            List<String> cells = cellTexts(row);
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
     * 从 Word 段落中提取嵌入的图片，按出现顺序保存到本地并生成 IMAGE 块。
     */
    private List<DocBlock> extractImagesFromParagraph(XWPFParagraph paragraph, Long docId,
                                                      AtomicInteger imageIndex) throws IOException {
        List<DocBlock> blocks = new ArrayList<>();
        for (XWPFRun run : paragraph.getRuns()) {
            for (XWPFPicture picture : run.getEmbeddedPictures()) {
                XWPFPictureData pictureData = picture.getPictureData();
                if (pictureData == null) {
                    continue;
                }
                String imagePath = saveImage(pictureData, docId, imageIndex.getAndIncrement());
                if (imagePath != null) {
                    blocks.add(DocBlock.image(imagePath, null));
                }
            }
        }
        return blocks;
    }

    /**
     * 保存图片到本地知识库目录。
     *
     * @return 图片本地绝对路径；保存失败时返回 null
     */
    private String saveImage(XWPFPictureData pictureData, Long docId, int index) {
        try {
            String extension = pictureData.suggestFileExtension();
            if (extension == null || extension.isBlank()) {
                extension = "png";
            }
            String fileName = String.format("image_%03d.%s", index, extension);
            Path imagePath = saveImageBytes(pictureData.getData(), docId, fileName);
            log.info("Word 图片已保存: {}", imagePath);
            return imagePath.toString().replace("\\", "/");
        } catch (IOException e) {
            log.error("保存 Word 图片失败, docId={}, index={}", docId, index, e);
            return null;
        }
    }

    private Path saveImageBytes(byte[] imageBytes, Long docId, String fileName) throws IOException {
        Path imageDir = Paths.get(uploadPath, "images", String.valueOf(docId));
        if (!Files.exists(imageDir)) {
            Files.createDirectories(imageDir);
        }
        Path imagePath = imageDir.resolve(fileName);
        Files.write(imagePath, imageBytes);
        return imagePath;
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
    private List<DocBlock> parsePdf(InputStream is, Long docId) throws IOException {
        List<DocBlock> blocks = new ArrayList<>();
        try (PDDocument document = PDDocument.load(is)) {
            int pageCount = document.getNumberOfPages();
            List<String> headingContext = new ArrayList<>();
            StringBuilder paragraph = new StringBuilder();
            LineInfo previousLine = null;
            AtomicInteger imageIndex = new AtomicInteger(1);

            for (int pageNum = 1; pageNum <= pageCount; pageNum++) {
                blocks.addAll(extractImagesFromPdfPage(document.getPage(pageNum - 1), docId, pageNum, imageIndex));

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

    private List<DocBlock> extractImagesFromPdfPage(PDPage page, Long docId, int pageNum,
                                                    AtomicInteger imageIndex) throws IOException {
        List<DocBlock> blocks = new ArrayList<>();
        extractImagesFromPdfResources(page.getResources(), docId, pageNum, imageIndex, blocks);
        return blocks;
    }

    private void extractImagesFromPdfResources(PDResources resources, Long docId, int pageNum,
                                               AtomicInteger imageIndex, List<DocBlock> blocks) throws IOException {
        if (resources == null) {
            return;
        }
        for (COSName name : resources.getXObjectNames()) {
            PDXObject xObject = resources.getXObject(name);
            if (xObject instanceof PDImageXObject imageObject) {
                String imagePath = savePdfImage(imageObject, docId, pageNum, imageIndex.getAndIncrement());
                if (imagePath != null) {
                    blocks.add(DocBlock.image(imagePath, pageNum));
                }
            } else if (xObject instanceof PDFormXObject formObject) {
                extractImagesFromPdfResources(formObject.getResources(), docId, pageNum, imageIndex, blocks);
            }
        }
    }

    private String savePdfImage(PDImageXObject imageObject, Long docId, int pageNum, int index) {
        try {
            BufferedImage image = imageObject.getImage();
            if (image == null) {
                return null;
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "png", outputStream);
            String fileName = String.format("page_%03d_image_%03d.png", pageNum, index);
            Path imagePath = saveImageBytes(outputStream.toByteArray(), docId, fileName);
            log.info("PDF image saved: {}", imagePath);
            return imagePath.toString().replace("\\", "/");
        } catch (IOException e) {
            log.error("Failed to save PDF image, docId={}, pageNum={}, index={}", docId, pageNum, index, e);
            return null;
        }
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
