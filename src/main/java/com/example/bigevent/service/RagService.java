package com.example.bigevent.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RagService {

    @Autowired
    private EmbeddingStoreIngestor ingestor;

    @Autowired
    private ContentRetriever contentRetriever;

    @Autowired
    private Aiservice aiService;

    public void addKnowledge(String text) {
        Document document = Document.from(text);
        ingestor.ingest(document);
    }

    public String ragChat(String query) {
        List<Content> contents = contentRetriever.retrieve(Query.from(query));

        String knowledge = contents.stream()
                .map(content -> content.textSegment().text())
                .collect(Collectors.joining("\n\n"));

        String enhancedPrompt = "基于以下知识库内容回答问题：\n\n" +
                "知识库：\n" + knowledge + "\n\n" +
                "问题：" + query + "\n\n" +
                "请根据知识库内容回答，如果知识库中没有相关信息，请说明。";

        return aiService.chat(enhancedPrompt);
    }
}
