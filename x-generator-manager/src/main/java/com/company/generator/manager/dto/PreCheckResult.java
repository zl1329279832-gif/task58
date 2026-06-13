package com.company.generator.manager.dto;

import com.alibaba.fastjson.annotation.JSONField;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 预检结果
 */
public class PreCheckResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 预检会话ID，用于确认生成时引用 */
    private String preCheckId;
    /** 方案ID */
    private String schemeId;
    /** 表ID */
    private String tableId;
    /** 模板方案ID */
    private String templateSchemeId;
    /** 每个文件的预检条目 */
    private List<FilePreCheckEntry> entries = new ArrayList<>();
    /** 全局警告信息 */
    private List<String> globalWarnings = new ArrayList<>();
    /** 是否存在风险 */
    private boolean hasRisks;
    /** 是否存在覆盖 */
    private boolean hasOverwrites;
    /** 创建时间戳（用于TTL过期） */
    @JSONField(serialize = false)
    private long createTime;

    public PreCheckResult() {
        this.createTime = System.currentTimeMillis();
    }

    public void addEntry(FilePreCheckEntry entry) {
        this.entries.add(entry);
        if (entry.getAction() == FileAction.RISK) {
            this.hasRisks = true;
        }
        if (entry.getAction() == FileAction.OVERWRITE) {
            this.hasOverwrites = true;
        }
    }

    public String getPreCheckId() {
        return preCheckId;
    }

    public void setPreCheckId(String preCheckId) {
        this.preCheckId = preCheckId;
    }

    public String getSchemeId() {
        return schemeId;
    }

    public void setSchemeId(String schemeId) {
        this.schemeId = schemeId;
    }

    public String getTableId() {
        return tableId;
    }

    public void setTableId(String tableId) {
        this.tableId = tableId;
    }

    public String getTemplateSchemeId() {
        return templateSchemeId;
    }

    public void setTemplateSchemeId(String templateSchemeId) {
        this.templateSchemeId = templateSchemeId;
    }

    public List<FilePreCheckEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<FilePreCheckEntry> entries) {
        this.entries = entries;
    }

    public List<String> getGlobalWarnings() {
        return globalWarnings;
    }

    public void setGlobalWarnings(List<String> globalWarnings) {
        this.globalWarnings = globalWarnings;
    }

    public boolean isHasRisks() {
        return hasRisks;
    }

    public void setHasRisks(boolean hasRisks) {
        this.hasRisks = hasRisks;
    }

    public boolean isHasOverwrites() {
        return hasOverwrites;
    }

    public void setHasOverwrites(boolean hasOverwrites) {
        this.hasOverwrites = hasOverwrites;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }
}
