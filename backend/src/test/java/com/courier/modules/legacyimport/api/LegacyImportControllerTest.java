package com.courier.modules.legacyimport.api;

import com.courier.modules.legacyimport.application.CommitResult;
import com.courier.modules.legacyimport.application.LegacyImportService;
import com.courier.modules.legacyimport.application.PreviewResult;
import com.courier.shared.exception.GlobalExceptionHandler;
import com.courier.shared.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real method security + real exception handler around a mocked service. */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = LegacyImportControllerTest.TestConfig.class)
class LegacyImportControllerTest {

    @Configuration
    @EnableWebMvc
    @EnableMethodSecurity
    @Import({LegacyImportController.class, GlobalExceptionHandler.class})
    static class TestConfig {
        @Bean LegacyImportService legacyImportService() { return mock(LegacyImportService.class); }
    }

    @Autowired WebApplicationContext context;
    @Autowired LegacyImportService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        reset(service);
        actAs(Set.of("COMPANY_ADMIN"), Set.of("LEGACY_IMPORT_CREATE", "LEGACY_IMPORT_READ"));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void actAs(Set<String> roles, Set<String> permissions) {
        AuthenticatedUser p = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "a@b.com", roles,
                permissions, "jti");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(p, null, p.authorities()));
    }

    private static MockMultipartFile xls(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, "application/vnd.ms-excel", bytes);
    }

    @Test
    void previewReturnsServiceResult() throws Exception {
        when(service.preview(eq("c.xls"), any())).thenReturn(
                new PreviewResult("h", 2, 1, 1, 0, 0, List.of(), List.of()));

        mvc.perform(multipart("/api/v1/legacy-import/preview").file(xls("c.xls", "<table/>".getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.newCount").value(1));
    }

    @Test
    void commitReturnsCounts() throws Exception {
        when(service.commit(eq("c.xls"), any())).thenReturn(new CommitResult(UUID.randomUUID(), 5, 2, 1, List.of()));

        mvc.perform(multipart("/api/v1/legacy-import/commit").file(xls("c.xls", "<table/>".getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(5))
                .andExpect(jsonPath("$.data.skippedExisting").value(2));
    }

    @Test
    void branchManagerIsForbidden() throws Exception {
        actAs(Set.of("BRANCH_MANAGER"), Set.of("LEGACY_IMPORT_CREATE", "LEGACY_IMPORT_READ"));

        mvc.perform(multipart("/api/v1/legacy-import/preview").file(xls("c.xls", "x".getBytes())))
                .andExpect(status().isForbidden());
        verify(service, never()).preview(any(), any());
    }

    @Test
    void companyAdminWithoutPermissionCodeIsForbidden() throws Exception {
        actAs(Set.of("COMPANY_ADMIN"), Set.of());

        mvc.perform(multipart("/api/v1/legacy-import/commit").file(xls("c.xls", "x".getBytes())))
                .andExpect(status().isForbidden());
        verify(service, never()).commit(any(), any());
    }

    @Test
    void nonExcelExtensionRejected() throws Exception {
        mvc.perform(multipart("/api/v1/legacy-import/preview")
                        .file(new MockMultipartFile("file", "x.pdf", "application/pdf", "x".getBytes())))
                .andExpect(status().is4xxClientError());
        verify(service, never()).preview(any(), any());
    }

    @Test
    void oversizeFileRejected() throws Exception {
        byte[] big = new byte[10 * 1024 * 1024 + 1];

        mvc.perform(multipart("/api/v1/legacy-import/preview").file(xls("big.xls", big)))
                .andExpect(status().is4xxClientError());
        verify(service, never()).preview(any(), any());
    }

    @Test
    void emptyFileRejected() throws Exception {
        mvc.perform(multipart("/api/v1/legacy-import/preview").file(xls("e.xls", new byte[0])))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void originMapValidatesBody() throws Exception {
        mvc.perform(put("/api/v1/legacy-import/origin-map").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origin\":\"\",\"branchId\":null}"))
                .andExpect(status().is4xxClientError());
        verify(service, never()).saveOriginMapping(any(), any());
    }

    @Test
    void originMapDelegates() throws Exception {
        UUID branch = UUID.randomUUID();

        mvc.perform(put("/api/v1/legacy-import/origin-map").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origin\":\"BHOSARI\",\"branchId\":\"" + branch + "\"}"))
                .andExpect(status().isOk());
        verify(service).saveOriginMapping("BHOSARI", branch);
    }

    @Test
    void batchesListed() throws Exception {
        when(service.listBatches()).thenReturn(List.of());

        mvc.perform(get("/api/v1/legacy-import/batches")).andExpect(status().isOk());
    }
}
