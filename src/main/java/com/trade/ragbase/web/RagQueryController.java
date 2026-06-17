package com.trade.ragbase.web;

import com.trade.ragbase.common.ApiResponse;
import com.trade.ragbase.dto.RagQueryRequest;
import com.trade.ragbase.service.RagQueryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rag")
public class RagQueryController {

    private final RagQueryService ragQueryService;

    public RagQueryController(RagQueryService ragQueryService) {
        this.ragQueryService = ragQueryService;
    }

    @PostMapping("/query")
    public ApiResponse<String> query(@RequestBody RagQueryRequest request) {
        return ApiResponse.ok(ragQueryService.query(request.getQuestion(), request.getKbIds()));
    }
}
