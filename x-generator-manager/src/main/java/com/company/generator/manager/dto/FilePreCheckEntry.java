package com.company.generator.manager.dto;

import com.alibaba.fastjson.annotation.JSONField;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 单个文件的预检结果
 */
public class FilePreCheckEntry implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 模板ID */
    private String templateId;
    /** 模板名称 */
    private String templateName;
    /** 目标文件绝对路径 */
    private String targetFilePath;
    /** 预检操作类型 */
    private FileAction action;
    /** 生成的内容（仅服务端缓存，不返回给前端） */
    @JSONField(serialize = false)
    private String generatedContent;
    /** 已存在文件的内容预览（前50行） */
    private String existingContentPreview;
    /** 差异摘要 */
    private String diffSummary;
    /** 风险信息列表 */
    private List<String> risks = new ArrayList<>();
    /** 已存在文件大小（字节），不存在为-1 */
    private long existingFileSize = -1;
    /** 已存在文件最后修改时间戳，不存在为-1 */
    private long existingFileLastModified = -1;

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public String getTargetFilePath() {
        return targetFilePath;
    }

    public void setTargetFilePath(String targetFilePath) {
        this.targetFilePath = targetFilePath;
    }

    public FileAction getAction() {
        return action;
    }

    public void setAction(FileAction action) {
        this.action = action;
    }

    public String getGeneratedContent() {
        return generatedContent;
    }

    public void setGeneratedContent(String generatedContent) {
        this.generatedContent = generatedContent;
    }

    public String getExistingContentPreview() {
        return existingContentPreview;
    }

    public void setExistingContentPreview(String existingContentPreview) {
        this.existingContentPreview = existingContentPreview;
    }

    public String getDiffSummary() {
        return diffSummary;
    }

    public void setDiffSummary(String diffSummary) {
        this.diffSummary = diffSummary;
    }

    public List<String> getRisks() {
        return risks;
    }

    public void setRisks(List<String> risks) {
        this.risks = risks;
    }

    public long getExistingFileSize() {
        return existingFileSize;
    }

    public void setExistingFileSize(long existingFileSize) {
        this.existingFileSize = existingFileSize;
    }

    public long getExistingFileLastModified() {
        return existingFileLastModified;
    }

    public void setExistingFileLastModified(long existingFileLastModified) {
        this.existingFileLastModified = existingFileLastModified;
    }
}
