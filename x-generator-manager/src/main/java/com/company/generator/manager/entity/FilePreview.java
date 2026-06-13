package com.company.generator.manager.entity;

/**
 * 单文件预检结果
 */
public class FilePreview implements java.io.Serializable {

	/** 文件状态枚举 */
	public enum FileStatus {
		/** 新文件，目标路径不存在 */
		NEW,
		/** 覆盖，目标文件存在且内容不同 */
		OVERWRITE,
		/** 跳过，目标文件存在且内容完全一致 */
		SKIP,
		/** 风险，目标文件存在且可能被用户手动修改过 */
		RISK
	}

	/** 模板名称 */
	private String templateName;
	/** 模板ID */
	private String templateId;
	/** 目标文件绝对路径 */
	private String filePath;
	/** 文件状态 */
	private FileStatus status;
	/** 生成的新内容 */
	private String newContent;
	/** 已有的文件内容（新文件时为null） */
	private String existingContent;
	/** 差异摘要 */
	private String diffSummary;

	public FilePreview() {
	}

	public String getTemplateName() {
		return templateName;
	}

	public void setTemplateName(String templateName) {
		this.templateName = templateName;
	}

	public String getTemplateId() {
		return templateId;
	}

	public void setTemplateId(String templateId) {
		this.templateId = templateId;
	}

	public String getFilePath() {
		return filePath;
	}

	public void setFilePath(String filePath) {
		this.filePath = filePath;
	}

	public FileStatus getStatus() {
		return status;
	}

	public void setStatus(FileStatus status) {
		this.status = status;
	}

	public String getNewContent() {
		return newContent;
	}

	public void setNewContent(String newContent) {
		this.newContent = newContent;
	}

	public String getExistingContent() {
		return existingContent;
	}

	public void setExistingContent(String existingContent) {
		this.existingContent = existingContent;
	}

	public String getDiffSummary() {
		return diffSummary;
	}

	public void setDiffSummary(String diffSummary) {
		this.diffSummary = diffSummary;
	}
}
