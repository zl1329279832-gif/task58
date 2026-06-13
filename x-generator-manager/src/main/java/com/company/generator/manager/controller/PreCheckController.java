package com.company.generator.manager.controller;

import com.company.generator.manager.common.exception.GenerationException;
import com.company.generator.manager.dto.PreCheckRequest;
import com.company.generator.manager.dto.PreCheckResult;
import com.company.generator.manager.service.IPreCheckService;
import com.company.manerger.sys.common.base.http.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

/**
 * 代码生成预检与差异预览控制器
 */
@RestController
@RequestMapping("${admin.url.prefix}/generator/precheck")
public class PreCheckController {

    @Autowired
    private IPreCheckService preCheckService;

    /**
     * 执行预检（dry-run）
     * 读取表结构、字段类型映射、模板变量和目标文件路径，
     * 返回将新增、覆盖、跳过、存在风险的文件清单以及关键差异摘要
     */
    @PostMapping("dryrun")
    public Response dryRun(@RequestBody PreCheckRequest request) {
        try {
            PreCheckResult result = preCheckService.preCheck(request);
            Response response = Response.ok("预检查完成");
            response.put("preCheckId", result.getPreCheckId());
            response.put("entries", result.getEntries());
            response.put("globalWarnings", result.getGlobalWarnings());
            response.put("hasRisks", result.isHasRisks());
            response.put("hasOverwrites", result.isHasOverwrites());
            return response;
        } catch (GenerationException e) {
            return Response.error("预检查失败: " + e.getMessage());
        }
    }

    /**
     * 确认生成
     * 用户审查预检结果后，提交已批准的模板ID列表执行真实生成
     */
    @PostMapping("{preCheckId}/confirm")
    public Response confirm(@PathVariable("preCheckId") String preCheckId,
                            @RequestParam("approvedTemplateIds") String[] approvedTemplateIds) {
        try {
            preCheckService.confirmedGenerate(preCheckId, Arrays.asList(approvedTemplateIds));
            return Response.ok("代码生成成功");
        } catch (Exception e) {
            return Response.error("代码生成失败: " + e.getMessage());
        }
    }

    /**
     * 获取预检结果
     */
    @GetMapping("{preCheckId}")
    public Response getResult(@PathVariable("preCheckId") String preCheckId) {
        PreCheckResult result = preCheckService.getPreCheckResult(preCheckId);
        if (result == null) {
            return Response.error("预检结果不存在或已过期");
        }
        Response response = Response.ok("获取成功");
        response.put("preCheckId", result.getPreCheckId());
        response.put("entries", result.getEntries());
        response.put("globalWarnings", result.getGlobalWarnings());
        response.put("hasRisks", result.isHasRisks());
        response.put("hasOverwrites", result.isHasOverwrites());
        return response;
    }
}
