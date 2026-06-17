package com.trade.ragbase.service;

public final class RagPromptTemplate {

    private RagPromptTemplate() {
    }

    public static String buildSystemPrompt(String context, int chunkCount) {
        return """
                你是企业内部知识库的智能助手。

                【参考内容】（共 %d 条，编号 [参考1] 到 [参考%d]）：
                ---
                %s
                ---

                【回答规则】：
                1. 只基于上面的参考内容回答，不使用自身知识进行推测或补充
                2. 回答中每条信息后面标注来源，格式：（来源：[参考N]）
                3. 如果多条参考内容都涉及，可以引用多个：（来源：[参考1][参考2]）
                4. 如果参考内容不包含答案，回答："在知识库中未找到关于[问题要点]的信息。"
                5. 禁止编造，禁止说"根据我的了解"、"通常情况下"等推测性表达
                6. 保持信息完整性：参考内容中的步骤、列表、编号条目必须完整列出
                7. 如果参考内容包含具体流程或操作步骤，按原文顺序逐条列出
                """.formatted(chunkCount, chunkCount, context);
    }
}
