package com.trade.ragbase.service.splitter;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChunkConfig {

    @Builder.Default
    private int chunkSize = 512;

    @Builder.Default
    private int chunkOverlap = 64;

    @Builder.Default
    private boolean structureAware = true;

    public static ChunkConfig defaultConfig() {
        return ChunkConfig.builder().build();
    }
}
