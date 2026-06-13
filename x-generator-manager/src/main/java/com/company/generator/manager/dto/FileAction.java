package com.company.generator.manager.dto;

/**
 * 文件预检操作类型
 */
public enum FileAction {
    /** 文件不存在，将新增 */
    ADD,
    /** 文件已存在且内容不同，将覆盖 */
    OVERWRITE,
    /** 文件已存在且内容相同，跳过 */
    SKIP,
    /** 存在风险（类型映射缺失、模板变量缺失等） */
    RISK
}
