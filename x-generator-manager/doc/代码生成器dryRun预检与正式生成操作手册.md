# 代码生成器 dryRun 预检与正式生成操作手册

> 适用模块：`x-generator-manager`
> 涉及源码版本：当前仓库 `v3.0-master` 分支

---

## 1. 概述

代码生成器采用 **两阶段提交** 模式保障生成安全：先通过 `dryRun`（预检）模拟完整的代码生成过程但不写入磁盘，用户确认预检结果后再调用 `generateConfirmed`（正式生成）将文件落盘。这一机制可以有效避免在不了解影响范围的情况下直接覆盖目标目录中的已有文件。

### REST 入口

| 端点 | 方法 | 说明 |
|------|------|------|
| `/table/dryRun` | POST | 接收 `DryRunRequest` JSON，返回预检结果 |
| `/table/generateConfirmed` | POST | 接收 `dryRunId` 参数，执行正式生成 |

源码位置：`x-generator-manager/src/main/java/com/company/generator/manager/controller/TableController.java`（第 355-393 行）

---

## 2. dryRun → generateConfirmed 状态机

整个生成操作的生命周期如下：

```
                          ┌─────────────────────────────────────────┐
                          │            用户发起预检请求              │
                          │      POST /table/dryRun                │
                          │      Body: DryRunRequest               │
                          └──────────────┬──────────────────────────┘
                                         │
                                         ▼
                          ┌─────────────────────────────────────────┐
                          │         Phase 1: dryRun()               │
                          │                                         │
                          │  1. 加载表、列、数据源                    │
                          │  2. detectMissingFieldTypes 检测类型缺失  │
                          │  3. 构建/复用 Scheme                     │
                          │  4. 加载选中模板、应用路径/包名覆盖        │
                          │  5. detectMissingTemplateVars 检测变量缺失│
                          │  6. parseTemplate 渲染模板（不落盘）      │
                          │  7. 比对目标文件，标记 FileStatus         │
                          │  8. 生成 dryRunId（UUID），缓存结果       │
                          │  9. 写入 GenerationLog (DRY_RUN)        │
                          └──────────────┬──────────────────────────┘
                                         │
                                         ▼
                          ┌─────────────────────────────────────────┐
                          │       返回 DryRunResult 给调用方         │
                          │  包含: dryRunId, files[], missingField-  │
                          │  Types[], missingTemplateVars[], summary │
                          └──────────────┬──────────────────────────┘
                                         │
                            用户审查预检结果，决定是否继续
                                         │
                          ┌──────────────┴──────────────────────────┐
                          │                                         │
                     [确认生成]                               [放弃/超时]
                          │                                         │
                          ▼                                         ▼
           ┌──────────────────────────┐              缓存自然淘汰或服务重启
           │ Phase 2: generateConfirmed│              后 dryRunId 失效，
           │                          │              需重新执行 dryRun()
           │ POST /table/generate-    │
           │ Confirmed?dryRunId=xxx   │
           │                          │
           │ 1. 从缓存取 DryRunResult  │
           │ 2. 遍历 files:           │
           │    - SKIP → 跳过         │
           │    - NEW/RISK → 写入磁盘  │
           │ 3. 写入 GenerationLog    │
           │    (GENERATE)            │
           │ 4. 清除缓存               │
           └──────────────────────────┘
```

### 状态流转规则

| 当前状态 | 触发动作 | 下一状态 | 备注 |
|---------|---------|---------|------|
| (无) | 调用 `POST /table/dryRun` | 预检完成，结果已缓存 | 记录 `DRY_RUN` 日志 |
| 预检完成 | 调用 `POST /table/generateConfirmed` | 生成完成，缓存已清除 | 记录 `GENERATE` 日志 |
| 预检完成 | 缓存被淘汰/服务重启 | 过期失效 | 需重新 dryRun |
| 过期失效 | 调用 `generateConfirmed` | 抛出异常 | 提示"请重新执行预检" |

核心实现位于：`x-generator-manager/src/main/java/com/company/generator/manager/service/impl/TableServiceImpl.java`
- `dryRun()` 方法：第 352-518 行
- `generateConfirmed()` 方法：第 520-571 行

---

## 3. dryRunId 过期与重跑规则

### dryRunId 生成方式

```java
String dryRunId = UUID.randomUUID().toString().replace("-", "");
// 示例: "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6"
```

32 位十六进制字符串，基于 `java.util.UUID.randomUUID()`，去除连字符。每次调用 `dryRun()` 都会生成全新的 ID，不会复用。

### 缓存策略

预检结果以 `"dryrun_" + dryRunId` 为 key 存入 EhCache（通过 `CacheUtils.put()`）。

缓存配置文件：`x-generator-manager/src/main/resources/ehcache/ehcache.xml`

当前没有为 `dryrun_*` 配置专用的 `<cache>` 区域，因此使用 `<defaultCache>` 配置：

| 参数 | 值 | 含义 |
|------|-----|------|
| `eternal` | `true` | 条目不按时间过期 |
| `maxElementsInMemory` | `10000` | 内存中最大条目数 |
| `memoryStoreEvictionPolicy` | `LFU` | 达到上限时按最不常用策略淘汰 |
| `overflowToDisk` | `true` | 超出内存上限时溢出到磁盘 |
| `diskPersistent` | `false` | 磁盘缓存不持久化，服务重启后丢失 |

### dryRunId 何时失效

| 场景 | 原因 | 结果 |
|------|------|------|
| 正式生成完成后 | `CacheUtils.remove("dryrun_" + dryRunId)` 主动清除 | 同一 dryRunId 不可二次使用 |
| 服务重启 | `diskPersistent=false`，缓存不持久化 | 所有未确认的 dryRunId 全部丢失 |
| 内存淘汰 | 缓存条目总数超过 10000 时 LFU 淘汰 | 低频访问的预检结果被回收 |

### 失效后的表现

调用 `generateConfirmed(dryRunId)` 时，若缓存中找不到对应条目，将抛出：

```
GenerationException("预检结果不存在或已过期，请重新执行预检: " + dryRunId)
```

### 重跑规则

- **不支持用旧 dryRunId 重跑**。过期后必须重新调用 `dryRun()` 获取新的 dryRunId。
- 每次 `dryRun()` 都是完整的独立预检，基于当时的表结构、模板内容和目标目录状态进行全量模拟。
- 同一张表可以并发执行多次 `dryRun()`，各自拥有独立的 dryRunId 和缓存条目。

---

## 4. DryRunResult 各失败类型说明

### 4.1 DryRunResult 顶层结构

源码：`x-generator-manager/src/main/java/com/company/generator/manager/entity/DryRunResult.java`

| 字段 | 类型 | 说明 |
|------|------|------|
| `dryRunId` | `String` | 预检 ID，用于后续调用 `generateConfirmed` |
| `files` | `List<FilePreview>` | 每个模板文件的预检结果列表 |
| `missingFieldTypes` | `List<String>` | 缺失的字段类型映射列表 |
| `missingTemplateVars` | `List<String>` | 缺失的模板变量列表 |
| `summary` | `String` | 人类可读的摘要字符串 |
| `hasRisk` | `boolean` | 是否存在风险（有 OVERWRITE 或 RISK 状态的文件） |
| `createdAt` | `Date` | 预检执行时间 |

摘要格式示例：
```
共 5 个文件：新增 3，覆盖 0，跳过 1，风险 1；缺失类型映射 1 项；缺失模板变量 2 项
```

### 4.2 失败类型一：缺失字段类型映射（missingFieldTypes）

**含义**：数据库表中某些列的数据类型在类型映射 XML（如 `mysql-definition.xml`）中没有对应的 Java 类型定义。

**检测逻辑**（`TableServiceImpl.detectMissingFieldTypes()`，第 602-618 行）：
1. 遍历表的所有列，取每列的 `typeName`
2. 分别以 `toUpperCase()` 和 `toLowerCase()` 在 `DbTypeConvert` 中查找
3. 两次查找均未命中的类型名加入 `missingFieldTypes`

**典型场景**：
- 使用了 MySQL 的 `GEOMETRY`、`JSON`、`ENUM`、`SET` 等在 `mysql-definition.xml` 中未配置映射的类型
- 连接了 PostgreSQL 等其他数据库但使用了 MySQL 的类型定义文件

**影响**：缺失映射的列将在 `Column` 构造时回退为 `String` 类型（`Column.java` 第 188 行），生成的 Entity 中对应字段类型为 `String`，可能不符合业务预期。

**处理方式**：在对应的 `*-definition.xml` 的 `<dbtojava-types>` 中补充映射条目（详见第 5 节）。

### 4.3 失败类型二：缺失模板变量（missingTemplateVars）

**含义**：FreeMarker 模板中通过 `${varName}` 引用的变量在数据模型中不存在。

**检测逻辑**（`TableServiceImpl.detectMissingTemplateVars()`，第 623-637 行）：
1. 对模板内容进行 HTML 反转义
2. 用正则 `\$\{([a-zA-Z_][a-zA-Z0-9_]*)` 提取所有顶层变量名
3. 在数据模型 `Map<String, Object>` 中查找，不存在的加入 `missingTemplateVars`

**注意**：检测仅覆盖顶层变量（如 `${entityName}`），不覆盖嵌套属性（如 `${table.tableName}`）和 FreeMarker 指令内的变量（如 `<#list>`）。

**典型场景**：
- 模板中使用了自定义变量但未在 Scheme/Template 的数据模型中配置
- 模板从其他项目复制过来，引用了本项目不存在的上下文变量

**影响**：FreeMarker 渲染时可能抛出 `TemplateException`，导致该模板的预检/生成失败。

### 4.4 失败类型三：目标文件已存在（FileStatus）

源码：`x-generator-manager/src/main/java/com/company/generator/manager/entity/FilePreview.java`

每个模板的预检结果以 `FilePreview` 描述，其 `status` 字段标识文件冲突级别：

| FileStatus | 含义 | 触发条件 | generateConfirmed 行为 |
|------------|------|---------|----------------------|
| `NEW` | 新文件 | 目标路径不存在 | 创建文件并写入 |
| `SKIP` | 跳过 | 目标文件存在且内容完全一致 | 不做任何操作 |
| `RISK` | 风险 | 目标文件存在且内容不同 | **删除旧文件后写入新内容** |
| `OVERWRITE` | 覆盖（保留） | 当前代码路径未使用，枚举值已定义 | 同 RISK |

**重点关注 `RISK` 状态**：这表示目标文件已被手动修改或从上次生成后发生了变化。`generateConfirmed` 会**直接覆盖**该文件。用户应在确认生成前仔细审查 `diffSummary` 字段中的差异摘要（格式：`"新增 X 行，删除 Y 行（原文件 M 行，新文件 N 行）"`）。

当 `DryRunResult.hasRisk == true` 时，前端/调用方应向用户显示警告并要求二次确认。

---

## 5. DbTypeConvert 与 mysql-definition.xml 映射规则

### 5.1 架构概览

```
WebConfig.java                         DefinitionBuilder                  DbTypeConvert
    │                                       │                                  │
    │ @Bean: 设置 fileNames 为               │ Spring 启动时加载                  │
    │ "classpath*:/config/definition/       │ *-definition.xml 文件             │
    │  *-definition.xml"                    │ 并解析为 Definition 对象           │
    │                                       │ 缓存到 definitionMap              │
    └───────────────────────────────────────┘                                  │
                                                                               │
                                DefinitionUtils.getDefinition(dbType) ────────►│
                                                                               │
                                DbTypeConvert 按 dbType 从 Definition          │
                                中提取 <dbtojava-types> 构建 HashMap            │
                                                                               ▼
                                              typeMap: { "VARCHAR" -> Type{String}, ... }
```

### 5.2 Bean 注册

`WebConfig.java`（第 47-52 行）：

```java
@Bean
public DefinitionBuilder definitionBuilder(){
    DefinitionBuilder definitionBuilder = new DefinitionBuilder();
    definitionBuilder.setFileNames(new String[]{
        "classpath*:/config/definition/*-definition.xml"
    });
    return definitionBuilder;
}
```

Spring 容器启动后，`DefinitionBuilder` 监听 `ContextRefreshedEvent`，扫描匹配 `*-definition.xml` 模式的所有资源文件，通过 JAXB 反序列化为 `Definition` 对象，以 `<db-type>` 值为 key 存入 `definitionMap`。

### 5.3 mysql-definition.xml 结构

文件路径：`x-generator-manager/src/main/resources/config/definition/mysql-definition.xml`

#### 基本信息

```xml
<name>MySql</name>
<db-type>MySql</db-type>             <!-- 唯一标识，DbTypeConvert 按此 key 查找 -->
<db-driver>com.mysql.jdbc.Driver</db-driver>
```

#### DB-to-Java 类型映射表（`<dbtojava-types>`）

当前已配置 29 条映射：

| DB 类型 | Java 类型 | 全限定类名 |
|---------|-----------|-----------|
| `VARCHAR` | `String` | - |
| `CHAR` | `String` | - |
| `TEXT` | `String` | - |
| `TINYTEXT` | `String` | - |
| `MEDIUMTEXT` | `String` | - |
| `LONGTEXT` | `String` | - |
| `INT` | `Integer` | - |
| `INTEGER` | `Integer` | - |
| `INT UNSIGNED` | `Integer` | - |
| `TINYINT` | `Integer` | - |
| `TINYINT UNSIGNED` | `Integer` | - |
| `SMALLINT` | `Short` | - |
| `SMALLINT UNSIGNED` | `Integer` | - |
| `MEDIUMINT` | `Integer` | - |
| `MEDIUMINT UNSIGNED` | `Long` | - |
| `BIGINT` | `Long` | - |
| `BIGINT UNSIGNED` | `Long` | - |
| `FLOAT` | `Float` | - |
| `FLOAT UNSIGNED` | `Float` | - |
| `DOUBLE` | `Double` | - |
| `DOUBLE UNSIGNED` | `Double` | - |
| `DECIMAL` | `BigDecimal` | `java.math.BigDecimal` |
| `DECIMAL UNSIGNED` | `BigDecimal` | `java.math.BigDecimal` |
| `BIT` | `Boolean` | - |
| `BOOLEAN` | `Boolean` | - |
| `DATE` | `Date` | `java.util.Date` |
| `TIME` | `Date` | `java.util.Date` |
| `YEAR` | `Date` | `java.util.Date` |
| `DATETIME` | `Date` | `java.util.Date` |
| `TIMESTAMP` | `Date` | `java.util.Date` |
| `BINARY` | `byte[]` | - |
| `VARBINARY` | `byte[]` | - |
| `TINYBLOB` | `byte[]` | - |
| `BLOB` | `byte[]` | - |
| `MEDIUMBLOB` | `byte[]` | - |
| `LONGBLOB` | `byte[]` | - |

#### Java-to-Class 映射表（`<typetofull-types>`）

| Java 短名 | 全限定类名 |
|-----------|-----------|
| `Date` | `java.util.Date` |
| `BigDecimal` | `java.math.BigDecimal` |

#### 查找规则

`DbTypeConvert.getType(String)` 执行 **精确匹配**（`HashMap.get()`），大小写敏感。`detectMissingFieldTypes()` 会分别以 `toUpperCase()` 和 `toLowerCase()` 尝试查找，但 XML 中映射条目的 `<db-type>` 统一使用大写（如 `VARCHAR`、`INT UNSIGNED`）。因此只要数据库驱动返回的类型名能被 `toUpperCase()` 匹配到 XML 中的条目，即可正确映射。

### 5.4 扩展新数据库类型步骤

以添加 PostgreSQL 支持为例：

**第 1 步**：在 `x-generator-manager/src/main/resources/config/definition/` 下创建 `postgresql-definition.xml`，文件名必须匹配 `*-definition.xml` 模式：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<definition>
    <name>PostgreSQL</name>
    <description>PostgreSQL数据库定义</description>
    <db-type>PostgreSQL</db-type>   <!-- 这是 DbTypeConvert 的查找 key -->
    <db-url>jdbc:postgresql://SERVERADDRESS:PORT/YOURDATABASENAME</db-url>
    <db-driver>org.postgresql.Driver</db-driver>
    <db>
        <all-types>
            VARCHAR,CHAR,TEXT,INTEGER,BIGINT,SMALLINT,SERIAL,BIGSERIAL,
            BOOLEAN,REAL,DOUBLE PRECISION,NUMERIC,DATE,TIME,TIMESTAMP,
            BYTEA,UUID,JSON,JSONB
        </all-types>
        <char-types>VARCHAR,CHAR,TEXT</char-types>
        <float-types>REAL,DOUBLE PRECISION,NUMERIC</float-types>
        <alone-types>DATE,TIME,TIMESTAMP,BOOLEAN,TEXT,BYTEA,UUID,JSON,JSONB</alone-types>
        <blob-types>BYTEA</blob-types>
        <sqls>
            <!-- 根据需要添加 dropTable / createTable 的 PostgreSQL SQL 模板 -->
        </sqls>
    </db>
    <dbtojava-types>
        <type><java-type>String</java-type><db-type>VARCHAR</db-type></type>
        <type><java-type>Integer</java-type><db-type>INTEGER</db-type></type>
        <type><java-type>Long</java-type><db-type>BIGINT</db-type></type>
        <!-- ... 补充所有需要的映射 -->
    </dbtojava-types>
    <typetofull-types>
        <type><java-type>Date</java-type><full-type>java.util.Date</full-type></type>
        <type><java-type>BigDecimal</java-type><full-type>java.math.BigDecimal</full-type></type>
    </typetofull-types>
</definition>
```

**第 2 步**：无需修改任何 Java 代码。`DefinitionBuilder` 的 bean 配置使用通配符 `classpath*:/config/definition/*-definition.xml`，新文件会在 Spring 启动时被自动扫描和加载。

**第 3 步**：在代码生成器的"数据源管理"页面新增数据源时，将数据库类型设置为 `PostgreSQL`（与 XML 中 `<db-type>` 值一致）。

**第 4 步**：重启 `x-generator-manager` 服务使新定义生效。

> **参考**：已有的 Oracle 定义文件位于 `x-generator-manager/src/main/resources/config/definition/oracle-definition.xml`，可作为编写新定义的参照。

---

## 6. FreeMarker 模板说明

本项目的 FreeMarker 模板**不以 `.ftl` 文件形式存储在磁盘**，而是保存在数据库的 `generator_template` 表中，字段为 `template_content`。

运行时通过 `StringTemplateLoader` 加载内存模板进行渲染（`TableServiceImpl.parseTemplate()`，第 331-344 行）：

```java
Configuration configuration = new Configuration();
StringTemplateLoader stringLoader = new StringTemplateLoader();
stringLoader.putTemplate(tempname, content);
Template template = new Template(tempname, new StringReader(content), configuration);
template.process(rootMap, stringWriter);
```

唯一使用磁盘上 FreeMarker 语法的文件是类型定义 XML 中嵌入的 SQL 模板：
- `x-generator-manager/src/main/resources/config/definition/mysql-definition.xml` — `<sqls>` 节点内的 `createTable` 模板
- `x-generator-manager/src/main/resources/config/definition/oracle-definition.xml` — 同上

---

## 7. GenerationLog 审计字段说明

### 表结构

表名：`generator_generation_log`

DDL 文件：`x-generator-manager/src/main/resources/db/generation_log.sql`

实体类：`x-generator-manager/src/main/java/com/company/generator/manager/entity/GenerationLog.java`

| 字段 | DB 列名 | 类型 | 说明 |
|------|---------|------|------|
| `id` | `id` | `varchar(64)` PK | 主键，MyBatis-Plus UUID 自动生成 |
| `tableId` | `table_id` | `varchar(64)` | 来源表 ID，关联 `generator_table.id` |
| `schemeId` | `scheme_id` | `varchar(64)` | 生成方案 ID，关联 `generator_scheme.id` |
| `templateSchemeId` | `template_scheme_id` | `varchar(64)` | 模板方案 ID，关联 `generator_template_scheme.id` |
| `entityName` | `entity_name` | `varchar(255)` | 生成的 Java 实体类名（如 `TomatoCouponUser`） |
| `operationType` | `operation_type` | `varchar(32)` NOT NULL | 操作类型，取值：`DRY_RUN`（预检）/ `GENERATE`（正式生成） |
| `dryRunId` | `dry_run_id` | `varchar(64)` | 预检 ID，将同一次操作的 DRY_RUN 和 GENERATE 两条日志关联起来 |
| `fileCount` | `file_count` | `int` | 本次操作涉及的模板文件总数 |
| `newCount` | `new_count` | `int` | 新增文件数（目标路径不存在） |
| `overwriteCount` | `overwrite_count` | `int` | 覆盖文件数 |
| `skipCount` | `skip_count` | `int` | 跳过文件数（内容完全一致） |
| `riskCount` | `risk_count` | `int` | 风险文件数（目标文件已存在且内容不同） |
| `resultJson` | `result_json` | `text` | 完整 `DryRunResult` 的 JSON 序列化，用于事后审计和问题排查 |
| `createDate` | `create_date` | `datetime` | 操作时间戳 |

### 索引

| 索引名 | 列 | 用途 |
|--------|-----|------|
| `idx_dry_run_id` | `dry_run_id` | 按 dryRunId 关联查询同一次操作的预检与正式生成日志 |
| `idx_table_id` | `table_id` | 按表维度查看生成历史 |
| `idx_operation_type` | `operation_type` | 按操作类型筛选 |

### 日志写入时机

| 操作 | operationType | 写入位置 |
|------|---------------|---------|
| `dryRun()` 执行完成后 | `DRY_RUN` | `TableServiceImpl` 第 501-515 行 |
| `generateConfirmed()` 文件写入完成后 | `GENERATE` | `TableServiceImpl` 第 557-567 行 |

**注意**：`GENERATE` 日志中的 `tableId`、`schemeId`、`templateSchemeId`、`entityName` 字段当前未赋值（第 557-567 行只设置了 `operationType`、`dryRunId` 和文件统计字段）。如需完整审计链，可通过 `dryRunId` 关联到对应的 `DRY_RUN` 日志获取这些信息。

---

## 8. README 平台目录与 generator 模块的关系

### 现状

仓库根目录的 `README.md`（第 118-144 行）列出了平台目录结构：

```
x-manerger-sys-common     后台管理系统公用模块
    x-manerger-sys-common-base
    x-manerger-sys-common-email
    x-manerger-sys-common-mybatis
    ...（共 12 个子模块）
x-manerger-sys-service     后台管理模块
x-restful                  业务系统模块
x-micro-service            微服务模块
x-skywalking-agent         SkyWalking agent探针模块
```

**`x-generator-manager` 未被列入该目录结构。**

虽然 README 在多处提及代码生成能力（第 8、10、29 行等），但从未将 `x-generator-manager` 作为独立模块出现在平台目录树中。

### 模块间依赖关系

`x-generator-manager` 与平台其他模块的关系：

- **依赖**：`x-generator-manager` 的 `pom.xml` 引用了 `x-manerger-sys-common` 的若干子模块（`base`、`mybatis`、`utils` 等）作为基础设施
- **独立部署**：`x-generator-manager` 是独立的 Spring Boot 应用（含 `XGeneratorManagerApplication.java` 入口类），拥有独立的端口和数据库配置
- **代码生成的产物**：生成器产出的代码（Entity、Service、Controller 等）被写入到 `x-restful`、`x-manerger-sys-service` 等业务模块的源码目录中，但生成器本身与这些目标模块没有编译期依赖

> 建议在 README 的目录结构中补充 `x-generator-manager` 条目，以免新成员忽略该模块的存在。

---

## 9. TomatoCouponUserServiceImpl FIXME 跨模块缺口

> **声明**：以下内容仅做文档化记录，不在本手册范围内修复。

### 文件位置

`x-restful/x-business-goods/src/main/java/com/company/business/goods/moudle/service/impl/TomatoCouponUserServiceImpl.java`

### FIXME 清单

该文件的 `getUserFansList()` 方法（第 129-168 行）中存在 5 处 FIXME 标记，均集中在粉丝用户列表的业务聚合逻辑中：

| 行号 | FIXME 内容 | 缺口描述 |
|------|-----------|---------|
| 133 | `需要获取对应的粉丝状态/未下单用户/离开N天/赚取佣金额度` | 方法整体声明：当前实现未完整覆盖所需的聚合维度 |
| 148 | `用户离开时间超过5天` | 退出时间阈值硬编码为常量 `NUMBER_5`，判断逻辑可能不完整 |
| 152 | `查询订单表` | 通过 `orderService.getOrderByUserName()` 跨模块查询订单，存在 N+1 查询问题 |
| 155 | `未下单用户` | 未下单用户仅设置 `status=1`，无其他处理 |
| 158 | `已下单用户，计算收益` | 佣金计算通过 `show(tomatoUserEntity)` 实现，方法名不自解释 |

### 跨模块缺口说明

`TomatoCouponUserServiceImpl` 位于 `x-business-goods` 模块，但其业务逻辑需要聚合来自多个服务的数据：

- `ITomatoUserService` — 用户基础信息
- `IOrderService` — 订单数据（跨模块查询）
- `IPropertyLogService` / `IPropertyInviterLogService` — 佣金/收益计算

这些 FIXME 表明该文件是通过代码生成器生成 Service 骨架后手动补充业务逻辑的典型案例，但聚合查询和跨模块数据编排部分尚未完成。代码生成器无法自动处理这类跨模块业务编排——生成器只负责单表 CRUD 骨架，跨表/跨模块的业务逻辑需要开发者手动实现。

**这正是 dryRun 预检的价值所在**：当生成器重新生成 `TomatoCouponUserServiceImpl` 时，`RISK` 状态会提醒开发者该文件已被手动修改过（包含这些 FIXME 及其周围的业务代码），避免盲目覆盖导致手写业务逻辑丢失。
