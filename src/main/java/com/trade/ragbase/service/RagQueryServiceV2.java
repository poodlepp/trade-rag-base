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
public class RagQueryServiceV2 {

    private final HybridRetrieverService hybridRetriever;
    private final ChatClient chatClient;
    private final int returnTopN;

    @Autowired
    public RagQueryServiceV2(
            HybridRetrieverService hybridRetriever,
            ChatClient chatClient,
            @Value("${rag.retrieval.return-top-n:5}") int returnTopN) {
        this.hybridRetriever = hybridRetriever;
        this.chatClient = chatClient;
        this.returnTopN = returnTopN;
    }

    public String query(String question, List<Long> kbIds) {
        validate(question, kbIds);

        List<HybridRetrieverService.ScoredChunk> scoredChunks =
                hybridRetriever.retrieve(question, kbIds, returnTopN);

        if (scoredChunks.isEmpty()) {
            return buildNotFoundResponse();
        }

        List<DocChunk> chunks = scoredChunks.stream()
                .map(HybridRetrieverService.ScoredChunk::chunk)
                .collect(Collectors.toList());
        return generateAnswer(question, chunks);
    }

    private String generateAnswer(String question, List<DocChunk> chunks) {
        String context = buildContext(chunks);
        String systemPrompt = """
                你是企业内部知识库的智能助手。根据以下参考内容回答用户问题。
                
                规则：
                1. 只基于参考内容回答，不使用自身知识推测
                2. 参考内容不足时，明确告知"未在知识库找到相关信息"
                3. 回答用中文，准确简洁
                4. 禁止编造参考内容之外的信息
                
                参考内容：
                ---
                %s
                ---
                """.formatted(context);

        log.debug("[RAG-V2] 开始生成回答，context长度={}", context.length());
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

    private String buildNotFoundResponse() {
        return "在您选择的知识库中未找到与该问题相关的内容。建议您：\n"
                + "1. 确认问题是否与知识库主题相关\n"
                + "2. 尝试用不同关键词提问\n"
                + "3. 联系相关部门获取准确信息";
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
