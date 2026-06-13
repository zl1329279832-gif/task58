package com.company.generator.manager.service.impl;

import com.company.generator.manager.common.data.DbColumnInfo;
import com.company.generator.manager.common.data.DbTableInfo;
import com.company.generator.manager.common.definition.type.DbTypeConvert;
import com.company.generator.manager.common.definition.type.ITypeConvert;
import com.company.generator.manager.common.exception.GenerationException;
import com.company.generator.manager.entity.*;
import com.company.generator.manager.mapper.TableMapper;
import com.company.generator.manager.service.IColumnService;
import com.company.generator.manager.service.IDataSourceService;
import com.company.generator.manager.service.IGenerationLogService;
import com.company.generator.manager.service.ISchemeService;
import com.company.generator.manager.service.ITableService;
import com.company.generator.manager.service.ITemplateService;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.company.manerger.sys.common.mybatis.service.impl.CommonServiceImpl;
import com.company.manerger.sys.common.mybatis.wrapper.EntityWrapper;
import com.company.manerger.sys.common.utils.CacheUtils;
import com.company.manerger.sys.common.utils.DateUtils;
import com.company.manerger.sys.common.utils.ServletUtils;
import com.company.manerger.sys.common.utils.StringUtils;
import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Transactional
@Service("tableService")
public class TableServiceImpl extends CommonServiceImpl<TableMapper, Table> implements ITableService {
	@Autowired
	private IColumnService columnService;
	@Autowired
	private IDataSourceService dataSourceService;
	@Autowired
	private ITemplateService templateService;
	@Autowired
	private IGenerationLogService generationLogService;
	@Autowired
	private ISchemeService schemeService;
	@Override
	public List<DbTableInfo> getTableNameList(String soureid) {
		return dataSourceService.getDbHelper(soureid).getDbTables();
	}

	@Override
	public boolean insert(Table table) {
		table.setSyncDatabase(Boolean.FALSE);
		// 保存主表
		super.insert(table);
		// 字段
		String columnListStr = StringEscapeUtils.unescapeHtml4(ServletUtils.getRequest().getParameter("columnList"));
		List<Column> columnList = JSONObject.parseArray(columnListStr, Column.class);
		for (int i = 0; i < columnList.size(); i++) {
			// 保存字段列表
			Column column = columnList.get(i);
			column.setId("");
			column.setSort(i);
			column.setTable(table);
			columnService.insert(column);
		}
		return true;
	}

	@Override
	public boolean insertOrUpdate(Table table) {
		// 删除已经删除的数据
		List<Column> oldColumnList = columnService.selectListByTableId(table.getId());
		// 字段
		String columnListStr = StringEscapeUtils.unescapeHtml4(ServletUtils.getRequest().getParameter("columnList"));
		List<Column> columnList = JSONObject.parseArray(columnListStr, Column.class);

		// 更新主表
		super.insertOrUpdate(table);
		columnList = JSONObject.parseArray(columnListStr, Column.class);
		List<String> newsIdList = new ArrayList<String>();
		int sort = 1;
		// 保存或更新数据
		for (Column column : columnList) {
			column.setSort(sort);
			// 保存字段列表
			if (StringUtils.isEmpty(column.getId()) || column.getId().contains("templateid")) {
				// 保存字段列表
				column.setId("");
				column.setTable(table);
				columnService.insert(column);
			} else {
				// 设置不变更的字段
				column.setTable(table);
				columnService.insertOrUpdate(column);
			}
			sort++;
			newsIdList.add(column.getId());
		}

		// 删除老数据
		for (Column column : oldColumnList) {
			String columnId = column.getId();
			if (!newsIdList.contains(columnId)) {
				columnService.deleteById(columnId);
			}
		}
		return true;
	}

	@Override
	public boolean deleteBatchIds(Collection<? extends Serializable> idList) {
		for (Serializable id : idList) {
			deleteById(id);
		}
		return true;
	}

	@Override
	public boolean deleteById(Serializable id) {
		// 删除已经删除的数据
		Table table = selectById(id);
		// 先刪除表
		try {
			dataSourceService.getDbHelper(table.getSourceId()).dropTable(table.getTableName());
		} catch (Exception e) {
			e.printStackTrace();
			// 部分数据库在没有表而执行删表语句时会报错
			// logger.error(e.getMessage());
		}
		removeById(id);
		return true;
	}

	@Override
	public void removeById(Serializable id) {
		// 删除已经删除的数据
		List<Column> columnList = columnService.selectListByTableId((String) id);
		// 保存或更新数据
		for (Column column : columnList) {
			columnService.deleteById(column.getId());
		}
		super.deleteById(id);
	}

	@Override
	public void generateCode(Scheme scheme, List<Template> templates,List<Template> allTemplates) throws IOException, GenerationException {
		//生成代码
		for (Template template:templates) {
			generateCode(scheme,template,allTemplates);
		}
	}

	@Override
	public void importDatabase(Table table) {
		String tableName = table.getTableName();
		String title=tableName;
		if (tableName.contains(":")){
			String[] tableInfos=tableName.split(":");
			tableName=tableInfos[0];
			title=tableInfos[1];
		}
		table.setTitle(title);
		table.setRemarks(title);
		table.setTableName(tableName);
		table.setSyncDatabase(Boolean.TRUE);
		table.setTest(Boolean.FALSE);
		// 保存主表
		super.insert(table);
		DataSource dataSource=dataSourceService.selectById(table.getSourceId());
		List<DbColumnInfo> dbColumnInfos = dataSourceService.getDbHelper(table.getSourceId()).getDbColumnInfo(tableName);
		for (int j = 0; j < dbColumnInfos.size(); j++) {
			Column column = new Column(dbColumnInfos.get(j),dataSource.getDbType());
			column.setSort(j + 1);
			// 保存字段列表
			column.setTable(table);
			columnService.insert(column);
		}

	}

	public void dropTable(String tableid) {
		Table table = selectById(tableid);
		try {
			dataSourceService.getDbHelper(table.getSourceId()).dropTable(table.getTableName());
		} catch (Exception e) {
			// 部分数据库在没有表而执行删表语句时会报错
			// logger.error(e.getMessage());
		}
	}

	@Override
	public void syncDatabase(String tableid) throws TemplateException, IOException {
		Table table=selectById(tableid);
		DataSource dataSource=dataSourceService.selectById(table.getSourceId());
		List<Column> columns = columnService.selectListByTableId(table.getId());
		for (Column column:columns) {
			column.setDbType(dataSource.getDbType());
		}
		table.setColumns(columns);
		Map<String, Object> tableInfo = new HashMap<String, Object>();
		tableInfo.put("table", table);
		tableInfo.put("dbType", dataSource.getDbType());
		dataSourceService.getDbHelper(table.getSourceId()).createTable(tableInfo);
		table.setSyncDatabase(Boolean.TRUE);
		super.insertOrUpdate(table);
	}

	@Override
	public List<Table> findSubTable(String tablename) {
		return baseMapper.findSubTables(tablename);
	}

	public void generateCode(Scheme scheme, Template template,List<Template> allTemplates) throws IOException, GenerationException {
		 try {
			 Map<String, Object> ftlMap=getFtlMap(scheme,template,allTemplates);
			 //获取内容
			 String content=parseTemplate(ftlMap,template.getTemplateContent());
			 //获取路径
             File outFile=getOutPath(scheme, template);
			 //保存文件
			 FileUtils.write(outFile,content,"UTF-8");
		 } catch (TemplateException e) {
			 throw  new GenerationException("“"+template.getName()+"”"+e.getFTLInstructionStack());
		 }
	}


	public Map<String, Object> getFtlMap(Scheme scheme, Template template, List<Template> allTemplates) {
		Map<String, Object> dataMap = new HashMap<String, Object>();
		//文件导入的以后再处理
		dataMap =JSON.parseObject(JSON.toJSON(scheme).toString(),Map.class);
		String packageName = parsePackageName(template.getTargetPackage(),scheme.getModuleName());
		dataMap.put("targetPackage",packageName);
		//获取table
		List<Column> columns=columnService.selectListByTableId(scheme.getTable().getId());
		dataMap.put("columns",columns);
		//引入其他模型的一些公用参数
		for (Template templateItem:allTemplates) {
			//包名字中加入模板
			String templateItemPackageName =  parsePackageName(templateItem.getTargetPackage(),scheme.getModuleName());
			templateItem.setTargetPackage(templateItemPackageName);
			dataMap.put(templateItem.getKey(),templateItem);
		}
		//设置生成的时间
		String time= DateUtils.formatDateTime(new Date());
		dataMap.put("time",time);
		//获得实体导入
	    /*List<String> importTypes = new ArrayList<String>();
		List<AttributeInfo> attributeInfos = generatorInfo.getAttributeInfos();
		Map<String, Boolean> tempImportMap = new HashMap<String, Boolean>();
		if (attributeInfos!=null) {
			for (AttributeInfo attributeInfo : attributeInfos) {
				String importType = attributeInfo.getImportType();
				if (!StringUtils.isEmpty(importType)&&!tempImportMap.containsKey(importType)) {
					importTypes.add(importType);
					tempImportMap.put(importType, true);
				}
			}
			generatorInfo.setImportTypes(importTypes);
		}*/
		return dataMap;
	}

	private String parsePackageName(String packageName,String moduleName){
		if (StringUtils.isEmpty(packageName)){
			return "";
		}
		if (!StringUtils.isEmpty(moduleName)){
			packageName =  packageName.replace("[moduleName]",moduleName);
		}else if(packageName.startsWith("[moduleName]")){
			packageName =  packageName.replace("[moduleName].", "");
		}else{
			packageName =  packageName.replace(".[moduleName]", "");
		}
		return packageName;
	}

	protected File getOutPath(Scheme scheme, Template template) {
		String outPath=template.getTargetPath();
		String packageNamePath = "";
		// 默认生成的包名
		String packageName = template.getTargetPackage();
		//包名字中加入模板
		packageName = parsePackageName(packageName,scheme.getModuleName());
		if (template.getEnablePackage().equals("1")){
			if (!"".endsWith(packageName)) {
//				outPath += File.separator + packageName;
				packageNamePath = packageName;
			}
		}
		/*// 当前模块名
		String moduleName = scheme.getModuleName();
		if (!"".endsWith(moduleName)) {
			outPath += File.separator + moduleName;
		}*/
		//
//		outPath = outPath.replace(".", File.separator).trim();
		packageNamePath = packageNamePath.replace(".", File.separator).trim();
		outPath += File.separator + packageNamePath;
		File outPathFile = new File(outPath);
		if (!outPathFile.exists()) {
			outPathFile.mkdirs();
		}
		//对文件进行格式化
		String fileName = template.getNameFormat().replace("[entityName]", scheme.getEntityName());
		if (!StringUtils.isEmpty(template.getNameUnderline())&&template.getNameUnderline().equals("1")) {
			fileName = StringUtils.camelToUnderline(fileName);
		}

		File outFile = new File(outPath + File.separator+ fileName);
		if (outFile.exists()) {
			outFile.delete();
		}
		return outFile;
	}

	/**
	 * 模版解析
	 * @param rootMap
	 * @param content
	 * @return
	 * @throws TemplateException
	 * @throws IOException
	 */
	private String parseTemplate(Map<String, Object> rootMap, String content) throws TemplateException, IOException {
		content=StringEscapeUtils.unescapeHtml4(content);
		String tempname = StringUtils.hashKeyForDisk(content);
		Configuration configuration = new Configuration();
		configuration.setNumberFormat("#");
		StringTemplateLoader stringLoader = new StringTemplateLoader();
		stringLoader.putTemplate(tempname, content);
		freemarker.template.Template template = new freemarker.template.Template(tempname, new StringReader(content),configuration);
		StringWriter stringWriter = new StringWriter();
		template.process(rootMap, stringWriter);
		configuration.setTemplateLoader(stringLoader);
		content = stringWriter.toString();
		return content;
	}

	// ==================== 预检与确认生成 ====================

	/** FreeMarker 变量引用正则：${xxx} 或 ${xxx.yyy} */
	private static final Pattern FTL_VAR_PATTERN = Pattern.compile("\\$\\{([a-zA-Z_][a-zA-Z0-9_]*)");

	@Override
	public DryRunResult dryRun(DryRunRequest request) throws IOException, GenerationException {
		// 1. 加载基础数据
		Table table = selectById(request.getTableId());
		if (table == null) {
			throw new GenerationException("表不存在: " + request.getTableId());
		}
		DataSource dataSource = dataSourceService.selectById(table.getSourceId());
		List<Column> columns = columnService.selectListByTableId(table.getId());

		// 2. 检测缺失的字段类型映射
		List<String> missingFieldTypes = detectMissingFieldTypes(columns, dataSource.getDbType());

		// 3. 构建 Scheme（复用已有的或构建临时对象）
		Scheme scheme;
		if (!StringUtils.isEmpty(request.getSchemeId())) {
			scheme = schemeService.selectById(request.getSchemeId());
		} else {
			// 查找该表是否已有 scheme
			scheme = schemeService.selectOne(new EntityWrapper<Scheme>(Scheme.class).eq("table.id", table.getId()));
			if (scheme == null) {
				scheme = new Scheme();
			}
		}
		// 覆盖参数
		if (!StringUtils.isEmpty(request.getEntityName())) {
			scheme.setEntityName(request.getEntityName());
		}
		if (!StringUtils.isEmpty(request.getModuleName())) {
			scheme.setModuleName(request.getModuleName());
		}
		if (!StringUtils.isEmpty(request.getFunctionAuthor())) {
			scheme.setFunctionAuthor(request.getFunctionAuthor());
		}
		if (!StringUtils.isEmpty(request.getFunctionDesc())) {
			scheme.setFunctionDesc(request.getFunctionDesc());
		}
		if (!StringUtils.isEmpty(request.getFunctionName())) {
			scheme.setFunctionName(request.getFunctionName());
		}
		scheme.setTable(table);
		scheme.setTemplateSchemeId(request.getTemplateSchemeId());
		scheme.setTableName(table.getTableName());
		scheme.setTableType(table.getTableType());

		// 4. 加载选中的模板和全部模板
		List<Template> selectedTemplates = new ArrayList<>();
		for (String templateKey : request.getTemplateKeys()) {
			Template template = templateService.selectById(templateKey);
			if (template != null) {
				// 应用路径/包名覆盖
				if (request.getTemplatePaths() != null && request.getTemplatePaths().containsKey(templateKey)) {
					template.setTargetPath(request.getTemplatePaths().get(templateKey));
				}
				if (request.getTemplatePackages() != null && request.getTemplatePackages().containsKey(templateKey)) {
					template.setTargetPackage(request.getTemplatePackages().get(templateKey));
				}
				selectedTemplates.add(template);
			}
		}

		List<Template> allTemplates = templateService.selectList(
				new EntityWrapper<Template>(Template.class).eq("scheme_id", request.getTemplateSchemeId()));
		for (Template t : allTemplates) {
			if (request.getTemplatePaths() != null && request.getTemplatePaths().containsKey(t.getId())) {
				t.setTargetPath(request.getTemplatePaths().get(t.getId()));
			}
			if (request.getTemplatePackages() != null && request.getTemplatePackages().containsKey(t.getId())) {
				t.setTargetPackage(request.getTemplatePackages().get(t.getId()));
			}
		}

		// 5. 检测缺失的模板变量
		Set<String> allMissingVars = new LinkedHashSet<>();

		// 6. 对每个选中的模板进行预检
		List<FilePreview> filePreviews = new ArrayList<>();
		for (Template template : selectedTemplates) {
			FilePreview preview = new FilePreview();
			preview.setTemplateId(template.getId());
			preview.setTemplateName(template.getName());

			// 构建数据模型
			Map<String, Object> ftlMap = getFtlMap(scheme, template, allTemplates);

			// 检测模板变量缺失
			List<String> missingVars = detectMissingTemplateVars(template.getTemplateContent(), ftlMap);
			allMissingVars.addAll(missingVars);

			// 生成内容
			String newContent;
			try {
				newContent = parseTemplate(ftlMap, template.getTemplateContent());
			} catch (TemplateException e) {
				throw new GenerationException("模板"" + template.getName() + ""解析失败: " + e.getFTLInstructionStack());
			}
			preview.setNewContent(newContent);

			// 获取目标路径（不删除已有文件）
			File outFile = getOutPathPreview(scheme, template);
			preview.setFilePath(outFile.getAbsolutePath());

			// 判断文件状态
			if (outFile.exists()) {
				String existingContent = FileUtils.readFileToString(outFile, "UTF-8");
				preview.setExistingContent(existingContent);
				if (existingContent.equals(newContent)) {
					preview.setStatus(FilePreview.FileStatus.SKIP);
					preview.setDiffSummary("内容完全一致，无需变更");
				} else {
					preview.setStatus(FilePreview.FileStatus.RISK);
					preview.setDiffSummary(computeDiffSummary(existingContent, newContent));
				}
			} else {
				preview.setExistingContent(null);
				preview.setStatus(FilePreview.FileStatus.NEW);
				preview.setDiffSummary("新增文件");
			}

			filePreviews.add(preview);
		}

		// 7. 组装 DryRunResult
		String dryRunId = UUID.randomUUID().toString().replace("-", "");
		DryRunResult result = new DryRunResult();
		result.setDryRunId(dryRunId);
		result.setFiles(filePreviews);
		result.setMissingFieldTypes(missingFieldTypes);
		result.setMissingTemplateVars(new ArrayList<>(allMissingVars));
		result.setCreatedAt(new Date());

		// 统计
		int newCount = 0, overwriteCount = 0, skipCount = 0, riskCount = 0;
		for (FilePreview fp : filePreviews) {
			switch (fp.getStatus()) {
				case NEW: newCount++; break;
				case OVERWRITE: overwriteCount++; break;
				case SKIP: skipCount++; break;
				case RISK: riskCount++; break;
			}
		}
		result.setHasRisk(riskCount > 0 || overwriteCount > 0);
		result.setSummary(String.format("共 %d 个文件：新增 %d，覆盖 %d，跳过 %d，风险 %d；缺失类型映射 %d 项；缺失模板变量 %d 项",
				filePreviews.size(), newCount, overwriteCount, skipCount, riskCount,
				missingFieldTypes.size(), allMissingVars.size()));

		// 8. 缓存 DryRunResult
		CacheUtils.put("dryrun_" + dryRunId, result);

		// 9. 记录预检日志
		GenerationLog log = new GenerationLog();
		log.setTableId(table.getId());
		log.setSchemeId(scheme.getId());
		log.setTemplateSchemeId(request.getTemplateSchemeId());
		log.setEntityName(scheme.getEntityName());
		log.setOperationType("DRY_RUN");
		log.setDryRunId(dryRunId);
		log.setFileCount(filePreviews.size());
		log.setNewCount(newCount);
		log.setOverwriteCount(overwriteCount);
		log.setSkipCount(skipCount);
		log.setRiskCount(riskCount);
		log.setResultJson(JSON.toJSONString(result));
		log.setCreateDate(new Date());
		generationLogService.insert(log);

		return result;
	}

	@Override
	public void generateConfirmed(String dryRunId) throws IOException, GenerationException {
		// 1. 从缓存中加载 DryRunResult
		DryRunResult result = (DryRunResult) CacheUtils.get("dryrun_" + dryRunId);
		if (result == null) {
			throw new GenerationException("预检结果不存在或已过期，请重新执行预检: " + dryRunId);
		}

		// 2. 写入文件
		int newCount = 0, overwriteCount = 0, skipCount = 0, riskCount = 0;
		for (FilePreview preview : result.getFiles()) {
			if (preview.getStatus() == FilePreview.FileStatus.SKIP) {
				skipCount++;
				continue;
			}

			File outFile = new File(preview.getFilePath());
			// 确保目录存在
			File parentDir = outFile.getParentFile();
			if (parentDir != null && !parentDir.exists()) {
				parentDir.mkdirs();
			}
			// 如果文件已存在，先删除
			if (outFile.exists()) {
				outFile.delete();
			}
			FileUtils.write(outFile, preview.getNewContent(), "UTF-8");

			switch (preview.getStatus()) {
				case NEW: newCount++; break;
				case OVERWRITE: overwriteCount++; break;
				case RISK: riskCount++; break;
				default: break;
			}
		}

		// 3. 记录生成日志
		GenerationLog log = new GenerationLog();
		log.setOperationType("GENERATE");
		log.setDryRunId(dryRunId);
		log.setFileCount(result.getFiles().size());
		log.setNewCount(newCount);
		log.setOverwriteCount(overwriteCount);
		log.setSkipCount(skipCount);
		log.setRiskCount(riskCount);
		log.setResultJson(JSON.toJSONString(result));
		log.setCreateDate(new Date());
		generationLogService.insert(log);

		// 4. 清除缓存
		CacheUtils.remove("dryrun_" + dryRunId);
	}

	/**
	 * 获取输出路径（预览模式，不删除已有文件）
	 */
	protected File getOutPathPreview(Scheme scheme, Template template) {
		String outPath = template.getTargetPath();
		String packageNamePath = "";
		String packageName = template.getTargetPackage();
		packageName = parsePackageName(packageName, scheme.getModuleName());
		if (template.getEnablePackage().equals("1")) {
			if (!"".endsWith(packageName)) {
				packageNamePath = packageName;
			}
		}
		packageNamePath = packageNamePath.replace(".", File.separator).trim();
		outPath += File.separator + packageNamePath;
		File outPathFile = new File(outPath);
		if (!outPathFile.exists()) {
			outPathFile.mkdirs();
		}
		String fileName = template.getNameFormat().replace("[entityName]", scheme.getEntityName());
		if (!StringUtils.isEmpty(template.getNameUnderline()) && template.getNameUnderline().equals("1")) {
			fileName = StringUtils.camelToUnderline(fileName);
		}
		return new File(outPath + File.separator + fileName);
	}

	/**
	 * 检测缺失的字段类型映射
	 */
	private List<String> detectMissingFieldTypes(List<Column> columns, String dbType) {
		Set<String> missing = new LinkedHashSet<>();
		ITypeConvert typeConvert = DbTypeConvert.getTypeConvert(DbTypeConvert.TYPE_DB_TO_JAVA, dbType);
		for (Column column : columns) {
			String typeName = column.getTypeName();
			if (!StringUtils.isEmpty(typeName)) {
				com.company.generator.manager.common.definition.data.Type type = typeConvert.getType(typeName.toUpperCase());
				if (type == null) {
					type = typeConvert.getType(typeName.toLowerCase());
				}
				if (type == null) {
					missing.add(typeName);
				}
			}
		}
		return new ArrayList<>(missing);
	}

	/**
	 * 检测模板中引用但数据模型中不存在的变量
	 */
	private List<String> detectMissingTemplateVars(String templateContent, Map<String, Object> dataMap) {
		if (StringUtils.isEmpty(templateContent)) {
			return Collections.emptyList();
		}
		String unescaped = StringEscapeUtils.unescapeHtml4(templateContent);
		Set<String> missing = new LinkedHashSet<>();
		Matcher matcher = FTL_VAR_PATTERN.matcher(unescaped);
		while (matcher.find()) {
			String varName = matcher.group(1);
			if (!dataMap.containsKey(varName)) {
				missing.add(varName);
			}
		}
		return new ArrayList<>(missing);
	}

	/**
	 * 计算差异摘要（基于行级别的增删统计）
	 */
	private String computeDiffSummary(String existingContent, String newContent) {
		String[] existingLines = existingContent.split("\n", -1);
		String[] newLines = newContent.split("\n", -1);

		Set<String> existingLineSet = new LinkedHashSet<>(Arrays.asList(existingLines));
		Set<String> newLineSet = new LinkedHashSet<>(Arrays.asList(newLines));

		int added = 0;
		int removed = 0;
		for (String line : newLines) {
			if (!existingLineSet.contains(line)) {
				added++;
			}
		}
		for (String line : existingLines) {
			if (!newLineSet.contains(line)) {
				removed++;
			}
		}

		return String.format("新增 %d 行，删除 %d 行（原文件 %d 行，新文件 %d 行）",
				added, removed, existingLines.length, newLines.length);
	}
}
