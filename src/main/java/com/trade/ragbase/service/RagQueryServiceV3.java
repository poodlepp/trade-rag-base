package com.trade.ragbase.service;

import java.util.List;
import java.util.stream.Collectors;

import com.trade.ragbase.entity.DocChunk;
import com.trade.ragbase.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RagQueryServiceV3 {

    private final EnhancedRetrieverService enhancedRetriever;
    private final ChatClient chatClient;
    private final int returnTopN;

    @Autowired
    public RagQueryServiceV3(
            EnhancedRetrieverService enhancedRetriever,
            ChatClient chatClient,
            @Value("${rag.retrieval.return-top-n:5}") int returnTopN) {
        this.enhancedRetriever = enhancedRetriever;
        this.chatClient = chatClient;
        this.returnTopN = returnTopN;
    }

    public String query(String question, List<Long> kbIds) {
        validate(question, kbIds);

        List<HybridRetrieverService.ScoredChunk> scoredChunks =
                enhancedRetriever.retrieveWithHyde(question, kbIds, returnTopN);
        if (scoredChunks.isEmpty()) {
            return "在您选择的知识库中未找到与该问题相关的内容。";
        }

        List<DocChunk> chunks = scoredChunks.stream()
                .map(HybridRetrieverService.ScoredChunk::chunk)
                .collect(Collectors.toList());
        return generateAnswer(question, chunks);
    }

    private String generateAnswer(String question, List<DocChunk> chunks) {
        String context = buildContext(chunks);
        String systemPrompt = """
                你是企业内部知识库的智能助手。根据提供的参考内容回答问题。
                规则：只根据参考内容回答，不要编造；如果参考内容不够，告诉用户未找到相关信息。
                
                参考内容：
                ---
                %s
                ---
                """.formatted(context);

        log.debug("[RAG-V3] 开始生成回答，context长度={}", context.length());
        return chatClient.prompt()
                .system(systemPrompt)
                .user(question)
                .call()
                .content();
    }

    private String buildContext(List<DocChunk> chunks) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            DocChunk chunk = chunks.get(i);
            builder.append("[参考").append(i + 1).append("]");
            if (chunk.getSectionTitle() != null) {
                builder.append(" ").append(chunk.getSectionTitle());
            }
            builder.append("\n").append(chunk.getContent()).append("\n\n");
        }
        return builder.toString().strip();
    }

    private void validate(String question, List<Long> kbIds) {
        if (question == null || question.isBlank()) {
            throw BizException.badRequest("问题不能为空");
        }
        if (kbIds == null || kbIds.isEmpty()) {
            throw BizException.badRequest("知识库 ID 不能为空");
        }
    }
}
