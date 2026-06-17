package com.trade.ragbase.dto;

import java.util.List;

import lombok.Data;

@Data
public class RagQueryRequest {

    private String question;
    private List<Long> kbIds;
}
