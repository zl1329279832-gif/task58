package com.company.generator.manager.service.impl;

import com.company.generator.manager.common.exception.GenerationException;
import com.company.generator.manager.dto.*;
import com.company.generator.manager.entity.*;
import com.company.generator.manager.mapper.PreCheckRecordMapper;
import com.company.generator.manager.service.*;
import com.company.manerger.sys.common.mybatis.wrapper.EntityWrapper;
import freemarker.template.TemplateException;
import org.apache.commons.io.FileUtils;
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
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class PreCheckServiceImplTest {

    @InjectMocks
    private PreCheckServiceImpl preCheckService;

    @Mock
    private ISchemeService schemeService;
    @Mock
    private ITableService tableService;
    @Mock
    private IColumnService columnService;
    @Mock
    private ITemplateService templateService;
    @Mock
    private ITemplateSchemeService templateSchemeService;
    @Mock
    private IDataSourceService dataSourceService;
    @Mock
    private PreCheckRecordMapper preCheckRecordMapper;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private Scheme scheme;
    private Table table;
    private DataSource dataSource;
    private TemplateScheme templateScheme;
    private Template template;
    private Column column;
    private PreCheckRequest request;

    @Before
    public void setUp() {
        // 构建基本测试数据
        table = new Table();
        table.setId("table-001");
        table.setSourceId("ds-001");
        table.setTableName("t_user");
        table.setSyncDatabase(true);

        scheme = new Scheme();
        scheme.setId("scheme-001");
        scheme.setTable(table);
        scheme.setEntityName("User");
        scheme.setTableName("t_user");
        scheme.setModuleName("system");
        scheme.setFunctionAuthor("test");
        scheme.setFunctionDesc("用户管理");
        scheme.setFunctionName("user");
        scheme.setTemplateSchemeId("ts-001");

        dataSource = new DataSource();
        dataSource.setId("ds-001");
        dataSource.setDbType("mysql");

        templateScheme = new TemplateScheme();
        templateScheme.setId("ts-001");
        templateScheme.setTitle("默认方案");

        template = new Template();
        template.setId("tpl-001");
        template.setName("Entity模板");
        template.setKey("entity");
        template.setTemplateContent("package ${targetPackage};\npublic class ${entityName} {\n}");
        template.setSchemeId("ts-001");
        template.setNameFormat("[entityName].java");
        template.setEnablePackage("1");
        template.setTargetPackage("com.example.[moduleName].entity");
        template.setTargetPath(tempFolder.getRoot().getAbsolutePath());
        template.setSort(1);

        column = new Column();
        column.setId("col-001");
        column.setColumnName("username");
        column.setTypeName("VARCHAR");
        column.setJavaType("String");
        column.setJavaField("username");

        // 构建请求
        request = new PreCheckRequest();
        request.setSchemeId("scheme-001");
        request.setTableId("table-001");
        request.setTemplateSchemeId("ts-001");

        PreCheckRequest.TemplateTarget target = new PreCheckRequest.TemplateTarget();
        target.setTemplateId("tpl-001");
        target.setTargetPath(tempFolder.getRoot().getAbsolutePath());
        target.setTargetPackage("com.example.system.entity");
        request.setSelectedTemplates(Collections.singletonList(target));
    }

    private void setupCommonMocks() {
        when(schemeService.selectById("scheme-001")).thenReturn(scheme);
        when(tableService.selectById("table-001")).thenReturn(table);
        when(templateSchemeService.selectById("ts-001")).thenReturn(templateScheme);
        when(dataSourceService.selectById("ds-001")).thenReturn(dataSource);
        when(columnService.selectListByTableId("table-001")).thenReturn(Collections.singletonList(column));
        when(templateService.selectList(any(EntityWrapper.class))).thenReturn(Collections.singletonList(template));
        when(preCheckRecordMapper.insert(any(PreCheckRecord.class))).thenReturn(1);
    }

    // ===== 测试1: 字段类型映射缺失 =====
    @Test
    public void testPreCheck_columnWithMissingTypeMapping() throws Exception {
        setupCommonMocks();

        // 设置一个未知数据库类型的字段
        Column unknownColumn = new Column();
        unknownColumn.setId("col-002");
        unknownColumn.setColumnName("status");
        unknownColumn.setTypeName("UNSUPPORTED_CUSTOM_TYPE");
        unknownColumn.setJavaType("String");
        unknownColumn.setJavaField("status");

        when(columnService.selectListByTableId("table-001")).thenReturn(Arrays.asList(column, unknownColumn));

        File targetFile = new File(tempFolder.getRoot(), "com/example/system/entity/User.java");
        when(tableService.resolveOutPath(any(Scheme.class), any(Template.class))).thenReturn(targetFile);
        when(tableService.getFtlMap(any(), any(), anyList())).thenReturn(new HashMap<>());
        when(tableService.parseTemplate(anyMap(), anyString())).thenReturn("generated content");

        PreCheckResult result = preCheckService.preCheck(request);

        assertNotNull(result);
        assertNotNull(result.getPreCheckId());
        // globalWarnings应包含类型映射相关的警告
        assertFalse("应有全局警告信息", result.getGlobalWarnings().isEmpty());
    }

    // ===== 测试2: 模板变量缺失 =====
    @Test
    public void testPreCheck_templateVariableMissing() throws Exception {
        setupCommonMocks();

        File targetFile = new File(tempFolder.getRoot(), "User.java");
        when(tableService.resolveOutPath(any(Scheme.class), any(Template.class))).thenReturn(targetFile);
        when(tableService.getFtlMap(any(), any(), anyList())).thenReturn(new HashMap<>());
        when(tableService.parseTemplate(anyMap(), anyString()))
                .thenThrow(new TemplateException("Undefined variable: undefinedVar", null));

        PreCheckResult result = preCheckService.preCheck(request);

        assertNotNull(result);
        assertEquals(1, result.getEntries().size());

        FilePreCheckEntry entry = result.getEntries().get(0);
        assertEquals(FileAction.RISK, entry.getAction());
        assertFalse("应包含模板变量缺失的风险信息", entry.getRisks().isEmpty());
        assertTrue(entry.getRisks().get(0).contains("模板变量缺失或渲染失败"));
        assertTrue(result.isHasRisks());
    }

    // ===== 测试3: 目标文件已存在 - 覆盖 =====
    @Test
    public void testPreCheck_existingFile_overwrite() throws Exception {
        setupCommonMocks();

        // 创建一个已存在的文件，内容不同
        File existingFile = tempFolder.newFile("User.java");
        FileUtils.write(existingFile, "old content here", "UTF-8");

        when(tableService.resolveOutPath(any(Scheme.class), any(Template.class))).thenReturn(existingFile);
        when(tableService.getFtlMap(any(), any(), anyList())).thenReturn(new HashMap<>());
        when(tableService.parseTemplate(anyMap(), anyString())).thenReturn("new generated content");

        PreCheckResult result = preCheckService.preCheck(request);

        assertEquals(1, result.getEntries().size());
        FilePreCheckEntry entry = result.getEntries().get(0);
        assertEquals(FileAction.OVERWRITE, entry.getAction());
        assertNotNull("覆盖时应有差异摘要", entry.getDiffSummary());
        assertNotNull("覆盖时应有已存在内容预览", entry.getExistingContentPreview());
        assertTrue(entry.getExistingFileSize() > 0);
        assertTrue(entry.getExistingFileLastModified() > 0);
        assertTrue(result.isHasOverwrites());
    }

    // ===== 测试4: 目标文件已存在 - 内容相同则跳过 =====
    @Test
    public void testPreCheck_existingFile_skip() throws Exception {
        setupCommonMocks();

        String sameContent = "package com.example;\npublic class User {}";
        File existingFile = tempFolder.newFile("User.java");
        FileUtils.write(existingFile, sameContent, "UTF-8");

        when(tableService.resolveOutPath(any(Scheme.class), any(Template.class))).thenReturn(existingFile);
        when(tableService.getFtlMap(any(), any(), anyList())).thenReturn(new HashMap<>());
        when(tableService.parseTemplate(anyMap(), anyString())).thenReturn(sameContent);

        PreCheckResult result = preCheckService.preCheck(request);

        assertEquals(1, result.getEntries().size());
        FilePreCheckEntry entry = result.getEntries().get(0);
        assertEquals(FileAction.SKIP, entry.getAction());
        assertNull("跳过时不应有差异摘要", entry.getDiffSummary());
    }

    // ===== 测试5: 目标文件不存在 - 新增 =====
    @Test
    public void testPreCheck_newFile_add() throws Exception {
        setupCommonMocks();

        File nonExistentFile = new File(tempFolder.getRoot(), "nonexistent/User.java");

        when(tableService.resolveOutPath(any(Scheme.class), any(Template.class))).thenReturn(nonExistentFile);
        when(tableService.getFtlMap(any(), any(), anyList())).thenReturn(new HashMap<>());
        when(tableService.parseTemplate(anyMap(), anyString())).thenReturn("generated content");

        PreCheckResult result = preCheckService.preCheck(request);

        assertEquals(1, result.getEntries().size());
        FilePreCheckEntry entry = result.getEntries().get(0);
        assertEquals(FileAction.ADD, entry.getAction());
        assertEquals(-1, entry.getExistingFileSize());
        assertEquals(-1, entry.getExistingFileLastModified());
        assertFalse(result.isHasOverwrites());
    }

    // ===== 测试6: dry-run不写文件 =====
    @Test
    public void testDryRun_doesNotWriteFiles() throws Exception {
        setupCommonMocks();

        File targetFile = new File(tempFolder.getRoot(), "output/com/example/User.java");
        assertFalse("执行前文件不应存在", targetFile.exists());

        when(tableService.resolveOutPath(any(Scheme.class), any(Template.class))).thenReturn(targetFile);
        when(tableService.getFtlMap(any(), any(), anyList())).thenReturn(new HashMap<>());
        when(tableService.parseTemplate(anyMap(), anyString())).thenReturn("generated content");

        preCheckService.preCheck(request);

        assertFalse("dry-run后文件不应被创建", targetFile.exists());
        assertFalse("dry-run后父目录不应被创建", targetFile.getParentFile().exists());
    }

    // ===== 测试7: 确认生成写入已批准的文件 =====
    @Test
    public void testConfirmedGenerate_writesApprovedFiles() throws Exception {
        setupCommonMocks();

        File targetFile = new File(tempFolder.getRoot(), "output/User.java");
        String generatedContent = "package com.example;\npublic class User {}";

        when(tableService.resolveOutPath(any(Scheme.class), any(Template.class))).thenReturn(targetFile);
        when(tableService.getFtlMap(any(), any(), anyList())).thenReturn(new HashMap<>());
        when(tableService.parseTemplate(anyMap(), anyString())).thenReturn(generatedContent);

        // 先执行预检
        PreCheckResult result = preCheckService.preCheck(request);
        String preCheckId = result.getPreCheckId();

        // 确认生成
        preCheckService.confirmedGenerate(preCheckId, Collections.singletonList("tpl-001"));

        assertTrue("确认生成后文件应存在", targetFile.exists());
        String writtenContent = FileUtils.readFileToString(targetFile, "UTF-8");
        assertEquals("写入的内容应与生成的内容一致", generatedContent, writtenContent);
    }

    // ===== 测试8: 确认生成跳过未批准的模板 =====
    @Test
    public void testConfirmedGenerate_skipsUnapproved() throws Exception {
        setupCommonMocks();

        // 设置两个模板
        Template template2 = new Template();
        template2.setId("tpl-002");
        template2.setName("Service模板");
        template2.setKey("service");
        template2.setTemplateContent("package ${targetPackage};\npublic interface ${entityName}Service {}");
        template2.setSchemeId("ts-001");
        template2.setNameFormat("[entityName]Service.java");
        template2.setEnablePackage("1");
        template2.setTargetPackage("com.example.[moduleName].service");
        template2.setTargetPath(tempFolder.getRoot().getAbsolutePath());
        template2.setSort(2);

        when(templateService.selectList(any(EntityWrapper.class))).thenReturn(Arrays.asList(template, template2));

        PreCheckRequest.TemplateTarget target2 = new PreCheckRequest.TemplateTarget();
        target2.setTemplateId("tpl-002");
        target2.setTargetPath(tempFolder.getRoot().getAbsolutePath());
        target2.setTargetPackage("com.example.system.service");

        request.setSelectedTemplates(Arrays.asList(
                request.getSelectedTemplates().get(0), target2));

        File file1 = new File(tempFolder.getRoot(), "output/User.java");
        File file2 = new File(tempFolder.getRoot(), "output/UserService.java");

        when(tableService.resolveOutPath(any(Scheme.class), eq(template))).thenReturn(file1);
        when(tableService.resolveOutPath(any(Scheme.class), eq(template2))).thenReturn(file2);
        when(tableService.getFtlMap(any(), any(), anyList())).thenReturn(new HashMap<>());
        when(tableService.parseTemplate(anyMap(), anyString())).thenReturn("generated content");

        // 执行预检
        PreCheckResult result = preCheckService.preCheck(request);

        // 只批准第一个模板
        preCheckService.confirmedGenerate(result.getPreCheckId(), Collections.singletonList("tpl-001"));

        assertTrue("已批准的模板文件应存在", file1.exists());
        assertFalse("未批准的模板文件不应存在", file2.exists());
    }

    // ===== 测试9: 预检结果过期 =====
    @Test(expected = GenerationException.class)
    public void testConfirmedGenerate_expiredPreCheckId() throws Exception {
        preCheckService.confirmedGenerate("nonexistent-id", Collections.singletonList("tpl-001"));
    }
}
