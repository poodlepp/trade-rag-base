package com.trade.ragbase.dto;

import lombok.Data;

@Data
public class IndexStatusResponse {

    private Long docId;
    private String fileName;
    private String status;
    private String errorMsg;
    private Integer chunkCount;
    private Integer tokenCount;
    private String indexedAt;
    private Integer retryCount;
}
