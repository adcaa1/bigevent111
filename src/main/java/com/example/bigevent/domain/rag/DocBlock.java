package com.example.bigevent.domain.rag;

import lombok.Data;

/**
 * 文档结构化块。
 * <p>
 * 用于把原始文档解析成带语义边界的块，再交给分块器生成 {@link dev.langchain4j.data.segment.TextSegment}。
 */
@Data
public class DocBlock {

    public enum Type {
        /**
         * 标题
         */
        HEADING,
        /**
         * 正文段落
         */
        PARAGRAPH,
        /**
         * Word / PDF 等文档中的表格行
         */
        TABLE_ROW,
        /**
         * Excel 工作表中的一行记录
         */
        SPREADSHEET_ROW,
        /**
         * 纯文本按行/段解析后的普通文本
         */
        PLAIN_TEXT,
        /**
         * 文档中嵌入的图片（如 Word、PDF 中的图片）
         */
        IMAGE
    }

    /**
     * 块类型
     */
    private Type type;

    /**
     * 块的文本内容
     */
    private String text;

    /**
     * 标题层级，仅 HEADING 有效
     */
    private Integer level;

    /**
     * 页码，PDF 等支持分页的文档可用
     */
    private Integer pageNum;

    /**
     * 图片本地存储路径，仅 IMAGE 类型有效
     */
    private String imagePath;

    /**
     * 是否禁止再分块。图片描述等需要保持完整语义的块可设置为 true。
     */
    private boolean unsplittable;

    public DocBlock() {
    }

    public DocBlock(Type type, String text) {
        this.type = type;
        this.text = text;
    }

    public DocBlock(Type type, String text, Integer pageNum) {
        this.type = type;
        this.text = text;
        this.pageNum = pageNum;
    }

    public DocBlock(Type type, String text, Integer level, Integer pageNum) {
        this.type = type;
        this.text = text;
        this.level = level;
        this.pageNum = pageNum;
    }

    public static DocBlock image(String imagePath, Integer pageNum) {
        DocBlock block = new DocBlock();
        block.setType(Type.IMAGE);
        block.setImagePath(imagePath);
        block.setPageNum(pageNum);
        return block;
    }
}


