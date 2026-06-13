package com.company.generator.manager.service;

import com.company.generator.manager.common.dao.DbHelper;
import com.company.generator.manager.common.data.DbColumnInfo;
import com.company.generator.manager.common.definition.DefinitionBuilder;
import com.company.generator.manager.common.definition.DefinitionUtils;
import com.company.generator.manager.common.definition.data.Db;
import com.company.generator.manager.common.definition.data.Definition;
import com.company.generator.manager.common.definition.data.Type;
import com.company.generator.manager.common.definition.type.DbTypeConvert;
import com.company.generator.manager.common.definition.type.ITypeConvert;
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
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 综合测试：MySQL 5.7/8 元数据、关键字字段、无主键表、decimal 精度、重复生成
 */
@RunWith(MockitoJUnitRunner.class)
public class MetadataAndCodeGenTest {

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

	private static final String MYSQL_DB_TYPE = "MySql";
	private static final String ORACLE_DB_TYPE = "Oracle";

	@Before
	public void setUp() throws Exception {
		setupDefinitionBuilder();
		clearDbTypeConvertCache();
	}

	// ==================== DefinitionBuilder 初始化 ====================

	private void setupDefinitionBuilder() throws Exception {
		// MySQL 定义
		Definition mysqlDef = new Definition();
		mysqlDef.setDbType(MYSQL_DB_TYPE);
		mysqlDef.setName("MySQL");

		Db mysqlDb = new Db();
		mysqlDb.setAllTypes("VARCHAR,TINYINT,SMALLINT,MEDIUMINT,INT,INTEGER,BIGINT,FLOAT,DOUBLE,DECIMAL,DATETIME,TIMESTAMP,TEXT,BIT,ENUM,SET,JSON,YEAR,BINARY,VARBINARY,CHAR,DATE,TIME");
		mysqlDb.setCharTypes("CHAR,VARCHAR,TINYTEXT,TEXT,MEDIUMTEXT,LONGTEXT,ENUM,SET,JSON");
		mysqlDb.setFloatTypes("FLOAT,DOUBLE,DECIMAL");
		mysqlDb.setAloneTypes("DATE,TIME,YEAR,DATETIME,TIMESTAMP,TINYBLOB,BLOB,TEXT,MEDIUMBLOB,MEDIUMTEXT,LONGBLOB,LONGTEXT");
		mysqlDb.setBlobTypes("TINYBLOB,BLOB,TEXT,MEDIUMBLOB,MEDIUMTEXT,LONGBLOB,LONGTEXT");
		mysqlDef.setDb(mysqlDb);

		List<Type> mysqlTypes = new ArrayList<>();
		addType(mysqlTypes, "VARCHAR", "String", "java.lang.String");
		addType(mysqlTypes, "INT", "Integer", "java.lang.Integer");
		addType(mysqlTypes, "INTEGER", "Integer", "java.lang.Integer");
		addType(mysqlTypes, "MEDIUMINT", "Integer", "java.lang.Integer");
		addType(mysqlTypes, "BIGINT", "Long", "java.lang.Long");
		addType(mysqlTypes, "BIGINT UNSIGNED", "Long", "java.lang.Long");
		addType(mysqlTypes, "INT UNSIGNED", "Integer", "java.lang.Integer");
		addType(mysqlTypes, "TINYINT UNSIGNED", "Integer", "java.lang.Integer");
		addType(mysqlTypes, "SMALLINT", "Short", "java.lang.Short");
		addType(mysqlTypes, "SMALLINT UNSIGNED", "Integer", "java.lang.Integer");
		addType(mysqlTypes, "TINYINT", "Short", "java.lang.Short");
		addType(mysqlTypes, "BIT", "Boolean", "java.lang.Boolean");
		addType(mysqlTypes, "FLOAT", "Float", "java.lang.Float");
		addType(mysqlTypes, "FLOAT UNSIGNED", "Float", "java.lang.Float");
		addType(mysqlTypes, "DOUBLE", "Double", "java.lang.Double");
		addType(mysqlTypes, "DOUBLE UNSIGNED", "Double", "java.lang.Double");
		addType(mysqlTypes, "DECIMAL", "BigDecimal", "java.math.BigDecimal");
		addType(mysqlTypes, "DECIMAL UNSIGNED", "BigDecimal", "java.math.BigDecimal");
		addType(mysqlTypes, "DATETIME", "Date", "java.util.Date");
		addType(mysqlTypes, "TIMESTAMP", "Date", "java.util.Date");
		addType(mysqlTypes, "DATE", "Date", "java.util.Date");
		addType(mysqlTypes, "TEXT", "String", "java.lang.String");
		addType(mysqlTypes, "TINYTEXT", "String", "java.lang.String");
		addType(mysqlTypes, "MEDIUMTEXT", "String", "java.lang.String");
		addType(mysqlTypes, "LONGTEXT", "String", "java.lang.String");
		addType(mysqlTypes, "CHAR", "String", "java.lang.String");
		addType(mysqlTypes, "ENUM", "String", "java.lang.String");
		addType(mysqlTypes, "SET", "String", "java.lang.String");
		addType(mysqlTypes, "JSON", "String", "java.lang.String");
		addType(mysqlTypes, "YEAR", "Integer", "java.lang.Integer");
		addType(mysqlTypes, "BINARY", "byte[]", null);
		addType(mysqlTypes, "VARBINARY", "byte[]", null);
		mysqlDef.setDbtojavaTypes(mysqlTypes);
		mysqlDef.setJavatoclassTypes(new ArrayList<>());

		// Oracle 定义
		Definition oracleDef = new Definition();
		oracleDef.setDbType(ORACLE_DB_TYPE);
		oracleDef.setName("Oracle");

		Db oracleDb = new Db();
		oracleDb.setAllTypes("VARCHAR2,NUMBER,DATE,TIMESTAMP,CLOB,BLOB,CHAR");
		oracleDb.setCharTypes("CHAR,VARCHAR2,CLOB");
		oracleDb.setFloatTypes("NUMBER");
		oracleDb.setAloneTypes("DATE,TIMESTAMP,CLOB,BLOB");
		oracleDb.setBlobTypes("CLOB,BLOB");
		oracleDef.setDb(oracleDb);

		List<Type> oracleTypes = new ArrayList<>();
		addType(oracleTypes, "VARCHAR2", "String", "java.lang.String");
		addType(oracleTypes, "NUMBER", "Double", "java.lang.Double");
		addType(oracleTypes, "DATE", "Date", "java.util.Date");
		addType(oracleTypes, "TIMESTAMP", "Date", "java.util.Date");
		addType(oracleTypes, "CHAR", "String", "java.lang.String");
		addType(oracleTypes, "CLOB", "String", "java.lang.String");
		addType(oracleTypes, "BLOB", "byte[]", null);
		oracleDef.setDbtojavaTypes(oracleTypes);
		oracleDef.setJavatoclassTypes(new ArrayList<>());

		// 注入到 DefinitionBuilder
		DefinitionBuilder builder = new DefinitionBuilder();
		Field defMapField = DefinitionBuilder.class.getDeclaredField("definitionMap");
		defMapField.setAccessible(true);
		Map<String, Definition> defMap = new HashMap<>();
		defMap.put(MYSQL_DB_TYPE, mysqlDef);
		defMap.put(ORACLE_DB_TYPE, oracleDef);
		defMapField.set(builder, defMap);

		Field defListField = DefinitionBuilder.class.getDeclaredField("definitionList");
		defListField.setAccessible(true);
		List<Definition> defList = new ArrayList<>();
		defList.add(mysqlDef);
		defList.add(oracleDef);
		defListField.set(builder, defList);

		Field staticField = DefinitionBuilder.class.getDeclaredField("definitionBuilder");
		staticField.setAccessible(true);
		staticField.set(null, builder);

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

	// ==================== 测试：MySQL 5.7/8 类型映射 ====================

	/**
	 * DECIMAL 类型应映射为 BigDecimal 而非 Double（保证精度）
	 */
	@Test
	public void testDecimalMapsToBigDecimal() {
		DbColumnInfo info = new DbColumnInfo("price", "DECIMAL", "10", "价格", false, false, false, null, "2", false);
		Column col = new Column(info, MYSQL_DB_TYPE);

		assertEquals("DECIMAL 应映射为 BigDecimal", "BigDecimal", col.getJavaType());
		assertEquals("DECIMAL", col.getTypeName());
		assertEquals("2", col.getDecimalDigits());
		assertEquals("10", col.getColumnSize());
	}

	/**
	 * DECIMAL UNSIGNED 也应映射为 BigDecimal
	 */
	@Test
	public void testDecimalUnsignedMapsToBigDecimal() {
		DbColumnInfo info = new DbColumnInfo("amount", "DECIMAL UNSIGNED", "12", "金额", false, false, false, null, "4", false);
		Column col = new Column(info, MYSQL_DB_TYPE);

		assertEquals("BigDecimal", col.getJavaType());
		assertEquals("DECIMAL UNSIGNED", col.getTypeName());
	}

	/**
	 * TINYINT(1) 应映射为 Boolean（常用于 is_active 等标志位）
	 */
	@Test
	public void testTinyInt1MapsToBoolean() {
		DbColumnInfo info = new DbColumnInfo("is_active", "TINYINT", "1", "是否激活", false, false, false, "0", "0", false);
		Column col = new Column(info, MYSQL_DB_TYPE);

		assertEquals("TINYINT(1) 应映射为 Boolean", "Boolean", col.getJavaType());
	}

	/**
	 * TINYINT(4) 不应映射为 Boolean（正常数值）
	 */
	@Test
	public void testTinyInt4MapsToShort() {
		DbColumnInfo info = new DbColumnInfo("age", "TINYINT", "4", "年龄", false, false, false, null, "0", false);
		Column col = new Column(info, MYSQL_DB_TYPE);

		assertEquals("TINYINT(4) 应映射为 Short", "Short", col.getJavaType());
	}

	/**
	 * BIT(1) 应映射为 Boolean
	 */
	@Test
	public void testBit1MapsToBoolean() {
		DbColumnInfo info = new DbColumnInfo("flag", "BIT", "1", "标志位", false, false, false, null, "0", false);
		Column col = new Column(info, MYSQL_DB_TYPE);

		assertEquals("BIT(1) 应映射为 Boolean", "Boolean", col.getJavaType());
	}

	/**
	 * MEDIUMINT 应映射为 Integer
	 */
	@Test
	public void testMediumIntMapsToInteger() {
		DbColumnInfo info = new DbColumnInfo("count", "MEDIUMINT", "7", "计数", false, false, false, null, "0", false);
		Column col = new Column(info, MYSQL_DB_TYPE);

		assertEquals("MEDIUMINT 应映射为 Integer", "Integer", col.getJavaType());
	}

	/**
	 * FLOAT 应映射为 Float
	 */
	@Test
	public void testFloatMapsToFloat() {
		DbColumnInfo info = new DbColumnInfo("weight", "FLOAT", "8", "重量", false, false, false, null, "2", false);
		Column col = new Column(info, MYSQL_DB_TYPE);

		assertEquals("FLOAT 应映射为 Float", "Float", col.getJavaType());
	}

	/**
	 * BIGINT UNSIGNED 应映射为 Long
	 */
	@Test
	public void testBigIntUnsignedMapsToLong() {
		DbColumnInfo info = new DbColumnInfo("big_id", "BIGINT UNSIGNED", "20", "大ID", false, true, false, null, "0", true);
		Column col = new Column(info, MYSQL_DB_TYPE);

		assertEquals("Long", col.getJavaType());
		assertTrue("应标记为自增", col.getAutoIncrement());
	}

	/**
	 * INT UNSIGNED 应映射为 Integer
	 */
	@Test
	public void testIntUnsignedMapsToInteger() {
		DbColumnInfo info = new DbColumnInfo("uid", "INT UNSIGNED", "10", "用户ID", false, true, false, null, "0", true);
		Column col = new Column(info, MYSQL_DB_TYPE);

		assertEquals("Integer", col.getJavaType());
	}

	/**
	 * JSON 类型应映射为 String
	 */
	@Test
	public void testJsonMapsToString() {
		DbColumnInfo info = new DbColumnInfo("metadata", "JSON", "1000", "元数据", true, false, false, null, "0", false);
		Column col = new Column(info, MYSQL_DB_TYPE);

		assertEquals("String", col.getJavaType());
	}

	/**
	 * ENUM 类型应映射为 String
	 */
	@Test
	public void testEnumMapsToString() {
		DbColumnInfo info = new DbColumnInfo("status", "ENUM", "10", "状态", false, false, false, "'active'", "0", false);
		Column col = new Column(info, MYSQL_DB_TYPE);

		assertEquals("String", col.getJavaType());
	}

	/**
	 * YEAR 类型应映射为 Integer
	 */
	@Test
	public void testYearMapsToInteger() {
		DbColumnInfo info = new DbColumnInfo("birth_year", "YEAR", "4", "出生年", true, false, false, null, "0", false);
		Column col = new Column(info, MYSQL_DB_TYPE);

		assertEquals("Integer", col.getJavaType());
	}

	/**
	 * 未知类型应回退为 String
	 */
	@Test
	public void testUnknownTypeFallbacksToString() {
		DbColumnInfo info = new DbColumnInfo("unknown_col", "GEOMETRY", "0", "未知类型", true, false, false, null, "0", false);
		Column col = new Column(info, MYSQL_DB_TYPE);

		assertEquals("未知类型应回退为 String", "String", col.getJavaType());
	}

	/**
	 * 小写类型名也能正确映射（MySQL 8 的 information_schema 返回小写）
	 */
	@Test
	public void testLowercaseTypeNameMapsCorrectly() {
		DbColumnInfo info = new DbColumnInfo("col", "varchar", "255", "小写类型", true, false, false, null, "0", false);
		Column col = new Column(info, MYSQL_DB_TYPE);

		assertEquals("小写 varchar 应映射为 String", "String", col.getJavaType());
		assertEquals("typeName 应标准化为大写", "VARCHAR", col.getTypeName());
	}

	// ==================== 测试：DbTypeConvert 缓存隔离 ====================

	/**
	 * 不同 dbType 的类型转换器应独立缓存，不互相干扰
	 */
	@Test
	public void testDbTypeConvertCacheIsolation() {
		ITypeConvert mysqlConvert = DbTypeConvert.getTypeConvert(DbTypeConvert.TYPE_DB_TO_JAVA, MYSQL_DB_TYPE);
		ITypeConvert oracleConvert = DbTypeConvert.getTypeConvert(DbTypeConvert.TYPE_DB_TO_JAVA, ORACLE_DB_TYPE);

		// MySQL 的 VARCHAR → String
		assertNotNull("MySQL VARCHAR 应有映射", mysqlConvert.getType("VARCHAR"));
		assertEquals("String", mysqlConvert.getType("VARCHAR").getJavaType());

		// Oracle 的 VARCHAR2 → String（MySQL 没有 VARCHAR2）
		assertNotNull("Oracle VARCHAR2 应有映射", oracleConvert.getType("VARCHAR2"));
		assertNull("MySQL 不应有 VARCHAR2", mysqlConvert.getType("VARCHAR2"));

		// MySQL 的 DECIMAL → BigDecimal
		assertNotNull(mysqlConvert.getType("DECIMAL"));
		assertEquals("BigDecimal", mysqlConvert.getType("DECIMAL").getJavaType());

		// Oracle 的 NUMBER → Double（MySQL 没有 NUMBER）
		assertNotNull("Oracle NUMBER 应有映射", oracleConvert.getType("NUMBER"));
		assertNull("MySQL 不应有 NUMBER", mysqlConvert.getType("NUMBER"));
	}

	// ==================== 测试：DbHelper 关键字处理 ====================

	/**
	 * SQL 关键字应被正确识别
	 */
	@Test
	public void testIsKeywordDetection() {
		assertTrue("ORDER 是关键字", DbHelper.isKeyword("order"));
		assertTrue("SELECT 是关键字", DbHelper.isKeyword("SELECT"));
		assertTrue("GROUP 是关键字", DbHelper.isKeyword("group"));
		assertTrue("INDEX 是关键字", DbHelper.isKeyword("INDEX"));
		assertTrue("KEY 是关键字", DbHelper.isKeyword("key"));
		assertTrue("TABLE 是关键字", DbHelper.isKeyword("Table"));
		assertTrue("STATUS 是关键字", DbHelper.isKeyword("status"));
		assertTrue("COMMENT 是关键字", DbHelper.isKeyword("comment"));

		assertFalse("user_name 不是关键字", DbHelper.isKeyword("user_name"));
		assertFalse("created_at 不是关键字", DbHelper.isKeyword("created_at"));
		assertFalse("price 不是关键字", DbHelper.isKeyword("price"));
	}

	/**
	 * 反引号转义应正确处理
	 */
	@Test
	public void testQuoteIdentifier() {
		assertEquals("`order`", DbHelper.quoteIdentifier("order"));
		assertEquals("`select`", DbHelper.quoteIdentifier("select"));
		assertEquals("`user_name`", DbHelper.quoteIdentifier("user_name"));
		// 已有反引号的不应重复添加
		assertEquals("`order`", DbHelper.quoteIdentifier("`order`"));
		assertNull(DbHelper.quoteIdentifier(null));
	}

	// ==================== 测试：Column autoIncrement ====================

	/**
	 * 自增列应正确标记
	 */
	@Test
	public void testAutoIncrementColumn() {
		DbColumnInfo info = new DbColumnInfo("id", "BIGINT", "20", "主键", false, true, false, null, "0", true);
		Column col = new Column(info, MYSQL_DB_TYPE);

		assertTrue("应标记为自增", col.getAutoIncrement());
		assertTrue("应标记为主键", col.getParmaryKey());
	}

	/**
	 * 非自增列不应被标记
	 */
	@Test
	public void testNonAutoIncrementColumn() {
		DbColumnInfo info = new DbColumnInfo("name", "VARCHAR", "255", "名称", false, false, false, null, "0", false);
		Column col = new Column(info, MYSQL_DB_TYPE);

		assertFalse("不应标记为自增", col.getAutoIncrement());
	}

	// ==================== 测试：无主键表 ====================

	/**
	 * 无主键表的 hasPrimaryKey 应为 false
	 */
	@Test
	public void testNoPrimaryKeyTableInDryRun() throws Exception {
		Table table = buildTable("no_pk_table");
		DataSource ds = buildDataSource();

		// 所有列都没有主键标记
		List<Column> columns = new ArrayList<>();
		columns.add(buildColumn("col_a", "VARCHAR", "String", false));
		columns.add(buildColumn("col_b", "INT", "Integer", false));
		columns.add(buildColumn("col_c", "DATETIME", "Date", false));

		String targetDir = tempFolder.newFolder("no_pk_output").getAbsolutePath();
		Template template = buildTemplate(targetDir,
				"package ${targetPackage};\n" +
				"<#if hasPrimaryKey>\n" +
				"// HAS_PK\n" +
				"<#else>\n" +
				"// NO_PK\n" +
				"</#if>\n" +
				"public class ${entityName} {\n" +
				"<#list columns as column>\n" +
				"    private ${column.javaType} ${column.javaField};\n" +
				"</#list>\n" +
				"}\n"
		);
		List<Template> allTemplates = Arrays.asList(template);

		TableServiceImpl spyService = spy(tableService);
		injectMocks(spyService);

		doReturn(table).when(spyService).selectById(table.getId());
		when(dataSourceService.selectById(ds.getId())).thenReturn(ds);
		when(columnService.selectListByTableId(table.getId())).thenReturn(columns);
		when(templateService.selectById(template.getId())).thenReturn(template);
		when(templateService.selectList(any(EntityWrapper.class))).thenReturn(allTemplates);
		when(schemeService.selectOne(any(EntityWrapper.class))).thenReturn(null);
		when(generationLogService.insert(any(GenerationLog.class))).thenReturn(true);

		DryRunRequest request = buildDryRunRequest(table.getId());
		DryRunResult result = spyService.dryRun(request);

		assertNotNull(result);
		assertEquals(1, result.getFiles().size());
		String content = result.getFiles().get(0).getNewContent();
		assertTrue("无主键表应包含 NO_PK 标记", content.contains("NO_PK"));
		assertFalse("无主键表不应包含 HAS_PK 标记", content.contains("HAS_PK"));
	}

	/**
	 * 有主键表的 hasPrimaryKey 应为 true
	 */
	@Test
	public void testWithPrimaryKeyTableInDryRun() throws Exception {
		Table table = buildTable("with_pk_table");
		DataSource ds = buildDataSource();

		List<Column> columns = new ArrayList<>();
		columns.add(buildColumn("id", "VARCHAR", "String", true));  // 主键
		columns.add(buildColumn("name", "VARCHAR", "String", false));

		String targetDir = tempFolder.newFolder("with_pk_output").getAbsolutePath();
		Template template = buildTemplate(targetDir,
				"package ${targetPackage};\n" +
				"<#if hasPrimaryKey>\n" +
				"// HAS_PK\n" +
				"<#else>\n" +
				"// NO_PK\n" +
				"</#if>\n" +
				"public class ${entityName} {}\n"
		);
		List<Template> allTemplates = Arrays.asList(template);

		TableServiceImpl spyService = spy(tableService);
		injectMocks(spyService);

		doReturn(table).when(spyService).selectById(table.getId());
		when(dataSourceService.selectById(ds.getId())).thenReturn(ds);
		when(columnService.selectListByTableId(table.getId())).thenReturn(columns);
		when(templateService.selectById(template.getId())).thenReturn(template);
		when(templateService.selectList(any(EntityWrapper.class))).thenReturn(allTemplates);
		when(schemeService.selectOne(any(EntityWrapper.class))).thenReturn(null);
		when(generationLogService.insert(any(GenerationLog.class))).thenReturn(true);

		DryRunRequest request = buildDryRunRequest(table.getId());
		DryRunResult result = spyService.dryRun(request);

		String content = result.getFiles().get(0).getNewContent();
		assertTrue("有主键表应包含 HAS_PK 标记", content.contains("HAS_PK"));
	}

	// ==================== 测试：关键字表名/字段名 ====================

	/**
	 * 包含 SQL 关键字的字段名应在模板数据中正确传递
	 */
	@Test
	public void testKeywordColumnNamesInTemplate() throws Exception {
		Table table = buildTable("t_keyword_test");
		DataSource ds = buildDataSource();

		List<Column> columns = new ArrayList<>();
		columns.add(buildColumn("id", "VARCHAR", "String", true));
		columns.add(buildColumn("order", "INT", "Integer", false));   // 关键字
		columns.add(buildColumn("status", "VARCHAR", "String", false)); // 关键字
		columns.add(buildColumn("group", "VARCHAR", "String", false));  // 关键字

		String targetDir = tempFolder.newFolder("keyword_output").getAbsolutePath();
		Template template = buildTemplate(targetDir,
				"package ${targetPackage};\n" +
				"public class ${entityName} {\n" +
				"<#list columns as column>\n" +
				"    private ${column.javaType} ${column.javaField}; // col: ${column.columnName}\n" +
				"</#list>\n" +
				"}\n"
		);
		List<Template> allTemplates = Arrays.asList(template);

		TableServiceImpl spyService = spy(tableService);
		injectMocks(spyService);

		doReturn(table).when(spyService).selectById(table.getId());
		when(dataSourceService.selectById(ds.getId())).thenReturn(ds);
		when(columnService.selectListByTableId(table.getId())).thenReturn(columns);
		when(templateService.selectById(template.getId())).thenReturn(template);
		when(templateService.selectList(any(EntityWrapper.class))).thenReturn(allTemplates);
		when(schemeService.selectOne(any(EntityWrapper.class))).thenReturn(null);
		when(generationLogService.insert(any(GenerationLog.class))).thenReturn(true);

		DryRunRequest request = buildDryRunRequest(table.getId());
		DryRunResult result = spyService.dryRun(request);

		String content = result.getFiles().get(0).getNewContent();
		assertNotNull("生成的内容不应为空", content);
		assertTrue("应包含 order 字段", content.contains("col: order"));
		assertTrue("应包含 status 字段", content.contains("col: status"));
		assertTrue("应包含 group 字段", content.contains("col: group"));
	}

	// ==================== 测试：重复生成 ====================

	/**
	 * 重复生成相同内容时，文件应被标记为 SKIP
	 */
	@Test
	public void testDuplicateGenerationSkipsIdentical() throws Exception {
		Table table = buildTable("dup_test_table");
		DataSource ds = buildDataSource();
		List<Column> columns = Arrays.asList(
				buildColumn("id", "VARCHAR", "String", true),
				buildColumn("name", "VARCHAR", "String", false),
				buildColumn("amount", "DECIMAL", "BigDecimal", false)
		);

		File outputDir = tempFolder.newFolder("dup_output");
		Template template = buildTemplate(outputDir.getAbsolutePath(),
				"package ${targetPackage};\n\n" +
				"import java.math.BigDecimal;\n\n" +
				"public class ${entityName} {\n" +
				"<#list columns as column>\n" +
				"    private ${column.javaType} ${column.javaField};\n" +
				"</#list>\n" +
				"}\n"
		);
		List<Template> allTemplates = Arrays.asList(template);

		TableServiceImpl spyService = spy(tableService);
		injectMocks(spyService);

		doReturn(table).when(spyService).selectById(table.getId());
		when(dataSourceService.selectById(ds.getId())).thenReturn(ds);
		when(columnService.selectListByTableId(table.getId())).thenReturn(columns);
		when(templateService.selectById(template.getId())).thenReturn(template);
		when(templateService.selectList(any(EntityWrapper.class))).thenReturn(allTemplates);
		when(schemeService.selectOne(any(EntityWrapper.class))).thenReturn(null);
		when(generationLogService.insert(any(GenerationLog.class))).thenReturn(true);

		// 第一次 dry-run
		DryRunRequest request = buildDryRunRequest(table.getId());
		DryRunResult firstResult = spyService.dryRun(request);
		assertEquals("第一次应为 NEW", FilePreview.FileStatus.NEW, firstResult.getFiles().get(0).getStatus());

		// 确认生成，写入文件
		spyService.generateConfirmed(firstResult.getDryRunId());

		// 验证文件已写入
		File generatedFile = new File(firstResult.getFiles().get(0).getFilePath());
		assertTrue("文件应已写入", generatedFile.exists());

		// 清除缓存以模拟新的请求
		clearDbTypeConvertCache();

		// 第二次 dry-run（相同数据）
		DryRunResult secondResult = spyService.dryRun(request);
		assertEquals("第二次内容相同应为 SKIP",
				FilePreview.FileStatus.SKIP, secondResult.getFiles().get(0).getStatus());

		// 确认生成（SKIP 文件不应被重写）
		long lastModifiedBefore = generatedFile.lastModified();
		spyService.generateConfirmed(secondResult.getDryRunId());
		assertEquals("SKIP 文件不应被重写", lastModifiedBefore, generatedFile.lastModified());
	}

	/**
	 * 重复生成但内容已变更时，文件应被标记为 RISK
	 */
	@Test
	public void testDuplicateGenerationDetectsModification() throws Exception {
		Table table = buildTable("modified_test_table");
		DataSource ds = buildDataSource();
		List<Column> columns = Arrays.asList(
				buildColumn("id", "VARCHAR", "String", true),
				buildColumn("name", "VARCHAR", "String", false)
		);

		File outputDir = tempFolder.newFolder("modified_output");
		Template template = buildTemplate(outputDir.getAbsolutePath(),
				"package ${targetPackage};\n" +
				"public class ${entityName} {\n" +
				"<#list columns as column>\n" +
				"    private ${column.javaType} ${column.javaField};\n" +
				"</#list>\n" +
				"}\n"
		);
		List<Template> allTemplates = Arrays.asList(template);

		TableServiceImpl spyService = spy(tableService);
		injectMocks(spyService);

		doReturn(table).when(spyService).selectById(table.getId());
		when(dataSourceService.selectById(ds.getId())).thenReturn(ds);
		when(columnService.selectListByTableId(table.getId())).thenReturn(columns);
		when(templateService.selectById(template.getId())).thenReturn(template);
		when(templateService.selectList(any(EntityWrapper.class))).thenReturn(allTemplates);
		when(schemeService.selectOne(any(EntityWrapper.class))).thenReturn(null);
		when(generationLogService.insert(any(GenerationLog.class))).thenReturn(true);

		// 第一次生成
		DryRunRequest request = buildDryRunRequest(table.getId());
		DryRunResult firstResult = spyService.dryRun(request);
		spyService.generateConfirmed(firstResult.getDryRunId());

		// 模拟用户手动修改了文件
		File generatedFile = new File(firstResult.getFiles().get(0).getFilePath());
		org.apache.commons.io.FileUtils.write(generatedFile,
				"// 用户手动修改\npackage com.test.entity;\npublic class TestEntity { /* customized */ }\n",
				"UTF-8");

		// 清除缓存
		clearDbTypeConvertCache();

		// 第二次 dry-run
		DryRunResult secondResult = spyService.dryRun(request);
		assertEquals("用户修改过的文件应为 RISK",
				FilePreview.FileStatus.RISK, secondResult.getFiles().get(0).getStatus());
		assertTrue("hasRisk 应为 true", secondResult.isHasRisk());
	}

	// ==================== 测试：包名路径解析 ====================

	/**
	 * 空 moduleName 时，[moduleName] 占位符应被正确移除，不产生多余点号
	 */
	@Test
	public void testEmptyModuleNamePackageParsing() throws Exception {
		Table table = buildTable("pkg_test");
		DataSource ds = buildDataSource();
		List<Column> columns = Arrays.asList(buildColumn("name", "VARCHAR", "String", false));

		File outputDir = tempFolder.newFolder("pkg_output");
		// 包名中包含 [moduleName] 占位符
		Template template = buildTemplate(outputDir.getAbsolutePath(),
				"package ${targetPackage};\npublic class ${entityName} {}\n"
		);
		template.setTargetPackage("com.company.[moduleName].entity");
		List<Template> allTemplates = Arrays.asList(template);

		TableServiceImpl spyService = spy(tableService);
		injectMocks(spyService);

		doReturn(table).when(spyService).selectById(table.getId());
		when(dataSourceService.selectById(ds.getId())).thenReturn(ds);
		when(columnService.selectListByTableId(table.getId())).thenReturn(columns);
		when(templateService.selectById(template.getId())).thenReturn(template);
		when(templateService.selectList(any(EntityWrapper.class))).thenReturn(allTemplates);
		when(schemeService.selectOne(any(EntityWrapper.class))).thenReturn(null);
		when(generationLogService.insert(any(GenerationLog.class))).thenReturn(true);

		DryRunRequest request = buildDryRunRequest(table.getId());
		request.setModuleName("");  // 空模块名
		DryRunResult result = spyService.dryRun(request);

		String content = result.getFiles().get(0).getNewContent();
		// 包名应为 "com.company.entity"，不应有 "com.company..entity" 或 ".entity"
		assertTrue("包名不应包含连续点号", !content.contains("com.company..entity"));
		assertTrue("包名应以 com.company.entity 开头或包含该字符串",
				content.contains("com.company.entity"));
	}

	/**
	 * 有 moduleName 时，[moduleName] 应被正确替换
	 */
	@Test
	public void testWithModuleNamePackageParsing() throws Exception {
		Table table = buildTable("pkg_test2");
		DataSource ds = buildDataSource();
		List<Column> columns = Arrays.asList(buildColumn("name", "VARCHAR", "String", false));

		File outputDir = tempFolder.newFolder("pkg_output2");
		Template template = buildTemplate(outputDir.getAbsolutePath(),
				"package ${targetPackage};\npublic class ${entityName} {}\n"
		);
		template.setTargetPackage("com.company.[moduleName].entity");
		List<Template> allTemplates = Arrays.asList(template);

		TableServiceImpl spyService = spy(tableService);
		injectMocks(spyService);

		doReturn(table).when(spyService).selectById(table.getId());
		when(dataSourceService.selectById(ds.getId())).thenReturn(ds);
		when(columnService.selectListByTableId(table.getId())).thenReturn(columns);
		when(templateService.selectById(template.getId())).thenReturn(template);
		when(templateService.selectList(any(EntityWrapper.class))).thenReturn(allTemplates);
		when(schemeService.selectOne(any(EntityWrapper.class))).thenReturn(null);
		when(generationLogService.insert(any(GenerationLog.class))).thenReturn(true);

		DryRunRequest request = buildDryRunRequest(table.getId());
		request.setModuleName("order");
		DryRunResult result = spyService.dryRun(request);

		String content = result.getFiles().get(0).getNewContent();
		assertTrue("包名应包含替换后的模块名",
				content.contains("com.company.order.entity"));
	}

	// ==================== 测试：decimal 精度保留 ====================

	/**
	 * DECIMAL(10,2) 精度信息应在 Column 中正确保留
	 */
	@Test
	public void testDecimalPrecisionPreserved() {
		DbColumnInfo info = new DbColumnInfo("price", "DECIMAL", "10", "价格",
				false, false, false, null, "2", false);
		Column col = new Column(info, MYSQL_DB_TYPE);

		assertEquals("BigDecimal", col.getJavaType());
		assertEquals("10", col.getColumnSize());
		assertEquals("2", col.getDecimalDigits());
	}

	/**
	 * DECIMAL(20,6) 高精度小数位
	 */
	@Test
	public void testDecimalHighPrecisionPreserved() {
		DbColumnInfo info = new DbColumnInfo("exchange_rate", "DECIMAL", "20", "汇率",
				false, false, false, null, "6", false);
		Column col = new Column(info, MYSQL_DB_TYPE);

		assertEquals("BigDecimal", col.getJavaType());
		assertEquals("20", col.getColumnSize());
		assertEquals("6", col.getDecimalDigits());
	}

	// ==================== 测试：Column isFloat/isString 属性 ====================

	/**
	 * DECIMAL 的 isFloat 应为 true
	 */
	@Test
	public void testDecimalIsFloat() {
		DbColumnInfo info = new DbColumnInfo("amount", "DECIMAL", "10", "金额",
				false, false, false, null, "2", false);
		Column col = new Column(info, MYSQL_DB_TYPE);
		col.setDbType(MYSQL_DB_TYPE);

		assertTrue("DECIMAL 的 isFloat 应为 true", col.getIsFloat());
	}

	/**
	 * VARCHAR 的 isString 应为 true
	 */
	@Test
	public void testVarcharIsString() {
		DbColumnInfo info = new DbColumnInfo("name", "VARCHAR", "255", "名称",
				false, false, false, null, "0", false);
		Column col = new Column(info, MYSQL_DB_TYPE);
		col.setDbType(MYSQL_DB_TYPE);

		assertTrue("VARCHAR 的 isString 应为 true", col.getIsString());
	}

	/**
	 * INT 的 isAlone 应为 true
	 */
	@Test
	public void testIntIsAlone() {
		DbColumnInfo info = new DbColumnInfo("count", "INT", "11", "计数",
				false, false, false, null, "0", false);
		Column col = new Column(info, MYSQL_DB_TYPE);
		col.setDbType(MYSQL_DB_TYPE);

		// INT 在 alone-types 列表中（根据我们设置的 Definition）
		// 注意：alone-types 中没有 INT，所以 isAlone 应为 false
		assertFalse("INT 的 isAlone 应为 false", col.getIsAlone());
	}

	/**
	 * TEXT 的 isBlob 应为 true
	 */
	@Test
	public void testTextIsBlob() {
		DbColumnInfo info = new DbColumnInfo("content", "TEXT", "65535", "内容",
				true, false, false, null, "0", false);
		Column col = new Column(info, MYSQL_DB_TYPE);
		col.setDbType(MYSQL_DB_TYPE);

		assertTrue("TEXT 的 isBlob 应为 true", col.getIsBlob());
	}

	// ==================== 测试：模板渲染中 BigDecimal 类型引用 ====================

	/**
	 * 使用 BigDecimal 的模板应正确渲染
	 */
	@Test
	public void testBigDecimalTemplateRendering() throws Exception {
		Table table = buildTable("decimal_render_test");
		DataSource ds = buildDataSource();

		List<Column> columns = new ArrayList<>();
		columns.add(buildColumn("id", "VARCHAR", "String", true));
		columns.add(buildColumn("price", "DECIMAL", "BigDecimal", false));
		columns.add(buildColumn("rate", "DECIMAL", "BigDecimal", false));

		File outputDir = tempFolder.newFolder("decimal_render");
		Template template = buildTemplate(outputDir.getAbsolutePath(),
				"package ${targetPackage};\n\n" +
				"import java.math.BigDecimal;\n\n" +
				"public class ${entityName} {\n" +
				"<#list columns as column>\n" +
				"    /** ${column.remarks!column.columnName} */\n" +
				"    private ${column.javaType} ${column.javaField};\n" +
				"</#list>\n" +
				"}\n"
		);
		List<Template> allTemplates = Arrays.asList(template);

		TableServiceImpl spyService = spy(tableService);
		injectMocks(spyService);

		doReturn(table).when(spyService).selectById(table.getId());
		when(dataSourceService.selectById(ds.getId())).thenReturn(ds);
		when(columnService.selectListByTableId(table.getId())).thenReturn(columns);
		when(templateService.selectById(template.getId())).thenReturn(template);
		when(templateService.selectList(any(EntityWrapper.class))).thenReturn(allTemplates);
		when(schemeService.selectOne(any(EntityWrapper.class))).thenReturn(null);
		when(generationLogService.insert(any(GenerationLog.class))).thenReturn(true);

		DryRunRequest request = buildDryRunRequest(table.getId());
		DryRunResult result = spyService.dryRun(request);

		String content = result.getFiles().get(0).getNewContent();
		assertTrue("应包含 BigDecimal 类型", content.contains("BigDecimal"));
		assertTrue("应包含 price 字段", content.contains("price"));
		assertTrue("应包含 rate 字段", content.contains("rate"));
		assertTrue("应包含 import", content.contains("import java.math.BigDecimal"));
	}

	// ==================== 测试：Mapper XML 中字段引用一致性 ====================

	/**
	 * Mapper XML 模板中的字段名应与 Column.javaField 一致
	 */
	@Test
	public void testMapperXmlFieldReference() throws Exception {
		Table table = buildTable("mapper_xml_test");
		DataSource ds = buildDataSource();

		List<Column> columns = new ArrayList<>();
		columns.add(buildColumn("id", "VARCHAR", "String", true));
		columns.add(buildColumn("user_name", "VARCHAR", "String", false));
		columns.add(buildColumn("create_time", "DATETIME", "Date", false));
		columns.add(buildColumn("is_active", "TINYINT", "Boolean", false));

		File outputDir = tempFolder.newFolder("mapper_xml_output");
		Template template = buildTemplate(outputDir.getAbsolutePath(),
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
				"<mapper namespace=\"${targetPackage}.${entityName}Mapper\">\n" +
				"  <resultMap id=\"BaseResultMap\" type=\"${targetPackage}.${entityName}\">\n" +
				"<#list columns as column>\n" +
				"    <result column=\"${column.columnName}\" property=\"${column.javaField}\" />\n" +
				"</#list>\n" +
				"  </resultMap>\n" +
				"  <sql id=\"Base_Column_List\">\n" +
				"<#list columns as column>\n" +
				"    ${column.columnName}<#if column_has_next>,</#if>\n" +
				"</#list>\n" +
				"  </sql>\n" +
				"</mapper>\n"
		);
		List<Template> allTemplates = Arrays.asList(template);

		TableServiceImpl spyService = spy(tableService);
		injectMocks(spyService);

		doReturn(table).when(spyService).selectById(table.getId());
		when(dataSourceService.selectById(ds.getId())).thenReturn(ds);
		when(columnService.selectListByTableId(table.getId())).thenReturn(columns);
		when(templateService.selectById(template.getId())).thenReturn(template);
		when(templateService.selectList(any(EntityWrapper.class))).thenReturn(allTemplates);
		when(schemeService.selectOne(any(EntityWrapper.class))).thenReturn(null);
		when(generationLogService.insert(any(GenerationLog.class))).thenReturn(true);

		DryRunRequest request = buildDryRunRequest(table.getId());
		DryRunResult result = spyService.dryRun(request);

		String content = result.getFiles().get(0).getNewContent();
		// 验证 column → property 映射一致
		assertTrue("user_name 列应映射到 userName 属性", content.contains("column=\"user_name\" property=\"userName\""));
		assertTrue("create_time 列应映射到 createTime 属性", content.contains("column=\"create_time\" property=\"createTime\""));
		assertTrue("is_active 列应映射到 isActive 属性", content.contains("column=\"is_active\" property=\"isActive\""));
	}

	// ==================== 辅助方法 ====================

	private Table buildTable(String tableName) {
		Table table = new Table();
		table.setId("table-" + tableName);
		table.setTableName(tableName);
		table.setTitle(tableName);
		table.setClassName("TestEntity");
		table.setSourceId("ds-001");
		table.setSyncDatabase(Boolean.TRUE);
		table.setTest(Boolean.FALSE);
		table.setTableType("2");
		table.setRemarks("测试表");
		return table;
	}

	private DataSource buildDataSource() {
		DataSource ds = new DataSource();
		ds.setId("ds-001");
		ds.setDbType(MYSQL_DB_TYPE);
		ds.setDbKey("test");
		ds.setUrl("jdbc:mysql://localhost:3306/test");
		ds.setDbUser("root");
		ds.setDbPassword("123456");
		ds.setDriverClass("com.mysql.jdbc.Driver");
		ds.setDbName("test");
		return ds;
	}

	private Column buildColumn(String columnName, String typeName, String javaType, boolean isPK) {
		Column column = new Column();
		column.setId("col-" + columnName);
		column.setColumnName(columnName);
		column.setTypeName(typeName);
		column.setJavaType(javaType);
		column.setJavaField(com.company.manerger.sys.common.utils.StringUtils.underlineToCamel(columnName));
		column.setParmaryKey(isPK);
		column.setListable(Boolean.TRUE);
		column.setFormable(Boolean.TRUE);
		column.setNullable(!isPK);
		column.setRemarks(columnName);
		return column;
	}

	private Template buildTemplate(String targetPath, String content) {
		Template template = new Template();
		template.setId("tpl-001");
		template.setName("Entity模板");
		template.setKey("entity");
		template.setSchemeId("ts-001");
		template.setNameFormat("[entityName].java");
		template.setNameUnderline("0");
		template.setEnablePackage("1");
		template.setTargetPackage("com.test.entity");
		template.setTargetPath(targetPath);
		template.setSort(1);
		template.setTemplateContent(content);
		return template;
	}

	private DryRunRequest buildDryRunRequest(String tableId) {
		DryRunRequest request = new DryRunRequest();
		request.setTableId(tableId);
		request.setTemplateSchemeId("ts-001");
		request.setEntityName("TestEntity");
		request.setModuleName("test-module");
		request.setFunctionAuthor("tester");
		request.setFunctionDesc("测试功能");
		request.setFunctionName("测试");
		request.setTemplateKeys(Arrays.asList("tpl-001"));
		request.setTemplatePaths(new HashMap<>());
		request.setTemplatePackages(new HashMap<>());
		return request;
	}

	private void injectMocks(TableServiceImpl target) throws Exception {
		injectField(target, "columnService", columnService);
		injectField(target, "dataSourceService", dataSourceService);
		injectField(target, "templateService", templateService);
		injectField(target, "schemeService", schemeService);
		injectField(target, "generationLogService", generationLogService);
	}

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
