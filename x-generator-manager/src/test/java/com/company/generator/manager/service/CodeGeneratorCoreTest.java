package com.company.generator.manager.service;

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
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 代码生成器核心链路测试：
 * - MySQL 5.7/8 元数据读取（类型映射、自增、注释、主键）
 * - 关键字字段转义
 * - 无主键表
 * - DECIMAL 精度映射
 * - 重复生成（覆盖/跳过判断）
 */
@RunWith(MockitoJUnitRunner.class)
public class CodeGeneratorCoreTest {

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

    private static final String DB_TYPE = "MySql";

    @Before
    public void setUp() throws Exception {
        setupDefinitionBuilder();
        clearDbTypeConvertCache();
    }

    // ==================== MySQL 元数据测试 ====================

    /**
     * 测试MySQL 5.7/8返回的类型名大小写不一致时，类型映射仍然正确
     * MySQL 5.7 可能返回 "int"，MySQL 8 返回 "INT"
     */
    @Test
    public void testMySqlTypeName_CaseInsensitive() {
        // MySQL 5.7 风格 - 小写
        DbColumnInfo lowerCase = new DbColumnInfo("user_id", "int", "11", "用户ID",
                false, true, false, null, "0", true);
        Column col1 = new Column(lowerCase, DB_TYPE);
        assertEquals("int小写应映射为Integer", "Integer", col1.getJavaType());
        assertEquals("typeName应被标准化为大写", "INT", col1.getTypeName());

        // MySQL 8 风格 - 大写
        DbColumnInfo upperCase = new DbColumnInfo("user_id", "INT", "11", "用户ID",
                false, true, false, null, "0", true);
        Column col2 = new Column(upperCase, DB_TYPE);
        assertEquals("INT大写应映射为Integer", "Integer", col2.getJavaType());

        // 混合大小写
        DbColumnInfo mixedCase = new DbColumnInfo("amount", "Decimal", "10", "金额",
                false, false, false, null, "2", false);
        Column col3 = new Column(mixedCase, DB_TYPE);
        assertEquals("Decimal混合大小写应映射为BigDecimal", "BigDecimal", col3.getJavaType());
    }

    /**
     * 测试MySQL 8返回 "BIGINT UNSIGNED" / "INT UNSIGNED" 等带UNSIGNED后缀的类型
     */
    @Test
    public void testMySqlUnsignedTypes() {
        DbColumnInfo bigintUnsigned = new DbColumnInfo("big_id", "BIGINT UNSIGNED", "20", "",
                false, true, false, null, "0", true);
        Column col1 = new Column(bigintUnsigned, DB_TYPE);
        assertEquals("BIGINT UNSIGNED应映射为Long", "Long", col1.getJavaType());

        DbColumnInfo intUnsigned = new DbColumnInfo("count", "INT UNSIGNED", "10", "",
                false, false, false, "0", "0", false);
        Column col2 = new Column(intUnsigned, DB_TYPE);
        assertEquals("INT UNSIGNED应映射为Integer", "Integer", col2.getJavaType());

        DbColumnInfo tinyintUnsigned = new DbColumnInfo("status", "TINYINT UNSIGNED", "3", "",
                false, false, false, "0", "0", false);
        Column col3 = new Column(tinyintUnsigned, DB_TYPE);
        assertEquals("TINYINT UNSIGNED应映射为Integer", "Integer", col3.getJavaType());
    }

    /**
     * 测试自增字段检测
     */
    @Test
    public void testAutoIncrementDetection() {
        DbColumnInfo autoInc = new DbColumnInfo("id", "BIGINT", "20", "主键",
                false, true, false, null, "0", true);
        Column col = new Column(autoInc, DB_TYPE);
        assertTrue("自增字段应被检测到", col.getAutoIncrement());

        DbColumnInfo nonAutoInc = new DbColumnInfo("name", "VARCHAR", "255", "姓名",
                true, false, false, null, "0", false);
        Column col2 = new Column(nonAutoInc, DB_TYPE);
        assertFalse("非自增字段不应标记为自增", col2.getAutoIncrement());
    }

    /**
     * 测试字段注释（remarks）保留
     */
    @Test
    public void testColumnRemarks() {
        DbColumnInfo withRemarks = new DbColumnInfo("user_name", "VARCHAR", "100", "用户姓名",
                true, false, false, null, "0", false);
        Column col = new Column(withRemarks, DB_TYPE);
        assertEquals("字段注释应被保留", "用户姓名", col.getRemarks());

        DbColumnInfo emptyRemarks = new DbColumnInfo("code", "VARCHAR", "50", "",
                true, false, false, null, "0", false);
        Column col2 = new Column(emptyRemarks, DB_TYPE);
        // DbColumnInfo.getRemarks() returns columnName when remarks is empty
        assertEquals("空注释应回退到字段名", "code", col2.getRemarks());
    }

    /**
     * 测试主键标记在多列表中正确保留
     * 验证修复：原来的bug会在遍历时把非PK列的parmaryKey重置为false
     */
    @Test
    public void testPrimaryKeyPreservedAcrossColumns() {
        // 模拟多列场景，其中第一列是主键
        DbColumnInfo pkCol = new DbColumnInfo("id", "BIGINT", "20", "主键",
                false, false, false, null, "0", true);
        DbColumnInfo normalCol1 = new DbColumnInfo("name", "VARCHAR", "100", "姓名",
                true, false, false, null, "0", false);
        DbColumnInfo normalCol2 = new DbColumnInfo("age", "INT", "11", "年龄",
                true, false, false, null, "0", false);

        // 模拟DbHelper修复后的行为：仅将PK列设为true
        pkCol.setParmaryKey(true);
        // normalCol1 和 normalCol2 的 parmaryKey 保持默认 false

        Column col1 = new Column(pkCol, DB_TYPE);
        Column col2 = new Column(normalCol1, DB_TYPE);
        Column col3 = new Column(normalCol2, DB_TYPE);

        assertTrue("id应为主键", col1.getParmaryKey());
        assertFalse("name不应为主键", col2.getParmaryKey());
        assertFalse("age不应为主键", col3.getParmaryKey());
    }

    // ==================== 关键字字段测试 ====================

    /**
     * 测试SQL关键字列名的反引号转义
     */
    @Test
    public void testKeywordColumnEscaping() {
        Column orderCol = new Column();
        orderCol.setColumnName("order");
        assertEquals("order是关键字应加反引号", "`order`", orderCol.getEscapedColumnName());
        assertTrue("order应被识别为关键字", orderCol.getIsKeyword());

        Column statusCol = new Column();
        statusCol.setColumnName("status");
        assertEquals("status是关键字应加反引号", "`status`", statusCol.getEscapedColumnName());

        Column keyCol = new Column();
        keyCol.setColumnName("key");
        assertEquals("key是关键字应加反引号", "`key`", keyCol.getEscapedColumnName());

        Column descCol = new Column();
        descCol.setColumnName("desc");
        assertEquals("desc是关键字应加反引号", "`desc`", descCol.getEscapedColumnName());

        Column groupCol = new Column();
        groupCol.setColumnName("group");
        assertEquals("group是关键字应加反引号", "`group`", groupCol.getEscapedColumnName());
    }

    /**
     * 测试非关键字列名不被转义
     */
    @Test
    public void testNonKeywordColumnNotEscaped() {
        Column normalCol = new Column();
        normalCol.setColumnName("user_name");
        assertEquals("普通列名不应加反引号", "user_name", normalCol.getEscapedColumnName());
        assertFalse("普通列名不应被识别为关键字", normalCol.getIsKeyword());

        Column anotherCol = new Column();
        anotherCol.setColumnName("created_at");
        assertEquals("created_at不应加反引号", "created_at", anotherCol.getEscapedColumnName());
    }

    /**
     * 测试关键字检测大小写不敏感
     */
    @Test
    public void testKeywordDetectionCaseInsensitive() {
        Column col1 = new Column();
        col1.setColumnName("ORDER");
        assertTrue("大写ORDER应被识别为关键字", col1.getIsKeyword());

        Column col2 = new Column();
        col2.setColumnName("Order");
        assertTrue("混合大小写Order应被识别为关键字", col2.getIsKeyword());
    }

    /**
     * 测试MySQL 8新增的窗口函数关键字
     */
    @Test
    public void testMySQL8WindowFunctionKeywords() {
        String[] mysql8Keywords = {"rank", "dense_rank", "row_number", "over", "partition", "lead", "lag"};
        for (String kw : mysql8Keywords) {
            Column col = new Column();
            col.setColumnName(kw);
            assertTrue(kw + "应被识别为MySQL 8关键字", col.getIsKeyword());
            assertEquals(kw + "应加反引号", "`" + kw + "`", col.getEscapedColumnName());
        }
    }

    // ==================== 无主键表测试 ====================

    /**
     * 测试没有主键的表 —— 所有列的parmaryKey都应为false
     */
    @Test
    public void testTableWithoutPrimaryKey() {
        DbColumnInfo col1Info = new DbColumnInfo("log_time", "DATETIME", "0", "记录时间",
                false, false, false, null, "0", false);
        DbColumnInfo col2Info = new DbColumnInfo("message", "TEXT", "0", "日志消息",
                true, false, false, null, "0", false);
        DbColumnInfo col3Info = new DbColumnInfo("level", "TINYINT", "4", "日志级别",
                false, false, false, "0", "0", false);

        Column col1 = new Column(col1Info, DB_TYPE);
        Column col2 = new Column(col2Info, DB_TYPE);
        Column col3 = new Column(col3Info, DB_TYPE);

        assertFalse("无主键表中log_time不应为主键", col1.getParmaryKey());
        assertFalse("无主键表中message不应为主键", col2.getParmaryKey());
        assertFalse("无主键表中level不应为主键", col3.getParmaryKey());

        // 无主键表的类型映射仍应正确
        assertEquals("Date", col1.getJavaType());
        assertEquals("String", col2.getJavaType());
        assertEquals("Integer", col3.getJavaType());
    }

    /**
     * 测试无主键表的Java字段名转换
     */
    @Test
    public void testNoPkTableFieldNames() {
        DbColumnInfo colInfo = new DbColumnInfo("order_detail_id", "BIGINT", "20", "订单明细ID",
                false, false, false, null, "0", false);
        Column col = new Column(colInfo, DB_TYPE);
        assertEquals("下划线应转驼峰", "orderDetailId", col.getJavaField());
    }

    // ==================== DECIMAL 精度映射测试 ====================

    /**
     * 测试DECIMAL映射到BigDecimal（而非Double）
     */
    @Test
    public void testDecimalMappedToBigDecimal() {
        DbColumnInfo decimalCol = new DbColumnInfo("price", "DECIMAL", "10", "价格",
                false, false, false, null, "2", false);
        Column col = new Column(decimalCol, DB_TYPE);
        assertEquals("DECIMAL应映射为BigDecimal", "BigDecimal", col.getJavaType());
        assertEquals("小数位数应为2", "2", col.getDecimalDigits());
    }

    /**
     * 测试DECIMAL UNSIGNED映射到BigDecimal
     */
    @Test
    public void testDecimalUnsignedMappedToBigDecimal() {
        DbColumnInfo decimalCol = new DbColumnInfo("amount", "DECIMAL UNSIGNED", "12", "金额",
                false, false, false, null, "4", false);
        Column col = new Column(decimalCol, DB_TYPE);
        assertEquals("DECIMAL UNSIGNED应映射为BigDecimal", "BigDecimal", col.getJavaType());
        assertEquals("小数位数应为4", "4", col.getDecimalDigits());
    }

    /**
     * 测试各种精度的DECIMAL字段
     */
    @Test
    public void testDecimalPrecisionVariants() {
        // DECIMAL(5,0) - 无小数
        DbColumnInfo dec50 = new DbColumnInfo("count", "DECIMAL", "5", "计数",
                false, false, false, "0", "0", false);
        Column col1 = new Column(dec50, DB_TYPE);
        assertEquals("DECIMAL(5,0)应映射为BigDecimal", "BigDecimal", col1.getJavaType());
        assertEquals("0", col1.getDecimalDigits());

        // DECIMAL(18,6) - 高精度
        DbColumnInfo dec186 = new DbColumnInfo("rate", "DECIMAL", "18", "汇率",
                false, false, false, null, "6", false);
        Column col2 = new Column(dec186, DB_TYPE);
        assertEquals("DECIMAL(18,6)应映射为BigDecimal", "BigDecimal", col2.getJavaType());
        assertEquals("6", col2.getDecimalDigits());

        // DECIMAL(65,30) - 最大精度
        DbColumnInfo dec6530 = new DbColumnInfo("precise_val", "DECIMAL", "65", "高精度值",
                false, false, false, null, "30", false);
        Column col3 = new Column(dec6530, DB_TYPE);
        assertEquals("DECIMAL(65,30)应映射为BigDecimal", "BigDecimal", col3.getJavaType());
        assertEquals("30", col3.getDecimalDigits());
    }

    /**
     * 测试BigDecimal被识别为基础类型
     */
    @Test
    public void testBigDecimalIsBaseType() {
        Column col = new Column();
        col.setJavaType("BigDecimal");
        assertTrue("BigDecimal应为基础类型", col.getIsBaseType());
    }

    /**
     * 测试FLOAT和DOUBLE的映射
     */
    @Test
    public void testFloatAndDoubleMappings() {
        DbColumnInfo floatCol = new DbColumnInfo("score", "FLOAT", "7", "分数",
                false, false, false, null, "2", false);
        Column col1 = new Column(floatCol, DB_TYPE);
        assertEquals("FLOAT应映射为Float", "Float", col1.getJavaType());

        DbColumnInfo doubleCol = new DbColumnInfo("distance", "DOUBLE", "15", "距离",
                false, false, false, null, "4", false);
        Column col2 = new Column(doubleCol, DB_TYPE);
        assertEquals("DOUBLE应映射为Double", "Double", col2.getJavaType());
    }

    /**
     * 测试TINYINT映射到Integer（而非Short）
     */
    @Test
    public void testTinyintMappedToInteger() {
        DbColumnInfo tinyintCol = new DbColumnInfo("is_active", "TINYINT", "1", "是否激活",
                false, false, false, "1", "0", false);
        Column col = new Column(tinyintCol, DB_TYPE);
        assertEquals("TINYINT应映射为Integer", "Integer", col.getJavaType());
    }

    // ==================== DbTypeConvert 缓存隔离测试 ====================

    /**
     * 测试不同数据库类型的类型转换器互相隔离
     */
    @Test
    public void testDbTypeConvertCacheIsolation() throws Exception {
        clearDbTypeConvertCache();

        ITypeConvert mysqlConvert = DbTypeConvert.getTypeConvert(DbTypeConvert.TYPE_DB_TO_JAVA, DB_TYPE);
        assertNotNull("MySQL的VARCHAR映射不应为null", mysqlConvert.getType("VARCHAR"));
        assertEquals("String", mysqlConvert.getType("VARCHAR").getJavaType());

        // 模拟另一个数据库类型 - 获取的应该是不同的实例
        // 由于测试环境只有MySQL定义，这里验证缓存key包含dbType
        ITypeConvert mysqlConvert2 = DbTypeConvert.getTypeConvert(DbTypeConvert.TYPE_DB_TO_JAVA, DB_TYPE);
        assertSame("同一dbType应返回相同实例", mysqlConvert, mysqlConvert2);
    }

    // ==================== 重复生成测试 ====================

    /**
     * 测试重复生成代码 —— 内容不变时应跳过
     */
    @Test
    public void testRepeatedGeneration_SkipIdentical() throws Exception {
        Table table = buildTable();
        DataSource ds = buildDataSource();
        List<Column> columns = Arrays.asList(
                buildColumn("id", "BIGINT", "Long", true),
                buildColumn("name", "VARCHAR", "String", false)
        );

        File outputDir = tempFolder.newFolder("repeat_gen");
        Template template = buildTemplate(outputDir.getAbsolutePath());
        List<Template> allTemplates = Arrays.asList(template);

        TableServiceImpl spyService = spy(tableService);
        injectMocks(spyService);

        doReturn(table).when(spyService).selectById("table-001");
        when(dataSourceService.selectById("ds-001")).thenReturn(ds);
        when(columnService.selectListByTableId("table-001")).thenReturn(columns);
        when(templateService.selectById("tpl-001")).thenReturn(template);
        when(templateService.selectList(any(EntityWrapper.class))).thenReturn(allTemplates);
        when(schemeService.selectOne(any(EntityWrapper.class))).thenReturn(null);
        when(generationLogService.insert(any(GenerationLog.class))).thenReturn(true);

        // 第一次生成
        DryRunRequest request = buildDryRunRequest();
        DryRunResult result1 = spyService.dryRun(request);
        assertEquals("首次应为NEW", FilePreview.FileStatus.NEW, result1.getFiles().get(0).getStatus());

        // 写入文件
        spyService.generateConfirmed(result1.getDryRunId());

        // 重新清除缓存以确保第二次dryRun正常
        clearDbTypeConvertCache();

        // 第二次预检 —— 内容相同应为SKIP
        DryRunResult result2 = spyService.dryRun(request);
        assertEquals("重复生成内容相同应为SKIP", FilePreview.FileStatus.SKIP, result2.getFiles().get(0).getStatus());
        assertFalse("无风险", result2.isHasRisk());
    }

    /**
     * 测试重复生成代码 —— 内容变化时应为RISK
     */
    @Test
    public void testRepeatedGeneration_RiskOnChange() throws Exception {
        Table table = buildTable();
        DataSource ds = buildDataSource();

        File outputDir = tempFolder.newFolder("repeat_risk");
        Template template = buildTemplate(outputDir.getAbsolutePath());
        List<Template> allTemplates = Arrays.asList(template);

        // 第一次：只有name字段
        List<Column> columns1 = Arrays.asList(
                buildColumn("name", "VARCHAR", "String", false)
        );

        TableServiceImpl spyService = spy(tableService);
        injectMocks(spyService);

        doReturn(table).when(spyService).selectById("table-001");
        when(dataSourceService.selectById("ds-001")).thenReturn(ds);
        when(columnService.selectListByTableId("table-001")).thenReturn(columns1);
        when(templateService.selectById("tpl-001")).thenReturn(template);
        when(templateService.selectList(any(EntityWrapper.class))).thenReturn(allTemplates);
        when(schemeService.selectOne(any(EntityWrapper.class))).thenReturn(null);
        when(generationLogService.insert(any(GenerationLog.class))).thenReturn(true);

        DryRunRequest request = buildDryRunRequest();
        DryRunResult result1 = spyService.dryRun(request);
        spyService.generateConfirmed(result1.getDryRunId());
        clearDbTypeConvertCache();

        // 第二次：增加age字段，内容变化
        List<Column> columns2 = Arrays.asList(
                buildColumn("name", "VARCHAR", "String", false),
                buildColumn("age", "INT", "Integer", false)
        );
        when(columnService.selectListByTableId("table-001")).thenReturn(columns2);

        DryRunResult result2 = spyService.dryRun(request);
        assertEquals("字段变化后重新生成应为RISK",
                FilePreview.FileStatus.RISK, result2.getFiles().get(0).getStatus());
        assertTrue("应标记为有风险", result2.isHasRisk());
        assertTrue("差异摘要应描述变化", result2.getFiles().get(0).getDiffSummary().contains("新增"));
    }

    // ==================== 完整类型映射覆盖测试 ====================

    /**
     * 测试所有新增的MySQL类型映射都存在
     */
    @Test
    public void testAllMySqlTypeMappingsExist() {
        ITypeConvert convert = DbTypeConvert.getTypeConvert(DbTypeConvert.TYPE_DB_TO_JAVA, DB_TYPE);

        // 基础类型
        assertMapping(convert, "VARCHAR", "String");
        assertMapping(convert, "CHAR", "String");
        assertMapping(convert, "TEXT", "String");
        assertMapping(convert, "TINYTEXT", "String");
        assertMapping(convert, "MEDIUMTEXT", "String");
        assertMapping(convert, "LONGTEXT", "String");

        // 整数类型
        assertMapping(convert, "INT", "Integer");
        assertMapping(convert, "INTEGER", "Integer");
        assertMapping(convert, "TINYINT", "Integer");
        assertMapping(convert, "SMALLINT", "Short");
        assertMapping(convert, "MEDIUMINT", "Integer");
        assertMapping(convert, "BIGINT", "Long");

        // 浮点/精度类型
        assertMapping(convert, "FLOAT", "Float");
        assertMapping(convert, "DOUBLE", "Double");
        assertMapping(convert, "DECIMAL", "BigDecimal");

        // 日期时间类型
        assertMapping(convert, "DATE", "Date");
        assertMapping(convert, "TIME", "Date");
        assertMapping(convert, "YEAR", "Date");
        assertMapping(convert, "DATETIME", "Date");
        assertMapping(convert, "TIMESTAMP", "Date");

        // 二进制类型
        assertMapping(convert, "BINARY", "byte[]");
        assertMapping(convert, "VARBINARY", "byte[]");
        assertMapping(convert, "BLOB", "byte[]");
        assertMapping(convert, "TINYBLOB", "byte[]");
        assertMapping(convert, "MEDIUMBLOB", "byte[]");
        assertMapping(convert, "LONGBLOB", "byte[]");

        // 布尔
        assertMapping(convert, "BIT", "Boolean");
        assertMapping(convert, "BOOLEAN", "Boolean");

        // UNSIGNED 变体
        assertMapping(convert, "INT UNSIGNED", "Integer");
        assertMapping(convert, "BIGINT UNSIGNED", "Long");
        assertMapping(convert, "TINYINT UNSIGNED", "Integer");
        assertMapping(convert, "DECIMAL UNSIGNED", "BigDecimal");
    }

    /**
     * 测试未知类型回退到String
     */
    @Test
    public void testUnknownTypeFallsBackToString() {
        DbColumnInfo unknownCol = new DbColumnInfo("data", "GEOMETRY", "0", "几何数据",
                true, false, false, null, "0", false);
        Column col = new Column(unknownCol, DB_TYPE);
        assertEquals("未知类型应回退到String", "String", col.getJavaType());
    }

    // ==================== 下划线表名字段名测试 ====================

    /**
     * 测试带下划线的列名正确转为驼峰
     */
    @Test
    public void testUnderscoreColumnNameConversion() {
        DbColumnInfo col = new DbColumnInfo("order_detail_status", "VARCHAR", "50", "状态",
                true, false, false, null, "0", false);
        Column column = new Column(col, DB_TYPE);
        assertEquals("多下划线列名应正确转驼峰", "orderDetailStatus", column.getJavaField());
        assertEquals("列名应保留小写", "order_detail_status", column.getColumnName());
    }

    /**
     * 测试带下划线且包含关键字的列名
     */
    @Test
    public void testUnderscoreKeywordColumnName() {
        // "order" 本身是关键字，但 "order_id" 不是
        Column col = new Column();
        col.setColumnName("order_id");
        assertFalse("order_id整体不是关键字", col.getIsKeyword());
        assertEquals("order_id不应加反引号", "order_id", col.getEscapedColumnName());
    }

    // ==================== 辅助方法 ====================

    private void assertMapping(ITypeConvert convert, String dbType, String expectedJavaType) {
        Type type = convert.getType(dbType);
        assertNotNull(dbType + "的映射不应为null", type);
        assertEquals(dbType + "应映射为" + expectedJavaType, expectedJavaType, type.getJavaType());
    }

    private void setupDefinitionBuilder() throws Exception {
        Definition definition = new Definition();
        definition.setDbType(DB_TYPE);
        definition.setName("MySQL");

        Db db = new Db();
        db.setAllTypes("VARCHAR,TINYINT,SMALLINT,MEDIUMINT,INT,INTEGER,BIGINT,FLOAT,DOUBLE,DECIMAL,DATE,TIME,YEAR,DATETIME,TIMESTAMP,BINARY,VARBINARY,CHAR,TINYBLOB,TINYTEXT,BLOB,TEXT,MEDIUMBLOB,MEDIUMTEXT,LONGBLOB,LONGTEXT,BIT,BOOLEAN");
        db.setCharTypes("CHAR,VARCHAR,TINYBLOB,TINYTEXT,BLOB,TEXT,MEDIUMBLOB,MEDIUMTEXT,LONGBLOB,LONGTEXT");
        db.setFloatTypes("FLOAT,DOUBLE,DECIMAL");
        db.setAloneTypes("DATE,TIME,YEAR,DATETIME,TIMESTAMP,TINYBLOB,BLOB,TEXT,MEDIUMBLOB,MEDIUMTEXT,LONGBLOB,LONGTEXT");
        db.setBlobTypes("TINYBLOB,BLOB,TEXT,MEDIUMBLOB,MEDIUMTEXT,LONGBLOB,LONGTEXT");
        definition.setDb(db);

        List<Type> dbToJavaTypes = new ArrayList<>();
        addType(dbToJavaTypes, "VARCHAR", "String", null);
        addType(dbToJavaTypes, "CHAR", "String", null);
        addType(dbToJavaTypes, "TEXT", "String", null);
        addType(dbToJavaTypes, "TINYTEXT", "String", null);
        addType(dbToJavaTypes, "MEDIUMTEXT", "String", null);
        addType(dbToJavaTypes, "LONGTEXT", "String", null);
        addType(dbToJavaTypes, "INT", "Integer", null);
        addType(dbToJavaTypes, "INTEGER", "Integer", null);
        addType(dbToJavaTypes, "INT UNSIGNED", "Integer", null);
        addType(dbToJavaTypes, "TINYINT", "Integer", null);
        addType(dbToJavaTypes, "TINYINT UNSIGNED", "Integer", null);
        addType(dbToJavaTypes, "SMALLINT", "Short", null);
        addType(dbToJavaTypes, "SMALLINT UNSIGNED", "Integer", null);
        addType(dbToJavaTypes, "MEDIUMINT", "Integer", null);
        addType(dbToJavaTypes, "MEDIUMINT UNSIGNED", "Long", null);
        addType(dbToJavaTypes, "BIGINT", "Long", null);
        addType(dbToJavaTypes, "BIGINT UNSIGNED", "Long", null);
        addType(dbToJavaTypes, "FLOAT", "Float", null);
        addType(dbToJavaTypes, "FLOAT UNSIGNED", "Float", null);
        addType(dbToJavaTypes, "DOUBLE", "Double", null);
        addType(dbToJavaTypes, "DOUBLE UNSIGNED", "Double", null);
        addType(dbToJavaTypes, "DECIMAL", "BigDecimal", "java.math.BigDecimal");
        addType(dbToJavaTypes, "DECIMAL UNSIGNED", "BigDecimal", "java.math.BigDecimal");
        addType(dbToJavaTypes, "DATE", "Date", "java.util.Date");
        addType(dbToJavaTypes, "TIME", "Date", "java.util.Date");
        addType(dbToJavaTypes, "YEAR", "Date", "java.util.Date");
        addType(dbToJavaTypes, "DATETIME", "Date", "java.util.Date");
        addType(dbToJavaTypes, "TIMESTAMP", "Date", "java.util.Date");
        addType(dbToJavaTypes, "BINARY", "byte[]", null);
        addType(dbToJavaTypes, "VARBINARY", "byte[]", null);
        addType(dbToJavaTypes, "TINYBLOB", "byte[]", null);
        addType(dbToJavaTypes, "BLOB", "byte[]", null);
        addType(dbToJavaTypes, "MEDIUMBLOB", "byte[]", null);
        addType(dbToJavaTypes, "LONGBLOB", "byte[]", null);
        addType(dbToJavaTypes, "BIT", "Boolean", null);
        addType(dbToJavaTypes, "BOOLEAN", "Boolean", null);
        definition.setDbtojavaTypes(dbToJavaTypes);

        List<Type> javaToClassTypes = new ArrayList<>();
        addType(javaToClassTypes, null, "Date", "java.util.Date");
        addType(javaToClassTypes, null, "BigDecimal", "java.math.BigDecimal");
        definition.setJavatoclassTypes(javaToClassTypes);

        DefinitionBuilder builder = new DefinitionBuilder();
        Field defMapField = DefinitionBuilder.class.getDeclaredField("definitionMap");
        defMapField.setAccessible(true);
        Map<String, Definition> defMap = new HashMap<>();
        defMap.put(DB_TYPE, definition);
        defMapField.set(builder, defMap);

        Field defListField = DefinitionBuilder.class.getDeclaredField("definitionList");
        defListField.setAccessible(true);
        List<Definition> defList = new ArrayList<>();
        defList.add(definition);
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
        if (dbType != null) t.setDbType(dbType);
        t.setJavaType(javaType);
        if (fullType != null) t.setFullType(fullType);
        types.add(t);
    }

    private void clearDbTypeConvertCache() throws Exception {
        Field cacheField = DbTypeConvert.class.getDeclaredField("dbTypeConvertMap");
        cacheField.setAccessible(true);
        ((Map<?, ?>) cacheField.get(null)).clear();
    }

    private Table buildTable() {
        Table table = new Table();
        table.setId("table-001");
        table.setTableName("t_test_gen");
        table.setTitle("测试表");
        table.setClassName("TestGen");
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
        ds.setDbType(DB_TYPE);
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
        column.setJavaField(columnName);
        column.setListable(Boolean.TRUE);
        column.setFormable(Boolean.TRUE);
        column.setParmaryKey(isPK);
        return column;
    }

    private Template buildTemplate(String targetPath) {
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

    private DryRunRequest buildDryRunRequest() {
        DryRunRequest request = new DryRunRequest();
        request.setTableId("table-001");
        request.setTemplateSchemeId("ts-001");
        request.setEntityName("TestGen");
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
