package com.example.bigevent.service;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

/**
 * 图片描述服务。
 * <p>
 * 调用视觉语言模型（VLM）识别图片内容，生成可用于 RAG 检索的文字描述。
 */
@Slf4j
@Service
public class ImageDescriptionService {

    private static final String DESCRIPTION_PROMPT = """
            请详细描述这张图片的内容，用于后续文本检索：
            1. 如果是普通截图或照片，描述其中的关键对象、场景、文字和关系。
            2. 如果是流程图，请按步骤描述完整流程。
            3. 如果是架构图，请说明各组件及其关系。
            4. 如果是表格截图，请将其转换为结构化的文字表格。
            5. 尽可能提取图中所有可见文字。
            请用中文回答，保持简洁但信息完整。
            """;

    @Autowired
    private ChatModel chatModel;

    /**
     * 根据图片本地路径生成文字描述。
     *
     * @param imagePath 图片本地路径（相对或绝对路径均可）
     * @return 图片描述文本；失败时返回 null
     */
    public String describe(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            log.warn("图片描述路径为空");
            return null;
        }

        try {
            Path path = Paths.get(imagePath);
            if (!Files.exists(path)) {
                log.warn("图片文件不存在: {}", imagePath);
                return null;
            }

            byte[] imageBytes = Files.readAllBytes(path);
            String base64 = Base64.getEncoder().encodeToString(imageBytes);
            String mimeType = detectMimeType(imagePath);

            Image image = Image.builder()
                    .base64Data(base64)
                    .mimeType(mimeType)
                    .build();

            UserMessage message = UserMessage.from(
                    TextContent.from(DESCRIPTION_PROMPT),
                    ImageContent.from(image)
            );

            String description = chatModel.chat(message).aiMessage().text();
            log.info("图片 [{}] 描述生成成功，长度: {}", imagePath, description.length());
            return description;
        } catch (IOException e) {
            log.error("读取图片失败: {}", imagePath, e);
            return null;
        } catch (Exception e) {
            log.error("图片视觉描述生成失败: {}", imagePath, e);
            return null;
        }
    }

    private String detectMimeType(String imagePath) {
        String lower = imagePath.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".bmp")) {
            return "image/bmp";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/png";
    }
}
