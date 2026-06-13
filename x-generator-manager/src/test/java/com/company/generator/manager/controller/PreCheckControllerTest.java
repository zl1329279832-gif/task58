package com.company.generator.manager.controller;

import com.alibaba.fastjson.JSON;
import com.company.generator.manager.dto.*;
import com.company.generator.manager.service.IPreCheckService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@RunWith(MockitoJUnitRunner.class)
public class PreCheckControllerTest {

    @InjectMocks
    private PreCheckController preCheckController;

    @Mock
    private IPreCheckService preCheckService;

    private MockMvc mockMvc;

    @Before
    public void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(preCheckController).build();
    }

    @Test
    public void testDryRunEndpoint_success() throws Exception {
        PreCheckResult result = new PreCheckResult();
        result.setPreCheckId("test-precheck-id");
        result.setSchemeId("scheme-001");
        result.setTableId("table-001");
        result.setTemplateSchemeId("ts-001");
        result.setGlobalWarnings(new ArrayList<>());

        FilePreCheckEntry entry = new FilePreCheckEntry();
        entry.setTemplateId("tpl-001");
        entry.setTemplateName("Entity模板");
        entry.setTargetFilePath("/output/User.java");
        entry.setAction(FileAction.ADD);
        result.addEntry(entry);

        when(preCheckService.preCheck(any(PreCheckRequest.class))).thenReturn(result);

        PreCheckRequest request = new PreCheckRequest();
        request.setSchemeId("scheme-001");
        request.setTableId("table-001");
        request.setTemplateSchemeId("ts-001");

        PreCheckRequest.TemplateTarget target = new PreCheckRequest.TemplateTarget();
        target.setTemplateId("tpl-001");
        target.setTargetPath("/output");
        target.setTargetPackage("com.example.entity");
        request.setSelectedTemplates(Collections.singletonList(target));

        mockMvc.perform(post("/admin/generator/precheck/dryrun")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.preCheckId").value("test-precheck-id"))
                .andExpect(jsonPath("$.entries").isArray())
                .andExpect(jsonPath("$.entries[0].templateId").value("tpl-001"))
                .andExpect(jsonPath("$.entries[0].action").value("ADD"));
    }

    @Test
    public void testDryRunEndpoint_error() throws Exception {
        when(preCheckService.preCheck(any(PreCheckRequest.class)))
                .thenThrow(new com.company.generator.manager.common.exception.GenerationException("方案不存在"));

        PreCheckRequest request = new PreCheckRequest();
        request.setSchemeId("invalid-id");
        request.setTableId("table-001");
        request.setTemplateSchemeId("ts-001");
        request.setSelectedTemplates(new ArrayList<>());

        mockMvc.perform(post("/admin/generator/precheck/dryrun")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("预检查失败: 方案不存在"));
    }

    @Test
    public void testGetResultEndpoint_success() throws Exception {
        PreCheckResult result = new PreCheckResult();
        result.setPreCheckId("test-id");
        result.setGlobalWarnings(new ArrayList<>());
        result.setEntries(new ArrayList<>());

        when(preCheckService.getPreCheckResult("test-id")).thenReturn(result);

        mockMvc.perform(get("/admin/generator/precheck/test-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.preCheckId").value("test-id"));
    }

    @Test
    public void testGetResultEndpoint_notFound() throws Exception {
        when(preCheckService.getPreCheckResult("expired-id")).thenReturn(null);

        mockMvc.perform(get("/admin/generator/precheck/expired-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("预检结果不存在或已过期"));
    }

    @Test
    public void testConfirmEndpoint_success() throws Exception {
        mockMvc.perform(post("/admin/generator/precheck/test-id/confirm")
                        .param("approvedTemplateIds", "tpl-001", "tpl-002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("代码生成成功"));
    }
}
