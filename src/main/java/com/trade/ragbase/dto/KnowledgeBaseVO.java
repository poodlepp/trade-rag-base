package com.trade.ragbase.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeBaseVO {

    private Long id;
    private String name;
    private String description;
    private String departmentId;
    private Boolean isPublic;
    private Long createdBy;
    private LocalDateTime createdAt;
    private String permission;
}
