package com.company.generator.manager.service.impl;

import com.alibaba.fastjson.JSON;
import com.company.generator.manager.common.definition.type.DbTypeConvert;
import com.company.generator.manager.common.definition.data.Type;
import com.company.generator.manager.common.exception.GenerationException;
import com.company.generator.manager.dto.*;
import com.company.generator.manager.entity.*;
import com.company.generator.manager.mapper.PreCheckRecordMapper;
import com.company.generator.manager.service.*;
import com.company.manerger.sys.common.mybatis.wrapper.EntityWrapper;
import freemarker.template.TemplateException;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service("preCheckService")
public class PreCheckServiceImpl implements IPreCheckService {
    private static final Logger logger = LoggerFactory.getLogger(PreCheckServiceImpl.class);

    /** 预检结果缓存TTL（30分钟） */
    private static final long CACHE_TTL_MS = 30 * 60 * 1000L;
    /** 文件内容预览行数 */
    private static final int PREVIEW_LINES = 50;

    @Autowired
    private ISchemeService schemeService;
    @Autowired
    private ITableService tableService;
    @Autowired
    private IColumnService columnService;
    @Autowired
    private ITemplateService templateService;
    @Autowired
    private ITemplateSchemeService templateSchemeService;
    @Autowired
    private IDataSourceService dataSourceService;
    @Autowired
    private PreCheckRecordMapper preCheckRecordMapper;

    /** 预检结果缓存 */
    private final ConcurrentHashMap<String, PreCheckResult> preCheckCache = new ConcurrentHashMap<>();

    @Override
    public PreCheckResult preCheck(PreCheckRequest request) throws GenerationException {
        // 1. 验证输入并加载实体
        if (request.getSchemeId() == null || request.getSchemeId().isEmpty()) {
            throw new GenerationException("方案ID不能为空");
        }
        if (request.getTableId() == null || request.getTableId().isEmpty()) {
            throw new GenerationException("表ID不能为空");
        }
        if (request.getTemplateSchemeId() == null || request.getTemplateSchemeId().isEmpty()) {
            throw new GenerationException("模板方案ID不能为空");
        }

        Scheme scheme = schemeService.selectById(request.getSchemeId());
        if (scheme == null) {
            throw new GenerationException("方案不存在: " + request.getSchemeId());
        }

        Table table = tableService.selectById(request.getTableId());
        if (table == null) {
            throw new GenerationException("表不存在: " + request.getTableId());
        }

        TemplateScheme templateScheme = templateSchemeService.selectById(request.getTemplateSchemeId());
        if (templateScheme == null) {
            throw new GenerationException("模板方案不存在: " + request.getTemplateSchemeId());
        }

        // 加载数据源
        DataSource dataSource = dataSourceService.selectById(table.getSourceId());

        // 2. 加载字段并检查类型映射
        List<Column> columns = columnService.selectListByTableId(table.getId());
        List<String> globalWarnings = new ArrayList<>();

        if (dataSource != null) {
            checkTypeMappings(columns, dataSource.getDbType(), globalWarnings);
        } else {
            globalWarnings.add("数据源不存在(sourceId=" + table.getSourceId() + ")，无法检查字段类型映射");
        }

        // 3. 加载所有模板
        EntityWrapper<Template> entityWrapper = new EntityWrapper<>(Template.class);
        entityWrapper.eq("scheme_id", request.getTemplateSchemeId());
        entityWrapper.orderBy("sort");
        List<Template> allTemplates = templateService.selectList(entityWrapper);

        // 构建选中模板的ID->配置映射
        Map<String, PreCheckRequest.TemplateTarget> targetMap = new HashMap<>();
        if (request.getSelectedTemplates() != null) {
            for (PreCheckRequest.TemplateTarget target : request.getSelectedTemplates()) {
                targetMap.put(target.getTemplateId(), target);
            }
        }

        // 应用用户指定的targetPath/targetPackage覆盖
        List<Template> selectedTemplates = new ArrayList<>();
        for (Template template : allTemplates) {
            PreCheckRequest.TemplateTarget target = targetMap.get(template.getId());
            if (target != null) {
                if (target.getTargetPath() != null && !target.getTargetPath().isEmpty()) {
                    template.setTargetPath(target.getTargetPath());
                }
                if (target.getTargetPackage() != null && !target.getTargetPackage().isEmpty()) {
                    template.setTargetPackage(target.getTargetPackage());
                }
                selectedTemplates.add(template);
            }
        }

        if (selectedTemplates.isEmpty()) {
            throw new GenerationException("未选择任何模板");
        }

        // 4. 对每个选中模板进行预检
        PreCheckResult result = new PreCheckResult();
        result.setPreCheckId(UUID.randomUUID().toString().replace("-", ""));
        result.setSchemeId(request.getSchemeId());
        result.setTableId(request.getTableId());
        result.setTemplateSchemeId(request.getTemplateSchemeId());
        result.setGlobalWarnings(globalWarnings);

        for (Template template : selectedTemplates) {
            FilePreCheckEntry entry = checkSingleTemplate(scheme, template, allTemplates, columns, globalWarnings);
            result.addEntry(entry);
        }

        // 5. 缓存结果
        cleanExpiredCache();
        preCheckCache.put(result.getPreCheckId(), result);

        // 6. 记录预检操作
        saveRecord(result, "PRECHECK", "SUCCESS", null);

        return result;
    }

    @Override
    public void confirmedGenerate(String preCheckId, List<String> approvedTemplateIds) throws IOException, GenerationException {
        PreCheckResult result = getPreCheckResult(preCheckId);
        if (result == null) {
            throw new GenerationException("预检结果不存在或已过期: " + preCheckId);
        }

        Set<String> approvedSet = new HashSet<>(approvedTemplateIds);
        List<String> generatedFiles = new ArrayList<>();

        try {
            for (FilePreCheckEntry entry : result.getEntries()) {
                if (!approvedSet.contains(entry.getTemplateId())) {
                    continue;
                }
                if (entry.getAction() == FileAction.SKIP) {
                    continue;
                }
                if (entry.getGeneratedContent() == null) {
                    logger.warn("模板 '{}' 生成内容为空，跳过写入: {}", entry.getTemplateName(), entry.getTargetFilePath());
                    continue;
                }

                File outFile = new File(entry.getTargetFilePath());
                File parentDir = outFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }
                FileUtils.write(outFile, entry.getGeneratedContent(), "UTF-8");
                generatedFiles.add(entry.getTargetFilePath());
            }

            // 记录生成操作
            saveRecord(result, "GENERATE", "SUCCESS", null);
        } catch (IOException e) {
            saveRecord(result, "GENERATE", "FAILED", e.getMessage());
            throw e;
        } finally {
            preCheckCache.remove(preCheckId);
        }
    }

    @Override
    public PreCheckResult getPreCheckResult(String preCheckId) {
        PreCheckResult result = preCheckCache.get(preCheckId);
        if (result == null) {
            return null;
        }
        if (System.currentTimeMillis() - result.getCreateTime() > CACHE_TTL_MS) {
            preCheckCache.remove(preCheckId);
            return null;
        }
        return result;
    }

    /**
     * 检查字段类型映射
     */
    private void checkTypeMappings(List<Column> columns, String dbType, List<String> globalWarnings) {
        try {
            DbTypeConvert typeConvert = (DbTypeConvert) DbTypeConvert.getTypeConvert(DbTypeConvert.TYPE_DB_TO_JAVA, dbType);
            for (Column column : columns) {
                String typeName = column.getTypeName();
                if (typeName != null && !typeName.isEmpty()) {
                    Type mappedType = typeConvert.getType(typeName);
                    if (mappedType == null) {
                        globalWarnings.add("字段 '" + column.getColumnName() + "' 的数据库类型 '" + typeName + "' 没有对应的Java类型映射，将默认使用String");
                    }
                }
            }
        } catch (Exception e) {
            globalWarnings.add("类型映射检查失败: " + e.getMessage());
        }
    }

    /**
     * 对单个模板执行预检
     */
    private FilePreCheckEntry checkSingleTemplate(Scheme scheme, Template template,
                                                   List<Template> allTemplates, List<Column> columns,
                                                   List<String> globalWarnings) {
        FilePreCheckEntry entry = new FilePreCheckEntry();
        entry.setTemplateId(template.getId());
        entry.setTemplateName(template.getName());

        // 解析输出路径
        try {
            File targetFile = tableService.resolveOutPath(scheme, template);
            entry.setTargetFilePath(targetFile.getAbsolutePath());
        } catch (Exception e) {
            entry.setAction(FileAction.RISK);
            entry.getRisks().add("输出路径解析失败: " + e.getMessage());
            return entry;
        }

        // 渲染模板
        String generatedContent = null;
        try {
            Map<String, Object> ftlMap = tableService.getFtlMap(scheme, template, allTemplates);
            generatedContent = tableService.parseTemplate(ftlMap, template.getTemplateContent());
            entry.setGeneratedContent(generatedContent);
        } catch (TemplateException e) {
            entry.setAction(FileAction.RISK);
            entry.getRisks().add("模板变量缺失或渲染失败: " + e.getMessage());
            return entry;
        } catch (IOException e) {
            entry.setAction(FileAction.RISK);
            entry.getRisks().add("模板渲染IO异常: " + e.getMessage());
            return entry;
        }

        // 检查目标文件
        File targetFile = new File(entry.getTargetFilePath());
        if (!targetFile.exists()) {
            entry.setAction(FileAction.ADD);
        } else {
            entry.setExistingFileSize(targetFile.length());
            entry.setExistingFileLastModified(targetFile.lastModified());

            try {
                String existingContent = FileUtils.readFileToString(targetFile, "UTF-8");

                if (existingContent.equals(generatedContent)) {
                    entry.setAction(FileAction.SKIP);
                } else {
                    entry.setAction(FileAction.OVERWRITE);
                    entry.setDiffSummary(computeDiffSummary(existingContent, generatedContent));
                    entry.setExistingContentPreview(getContentPreview(existingContent, PREVIEW_LINES));
                }
            } catch (IOException e) {
                entry.setAction(FileAction.RISK);
                entry.getRisks().add("读取已存在文件失败: " + e.getMessage());
            }
        }

        // 附加全局风险信息（如字段类型映射缺失）
        if (!globalWarnings.isEmpty() && entry.getAction() != FileAction.RISK) {
            for (String warning : globalWarnings) {
                if (warning.startsWith("字段")) {
                    entry.getRisks().add(warning);
                }
            }
        }

        return entry;
    }

    /**
     * 计算差异摘要
     */
    private String computeDiffSummary(String existingContent, String generatedContent) {
        String[] existingLines = existingContent.split("\n", -1);
        String[] generatedLines = generatedContent.split("\n", -1);

        Set<String> existingSet = new HashSet<>(Arrays.asList(existingLines));
        Set<String> generatedSet = new HashSet<>(Arrays.asList(generatedLines));

        int addedCount = 0;
        for (String line : generatedLines) {
            if (!existingSet.contains(line)) {
                addedCount++;
            }
        }

        int removedCount = 0;
        for (String line : existingLines) {
            if (!generatedSet.contains(line)) {
                removedCount++;
            }
        }

        return "+" + addedCount + " 行新增, -" + removedCount + " 行删除 (原文件 " + existingLines.length + " 行, 新文件 " + generatedLines.length + " 行)";
    }

    /**
     * 获取内容预览（前N行）
     */
    private String getContentPreview(String content, int maxLines) {
        String[] lines = content.split("\n", -1);
        int lineCount = Math.min(lines.length, maxLines);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lineCount; i++) {
            if (i > 0) {
                sb.append("\n");
            }
            sb.append(lines[i]);
        }
        if (lines.length > maxLines) {
            sb.append("\n... (共 ").append(lines.length).append(" 行)");
        }
        return sb.toString();
    }

    /**
     * 清理过期的缓存
     */
    private void cleanExpiredCache() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, PreCheckResult>> it = preCheckCache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, PreCheckResult> cacheEntry = it.next();
            if (now - cacheEntry.getValue().getCreateTime() > CACHE_TTL_MS) {
                it.remove();
            }
        }
    }

    /**
     * 保存预检/生成记录
     */
    private void saveRecord(PreCheckResult result, String operationType, String status, String errorMessage) {
        try {
            PreCheckRecord record = new PreCheckRecord();
            record.setId(UUID.randomUUID().toString().replace("-", ""));
            record.setPreCheckId(result.getPreCheckId());
            record.setSchemeId(result.getSchemeId());
            record.setTableId(result.getTableId());
            record.setTemplateSchemeId(result.getTemplateSchemeId());
            record.setOperationType(operationType);
            record.setStatus(status);
            record.setErrorMessage(errorMessage);

            // 汇总文件操作
            Map<String, Integer> actionCount = new HashMap<>();
            for (FilePreCheckEntry entry : result.getEntries()) {
                String action = entry.getAction() != null ? entry.getAction().name() : "UNKNOWN";
                actionCount.put(action, actionCount.getOrDefault(action, 0) + 1);
            }
            record.setActionSummary(JSON.toJSONString(actionCount));
            record.setGlobalWarnings(JSON.toJSONString(result.getGlobalWarnings()));

            preCheckRecordMapper.insert(record);
        } catch (Exception e) {
            logger.error("保存预检记录失败", e);
        }
    }
}
