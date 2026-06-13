package com.company.generator.manager.entity;

import com.baomidou.mybatisplus.annotations.TableField;
import com.baomidou.mybatisplus.annotations.TableId;
import com.baomidou.mybatisplus.annotations.TableName;
import com.baomidou.mybatisplus.enums.IdType;
import com.company.manerger.sys.common.base.mvc.entity.AbstractEntity;

import java.util.Date;

@TableName("generator_generation_log")
public class GenerationLog extends AbstractEntity<String> implements java.io.Serializable {

	@TableId(value = "id", type = IdType.UUID)
	private String id;

	@TableField("table_id")
	private String tableId;

	@TableField("scheme_id")
	private String schemeId;

	@TableField("template_scheme_id")
	private String templateSchemeId;

	@TableField("entity_name")
	private String entityName;

	@TableField("operation_type")
	private String operationType;

	@TableField("dry_run_id")
	private String dryRunId;

	@TableField("file_count")
	private Integer fileCount;

	@TableField("new_count")
	private Integer newCount;

	@TableField("overwrite_count")
	private Integer overwriteCount;

	@TableField("skip_count")
	private Integer skipCount;

	@TableField("risk_count")
	private Integer riskCount;

	@TableField("result_json")
	private String resultJson;

	@TableField("create_date")
	private Date createDate;

	public GenerationLog() {
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getTableId() {
		return tableId;
	}

	public void setTableId(String tableId) {
		this.tableId = tableId;
	}

	public String getSchemeId() {
		return schemeId;
	}

	public void setSchemeId(String schemeId) {
		this.schemeId = schemeId;
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

	public String getOperationType() {
		return operationType;
	}

	public void setOperationType(String operationType) {
		this.operationType = operationType;
	}

	public String getDryRunId() {
		return dryRunId;
	}

	public void setDryRunId(String dryRunId) {
		this.dryRunId = dryRunId;
	}

	public Integer getFileCount() {
		return fileCount;
	}

	public void setFileCount(Integer fileCount) {
		this.fileCount = fileCount;
	}

	public Integer getNewCount() {
		return newCount;
	}

	public void setNewCount(Integer newCount) {
		this.newCount = newCount;
	}

	public Integer getOverwriteCount() {
		return overwriteCount;
	}

	public void setOverwriteCount(Integer overwriteCount) {
		this.overwriteCount = overwriteCount;
	}

	public Integer getSkipCount() {
		return skipCount;
	}

	public void setSkipCount(Integer skipCount) {
		this.skipCount = skipCount;
	}

	public Integer getRiskCount() {
		return riskCount;
	}

	public void setRiskCount(Integer riskCount) {
		this.riskCount = riskCount;
	}

	public String getResultJson() {
		return resultJson;
	}

	public void setResultJson(String resultJson) {
		this.resultJson = resultJson;
	}

	public Date getCreateDate() {
		return createDate;
	}

	public void setCreateDate(Date createDate) {
		this.createDate = createDate;
	}
}
