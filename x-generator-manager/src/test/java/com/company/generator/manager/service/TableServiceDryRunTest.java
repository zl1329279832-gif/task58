package com.company.generator.manager.service;

import com.company.generator.manager.common.data.DbColumnInfo;
import com.company.generator.manager.common.definition.DefinitionBuilder;
import com.company.generator.manager.common.definition.DefinitionUtils;
import com.company.generator.manager.common.definition.data.Db;
import com.company.generator.manager.common.definition.data.Definition;
import com.company.generator.manager.common.definition.data.Type;
import com.company.generator.manager.common.definition.type.DbTypeConvert;
import com.company.generator.manager.common.exception.GenerationException;
import com.company.generator.manager.entity.*;
import com.company.generator.manager.service.impl.TableServiceImpl;
import com.company.manerger.sys.common.mybatis.wrapper.EntityWrapper;
import com.company.manerger.sys.common.utils.CacheUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class TableServiceDryRunTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	@InjectMocks
	private TableServiceImpl tableService;

	@Mock
	private IColumnService columnService;
	@Mock
	private IDataSourceService dataSourceService;
	@Mock
	private ITemplateService templateService;
	@Mock
	private ISchemeService schemeService;
	@Mock
	private IGenerationLogService generationLogService;

	private static final String TEST_DB_TYPE = "mysql";
	private static final String TEST_TABLE_ID = "table-001";
	private static final String TEST_TEMPLATE_SCHEME_ID = "ts-001";
	private static final String TEST_TEMPLATE_ID = "tpl-001";

	@Before
	public void setUp() throws Exception {
		// 初始化 DefinitionBuilder 静态字段（模拟 Spring ContextRefreshedEvent）
		setupDefinitionBuilder();
		// 清除 DbTypeConvert 缓存，确保每次测试使用新数据
		clearDbTypeConvertCache();
	}

	/**
	 * 通过反射设置 DefinitionBuilder 的静态实例，模拟数据库类型定义
	 */
	private void setupDefinitionBuilder() throws Exception {
		Definition definition = new Definition();
		definition.setDbType(TEST_DB_TYPE);
		definition.setName("MySQL");

		// 设置 DB 基础类型信息
		Db db = new Db();
		db.setAllTypes("VARCHAR,INT,BIGINT,DATETIME,TEXT,DECIMAL,UNKNOWN_DB_TYPE");
		db.setCharTypes("VARCHAR,TEXT");
		db.setFloatTypes("DECIMAL");
		db.setAloneTypes("INT,BIGINT,DATETIME");
		db.setBlobTypes("");
		definition.setDb(db);

		// 设置 DB -> Java 类型映射
		List<Type> dbToJavaTypes = new ArrayList<>();
		addType(dbToJavaTypes, "VARCHAR", "String", "java.lang.String");
		addType(dbToJavaTypes, "INT", "Integer", "java.lang.Integer");
		addType(dbToJavaTypes, "BIGINT", "Long", "java.lang.Long");
		addType(dbToJavaTypes, "DATETIME", "Date|java.util.Date", "java.util.Date");
		addType(dbToJavaTypes, "TEXT", "String", "java.lang.String");
		addType(dbToJavaTypes, "DECIMAL", "Double", "java.lang.Double");
		definition.setDbtojavaTypes(dbToJavaTypes);
		definition.setJavatoclassTypes(new ArrayList<>());

		// 构建 DefinitionBuilder 并注入
		DefinitionBuilder builder = new DefinitionBuilder();
		Field defMapField = DefinitionBuilder.class.getDeclaredField("definitionMap");
		defMapField.setAccessible(true);
		Map<String, Definition> defMap = new HashMap<>();
		defMap.put(TEST_DB_TYPE, definition);
		defMapField.set(builder, defMap);

		Field defListField = DefinitionBuilder.class.getDeclaredField("definitionList");
		defListField.setAccessible(true);
		List<Definition> defList = new ArrayList<>();
		defList.add(definition);
		defListField.set(builder, defList);

		// 设置静态 definitionBuilder 字段
		Field staticField = DefinitionBuilder.class.getDeclaredField("definitionBuilder");
		staticField.setAccessible(true);
		staticField.set(null, builder);

		// 重置 DefinitionUtils 单例，让它使用新的 DefinitionBuilder
		Field duField = DefinitionUtils.class.getDeclaredField("definitionUtils");
		duField.setAccessible(true);
		duField.set(null, null);
	}

	private void addType(List<Type> types, String dbType, String javaType, String fullType) {
		Type t = new Type();
		t.setDbType(dbType);
		t.setJavaType(javaType);
		t.setFullType(fullType);
		types.add(t);
	}

	private void clearDbTypeConvertCache() throws Exception {
		Field cacheField = DbTypeConvert.class.getDeclaredField("dbTypeConvertMap");
		cacheField.setAccessible(true);
		((Map<?, ?>) cacheField.get(null)).clear();
	}

	// ==================== 辅助方法 ====================

	private DryRunRequest buildDryRunRequest() {
		DryRunRequest request = new DryRunRequest();
		request.setTableId(TEST_TABLE_ID);
		request.setTemplateSchemeId(TEST_TEMPLATE_SCHEME_ID);
		request.setEntityName("TestEntity");
		request.setModuleName("test-module");
		request.setFunctionAuthor("tester");
		request.setFunctionDesc("测试功能");
		request.setFunctionName("测试");
		request.setTemplateKeys(Arrays.asList(TEST_TEMPLATE_ID));
		request.setTemplatePaths(new HashMap<>());
		request.setTemplatePackages(new HashMap<>());
		return request;
	}

	private Table buildTable() {
		Table table = new Table();
		table.setId(TEST_TABLE_ID);
		table.setTableName("t_test");
		table.setTitle("测试表");
		table.setClassName("TestEntity");
		table.setSourceId("ds-001");
		table.setSyncDatabase(Boolean.TRUE);
		table.setTest(Boolean.FALSE);
		table.setTableType("2");
		table.setRemarks("测试表备注");
		return table;
	}

	private DataSource buildDataSource() {
		DataSource ds = new DataSource();
		ds.setId("ds-001");
		ds.setDbType(TEST_DB_TYPE);
		ds.setDbKey("test");
		ds.setUrl("jdbc:mysql://localhost:3306/test");
		ds.setDbUser("root");
		ds.setDbPassword("123456");
		ds.setDriverClass("com.mysql.jdbc.Driver");
		ds.setDbName("test");
		return ds;
	}

	private Column buildColumn(String columnName, String typeName, String javaType) {
		Column column = new Column();
		column.setId("col-" + columnName);
		column.setColumnName(columnName);
		column.setTypeName(typeName);
		column.setJavaType(javaType);
		column.setJavaField(columnName);
		column.setListable(Boolean.TRUE);
		column.setFormable(Boolean.TRUE);
		return column;
	}

	private Template buildTemplate(String targetPath) {
		Template template = new Template();
		template.setId(TEST_TEMPLATE_ID);
		template.setName("Entity模板");
		template.setKey("entity");
		template.setSchemeId(TEST_TEMPLATE_SCHEME_ID);
		template.setNameFormat("[entityName].java");
		template.setNameUnderline("0");
		template.setEnablePackage("1");
		template.setTargetPackage("com.test.entity");
		template.setTargetPath(targetPath);
		template.setSort(1);
		// 简单的 FreeMarker 模板
		template.setTemplateContent(
				"package ${targetPackage};\n\n" +
				"public class ${entityName} {\n" +
				"<#list columns as column>\n" +
				"    private ${column.javaType} ${column.javaField};\n" +
				"</#list>\n" +
				"}\n"
		);
		return template;
	}

	/**
	 * 使用 Mockito.lenient() 设置所有 mock 的基础行为
	 */
	private void setupCommonMocks(Table table, DataSource ds, List<Column> columns,
	                               Template template, List<Template> allTemplates) {
		// 模拟 selectById（来自 CommonServiceImpl / MyBatis-Plus）
		// TableServiceImpl.selectById 调用 baseMapper.selectById，我们使用 spy 无法直接 mock
		// 但由于 @InjectMocks 创建的实例，selectById 会调用真实的 baseMapper（null）
		// 因此我们使用 spy + doReturn 模式
		doReturn(table).when(tableService).selectById(TEST_TABLE_ID);

		when(dataSourceService.selectById(ds.getId())).thenReturn(ds);
		when(columnService.selectListByTableId(table.getId())).thenReturn(columns);
		when(templateService.selectById(template.getId())).thenReturn(template);
		when(templateService.selectList(any(EntityWrapper.class))).thenReturn(allTemplates);

		// Scheme 模拟：返回 null，让代码创建临时 Scheme
		when(schemeService.selectOne(any(EntityWrapper.class))).thenReturn(null);
		when(schemeService.selectById(anyString())).thenReturn(null);

		// GenerationLog 模拟
		when(generationLogService.insert(any(GenerationLog.class))).thenReturn(true);
	}

	// ==================== 测试用例 ====================

	/**
	 * 测试：字段类型缺失检测 —— Column 的 typeName 在类型映射表中找不到
	 */
	@Test
	public void testDryRun_MissingFieldType() throws Exception {
		Table table = buildTable();
		DataSource ds = buildDataSource();
		List<Column> columns = new ArrayList<>();
		// 一个正常映射的字段
		columns.add(buildColumn("name", "VARCHAR", "String"));
		// 一个无法映射的字段（UNKNOWN_DB_TYPE 不在类型映射中）
		columns.add(buildColumn("weird_col", "UNKNOWN_DB_TYPE", "String"));

		Template template = buildTemplate(tempFolder.getRoot().getAbsolutePath());
		List<Template> allTemplates = Arrays.asList(template);

		TableServiceImpl spyService = spy(tableService);
		// 重新注入 mock（因为 spy 创建了新的代理）
		injectField(spyService, "columnService", columnService);
		injectField(spyService, "dataSourceService", dataSourceService);
		injectField(spyService, "templateService", templateService);
		injectField(spyService, "schemeService", schemeService);
		injectField(spyService, "generationLogService", generationLogService);

		doReturn(table).when(spyService).selectById(TEST_TABLE_ID);
		when(dataSourceService.selectById(ds.getId())).thenReturn(ds);
		when(columnService.selectListByTableId(table.getId())).thenReturn(columns);
		when(templateService.selectById(template.getId())).thenReturn(template);
		when(templateService.selectList(any(EntityWrapper.class))).thenReturn(allTemplates);
		when(schemeService.selectOne(any(EntityWrapper.class))).thenReturn(null);
		when(generationLogService.insert(any(GenerationLog.class))).thenReturn(true);

		DryRunRequest request = buildDryRunRequest();
		DryRunResult result = spyService.dryRun(request);

		assertNotNull("预检结果不应为空", result);
		assertNotNull("missingFieldTypes 不应为空", result.getMissingFieldTypes());
		assertTrue("应检测到缺失的类型 UNKNOWN_DB_TYPE",
				result.getMissingFieldTypes().contains("UNKNOWN_DB_TYPE"));
		assertFalse("VARCHAR 不应出现在缺失列表中",
				result.getMissingFieldTypes().contains("VARCHAR"));
	}

	/**
	 * 测试：模板变量缺失检测 —— 模板引用了 ${nonExistent} 但数据模型中没有
	 */
	@Test
	public void testDryRun_MissingTemplateVariable() throws Exception {
		Table table = buildTable();
		DataSource ds = buildDataSource();
		List<Column> columns = Arrays.asList(buildColumn("name", "VARCHAR", "String"));

		// 模板中引用了 ${nonExistentVar} 这个不存在的变量
		Template template = buildTemplate(tempFolder.getRoot().getAbsolutePath());
		template.setTemplateContent(
				"package ${targetPackage};\n" +
				"// Author: ${functionAuthor}\n" +
				"// Missing: ${nonExistentVar}\n" +
				"public class ${entityName} {}\n"
		);
		List<Template> allTemplates = Arrays.asList(template);

		TableServiceImpl spyService = spy(tableService);
		injectField(spyService, "columnService", columnService);
		injectField(spyService, "dataSourceService", dataSourceService);
		injectField(spyService, "templateService", templateService);
		injectField(spyService, "schemeService", schemeService);
		injectField(spyService, "generationLogService", generationLogService);

		doReturn(table).when(spyService).selectById(TEST_TABLE_ID);
		when(dataSourceService.selectById(ds.getId())).thenReturn(ds);
		when(columnService.selectListByTableId(table.getId())).thenReturn(columns);
		when(templateService.selectById(template.getId())).thenReturn(template);
		when(templateService.selectList(any(EntityWrapper.class))).thenReturn(allTemplates);
		when(schemeService.selectOne(any(EntityWrapper.class))).thenReturn(null);
		when(generationLogService.insert(any(GenerationLog.class))).thenReturn(true);

		DryRunRequest request = buildDryRunRequest();
		DryRunResult result = spyService.dryRun(request);

		assertNotNull(result);
		assertNotNull(result.getMissingTemplateVars());
		assertTrue("应检测到缺失变量 nonExistentVar",
				result.getMissingTemplateVars().contains("nonExistentVar"));
	}

	/**
	 * 测试：目标文件已存在且内容不同 → 状态为 RISK
	 */
	@Test
	public void testDryRun_TargetFileAlreadyExists() throws Exception {
		Table table = buildTable();
		DataSource ds = buildDataSource();
		List<Column> columns = Arrays.asList(buildColumn("name", "VARCHAR", "String"));

		String targetDir = tempFolder.getRoot().getAbsolutePath();
		Template template = buildTemplate(targetDir);
		List<Template> allTemplates = Arrays.asList(template);

		// 预创建目标文件（模拟用户手动修改过的文件）
		// 目标路径: targetDir + /com/test/entity/TestEntity.java（enablePackage=1, targetPackage=com.test.entity）
		File packageDir = new File(targetDir, "com" + File.separator + "test" + File.separator + "entity");
		packageDir.mkdirs();
		File existingFile = new File(packageDir, "TestEntity.java");
		org.apache.commons.io.FileUtils.write(existingFile,
				"// 用户手动修改过的内容\npackage com.test.entity;\npublic class TestEntity { /* custom */ }\n",
				"UTF-8");

		TableServiceImpl spyService = spy(tableService);
		injectField(spyService, "columnService", columnService);
		injectField(spyService, "dataSourceService", dataSourceService);
		injectField(spyService, "templateService", templateService);
		injectField(spyService, "schemeService", schemeService);
		injectField(spyService, "generationLogService", generationLogService);

		doReturn(table).when(spyService).selectById(TEST_TABLE_ID);
		when(dataSourceService.selectById(ds.getId())).thenReturn(ds);
		when(columnService.selectListByTableId(table.getId())).thenReturn(columns);
		when(templateService.selectById(template.getId())).thenReturn(template);
		when(templateService.selectList(any(EntityWrapper.class))).thenReturn(allTemplates);
		when(schemeService.selectOne(any(EntityWrapper.class))).thenReturn(null);
		when(generationLogService.insert(any(GenerationLog.class))).thenReturn(true);

		DryRunRequest request = buildDryRunRequest();
		DryRunResult result = spyService.dryRun(request);

		assertNotNull(result);
		assertEquals(1, result.getFiles().size());
		FilePreview preview = result.getFiles().get(0);
		assertEquals("已存在且内容不同的文件应为 RISK 状态",
				FilePreview.FileStatus.RISK, preview.getStatus());
		assertNotNull("existingContent 不应为空", preview.getExistingContent());
		assertTrue("existingContent 应包含用户手动修改的内容",
				preview.getExistingContent().contains("用户手动修改过的内容"));
		assertTrue("hasRisk 应为 true", result.isHasRisk());
	}

	/**
	 * 测试：目标文件不存在 → 状态为 NEW
	 */
	@Test
	public void testDryRun_NewFile() throws Exception {
		Table table = buildTable();
		DataSource ds = buildDataSource();
		List<Column> columns = Arrays.asList(buildColumn("name", "VARCHAR", "String"));

		// 使用一个新的空目录，确保目标文件不存在
		File emptyDir = tempFolder.newFolder("empty_output");
		Template template = buildTemplate(emptyDir.getAbsolutePath());
		List<Template> allTemplates = Arrays.asList(template);

		TableServiceImpl spyService = spy(tableService);
		injectField(spyService, "columnService", columnService);
		injectField(spyService, "dataSourceService", dataSourceService);
		injectField(spyService, "templateService", templateService);
		injectField(spyService, "schemeService", schemeService);
		injectField(spyService, "generationLogService", generationLogService);

		doReturn(table).when(spyService).selectById(TEST_TABLE_ID);
		when(dataSourceService.selectById(ds.getId())).thenReturn(ds);
		when(columnService.selectListByTableId(table.getId())).thenReturn(columns);
		when(templateService.selectById(template.getId())).thenReturn(template);
		when(templateService.selectList(any(EntityWrapper.class))).thenReturn(allTemplates);
		when(schemeService.selectOne(any(EntityWrapper.class))).thenReturn(null);
		when(generationLogService.insert(any(GenerationLog.class))).thenReturn(true);

		DryRunRequest request = buildDryRunRequest();
		DryRunResult result = spyService.dryRun(request);

		assertNotNull(result);
		assertEquals(1, result.getFiles().size());
		FilePreview preview = result.getFiles().get(0);
		assertEquals("不存在的文件应为 NEW 状态",
				FilePreview.FileStatus.NEW, preview.getStatus());
		assertNull("NEW 文件的 existingContent 应为 null", preview.getExistingContent());
		assertNotNull("newContent 不应为空", preview.getNewContent());
	}

	/**
	 * 测试：dry-run 不写入任何文件到磁盘
	 */
	@Test
	public void testDryRun_NoFileWritten() throws Exception {
		Table table = buildTable();
		DataSource ds = buildDataSource();
		List<Column> columns = Arrays.asList(buildColumn("name", "VARCHAR", "String"));

		File outputDir = tempFolder.newFolder("no_write_check");
		Template template = buildTemplate(outputDir.getAbsolutePath());
		List<Template> allTemplates = Arrays.asList(template);

		TableServiceImpl spyService = spy(tableService);
		injectField(spyService, "columnService", columnService);
		injectField(spyService, "dataSourceService", dataSourceService);
		injectField(spyService, "templateService", templateService);
		injectField(spyService, "schemeService", schemeService);
		injectField(spyService, "generationLogService", generationLogService);

		doReturn(table).when(spyService).selectById(TEST_TABLE_ID);
		when(dataSourceService.selectById(ds.getId())).thenReturn(ds);
		when(columnService.selectListByTableId(table.getId())).thenReturn(columns);
		when(templateService.selectById(template.getId())).thenReturn(template);
		when(templateService.selectList(any(EntityWrapper.class))).thenReturn(allTemplates);
		when(schemeService.selectOne(any(EntityWrapper.class))).thenReturn(null);
		when(generationLogService.insert(any(GenerationLog.class))).thenReturn(true);

		DryRunRequest request = buildDryRunRequest();
		spyService.dryRun(request);

		// 验证目标文件未被创建
		File packageDir = new File(outputDir, "com" + File.separator + "test" + File.separator + "entity");
		File expectedFile = new File(packageDir, "TestEntity.java");
		assertFalse("dry-run 不应在磁盘上创建文件", expectedFile.exists());
	}

	/**
	 * 测试：确认生成 —— dry-run 后调用 generateConfirmed 写入文件
	 */
	@Test
	public void testGenerateConfirmed_WritesFiles() throws Exception {
		Table table = buildTable();
		DataSource ds = buildDataSource();
		List<Column> columns = Arrays.asList(buildColumn("name", "VARCHAR", "String"));

		File outputDir = tempFolder.newFolder("confirmed_write");
		Template template = buildTemplate(outputDir.getAbsolutePath());
		List<Template> allTemplates = Arrays.asList(template);

		TableServiceImpl spyService = spy(tableService);
		injectField(spyService, "columnService", columnService);
		injectField(spyService, "dataSourceService", dataSourceService);
		injectField(spyService, "templateService", templateService);
		injectField(spyService, "schemeService", schemeService);
		injectField(spyService, "generationLogService", generationLogService);

		doReturn(table).when(spyService).selectById(TEST_TABLE_ID);
		when(dataSourceService.selectById(ds.getId())).thenReturn(ds);
		when(columnService.selectListByTableId(table.getId())).thenReturn(columns);
		when(templateService.selectById(template.getId())).thenReturn(template);
		when(templateService.selectList(any(EntityWrapper.class))).thenReturn(allTemplates);
		when(schemeService.selectOne(any(EntityWrapper.class))).thenReturn(null);
		when(generationLogService.insert(any(GenerationLog.class))).thenReturn(true);

		// 先执行 dry-run
		DryRunRequest request = buildDryRunRequest();
		DryRunResult result = spyService.dryRun(request);
		String dryRunId = result.getDryRunId();

		// 确认生成
		spyService.generateConfirmed(dryRunId);

		// 验证文件已写入
		File packageDir = new File(outputDir, "com" + File.separator + "test" + File.separator + "entity");
		File generatedFile = new File(packageDir, "TestEntity.java");
		assertTrue("确认生成后文件应存在于磁盘", generatedFile.exists());
		String content = org.apache.commons.io.FileUtils.readFileToString(generatedFile, "UTF-8");
		assertTrue("生成的文件应包含实体类名", content.contains("TestEntity"));
		assertTrue("生成的文件应包含字段", content.contains("name"));
	}

	/**
	 * 测试：确认生成 —— 内容相同的文件（SKIP）不被覆盖
	 */
	@Test
	public void testGenerateConfirmed_SkipIdentical() throws Exception {
		Table table = buildTable();
		DataSource ds = buildDataSource();
		List<Column> columns = Arrays.asList(buildColumn("name", "VARCHAR", "String"));

		File outputDir = tempFolder.newFolder("skip_identical");
		Template template = buildTemplate(outputDir.getAbsolutePath());
		List<Template> allTemplates = Arrays.asList(template);

		TableServiceImpl spyService = spy(tableService);
		injectField(spyService, "columnService", columnService);
		injectField(spyService, "dataSourceService", dataSourceService);
		injectField(spyService, "templateService", templateService);
		injectField(spyService, "schemeService", schemeService);
		injectField(spyService, "generationLogService", generationLogService);

		doReturn(table).when(spyService).selectById(TEST_TABLE_ID);
		when(dataSourceService.selectById(ds.getId())).thenReturn(ds);
		when(columnService.selectListByTableId(table.getId())).thenReturn(columns);
		when(templateService.selectById(template.getId())).thenReturn(template);
		when(templateService.selectList(any(EntityWrapper.class))).thenReturn(allTemplates);
		when(schemeService.selectOne(any(EntityWrapper.class))).thenReturn(null);
		when(generationLogService.insert(any(GenerationLog.class))).thenReturn(true);

		// 先 dry-run 一次以获取生成的内容
		DryRunRequest request = buildDryRunRequest();
		DryRunResult firstResult = spyService.dryRun(request);

		// 将生成的内容预先写入目标文件（模拟内容完全一致的情况）
		FilePreview preview = firstResult.getFiles().get(0);
		File targetFile = new File(preview.getFilePath());
		targetFile.getParentFile().mkdirs();
		org.apache.commons.io.FileUtils.write(targetFile, preview.getNewContent(), "UTF-8");
		long originalLastModified = targetFile.lastModified();

		// 等待1秒确保时间戳不同
		Thread.sleep(1100);

		// 再次 dry-run（这次文件应该被标记为 SKIP）
		clearDbTypeConvertCache();
		DryRunResult secondResult = spyService.dryRun(request);
		FilePreview secondPreview = secondResult.getFiles().get(0);
		assertEquals("内容相同的文件应为 SKIP 状态",
				FilePreview.FileStatus.SKIP, secondPreview.getStatus());

		// 确认生成
		spyService.generateConfirmed(secondResult.getDryRunId());

		// 验证文件未被修改（lastModified 不变）
		assertEquals("SKIP 文件不应被重新写入", originalLastModified, targetFile.lastModified());
	}

	/**
	 * 测试：使用无效的 dryRunId 调用 generateConfirmed 应抛出异常
	 */
	@Test(expected = GenerationException.class)
	public void testGenerateConfirmed_InvalidDryRunId() throws Exception {
		TableServiceImpl spyService = spy(tableService);
		injectField(spyService, "generationLogService", generationLogService);

		spyService.generateConfirmed("non-existent-dry-run-id");
	}

	// ==================== 反射工具 ====================

	/**
	 * 通过反射向对象注入字段值
	 */
	private void injectField(Object target, String fieldName, Object value) throws Exception {
		Class<?> clazz = target.getClass();
		Field field = null;
		while (clazz != null) {
			try {
				field = clazz.getDeclaredField(fieldName);
				break;
			} catch (NoSuchFieldException e) {
				clazz = clazz.getSuperclass();
			}
		}
		if (field == null) {
			throw new NoSuchFieldException(fieldName);
		}
		field.setAccessible(true);
		field.set(target, value);
	}
}
