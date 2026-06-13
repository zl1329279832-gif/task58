package com.company.generator.manager.entity;

import com.baomidou.mybatisplus.annotations.TableField;
import com.baomidou.mybatisplus.annotations.TableName;
import com.company.manerger.sys.common.mybatis.base.AbstractEntity;

/**
 * 预检记录实体
 */
@TableName("generator_precheck_record")
public class PreCheckRecord extends AbstractEntity<String> implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    private String id;

    /** 预检会话ID */
    private String preCheckId;

    /** 方案ID */
    private String schemeId;

    /** 表ID */
    private String tableId;

    /** 模板方案ID */
    private String templateSchemeId;

    /** 操作类型：PRECHECK / GENERATE */
    private String operationType;

    /** 文件操作摘要（JSON格式） */
    @TableField("action_summary")
    private String actionSummary;

    /** 全局警告信息（JSON格式） */
    @TableField("global_warnings")
    private String globalWarnings;

    /** 操作状态：SUCCESS / FAILED */
    private String status;

    /** 错误信息 */
    private String errorMessage;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public String getActionSummary() {
        return actionSummary;
    }

    public void setActionSummary(String actionSummary) {
        this.actionSummary = actionSummary;
    }

    public String getGlobalWarnings() {
        return globalWarnings;
    }

    public void setGlobalWarnings(String globalWarnings) {
        this.globalWarnings = globalWarnings;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
