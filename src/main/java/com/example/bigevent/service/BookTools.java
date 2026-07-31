package com.example.bigevent.service;

import com.example.bigevent.constant.KnowledgeConstants;
import com.example.bigevent.domain.AiConversation;
import com.example.bigevent.domain.KnowledgeDoc;
import com.example.bigevent.domain.User;
import com.example.bigevent.mapper.Usermapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 图书助手工具集：通过自然语言操作知识库文档/图书。
 * <p>
 * 该类是 Spring 管理的单例 Bean，不持有任何用户状态。工具方法通过
 * {@link ToolMemoryId} 获取会话 ID，再查询 {@link AiConversationService}
 * 得到当前用户 ID，从而避开线程切换导致的 ThreadLocal 失效问题。
 */
@Slf4j
@Component
public class BookTools {

    private final KnowledgeDocService knowledgeDocService;
    private final AiConversationService aiConversationService;
    private final Usermapper usermapper;

    @Autowired
    public BookTools(KnowledgeDocService knowledgeDocService,
                     AiConversationService aiConversationService,
                     Usermapper usermapper) {
        this.knowledgeDocService = knowledgeDocService;
        this.aiConversationService = aiConversationService;
        this.usermapper = usermapper;
    }

    /**
     * 根据会话 ID 获取当前用户上下文（userId + departmentId）。
     * <p>
     * {@link ToolMemoryId} 参数不会被暴露给 LLM，由 LangChain4j 自动注入。
     * 参数类型声明为 Object，避免 LangChain4j 内部传入的类型与声明类型不一致导致反射失败。
     */
    private UserContext getUserContext(Object memoryId) {
        Long convId = toLong(memoryId);
        if (convId == null) {
            return null;
        }
        AiConversation conversation = aiConversationService.findById(convId);
        if (conversation == null) {
            return null;
        }
        Integer userId = conversation.getUserId();
        User user = usermapper.findById(userId);
        return new UserContext(userId, user != null ? user.getDepartmentId() : null);
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 列出当前用户有权限查看的图书/文档。
     */
    @Tool("列出我的图书/文档列表。可指定页码(pageNum)和每页大小(pageSize)，不传默认前10条。")
    public String listMyBooks(@ToolMemoryId Object memoryId,
                              @P(value = "页码，从1开始，可选", required = false) Integer pageNum,
                              @P(value = "每页大小，可选", required = false) Integer pageSize) {
        try {
            UserContext ctx = getUserContext(memoryId);
            if (ctx == null) {
                return "查询图书列表失败: 无法获取当前用户信息";
            }

            int page = pageNum == null || pageNum < 1 ? 1 : pageNum;
            int size = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 50);

            List<KnowledgeDoc> docs = knowledgeDocService.searchDocsByKeyword(ctx.userId, ctx.departmentId, null);
            int total = docs.size();
            int fromIndex = Math.min((page - 1) * size, total);
            int toIndex = Math.min(fromIndex + size, total);
            List<KnowledgeDoc> pageDocs = docs.subList(fromIndex, toIndex);

            if (pageDocs.isEmpty()) {
                return "你还没有上传任何图书/文档。可以上传 PDF、Word、TXT 或 Markdown 文件，也可以直接添加文本知识。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("第 ").append(page).append(" 页，共 ").append(total).append(" 本图书/文档：\n\n");
            for (int i = 0; i < pageDocs.size(); i++) {
                KnowledgeDoc doc = pageDocs.get(i);
                sb.append(i + 1).append(". ")
                  .append("ID: ").append(doc.getId())
                  .append(", 名称: ").append(doc.getFileName())
                  .append(", 类型: ").append(doc.getFileType())
                  .append(", 状态: ").append(formatStatus(doc.getStatus()))
                  .append(", 可见性: ").append(formatVisibility(doc.getVisibility()))
                  .append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("列出图书失败", e);
            return "查询图书列表失败: " + e.getMessage();
        }
    }

    /**
     * 按关键词搜索图书/文档。
     */
    @Tool("根据关键词搜索图书/文档，匹配文件名。参数keyword为关键词。")
    public String searchBooks(@ToolMemoryId Object memoryId,
                              @P("搜索关键词") String keyword) {
        try {
            UserContext ctx = getUserContext(memoryId);
            if (ctx == null) {
                return "搜索图书失败: 无法获取当前用户信息";
            }

            if (keyword == null || keyword.isBlank()) {
                return "请提供搜索关键词。";
            }
            List<KnowledgeDoc> docs = knowledgeDocService.searchDocsByKeyword(ctx.userId, ctx.departmentId, keyword.trim());
            if (docs.isEmpty()) {
                return "没有找到与 \"" + keyword + "\" 相关的图书/文档。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("找到 ").append(docs.size()).append(" 条结果：\n\n");
            for (int i = 0; i < docs.size(); i++) {
                KnowledgeDoc doc = docs.get(i);
                sb.append(i + 1).append(". ")
                  .append("ID: ").append(doc.getId())
                  .append(", 名称: ").append(doc.getFileName())
                  .append(", 类型: ").append(doc.getFileType())
                  .append(", 状态: ").append(formatStatus(doc.getStatus()))
                  .append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("搜索图书失败, keyword={}", keyword, e);
            return "搜索图书失败: " + e.getMessage();
        }
    }

    /**
     * 查询图书/文档详情。
     */
    @Tool("查询指定图书/文档的详情。参数可以是文档ID(docId)或书名/文件名(bookName)，至少传一个。")
    public String getBookDetail(@ToolMemoryId Object memoryId,
                                @P(value = "文档ID，可选", required = false) Long docId,
                                @P(value = "书名或文件名，可选", required = false) String bookName) {
        try {
            UserContext ctx = getUserContext(memoryId);
            if (ctx == null) {
                return "查询图书详情失败: 无法获取当前用户信息";
            }

            KnowledgeDoc doc = resolveDoc(ctx, docId, bookName);
            if (doc == null) {
                return "未找到指定的图书/文档，请检查 ID 或书名是否正确。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("图书/文档详情：\n");
            sb.append("ID: ").append(doc.getId()).append("\n");
            sb.append("名称: ").append(doc.getFileName()).append("\n");
            sb.append("类型: ").append(doc.getFileType()).append("\n");
            sb.append("大小: ").append(formatFileSize(doc.getFileSize())).append("\n");
            sb.append("状态: ").append(formatStatus(doc.getStatus())).append("\n");
            sb.append("可见性: ").append(formatVisibility(doc.getVisibility())).append("\n");
            sb.append("片段数: ").append(doc.getChunkCount() == null ? 0 : doc.getChunkCount()).append("\n");
            sb.append("创建时间: ").append(doc.getCreateTime()).append("\n");
            sb.append("更新时间: ").append(doc.getUpdateTime()).append("\n");
            if (doc.getFailReason() != null && !doc.getFailReason().isBlank()) {
                sb.append("失败原因: ").append(doc.getFailReason()).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("查询图书详情失败, docId={}, bookName={}", docId, bookName, e);
            return "查询图书详情失败: " + e.getMessage();
        }
    }

    /**
     * 获取图书/文档内容摘要。
     */
    @Tool("获取指定图书/文档的文本内容摘要。参数可以是文档ID(docId)或书名/文件名(bookName)，至少传一个。")
    public String getBookContent(@ToolMemoryId Object memoryId,
                                 @P(value = "文档ID，可选", required = false) Long docId,
                                 @P(value = "书名或文件名，可选", required = false) String bookName) {
        try {
            UserContext ctx = getUserContext(memoryId);
            if (ctx == null) {
                return "获取图书内容失败: 无法获取当前用户信息";
            }

            KnowledgeDoc doc = resolveDoc(ctx, docId, bookName);
            if (doc == null) {
                return "未找到指定的图书/文档，请检查 ID 或书名是否正确。";
            }
            String content = doc.getContent();
            if (content == null || content.isBlank()) {
                return "该文档暂无解析后的文本内容（可能仍在处理中）。";
            }
            int maxLen = 3000;
            String preview = content.length() > maxLen
                    ? content.substring(0, maxLen) + "\n\n...（内容已截断，共 " + content.length() + " 字符）"
                    : content;
            return "《" + doc.getFileName() + "》内容摘要：\n\n" + preview;
        } catch (Exception e) {
            log.error("获取图书内容失败, docId={}, bookName={}", docId, bookName, e);
            return "获取图书内容失败: " + e.getMessage();
        }
    }

    /**
     * 删除图书/文档。
     */
    @Tool("删除指定的图书/文档。参数可以是文档ID(docId)或书名/文件名(bookName)，至少传一个。")
    public String deleteBook(@ToolMemoryId Object memoryId,
                             @P(value = "文档ID，可选", required = false) Long docId,
                             @P(value = "书名或文件名，可选", required = false) String bookName) {
        try {
            UserContext ctx = getUserContext(memoryId);
            if (ctx == null) {
                return "删除图书失败: 无法获取当前用户信息";
            }

            KnowledgeDoc doc = resolveDoc(ctx, docId, bookName);
            if (doc == null) {
                return "未找到指定的图书/文档，请检查 ID 或书名是否正确。";
            }
            knowledgeDocService.deleteDoc(doc.getId(), ctx.userId);
            return "图书/文档 \"" + doc.getFileName() + "\"（ID: " + doc.getId() + "）已删除。";
        } catch (Exception e) {
            log.error("删除图书失败, docId={}, bookName={}", docId, bookName, e);
            return "删除图书失败: " + e.getMessage();
        }
    }

    /**
     * 重新处理图书/文档。
     */
    @Tool("重新解析并向量化处理指定的图书/文档。参数可以是文档ID(docId)或书名/文件名(bookName)，至少传一个。")
    public String reprocessBook(@ToolMemoryId Object memoryId,
                                @P(value = "文档ID，可选", required = false) Long docId,
                                @P(value = "书名或文件名，可选", required = false) String bookName) {
        try {
            UserContext ctx = getUserContext(memoryId);
            if (ctx == null) {
                return "重新处理图书失败: 无法获取当前用户信息";
            }

            KnowledgeDoc doc = resolveDoc(ctx, docId, bookName);
            if (doc == null) {
                return "未找到指定的图书/文档，请检查 ID 或书名是否正确。";
            }
            knowledgeDocService.reprocessDoc(doc.getId(), ctx.userId);
            return "图书/文档 \"" + doc.getFileName() + "\"（ID: " + doc.getId() + "）已提交重新处理，请稍后查询最新状态。";
        } catch (Exception e) {
            log.error("重新处理图书失败, docId={}, bookName={}", docId, bookName, e);
            return "重新处理图书失败: " + e.getMessage();
        }
    }

    /**
     * 添加文本知识。
     */
    @Tool("添加一段文本知识到知识库。参数text为文本内容，visibility为可见性：0私有 1部门 2公共，默认私有。")
    public String addTextKnowledge(@ToolMemoryId Object memoryId,
                                   @P(value = "文本内容", required = false) String text,
                                   @P(value = "可见性：0私有 1部门 2公共，可选，默认0", required = false) Integer visibility) {
        try {
            UserContext ctx = getUserContext(memoryId);
            if (ctx == null) {
                return "添加文本知识失败: 无法获取当前用户信息";
            }

            if (text == null || text.isBlank()) {
                return "请提供要添加的文本内容。";
            }
            int vis = visibility == null ? KnowledgeConstants.Visibility.PRIVATE : visibility;
            knowledgeDocService.createAndProcessTextDoc(text, ctx.userId, vis, ctx.departmentId);
            return "文本知识已添加成功，系统正在进行向量化处理。";
        } catch (Exception e) {
            log.error("添加文本知识失败", e);
            return "添加文本知识失败: " + e.getMessage();
        }
    }

    /**
     * 解析用户输入，定位到唯一文档。
     * <p>
     * 优先使用 docId；没有 docId 时按书名/文件名匹配，匹配到多个会提示用户补充。
     */
    private KnowledgeDoc resolveDoc(UserContext ctx, Long docId, String bookName) {
        if (docId != null) {
            return knowledgeDocService.findAuthorizedById(docId, ctx.userId, ctx.departmentId);
        }
        if (bookName == null || bookName.isBlank()) {
            return null;
        }

        KnowledgeDoc single = knowledgeDocService.findSingleDocByName(ctx.userId, ctx.departmentId, bookName);
        if (single != null) {
            return single;
        }

        // 匹配到多个或没有匹配，返回 null，由上层工具给出明确提示
        return null;
    }

    private String formatStatus(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case KnowledgeConstants.DocStatus.PENDING -> "待处理";
            case KnowledgeConstants.DocStatus.PROCESSING -> "处理中";
            case KnowledgeConstants.DocStatus.SUCCESS -> "成功";
            case KnowledgeConstants.DocStatus.FAILED -> "失败";
            default -> "未知";
        };
    }

    private String formatVisibility(Integer visibility) {
        if (visibility == null) return "未知";
        return switch (visibility) {
            case KnowledgeConstants.Visibility.PRIVATE -> "私有";
            case KnowledgeConstants.Visibility.DEPARTMENT -> "部门";
            case KnowledgeConstants.Visibility.PUBLIC -> "公共";
            default -> "未知";
        };
    }

    private String formatFileSize(Long size) {
        if (size == null) return "-";
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.2f KB", size / 1024.0);
        return String.format("%.2f MB", size / (1024.0 * 1024.0));
    }

    /**
     * 用户上下文值对象。
     */
    private record UserContext(Integer userId, Integer departmentId) {
    }
}
