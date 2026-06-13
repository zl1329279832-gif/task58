package com.company.generator.manager.dto;

import java.io.Serializable;
import java.util.List;

/**
 * 预检请求
 */
public class PreCheckRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 方案ID */
    private String schemeId;
    /** 表ID */
    private String tableId;
    /** 模板方案ID */
    private String templateSchemeId;
    /** 选中的模板及其目标配置 */
    private List<TemplateTarget> selectedTemplates;

    /**
     * 单个模板的目标配置
     */
    public static class TemplateTarget implements Serializable {
        private static final long serialVersionUID = 1L;

        private String templateId;
        private String targetPath;
        private String targetPackage;

        public String getTemplateId() {
            return templateId;
        }

        public void setTemplateId(String templateId) {
            this.templateId = templateId;
        }

        public String getTargetPath() {
            return targetPath;
        }

        public void setTargetPath(String targetPath) {
            this.targetPath = targetPath;
        }

        public String getTargetPackage() {
            return targetPackage;
        }

        public void setTargetPackage(String targetPackage) {
            this.targetPackage = targetPackage;
        }
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

    public List<TemplateTarget> getSelectedTemplates() {
        return selectedTemplates;
    }

    public void setSelectedTemplates(List<TemplateTarget> selectedTemplates) {
        this.selectedTemplates = selectedTemplates;
    }
}
