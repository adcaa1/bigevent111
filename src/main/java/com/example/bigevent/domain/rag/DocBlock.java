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
        PLAIN_TEXT
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
}
