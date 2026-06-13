# 代码生成器 dryRun 预检与正式生成操作手册

> 适用模块：`x-generator-manager`
> 维护团队：平台组
> 最后更新：2026-06-13

---

## 目录

1. [背景与动机](#1-背景与动机)
2. [核心概念速查](#2-核心概念速查)
3. [dryRun → generateConfirmed 状态机](#3-dryrun--generateconfirmed-状态机)
4. [dryRunId 过期与重跑规则](#4-dryrunid-过期与重跑规则)
5. [DryRunResult 各失败类型含义](#5-dryrunresult-各失败类型含义)
6. [DbTypeConvert 与 definition.xml 映射规则](#6-dbtypeconvert-与-definitionxml-映射规则)
7. [GenerationLog 审计字段说明](#7-generationlog-审计字段说明)
8. [Freemarker 模板路径说明](#8-freemarker-模板路径说明)
9. [附录：README 平台目录与 generator 模块关系](#9-附录readme-平台目录与-generator-模块关系)
10. [附录：TomatoCouponUserServiceImpl FIXME 跨模块缺口](#10-附录tomatocouponuserserviceimpl-fixme-跨模块缺口)

---

## 1. 背景与动机

README（约 118–144 行 "平台目录结构说明"）列出了 `x-manerger-sys-common`、`x-manerger-sys-service`、`x-restful`、`x-micro-service`、`x-skywalking-agent` 五大块，**完全没有提及 `x-generator-manager`**，也没有任何关于 dryRun 预检的描述。

同事第一次使用代码生成功能时，直接走了旧的 `POST /generateCode` 接口——该接口内部调用 `getOutPath()`，会先 `outFile.delete()` 再写入——导致目标目录中已有文件被无条件覆盖。

本手册旨在补全这一认知缺口：**所有代码生成必须先走 dryRun 预检，确认结果后再通过 generateConfirmed 落盘。**

---

## 2. 核心概念速查

| 概念 | 说明 |
|------|------|
| `DryRunRequest` | 预检请求 DTO，携带 tableId、templateSchemeId、templateKeys 等 |
| `DryRunResult` | 预检结果 DTO，含 dryRunId、files（FilePreview 列表）、缺失类型/变量列表 |
| `FilePreview` | 单文件预检结果，含 filePath、status（NEW/OVERWRITE/SKIP/RISK）、newContent、existingContent、diffSummary |
| `FilePreview.FileStatus` | 文件状态枚举：NEW / OVERWRITE / SKIP / RISK |
| `GenerationLog` | 审计日志实体，DRY_RUN 和 GENERATE 各写一条记录 |
| `DbTypeConvert` | 数据库类型 → Java 类型的运行时转换器，基于 definition XML 构建 |
| `definition XML` | 位于 `src/main/resources/config/definition/` 的数据库方言定义文件 |

### 关键源码位置

| 文件 | 路径（相对仓库根） |
|------|------|
| 预检/确认控制器 | `x-generator-manager/src/main/java/com/company/generator/manager/controller/TableController.java` |
| 预检/确认服务实现 | `x-generator-manager/src/main/java/com/company/generator/manager/service/impl/TableServiceImpl.java` |
| DryRunRequest DTO | `x-generator-manager/src/main/java/com/company/generator/manager/entity/DryRunRequest.java` |
| DryRunResult DTO | `x-generator-manager/src/main/java/com/company/generator/manager/entity/DryRunResult.java` |
| FilePreview DTO | `x-generator-manager/src/main/java/com/company/generator/manager/entity/FilePreview.java` |
| GenerationLog 实体 | `x-generator-manager/src/main/java/com/company/generator/manager/entity/GenerationLog.java` |
| DbTypeConvert | `x-generator-manager/src/main/java/com/company/generator/manager/common/definition/type/DbTypeConvert.java` |
| DefinitionBuilder | `x-generator-manager/src/main/java/com/company/generator/manager/common/definition/DefinitionBuilder.java` |
| MySQL 类型定义 | `x-generator-manager/src/main/resources/config/definition/mysql-definition.xml` |
| Oracle 类型定义 | `x-generator-manager/src/main/resources/config/definition/oracle-definition.xml` |
| 审计日志 DDL | `x-generator-manager/src/main/resources/db/generation_log.sql` |
| EhCache 配置 | `x-generator-manager/src/main/resources/ehcache/ehcache.xml` |
| 预检流程测试 | `x-generator-manager/src/test/java/.../service/TableServiceDryRunTest.java` |
| 核心类型映射测试 | `x-generator-manager/src/test/java/.../service/CodeGeneratorCoreTest.java` |

---

## 3. dryRun → generateConfirmed 状态机

### 3.1 接口总览

```
┌──────────────────────┐         ┌─────────────────────────┐
│ POST /dryRun         │         │ POST /generateConfirmed │
│ Body: DryRunRequest  │──────>  │ Param: dryRunId         │
│ Resp: DryRunResult   │  缓存   │ Resp: success / error   │
└──────────────────────┘         └─────────────────────────┘
        │                              │
        ▼                              ▼
  GenerationLog                  GenerationLog
  operationType=DRY_RUN          operationType=GENERATE
  (完整字段)                     (仅 dryRunId + 计数)
```

### 3.2 dryRun 阶段（9 步）

`TableServiceImpl.dryRun(DryRunRequest)` 内部流程：

| 步骤 | 操作 | 说明 |
|------|------|------|
| 1 | 加载基础数据 | 根据 `tableId` 获取 Table、DataSource、List\<Column\> |
| 2 | 检测缺失字段类型 | 遍历列，用 `DbTypeConvert` 查映射，收集 `missingFieldTypes` |
| 3 | 构建/复用 Scheme | 有 schemeId 就加载，否则查该表已有 scheme，都没有则 new |
| 4 | 加载模板 | 按 `templateKeys` 加载选中模板 + 加载 scheme 下全部模板；应用路径/包名覆盖 |
| 5 | 检测模板变量缺失 | 正则 `\$\{([a-zA-Z_][a-zA-Z0-9_]*)` 扫描模板内容，与数据模型 key 比对 |
| 6 | 逐模板预检 | 对每个选中模板：构建 ftlMap → parseTemplate 渲染 → getOutPathPreview 算路径（**不删除已有文件**） → 判定 FileStatus |
| 7 | 组装 DryRunResult | 生成 UUID dryRunId，统计 NEW/OVERWRITE/SKIP/RISK 计数，设置 hasRisk，拼接中文 summary |
| 8 | 写入 EhCache | `CacheUtils.put("dryrun_" + dryRunId, result)` |
| 9 | 写审计日志 | 插入 `GenerationLog`，`operationType = "DRY_RUN"`，全字段填充 |

### 3.3 文件状态判定逻辑

```
目标文件是否存在？
  ├── 否 → status = NEW，existingContent = null
  └── 是 → 读取已有内容
        ├── 内容与生成内容完全一致 → status = SKIP
        └── 内容不同（可能被用户手动修改） → status = RISK，计算 diffSummary
```

> **注意**：`OVERWRITE` 状态在枚举中定义但**当前代码中不会被赋值**。所有 "文件存在且内容不同" 的情况统一标记为 `RISK`。

### 3.4 generateConfirmed 阶段（4 步）

`TableServiceImpl.generateConfirmed(String dryRunId)` 内部流程：

| 步骤 | 操作 | 说明 |
|------|------|------|
| 1 | 从缓存加载 | `CacheUtils.get("dryrun_" + dryRunId)`，若为 null 则抛 `GenerationException` |
| 2 | 写入文件 | 遍历 FilePreview：SKIP 的文件跳过不写；其余 mkdir -p 父目录 → 删除已有文件 → `FileUtils.write(newContent)` |
| 3 | 写审计日志 | 插入 `GenerationLog`，`operationType = "GENERATE"`，通过 `dryRunId` 关联 |
| 4 | 清除缓存 | `CacheUtils.remove("dryrun_" + dryRunId)` |

### 3.5 旧路径（危险，应废弃）

```
POST /generateCode → tableService.generateCode(scheme, templates, allTemplates)
  → getOutPath()  ← 注意：这个方法内部会 outFile.delete()
  → FileUtils.write()
```

旧路径**没有预检、没有审计日志、没有缓存中间态**，直接覆盖目标文件。当前 UI 表单 `generate_code.html` 仍然连接到此旧接口，需要在前端层面改造为 dryRun + generateConfirmed 两步流程。

### 3.6 完整时序图

```
用户                     前端                        Controller              Service                   EhCache            DB
 │                       │                             │                      │                         │                 │
 │  打开"生成代码"页面    │                             │                      │                         │                 │
 │──────────────────────>│                             │                      │                         │                 │
 │                       │  GET /{id}/generateCode     │                      │                         │                 │
 │                       │────────────────────────────>│                      │                         │                 │
 │                       │  返回模板列表+方案表单       │                      │                         │                 │
 │                       │<────────────────────────────│                      │                         │                 │
 │                       │                             │                      │                         │                 │
 │  填写表单、勾选模板    │                             │                      │                         │                 │
 │  点击"预检"           │                             │                      │                         │                 │
 │──────────────────────>│                             │                      │                         │                 │
 │                       │  POST /dryRun               │                      │                         │                 │
 │                       │  Body: DryRunRequest        │                      │                         │                 │
 │                       │────────────────────────────>│  dryRun(request)     │                         │                 │
 │                       │                             │─────────────────────>│                         │                 │
 │                       │                             │                      │  1-6: 加载+渲染+判定     │                 │
 │                       │                             │                      │─────────────────────────────────────────>│
 │                       │                             │                      │  7: 组装 DryRunResult    │                 │
 │                       │                             │                      │  8: put("dryrun_"+id)    │                 │
 │                       │                             │                      │─────────────────────────>│                 │
 │                       │                             │                      │  9: insert GenerationLog │                 │
 │                       │                             │                      │─────────────────────────────────────────>│
 │                       │  DryRunResult               │                      │                         │                 │
 │                       │<────────────────────────────│<─────────────────────│                         │                 │
 │  看到预检结果          │                             │                      │                         │                 │
 │  - NEW 文件列表        │                             │                      │                         │                 │
 │  - RISK 文件 + diff    │                             │                      │                         │                 │
 │  - 缺失类型/变量警告   │                             │                      │                         │                 │
 │                       │                             │                      │                         │                 │
 │  确认无误，点击"生成"  │                             │                      │                         │                 │
 │──────────────────────>│                             │                      │                         │                 │
 │                       │  POST /generateConfirmed    │                      │                         │                 │
 │                       │  Param: dryRunId            │                      │                         │                 │
 │                       │────────────────────────────>│  generateConfirmed() │                         │                 │
 │                       │                             │─────────────────────>│                         │                 │
 │                       │                             │                      │  1: get("dryrun_"+id)    │                 │
 │                       │                             │                      │─────────────────────────>│                 │
 │                       │                             │                      │  2: 写入文件到磁盘       │                 │
 │                       │                             │                      │  3: insert GenerationLog │                 │
 │                       │                             │                      │─────────────────────────────────────────>│
 │                       │                             │                      │  4: remove cache         │                 │
 │                       │                             │                      │─────────────────────────>│                 │
 │                       │  200 OK "代码生成成功"       │                      │                         │                 │
 │                       │<────────────────────────────│<─────────────────────│                         │                 │
 │  看到成功提示          │                             │                      │                         │                 │
```

---

## 4. dryRunId 过期与重跑规则

### 4.1 存储机制

dryRunId 对应的 DryRunResult **仅存储在 EhCache 内存缓存中**，不持久化到数据库。

缓存 key 格式：`"dryrun_" + dryRunId`（32 位去横线 UUID）

### 4.2 EhCache 配置现状

当前 EhCache 配置文件 `src/main/resources/ehcache/ehcache.xml` 中**没有为 dryRun 定义专用 cache region**，因此走的是 `defaultCache`：

```xml
<defaultCache
    maxElementsInMemory = "10000"
    eternal = "true"          ← 永不超时
    timeToIdleSeconds = "0"
    timeToLiveSeconds = "0"
    memoryStoreEvictionPolicy = "LFU"
/>
```

`eternal = "true"` 意味着缓存条目理论上不会因时间过期，但以下情况会导致 dryRunId 失效：

| 失效场景 | 原因 |
|----------|------|
| JVM 重启 / 应用重启 | 内存缓存不持久化（`diskPersistent = "false"`） |
| LFU 淘汰 | 缓存条目数达到 10000 上限时，低频使用的条目被驱逐 |
| generateConfirmed 成功后主动清除 | 步骤 4 执行 `CacheUtils.remove()` |

### 4.3 过期/失效时的表现

调用 `generateConfirmed` 时若缓存中找不到对应 DryRunResult，会抛出：

```
GenerationException: 预检结果不存在或已过期，请重新执行预检: {dryRunId}
```

前端应捕获此错误并引导用户重新执行预检。

### 4.4 重跑规则

| 场景 | 操作 |
|------|------|
| dryRunId 有效 | 直接调用 `POST /generateConfirmed?dryRunId=xxx` |
| dryRunId 失效（JVM 重启 / LFU 淘汰） | 重新 `POST /dryRun` 获取新 dryRunId，不可复用旧 ID |
| 预检后修改了表结构或模板内容 | **必须重新预检**，旧 dryRunId 的缓存数据不反映最新变更 |
| 预检后修改了目标目录下的文件 | **建议重新预检**，因为预检时计算的 FileStatus 可能已不准确 |
| 同一张表/同一方案想生成多次 | 每次都需要独立执行 dryRun，每个 dryRunId 只能用于一次 generateConfirmed（成功后即清除） |

---

## 5. DryRunResult 各失败类型含义

### 5.1 DryRunResult 字段一览

| 字段 | 类型 | 说明 |
|------|------|------|
| `dryRunId` | String | 预检唯一标识，用于后续 generateConfirmed |
| `files` | List\<FilePreview\> | 每个选中模板的预检结果 |
| `missingFieldTypes` | List\<String\> | 缺失的数据库类型映射（见 5.2） |
| `missingTemplateVars` | List\<String\> | 缺失的模板变量（见 5.3） |
| `hasRisk` | boolean | 是否存在 RISK 或 OVERWRITE 状态的文件 |
| `summary` | String | 中文摘要，格式："共 N 个文件：新增 X，覆盖 Y，跳过 Z，风险 W；缺失类型映射 A 项；缺失模板变量 B 项" |
| `createdAt` | Date | 预检时间戳 |

### 5.2 missingFieldTypes — 缺字段类型

**含义**：数据库表的某些列的 `typeName`（如 `GEOMETRY`、`JSON`、`ENUM`）在对应数据库方言的 definition XML 的 `<dbtojava-types>` 中找不到映射条目。

**检测逻辑**（`TableServiceImpl.detectMissingFieldTypes`）：

```java
for (Column column : columns) {
    String typeName = column.getTypeName();
    Type type = typeConvert.getType(typeName.toUpperCase());
    if (type == null) {
        type = typeConvert.getType(typeName.toLowerCase());
    }
    if (type == null) {
        missing.add(typeName);  // 未找到映射
    }
}
```

**影响**：缺失映射的列在 Column 构造时会 fallback 为 `"String"` 类型（见 `Column` 构造器），生成的代码中这些字段会统一变成 `String`，可能不符合业务意图。

**处置**：

1. 在对应的 definition XML 的 `<dbtojava-types>` 中添加映射条目
2. 如果是新数据库方言，需要新建 definition XML（详见第 6 节）
3. 添加后重启应用使 DefinitionBuilder 重新加载

### 5.3 missingTemplateVars — 缺模板变量

**含义**：FreeMarker 模板中通过 `${varName}` 或 `${varName.xxx}` 引用了某些变量，但这些变量名在 `getFtlMap()` 构建的数据模型中不存在。

**检测逻辑**（`TableServiceImpl.detectMissingTemplateVars`）：

```java
Pattern: \$\{([a-zA-Z_][a-zA-Z0-9_]*)
对模板内容做正则匹配，提取所有引用的顶级变量名，
与 ftlMap 的 keySet 做差集。
```

**数据模型中已有的变量**（由 `getFtlMap` 构建）：

| 变量 | 来源 |
|------|------|
| `entityName`, `moduleName`, `functionAuthor`, `functionDesc`, `functionName`, `tableName`, `tableType` 等 | Scheme 对象 JSON 序列化后的所有字段 |
| `targetPackage` | 根据模板的 `targetPackage` + scheme 的 `moduleName` 拼接 |
| `columns` | 该表的所有 Column 列表 |
| `time` | 生成时间（`DateUtils.formatDateTime(new Date())`） |
| 各模板的 `key` | allTemplates 中每个模板以其 key 为 key 放入数据模型 |

**影响**：缺失变量在 FreeMarker 渲染时会抛 `TemplateException`（模板解析失败），dryRun 会捕获并包装为 `GenerationException`。

**处置**：

1. 检查模板中引用的变量名是否拼写正确
2. 如果是自定义变量，需要在 Scheme 或 Template 中补充对应字段
3. 如果是有意引用但不确定是否总有值，使用 FreeMarker 的默认值语法 `${varName!}` 或 `${varName!defaultValue}`

### 5.4 文件状态 — 目标文件已存在（RISK）

**含义**：目标路径下已存在同名文件，且其内容与本次生成的内容**不一致**。通常意味着：

- 上次生成后用户手动修改了该文件
- 上次生成后用户又执行了其他生成覆盖了部分内容
- 表结构或模板变更导致重复生成时产出不同

**判定逻辑**：

```java
if (outFile.exists()) {
    String existingContent = FileUtils.readFileToString(outFile, "UTF-8");
    if (existingContent.equals(newContent)) {
        status = SKIP;      // 内容完全一致，无需操作
        diffSummary = "内容完全一致，无需变更";
    } else {
        status = RISK;      // 内容不同，可能有用户修改
        diffSummary = computeDiffSummary(existing, new);
        // 格式："新增 X 行，删除 Y 行（原文件 N 行，新文件 M 行）"
    }
}
```

**RISK vs OVERWRITE**：

| 状态 | 赋值情况 | 说明 |
|------|----------|------|
| `NEW` | 当前代码实际赋值 | 目标文件不存在 |
| `SKIP` | 当前代码实际赋值 | 文件存在且内容一致 |
| `RISK` | 当前代码实际赋值 | 文件存在且内容不同 |
| `OVERWRITE` | **枚举定义但代码中从未赋值** | 死代码，预留未来区分"确定覆盖"与"风险覆盖" |

**处置**：

1. 在预检结果页面仔细比对 RISK 文件的 `newContent` 与 `existingContent`
2. 利用 `diffSummary` 快速判断变更规模
3. 如果确认要覆盖用户修改，可继续 generateConfirmed（会覆盖）
4. 如果不希望覆盖，取消本次生成，手动处理冲突后再重新预检

---

## 6. DbTypeConvert 与 definition.xml 映射规则

### 6.1 架构分层

```
XML 文件（静态定义）
   │
   ▼  JAXB 反序列化（应用启动时）
DefinitionBuilder  ←── ApplicationListener<ContextRefreshedEvent>
   │
   ▼  单例门面
DefinitionUtils.getDefinition(dbType)
   │
   ▼  运行时按需构建 + 静态缓存
DbTypeConvert.getTypeConvert("dbtojava", "MySql")
   │
   ▼  查找
DbTypeConvert.getType("VARCHAR") → Type{javaType="String", dbType="VARCHAR"}
```

### 6.2 definition XML 结构

每个数据库方言对应一个 XML 文件，结构如下：

```xml
<definition>
    <name>MySql</name>
    <db-type>MySql</db-type>                    <!-- 唯一标识，与 DataSource.dbType 对应 -->
    <db-url>jdbc:mysql://...</db-url>
    <db-driver>com.mysql.jdbc.Driver</db-driver>

    <db>
        <all-types>VARCHAR,INT,BIGINT,...</all-types>     <!-- 该方言支持的所有类型 -->
        <char-types>VARCHAR,CHAR,TEXT,...</char-types>     <!-- 字符类型子集 -->
        <float-types>FLOAT,DOUBLE,DECIMAL</float-types>    <!-- 浮点类型子集 -->
        <alone-types>DATE,DATETIME,TEXT,...</alone-types>  <!-- 不需要长度参数的类型 -->
        <blob-types>BLOB,TEXT,...</blob-types>             <!-- 大对象类型子集 -->
        <sqls>
            <sql>
                <id>dropTable</id>
                <content><![CDATA[DROP TABLE IF EXISTS ${tablename}]]></content>
            </sql>
            <sql>
                <id>createTable</id>
                <content><![CDATA[CREATE TABLE ...]]></content>  <!-- FreeMarker 模板 -->
            </sql>
        </sqls>
    </db>

    <dbtojava-types>                             <!-- DB类型 → Java类型 映射 -->
        <type>
            <java-type>String</java-type>
            <db-type>VARCHAR</db-type>
        </type>
        <type>
            <java-type>BigDecimal</java-type>
            <full-type>java.math.BigDecimal</full-type>   <!-- 需要 import 的全限定名 -->
            <db-type>DECIMAL</db-type>
        </type>
        <!-- ... 更多映射 ... -->
    </dbtojava-types>

    <typetofull-types>                           <!-- Java短名 → 全限定名（MySQL 用此节点） -->
        <type>
            <java-type>Date</java-type>
            <full-type>java.util.Date</full-type>
        </type>
    </typetofull-types>

    <!-- Oracle 使用 javatoclass-types 节点（功能等同 typetofull-types） -->
</definition>
```

### 6.3 MySQL 当前完整映射表

来源：`src/main/resources/config/definition/mysql-definition.xml`

| DB 类型 | Java 类型 | 全限定名（fullType） |
|---------|-----------|---------------------|
| VARCHAR | String | — |
| CHAR | String | — |
| TEXT | String | — |
| TINYTEXT | String | — |
| MEDIUMTEXT | String | — |
| LONGTEXT | String | — |
| INT | Integer | — |
| INTEGER | Integer | — |
| INT UNSIGNED | Integer | — |
| TINYINT | Integer | — |
| TINYINT UNSIGNED | Integer | — |
| MEDIUMINT | Integer | — |
| SMALLINT UNSIGNED | Integer | — |
| BIGINT | Long | — |
| BIGINT UNSIGNED | Long | — |
| MEDIUMINT UNSIGNED | Long | — |
| SMALLINT | Short | — |
| FLOAT | Float | — |
| FLOAT UNSIGNED | Float | — |
| DOUBLE | Double | — |
| DOUBLE UNSIGNED | Double | — |
| DECIMAL | BigDecimal | java.math.BigDecimal |
| DECIMAL UNSIGNED | BigDecimal | java.math.BigDecimal |
| DATE | Date | java.util.Date |
| DATETIME | Date | java.util.Date |
| TIMESTAMP | Date | java.util.Date |
| TIME | Date | java.util.Date |
| YEAR | Date | java.util.Date |
| BIT | Boolean | — |
| BOOLEAN | Boolean | — |
| BINARY | byte[] | — |
| VARBINARY | byte[] | — |
| TINYBLOB | byte[] | — |
| BLOB | byte[] | — |
| MEDIUMBLOB | byte[] | — |
| LONGBLOB | byte[] | — |

### 6.4 Oracle 当前完整映射表

来源：`src/main/resources/config/definition/oracle-definition.xml`

| DB 类型 | Java 类型 | 全限定名 |
|---------|-----------|---------|
| CHAR | String | — |
| VARCHAR2 | String | — |
| LONG | String | — |
| NUMBER | Double | — |
| LONGRAW | byte[] | — |
| DATE | Date | java.util.Date |
| TIMESTAMP | Date | java.util.Date |
| TIMESTAMP WITH LOCAL TIME ZONE | Date | java.util.Date |
| TIMESTAMP WITH TIME ZONE | Date | java.util.Date |

### 6.5 DbTypeConvert 缓存机制

```java
// 静态全局缓存
public static Map<String, DbTypeConvert> dbTypeConvertMap = new HashMap<>();

// 工厂方法
public static ITypeConvert getTypeConvert(String type, String dbType) {
    String cacheKey = type + "_" + dbType;  // 例如 "dbtojava_MySql"
    if (dbTypeConvertMap.containsKey(cacheKey)) {
        return dbTypeConvertMap.get(cacheKey);
    }
    DbTypeConvert typeConvert = new DbTypeConvert(type, dbType);
    dbTypeConvertMap.put(cacheKey, typeConvert);
    return typeConvert;
}
```

- `type` 参数取值：`"dbtojava"`（DB类型→Java类型）或 `"javatoclass"`（Java短名→全限定类名）
- 构造函数中从 `DefinitionUtils` 加载对应数据库的 Type 列表到 `typeMap`
- 缓存是 JVM 级别静态 Map，应用生命周期内不会重新加载
- 如需热更新映射，必须重启应用

### 6.6 扩展新数据库方言步骤

以添加 PostgreSQL 支持为例：

**第 1 步：创建 definition XML 文件**

在 `src/main/resources/config/definition/` 目录下新建 `postgresql-definition.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<definition>
    <name>PostgreSQL</name>
    <description>PostgreSQL 数据库定义</description>
    <db-type>PostgreSQL</db-type>
    <db-url><![CDATA[jdbc:postgresql://SERVERADDRESS:PORT/YOURDATABASENAME]]></db-url>
    <db-driver>org.postgresql.Driver</db-driver>

    <db>
        <all-types>
            VARCHAR,CHAR,TEXT,SMALLINT,INTEGER,BIGINT,
            SERIAL,BIGSERIAL,REAL,DOUBLE PRECISION,NUMERIC,DECIMAL,
            BOOLEAN,DATE,TIME,TIMESTAMP,TIMESTAMPTZ,
            BYTEA,UUID,JSON,JSONB,XML,INET,CIDR,MACADDR
        </all-types>
        <char-types>VARCHAR,CHAR,TEXT</char-types>
        <float-types>REAL,DOUBLE PRECISION,NUMERIC,DECIMAL</float-types>
        <alone-types>
            SMALLINT,INTEGER,BIGINT,SERIAL,BIGSERIAL,
            BOOLEAN,DATE,TIME,TIMESTAMP,TIMESTAMPTZ,
            BYTEA,UUID,JSON,JSONB,XML,INET,CIDR,MACADDR,TEXT
        </alone-types>
        <blob-types>BYTEA</blob-types>
        <sqls>
            <sql>
                <id>dropTable</id>
                <description>删除语句</description>
                <content><![CDATA[DROP TABLE IF EXISTS ${tablename}]]></content>
            </sql>
            <sql>
                <id>createTable</id>
                <description>创建表语句</description>
                <content><![CDATA[
                    CREATE TABLE ${table.tableName} (
                        id VARCHAR(64) NOT NULL,
                        <#if table.columns?exists>
                        <#list table.columns as attr>
                        <#if attr.columnName != "id">
                        ${attr.columnName} <#if attr.isAlone>${attr.typeName}<#else>${attr.typeName}(${attr.columnSize}<#if attr.isFloat>,${attr.decimalDigits}</#if>)</#if><#if !attr.nullable> NOT NULL</#if><#if attr_has_next>,</#if>
                        </#if>
                        </#list>
                        </#if>
                        PRIMARY KEY (id)
                    );
                ]]></content>
            </sql>
        </sqls>
    </db>

    <dbtojava-types>
        <type><java-type>String</java-type><db-type>VARCHAR</db-type></type>
        <type><java-type>String</java-type><db-type>CHAR</db-type></type>
        <type><java-type>String</java-type><db-type>TEXT</db-type></type>
        <type><java-type>String</java-type><db-type>UUID</db-type></type>
        <type><java-type>String</java-type><db-type>JSON</db-type></type>
        <type><java-type>String</java-type><db-type>JSONB</db-type></type>
        <type><java-type>String</java-type><db-type>XML</db-type></type>
        <type><java-type>Integer</java-type><db-type>INTEGER</db-type></type>
        <type><java-type>Integer</java-type><db-type>SERIAL</db-type></type>
        <type><java-type>Short</java-type><db-type>SMALLINT</db-type></type>
        <type><java-type>Long</java-type><db-type>BIGINT</db-type></type>
        <type><java-type>Long</java-type><db-type>BIGSERIAL</db-type></type>
        <type><java-type>Float</java-type><db-type>REAL</db-type></type>
        <type><java-type>Double</java-type><db-type>DOUBLE PRECISION</db-type></type>
        <type>
            <java-type>BigDecimal</java-type>
            <full-type>java.math.BigDecimal</full-type>
            <db-type>NUMERIC</db-type>
        </type>
        <type>
            <java-type>BigDecimal</java-type>
            <full-type>java.math.BigDecimal</full-type>
            <db-type>DECIMAL</db-type>
        </type>
        <type><java-type>Boolean</java-type><db-type>BOOLEAN</db-type></type>
        <type>
            <java-type>Date</java-type>
            <full-type>java.util.Date</full-type>
            <db-type>DATE</db-type>
        </type>
        <type>
            <java-type>Date</java-type>
            <full-type>java.util.Date</full-type>
            <db-type>TIME</db-type>
        </type>
        <type>
            <java-type>Date</java-type>
            <full-type>java.util.Date</full-type>
            <db-type>TIMESTAMP</db-type>
        </type>
        <type>
            <java-type>Date</java-type>
            <full-type>java.util.Date</full-type>
            <db-type>TIMESTAMPTZ</db-type>
        </type>
        <type><java-type>byte[]</java-type><db-type>BYTEA</db-type></type>
    </dbtojava-types>

    <typetofull-types>
        <type><java-type>Date</java-type><full-type>java.util.Date</full-type></type>
        <type><java-type>BigDecimal</java-type><full-type>java.math.BigDecimal</full-type></type>
    </typetofull-types>
</definition>
```

**第 2 步：确认 DefinitionBuilder 的文件匹配模式**

检查 Spring 配置中 `DefinitionBuilder` bean 的 `fileNames` 属性，确保其模式（如 `classpath*:config/definition/*-definition.xml`）能匹配到新文件。如果是逐个列举文件名的方式，需要显式加入新文件名。

**第 3 步：DataSource 中注册 dbType**

在管理后台的"数据源管理"页面新增数据源时，`dbType` 字段填写的值必须与 XML 中 `<db-type>` 的值**完全一致**（本例为 `PostgreSQL`）。

**第 4 步：重启应用**

`DefinitionBuilder` 在 `ContextRefreshedEvent` 时加载，`DbTypeConvert` 使用静态缓存。新增 dialect 后必须重启使 XML 被解析。

**第 5 步：验证**

1. 在管理后台添加 PostgreSQL 数据源，导入一张表
2. 对该表执行 `POST /dryRun`
3. 检查 `missingFieldTypes` 是否为空——若有遗漏的类型，回到 XML 补充 `<dbtojava-types>` 条目

---

## 7. GenerationLog 审计字段说明

### 7.1 表结构

DDL 位于 `src/main/resources/db/generation_log.sql`。

| 字段 | DB 列名 | 类型 | 说明 |
|------|---------|------|------|
| `id` | `id` | varchar(64), PK | UUID 主键，MyBatis-Plus `IdType.UUID` 自动生成 |
| `tableId` | `table_id` | varchar(64) | 关联的表 ID。DRY_RUN 时填充，**GENERATE 时当前代码未填充**（设计缺口） |
| `schemeId` | `scheme_id` | varchar(64) | 关联的生成方案 ID。同上，仅 DRY_RUN 填充 |
| `templateSchemeId` | `template_scheme_id` | varchar(64) | 关联的模板方案 ID。同上 |
| `entityName` | `entity_name` | varchar(255) | 生成的 Java 实体类名。同上 |
| `operationType` | `operation_type` | varchar(32), NOT NULL | **`DRY_RUN`** 或 **`GENERATE`** — 区分预检日志与生成日志 |
| `dryRunId` | `dry_run_id` | varchar(64), 索引 | 预检 UUID。GENERATE 日志通过此字段关联到对应的 DRY_RUN 日志 |
| `fileCount` | `file_count` | int | 处理的文件总数 |
| `newCount` | `new_count` | int | 新增文件数（status = NEW） |
| `overwriteCount` | `overwrite_count` | int | 覆盖文件数（status = OVERWRITE，当前实际不会出现） |
| `skipCount` | `skip_count` | int | 跳过文件数（status = SKIP，内容完全一致） |
| `riskCount` | `risk_count` | int | 风险文件数（status = RISK，内容不同） |
| `resultJson` | `result_json` | text | 完整的 `DryRunResult` JSON 序列化，含所有 FilePreview 详情 |
| `createDate` | `create_date` | datetime | 操作时间 |

### 7.2 索引

| 索引名 | 列 | 用途 |
|--------|----|------|
| `PRIMARY` | `id` | 主键 |
| `idx_dry_run_id` | `dry_run_id` | 通过 dryRunId 关联 DRY_RUN 和 GENERATE 日志 |
| `idx_table_id` | `table_id` | 按表维度查询生成历史 |
| `idx_operation_type` | `operation_type` | 按操作类型筛选 |

### 7.3 日志生命周期

```
预检阶段:
  INSERT INTO generator_generation_log
    (tableId, schemeId, templateSchemeId, entityName,
     operationType='DRY_RUN', dryRunId, fileCount, newCount,
     overwriteCount, skipCount, riskCount, resultJson, createDate)

确认生成阶段:
  INSERT INTO generator_generation_log
    (operationType='GENERATE', dryRunId=<同一个ID>,
     fileCount, newCount, overwriteCount, skipCount, riskCount,
     resultJson, createDate)
    -- 注意：tableId/schemeId/templateSchemeId/entityName 未填充
```

### 7.4 审计查询示例

查询某张表的所有预检记录：

```sql
SELECT * FROM generator_generation_log
WHERE table_id = ? AND operation_type = 'DRY_RUN'
ORDER BY create_date DESC;
```

通过 dryRunId 关联预检和生成：

```sql
SELECT dr.*, gen.*
FROM generator_generation_log dr
JOIN generator_generation_log gen ON dr.dry_run_id = gen.dry_run_id
WHERE dr.operation_type = 'DRY_RUN'
  AND gen.operation_type = 'GENERATE';
```

### 7.5 已知设计缺口

- `GENERATE` 日志未填充 `tableId`、`schemeId`、`templateSchemeId`、`entityName`，这些信息在缓存的 DryRunResult 中是存在的，但代码中没有从 result 中取出回填到日志
- `resultJson` 中序列化了完整的 DryRunResult（含所有文件内容），对于大项目可能占用较多存储空间

---

## 8. Freemarker 模板路径说明

### 8.1 模板存储位置

**代码生成用的 FreeMarker 模板不以 `.ftl` 文件形式存在于仓库中。**

模板内容存储在数据库表 `generator_template` 的 `template_content` 字段中（由 `Template` 实体映射），通过管理后台的"模板管理"功能维护。

### 8.2 模板渲染方式

`TableServiceImpl.parseTemplate()` 使用 FreeMarker 的 `StringTemplateLoader` 在内存中渲染：

```java
StringTemplateLoader stringLoader = new StringTemplateLoader();
stringLoader.putTemplate(tempname, content);  // content 从 DB 读取
Template template = new Template(tempname, new StringReader(content), configuration);
template.process(rootMap, stringWriter);       // rootMap = getFtlMap() 的返回值
```

### 8.3 模板中的数据模型变量

| 变量名 | 类型 | 说明 |
|--------|------|------|
| `${entityName}` | String | 实体类名 |
| `${moduleName}` | String | 模块名 |
| `${functionName}` | String | 功能名 |
| `${functionAuthor}` | String | 作者 |
| `${functionDesc}` | String | 功能描述 |
| `${tableName}` | String | 数据库表名 |
| `${tableType}` | String | 表类型 |
| `${targetPackage}` | String | 目标包名（经 moduleName 替换后） |
| `${columns}` | List\<Column\> | 列信息列表，每列有 columnName、javaField、javaType、typeName、columnSize、nullable、remarks 等 |
| `${time}` | String | 生成时间 |
| 各模板 key | Template | allTemplates 中每个模板以自身 key 为 key 放入，可通过 `${模板key.targetPackage}` 等方式引用其他模板的包路径 |

### 8.4 视图层模板（Beetl，非 FreeMarker）

管理后台的 HTML 页面使用 **Beetl** 模板引擎（不是 FreeMarker），位于：

```
x-generator-manager/src/main/resources/views/
├── error/                              # 错误页面（400, 401, 403, 404, 500）
├── layouts/                            # 布局模板（default, form, grid-select, list）
├── modules/generator/
│   ├── datasource/                     # 数据源管理（edit, list）
│   ├── index/                          # 首页
│   ├── login/                          # 登录
│   ├── table/                          # 表管理 + 代码生成
│   │   ├── generate_code.html          # ← 代码生成页面（当前仍连接旧 generateCode 接口）
│   │   ├── create_menu.html
│   │   ├── edit.html
│   │   ├── import_database.html
│   │   ├── list.html
│   │   ├── un_generate_code.html
│   │   └── un_sync_database.html
│   └── template/                       # 模板管理
│       ├── edit.html
│       ├── import.html
│       ├── scheme_edit.html
│       └── scheme_list.html
└── themes/default/include/             # 主题组件（header, footer, menu, topbar 等）
```

---

## 9. 附录：README 平台目录与 generator 模块关系

### 9.1 README 中的目录结构（第 118–144 行）

```
x-manerger-sys-common     后台管理系统公用模块
  x-manerger-sys-common-base           基础模块
  x-manerger-sys-common-email          邮件模块
  x-manerger-sys-common-mybatis        数据库操作模块、Mybatis-plus
  x-manerger-sys-common-oss            附件上传模块
  x-manerger-sys-common-quartz         任务模块
  x-manerger-sys-common-query          参数封装模块
  x-manerger-sys-common-security       鉴权模块
  x-manerger-sys-common-sms            短信模块
  x-manerger-sys-common-utils          工具模块
  x-manerger-sys-common-limit          限流模块
  x-manerger-sys-common-lock           分布式锁模块
  x-manerger-sys-common-idgenerator    id生成模块
  x-manerger-sys-common-queue          排队模块
x-manerger-sys-service    后台管理模块
x-restful                 业务系统模块
x-micro-service           微服务模块
  x-spring-cloud-gateway               本地配置模式路由
  x-spring-cloud-gateway-service       动态配置模式路由
  x-spring-cloud-gateway-provide       本地路由接口提供模块
  x-spring-boot-nacos                  动态路由接口提供模块
x-skywalking-agent        SkyWalking agent探针模块
```

### 9.2 x-generator-manager 的定位

`x-generator-manager` 是仓库中与上述模块**平级**的独立 Spring Boot 应用（端口 8081），但 README 中完全没有提及它。

**依赖关系**：

- `x-generator-manager` 依赖 `x-manerger-sys-common-base`（`AbstractEntity` 等基础类）
- `x-generator-manager` 依赖 `x-manerger-sys-common-mybatis`（MyBatis-Plus 配置）
- `x-generator-manager` 依赖 `x-manerger-sys-common-utils`（JaxbMapper、CacheUtils、DateUtils 等工具类）
- `x-generator-manager` **不依赖** `x-manerger-sys-service`、`x-restful`、`x-micro-service`

**独立性**：它是一个独立的代码生成工具，有自己的登录鉴权（LoginFilter + LoginInterceptor）、数据源管理、模板管理，不共享后台管理系统（`x-manerger-sys-service`）的用户体系。

### 9.3 建议

在 README "平台目录结构说明" 中补充：

```
x-generator-manager      代码生成器（独立工具，含 dryRun 预检）
```

---

## 10. 附录：TomatoCouponUserServiceImpl FIXME 跨模块缺口

### 10.1 位置

```
x-restful/x-business-goods/src/main/java/
  com/company/business/goods/moudle/service/impl/TomatoCouponUserServiceImpl.java
```

### 10.2 FIXME 内容

第 148–158 行区间包含多处 FIXME/fixme 标注：

```java
//fixme 用户离开时间超过5天                          ← 第 148 行
if (exitDay > TomatoConstant.Common.NUMBER_5) {
    userFansVo.setExitDay(exitDay);
}
//FIXME 查询订单表                                  ← 第 152 行
List<OrderEntity> orderList = orderService.getOrderByUserName(tomatoUserEntity.getUsername());
//fixme 未下单用户                                  ← 第 155 行
if (orderList.size() == TomatoConstant.Common.NUMBER_0) {
    userFansVo.setStatus(TomatoConstant.Common.NUMBER_1);
} else {//fixme 已下单用户，计算收益                ← 第 158 行
    userFansVo.setMoney(show(tomatoUserEntity));
}
```

### 10.3 跨模块缺口分析

| 问题 | 说明 |
|------|------|
| **N+1 查询风险** | 在用户粉丝列表遍历循环中，对每个用户调用 `orderService.getOrderByUserName()`，即每处理一个用户就发一次 DB 查询。当用户量较大时性能堪忧 |
| **跨模块耦合** | `TomatoCouponUserServiceImpl` 位于 `x-business-goods` 模块，但通过 `orderService` 查询订单表——订单数据通常属于交易/订单模块。这种跨模块直接调用绕过了服务边界 |
| **硬编码常量** | `NUMBER_5`、`NUMBER_0`、`NUMBER_1` 等魔法数字通过 `TomatoConstant` 引用，业务语义不直观（5天阈值、0单、1状态） |
| **生成器关联** | 如果代码生成器为业务表生成了 Service 层骨架代码，开发者在此基础上手工添加业务逻辑时，生成器无法预知跨模块依赖关系。生成的代码可能缺少必要的 import 或 Spring Bean 注入 |

### 10.4 本文档定位

> **本节仅作文档化记录，不做代码修复。** 修复涉及 `x-business-goods` 模块的业务逻辑重构和跨模块服务边界设计，超出代码生成器文档范畴。建议由业务团队创建独立 issue 跟踪。

---

*文档结束*
