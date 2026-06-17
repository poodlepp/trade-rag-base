package com.trade.ragbase.web;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.trade.ragbase.config.GlobalExceptionHandler;
import com.trade.ragbase.entity.KbDocument;
import com.trade.ragbase.service.DocumentUpdateService;
import com.trade.ragbase.service.PermissionService;

class DocumentUpdateControllerTest {

    @Test
    void replaceContentRequiresWriteAndReturnsUpdatedDocument() throws Exception {
        PermissionService permissionService = mock(PermissionService.class);
        DocumentUpdateService updateService = mock(DocumentUpdateService.class);
        KbDocument document = new KbDocument();
        document.setId(9L);
        document.setFileName("new-guide.pdf");
        document.setVersion(4);
        when(updateService.replaceDocument(org.mockito.Mockito.eq(9L), org.mockito.Mockito.any()))
                .thenReturn(document);
        MockMvc mockMvc = mockMvc(permissionService, updateService);

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/kb/2/documents/9/content")
                        .file("file", "new".getBytes()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(9)))
                .andExpect(jsonPath("$.data.fileName", is("new-guide.pdf")))
                .andExpect(jsonPath("$.data.version", is(4)));
        verify(permissionService).requireWrite(2L);
    }

    @Test
    void forceReindexRequiresWriteAndSubmitsTask() throws Exception {
        PermissionService permissionService = mock(PermissionService.class);
        DocumentUpdateService updateService = mock(DocumentUpdateService.class);
        MockMvc mockMvc = mockMvc(permissionService, updateService);

        mockMvc.perform(post("/api/v1/kb/2/documents/9/reindex-force"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)));
        verify(permissionService).requireWrite(2L);
        verify(updateService).forceReindexAndSubmit(9L);
    }

    private MockMvc mockMvc(PermissionService permissionService, DocumentUpdateService updateService) {
        return MockMvcBuilders.standaloneSetup(new DocumentUpdateController(permissionService, updateService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}
