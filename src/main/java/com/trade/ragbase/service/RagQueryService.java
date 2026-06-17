package com.trade.ragbase.service;

import java.util.List;
import java.util.stream.Collectors;

import com.trade.ragbase.entity.DocChunk;
import com.trade.ragbase.exception.BizException;
import com.trade.ragbase.repository.DocChunkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RagQueryService {

    private final EmbeddingService embeddingService;
    private final DocChunkRepository chunkRepository;
    private final ChatClient chatClient;
    private final int vectorTopK;

    @Autowired
    public RagQueryService(
            EmbeddingService embeddingService,
            DocChunkRepository chunkRepository,
            ChatClient chatClient,
            @Value("${rag.retrieval.vector-top-k:5}") int vectorTopK) {
        this.embeddingService = embeddingService;
        this.chunkRepository = chunkRepository;
        this.chatClient = chatClient;
        this.vectorTopK = vectorTopK;
    }

    public String query(String question, List<Long> kbIds) {
        validate(question, kbIds);

        float[] queryEmbedding = embeddingService.embed(question);
        List<DocChunk> retrievedChunks = retrieveChunks(queryEmbedding, kbIds, vectorTopK);

        if (retrievedChunks.isEmpty()) {
            return "在您选择的知识库中未找到与该问题相关的内容。请确认问题是否与知识库的主题相关，或尝试用不同的表达方式提问。";
        }

        return generateAnswer(question, retrievedChunks);
    }

    protected List<DocChunk> retrieveChunks(float[] queryEmbedding, List<Long> kbIds, int topK) {
        String embeddingText = toVectorString(queryEmbedding);
        List<DocChunk> allChunks = kbIds.stream()
                .flatMap(kbId -> chunkRepository.findByVectorSimilarity(kbId, embeddingText, topK).stream())
                .collect(Collectors.toList());

        if (allChunks.size() > topK) {
            allChunks = allChunks.subList(0, topK);
        }

        log.debug("[RAG] 向量检索完成：kbIds={}，召回{}条", kbIds, allChunks.size());
        return allChunks;
    }

    protected String generateAnswer(String question, List<DocChunk> chunks) {
        String context = buildContext(chunks);
        String systemPrompt = """
                你是企业内部知识库的智能助手。你的工作是根据提供的参考文档内容，准确回答员工的问题。
                
                重要规则：
                1. 只根据提供的【参考内容】回答问题，不要使用自己的知识进行推测或补充
                2. 如果参考内容不足以回答问题，明确告诉用户"在知识库中未找到相关信息"，并建议用户联系相关部门
                3. 回答要准确、简洁，用中文回答
                4. 如果参考内容涉及多个文档，综合各文档回答
                5. 禁止编造不在参考内容中的信息
                
                参考内容如下：
                ---
                %s
                ---
                """.formatted(context);

        log.debug("[RAG] 开始生成回答，context长度={}", context.length());
        long start = System.currentTimeMillis();
        String answer = chatClient.prompt()
                .system(systemPrompt)
                .user(question)
                .call()
                .content();
        log.info("[RAG] 生成完成，耗时={}ms，answer长度={}",
                System.currentTimeMillis() - start, answer == null ? 0 : answer.length());
        return answer;
    }

    protected String toVectorString(float[] embedding) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                builder.append(",");
            }
            builder.append(embedding[i]);
        }
        builder.append("]");
        return builder.toString();
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
