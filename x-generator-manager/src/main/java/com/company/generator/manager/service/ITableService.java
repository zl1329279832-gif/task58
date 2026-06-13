package com.company.generator.manager.service;

import com.company.generator.manager.common.data.DbTableInfo;
import com.company.generator.manager.common.exception.GenerationException;
import com.company.generator.manager.entity.DryRunRequest;
import com.company.generator.manager.entity.DryRunResult;
import com.company.generator.manager.entity.Scheme;
import com.company.generator.manager.entity.Table;
import com.company.generator.manager.entity.Template;
import com.company.manerger.sys.common.mybatis.service.ICommonService;
import freemarker.template.TemplateException;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;

public interface ITableService extends ICommonService<Table> {

    /**
     * 获得表列表
     *
     * @return
     */
    public List<DbTableInfo> getTableNameList(String soureid);

    /**
     * 代码生成
     *
     * @title: doGenerateCode
     * @description:代码生成
     * @return: void
     */
    public void generateCode(Scheme scheme, List<Template> templates, List<Template> allTemplates) throws IOException, GenerationException;

    /**
     * 代码生成
     *
     * @title: doGenerateCode
     * @description:代码生成
     * @return: void
     */
    public void importDatabase(Table table);

    public void dropTable(String tableid);

    /**
     * 数据库生成
     *
     * @title: syncDatabase
     * @description:数据库生成
     * @return: void
     */
    public void syncDatabase(String tableid) throws TemplateException, IOException;

    public void removeById(Serializable id);

    public List<Table> findSubTable(String tablename);

    /**
     * 生成方案预检（dry-run），不写入文件，返回预览结果
     */
    DryRunResult dryRun(DryRunRequest request) throws IOException, GenerationException;

    /**
     * 确认生成：根据预检结果写入文件
     */
    void generateConfirmed(String dryRunId) throws IOException, GenerationException;
}
