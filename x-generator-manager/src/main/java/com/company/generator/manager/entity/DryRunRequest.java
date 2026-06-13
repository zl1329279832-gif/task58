package com.company.generator.manager.entity;

import java.util.List;
import java.util.Map;

/**
 * 生成方案预检请求 DTO
 */
public class DryRunRequest implements java.io.Serializable {

	/** 已有方案ID，新增时为null */
	private String schemeId;
	/** 表ID */
	private String tableId;
	/** 模板方案ID */
	private String templateSchemeId;
	/** 实体名 */
	private String entityName;
	/** 模块名 */
	private String moduleName;
	/** 功能作者 */
	private String functionAuthor;
	/** 功能描述 */
	private String functionDesc;
	/** 功能名称 */
	private String functionName;
	/** 选中的模板ID列表 */
	private List<String> templateKeys;
	/** 每个模板的目标路径覆盖（key=templateId, value=targetPath） */
	private Map<String, String> templatePaths;
	/** 每个模板的目标包名覆盖（key=templateId, value=targetPackage） */
	private Map<String, String> templatePackages;

	public DryRunRequest() {
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

	public String getEntityName() {
		return entityName;
	}

	public void setEntityName(String entityName) {
		this.entityName = entityName;
	}

	public String getModuleName() {
		return moduleName;
	}

	public void setModuleName(String moduleName) {
		this.moduleName = moduleName;
	}

	public String getFunctionAuthor() {
		return functionAuthor;
	}

	public void setFunctionAuthor(String functionAuthor) {
		this.functionAuthor = functionAuthor;
	}

	public String getFunctionDesc() {
		return functionDesc;
	}

	public void setFunctionDesc(String functionDesc) {
		this.functionDesc = functionDesc;
	}

	public String getFunctionName() {
		return functionName;
	}

	public void setFunctionName(String functionName) {
		this.functionName = functionName;
	}

	public List<String> getTemplateKeys() {
		return templateKeys;
	}

	public void setTemplateKeys(List<String> templateKeys) {
		this.templateKeys = templateKeys;
	}

	public Map<String, String> getTemplatePaths() {
		return templatePaths;
	}

	public void setTemplatePaths(Map<String, String> templatePaths) {
		this.templatePaths = templatePaths;
	}

	public Map<String, String> getTemplatePackages() {
		return templatePackages;
	}

	public void setTemplatePackages(Map<String, String> templatePackages) {
		this.templatePackages = templatePackages;
	}
}
