package com.company.generator.manager.entity;

import java.util.Date;
import java.util.List;

/**
 * 预检结果 DTO
 */
public class DryRunResult implements java.io.Serializable {

	/** 预检ID，用于确认生成时关联 */
	private String dryRunId;
	/** 每个模板文件的预览结果 */
	private List<FilePreview> files;
	/** 缺失的字段类型映射（DB类型在类型映射表中找不到对应的Java类型） */
	private List<String> missingFieldTypes;
	/** 模板中引用但数据模型中不存在的变量 */
	private List<String> missingTemplateVars;
	/** 人类可读的摘要 */
	private String summary;
	/** 是否存在风险（有OVERWRITE或RISK状态的文件） */
	private boolean hasRisk;
	/** 预检时间 */
	private Date createdAt;

	public DryRunResult() {
	}

	public String getDryRunId() {
		return dryRunId;
	}

	public void setDryRunId(String dryRunId) {
		this.dryRunId = dryRunId;
	}

	public List<FilePreview> getFiles() {
		return files;
	}

	public void setFiles(List<FilePreview> files) {
		this.files = files;
	}

	public List<String> getMissingFieldTypes() {
		return missingFieldTypes;
	}

	public void setMissingFieldTypes(List<String> missingFieldTypes) {
		this.missingFieldTypes = missingFieldTypes;
	}

	public List<String> getMissingTemplateVars() {
		return missingTemplateVars;
	}

	public void setMissingTemplateVars(List<String> missingTemplateVars) {
		this.missingTemplateVars = missingTemplateVars;
	}

	public String getSummary() {
		return summary;
	}

	public void setSummary(String summary) {
		this.summary = summary;
	}

	public boolean isHasRisk() {
		return hasRisk;
	}

	public void setHasRisk(boolean hasRisk) {
		this.hasRisk = hasRisk;
	}

	public Date getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Date createdAt) {
		this.createdAt = createdAt;
	}
}
