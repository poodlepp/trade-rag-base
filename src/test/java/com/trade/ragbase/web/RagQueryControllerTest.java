package com.trade.ragbase.web;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.trade.ragbase.config.GlobalExceptionHandler;
import com.trade.ragbase.service.RagQueryService;

class RagQueryControllerTest {

    @Test
    void queryReturnsGeneratedAnswer() throws Exception {
        RagQueryService ragQueryService = mock(RagQueryService.class);
        when(ragQueryService.query("年假有几天", List.of(1L, 2L)))
                .thenReturn("员工年假为每年 10 天。");
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RagQueryController(ragQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/api/v1/rag/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "年假有几天",
                                  "kbIds": [1, 2]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data", is("员工年假为每年 10 天。")));
    }
}
