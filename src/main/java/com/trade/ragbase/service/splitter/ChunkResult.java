package com.trade.ragbase.service.splitter;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChunkResult {

    private int chunkIndex;

    private String content;

    private Integer pageNum;

    private String sectionTitle;

    private int estimatedTokens;
}
