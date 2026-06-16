package com.trade.ragbase.dto;

import lombok.Data;

@Data
public class DocumentUploadResponse {

    private Long docId;
    private String fileName;
    private String status;
    private String message;

    public static DocumentUploadResponse submitted(Long docId, String fileName) {
        DocumentUploadResponse response = new DocumentUploadResponse();
        response.setDocId(docId);
        response.setFileName(fileName);
        response.setStatus("PENDING");
        response.setMessage("文档已上传，正在后台索引，请通过 /status 接口查询进度");
        return response;
    }
}
