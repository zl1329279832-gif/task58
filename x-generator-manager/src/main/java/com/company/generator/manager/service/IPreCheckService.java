package com.company.generator.manager.service;

import com.company.generator.manager.common.exception.GenerationException;
import com.company.generator.manager.dto.PreCheckRequest;
import com.company.generator.manager.dto.PreCheckResult;

import java.io.IOException;
import java.util.List;

public interface IPreCheckService {

    /**
     * 执行预检（dry-run），不写入文件
     */
    PreCheckResult preCheck(PreCheckRequest request) throws GenerationException;

    /**
     * 确认生成，根据预检结果写入已批准的文件
     */
    void confirmedGenerate(String preCheckId, List<String> approvedTemplateIds) throws IOException, GenerationException;

    /**
     * 获取已缓存的预检结果
     */
    PreCheckResult getPreCheckResult(String preCheckId);
}
