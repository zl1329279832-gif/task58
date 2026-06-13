package com.company.generator.manager.common.dao;

import com.company.generator.manager.common.data.DbColumnInfo;
import com.company.generator.manager.common.data.DbTableInfo;
import com.company.generator.manager.common.definition.SqlUtils;
import com.company.manerger.sys.common.utils.StringUtils;
import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.sql.*;
import java.util.*;

/**
 * @description: 数据工具类
 */
public class DbHelper implements IDbHelper {

    private Connection connection;
    private String dbType;
    private String dbName;

    /** MySQL SQL 关键字集合，用于判断是否需要反引号转义 */
    private static final Set<String> MYSQL_KEYWORDS = new HashSet<>(Arrays.asList(
            "ORDER", "SELECT", "GROUP", "INDEX", "KEY", "TABLE", "COLUMN", "DELETE",
            "UPDATE", "INSERT", "FROM", "WHERE", "JOIN", "LEFT", "RIGHT", "INNER",
            "OUTER", "ON", "AS", "BY", "HAVING", "LIMIT", "OFFSET", "UNION", "ALL",
            "CREATE", "DROP", "ALTER", "ADD", "SET", "VALUES", "INTO", "BETWEEN",
            "LIKE", "IN", "IS", "NOT", "NULL", "AND", "OR", "EXISTS", "DESC", "ASC",
            "PRIMARY", "FOREIGN", "REFERENCES", "CONSTRAINT", "UNIQUE", "CHECK",
            "DEFAULT", "GRANT", "REVOKE", "STATUS", "COMMENT", "PARTITION", "RANGE",
            "LIST", "HASH", "TRIGGER", "PROCEDURE", "FUNCTION", "VIEW", "DATABASE",
            "SCHEMA", "USE", "SHOW", "DESCRIBE", "EXPLAIN", "LOCK", "UNLOCK",
            "READ", "WRITE", "START", "TRANSACTION", "COMMIT", "ROLLBACK", "SAVEPOINT",
            "RELEASE", "TO", "USER", "ROLE", "OPTION", "LEVEL", "ISOLATION",
            "REPEATABLE", "COMMITTED", "UNCOMMITTED", "SERIALIZABLE", "USAGE",
            "RENAME", "REPLACE", "CALL", "CONDITION", "HANDLER", "CONTINUE", "EXIT",
            "UNDO", "LOOP", "WHILE", "IF", "THEN", "ELSE", "ELSEIF", "END",
            "RETURN", "RETURNS", "CONTAINS", "MATCHED", "FULLTEXT", "SPATIAL",
            "ENGINE", "CHARSET", "CHARACTER", "COLLATE", "AUTO_INCREMENT", "UNSIGNED",
            "ZEROFILL", "BINARY", "VARBINARY", "BLOB", "TEXT", "INT", "INTEGER",
            "BIGINT", "SMALLINT", "TINYINT", "MEDIUMINT", "FLOAT", "DOUBLE", "DECIMAL",
            "NUMERIC", "DATE", "TIME", "TIMESTAMP", "DATETIME", "YEAR", "BIT",
            "BOOLEAN", "BOOL", "ENUM", "SET", "JSON", "GEOMETRY", "POINT",
            "LINESTRING", "POLYGON", "MULTIPOINT", "MULTILINESTRING", "MULTIPOLYGON",
            "GEOMETRYCOLLECTION", "SIGNAL", "RESIGNAL", "DIAGNOSTICS", "CONDITION",
            "CURRENT_DATE", "CURRENT_TIME", "CURRENT_TIMESTAMP", "LOCALTIME",
            "LOCALTIMESTAMP", "UTC_DATE", "UTC_TIME", "UTC_TIMESTAMP",
            "MOD", "DIV", "XOR", "RLIKE", "REGEXP", "SOUNDS", "LEADING", "TRAILING",
            "BOTH", "SECOND_MICROSECOND", "MINUTE_MICROSECOND", "MINUTE_SECOND",
            "HOUR_MICROSECOND", "HOUR_SECOND", "HOUR_MINUTE", "DAY_MICROSECOND",
            "DAY_SECOND", "DAY_MINUTE", "DAY_HOUR", "YEAR_MONTH", "TRUE", "FALSE"
    ));

    public DbHelper(Connection connection,String dbType,String dbName){
        this.connection=connection;
        this.dbType=dbType;
        this.dbName=dbName;
    }

    /**
     * 判断当前是否为 MySQL 数据库
     */
    private boolean isMySQL(String driverName) {
        return driverName != null && driverName.toUpperCase().contains("MYSQL");
    }

    /**
     * 判断当前是否为 Oracle 数据库
     */
    private boolean isOracle(String driverName) {
        return driverName != null && driverName.toUpperCase().contains("ORACLE");
    }

    /**
     * 判断表名或字段名是否为 SQL 关键字，需要反引号转义
     */
    public static boolean isKeyword(String name) {
        if (name == null) return false;
        return MYSQL_KEYWORDS.contains(name.toUpperCase());
    }

    /**
     * 对表名/字段名添加反引号转义（如果是关键字或包含特殊字符）
     */
    public static String quoteIdentifier(String name) {
        if (name == null) return null;
        // 已有反引号则不重复添加
        if (name.startsWith("`") && name.endsWith("`")) return name;
        return "`" + name.replace("`", "``") + "`";
    }

    @Override
    public List<DbTableInfo> getDbTables() {
        List<DbTableInfo> dbTableInfos = new ArrayList<DbTableInfo>();
        Statement stmt = null;
        ResultSet rs = null;
        try {
            connection.setAutoCommit(true);
            String driverName = connection.getMetaData().getDriverName();

            if (isMySQL(driverName)) {
                // MySQL: 使用 information_schema 读取表名和注释，确保准确性
                // 通过 dbName 过滤到当前数据库，避免扫描所有 schema
                String catalog = !StringUtils.isEmpty(dbName) ? dbName : connection.getCatalog();
                String sql = "SELECT TABLE_NAME, TABLE_COMMENT FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_NAME";
                PreparedStatement pstmt = connection.prepareStatement(sql);
                pstmt.setString(1, catalog);
                rs = pstmt.executeQuery();
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    String remarks = rs.getString("TABLE_COMMENT");
                    // MySQL 8 有时返回 "InnoDB free: ..." 等引擎信息作为 comment，过滤掉
                    if (remarks != null && remarks.startsWith("InnoDB free:")) {
                        remarks = "";
                    }
                    DbTableInfo dbTableInfo = new DbTableInfo();
                    dbTableInfo.setTableName(tableName);
                    dbTableInfo.setRemarks(remarks != null ? remarks : "");
                    dbTableInfos.add(dbTableInfo);
                }
                rs.close();
                pstmt.close();
            } else if (isOracle(driverName)) {
                // Oracle
                rs = connection.getMetaData().getTables(null, dbName != null ? dbName.toUpperCase() : null,
                        null, new String[]{"TABLE"});
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    String remarks = rs.getString("REMARKS");
                    DbTableInfo dbTableInfo = new DbTableInfo();
                    dbTableInfo.setTableName(tableName);
                    dbTableInfo.setRemarks(remarks != null ? remarks : "");
                    dbTableInfos.add(dbTableInfo);
                }
            } else {
                // 通用 JDBC
                String catalog = !StringUtils.isEmpty(dbName) ? dbName : null;
                rs = connection.getMetaData().getTables(catalog, null, null, new String[]{"TABLE"});
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    String remarks = rs.getString("REMARKS");
                    DbTableInfo dbTableInfo = new DbTableInfo();
                    dbTableInfo.setTableName(tableName);
                    dbTableInfo.setRemarks(remarks != null ? remarks : "");
                    dbTableInfos.add(dbTableInfo);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeQuietly(rs, stmt);
        }
        return dbTableInfos;
    }

    @Override
    public List<DbColumnInfo> getDbColumnInfo(String tableName) {
        List<DbColumnInfo> columnInfos = new ArrayList<DbColumnInfo>();
        ResultSet rs = null;
        try {
            connection.setAutoCommit(true);
            String driverName = connection.getMetaData().getDriverName();

            // 解析 schema 前缀：tableName 可能是 "schema.table" 格式
            String schemaPrefix = null;
            String pureTableName = tableName;
            if (tableName.contains(".")) {
                String[] parts = tableName.split("\\.", 2);
                schemaPrefix = parts[0];
                pureTableName = parts[1];
            }

            if (isMySQL(driverName)) {
                // MySQL: 使用 information_schema.COLUMNS 获取完整的字段信息
                // 包括注释、自增标志、精度等 JDBC metadata 可能丢失的信息
                String catalog = schemaPrefix != null ? schemaPrefix :
                        (!StringUtils.isEmpty(dbName) ? dbName : connection.getCatalog());

                // 1. 从 information_schema 读取字段基础信息
                String sql = "SELECT COLUMN_NAME, DATA_TYPE, COLUMN_TYPE, CHARACTER_MAXIMUM_LENGTH, "
                        + "NUMERIC_PRECISION, NUMERIC_SCALE, COLUMN_COMMENT, IS_NULLABLE, "
                        + "COLUMN_DEFAULT, COLUMN_KEY, EXTRA, ORDINAL_POSITION "
                        + "FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? "
                        + "ORDER BY ORDINAL_POSITION";
                PreparedStatement pstmt = connection.prepareStatement(sql);
                pstmt.setString(1, catalog);
                pstmt.setString(2, pureTableName);
                rs = pstmt.executeQuery();

                // 收集主键列名
                Set<String> primaryKeys = new HashSet<>();

                while (rs.next()) {
                    String columnName = rs.getString("COLUMN_NAME");
                    String dataType = rs.getString("DATA_TYPE");  // 基础类型，如 "int", "varchar"
                    String columnType = rs.getString("COLUMN_TYPE"); // 完整类型，如 "int unsigned", "varchar(255)"
                    String charMaxLen = rs.getString("CHARACTER_MAXIMUM_LENGTH");
                    String numPrecision = rs.getString("NUMERIC_PRECISION");
                    String numScale = rs.getString("NUMERIC_SCALE");
                    String remarks = rs.getString("COLUMN_COMMENT");
                    String isNullable = rs.getString("IS_NULLABLE");
                    String columnDef = rs.getString("COLUMN_DEFAULT");
                    String columnKey = rs.getString("COLUMN_KEY");
                    String extra = rs.getString("EXTRA");

                    // 确定列大小
                    String columnSize;
                    if (charMaxLen != null) {
                        columnSize = charMaxLen;
                    } else if (numPrecision != null) {
                        columnSize = numPrecision;
                    } else {
                        columnSize = "255";
                    }

                    // 确定小数位数
                    String decimalDigits = numScale != null ? numScale : "0";

                    // 规范化类型名：information_schema 返回小写，如 "bigint", "varchar"
                    // 需要转换为大写以匹配类型映射表
                    String typeName = dataType.toUpperCase();
                    // 处理 unsigned 后缀
                    if (columnType != null && columnType.toLowerCase().contains("unsigned")) {
                        typeName = typeName + " UNSIGNED";
                    }

                    // 处理备注
                    if (remarks == null) {
                        remarks = "";
                    } else {
                        remarks = remarks.replace("'", "");
                    }

                    // 处理默认值
                    if (columnDef != null) {
                        columnDef = columnDef.replace("'", "").trim();
                    }

                    // 是否为主键
                    boolean isPK = "PRI".equals(columnKey);
                    if (isPK) {
                        primaryKeys.add(columnName);
                    }

                    // 是否自增
                    boolean autoIncrement = extra != null && extra.toLowerCase().contains("auto_increment");

                    // 是否允许为空
                    boolean nullable = "YES".equalsIgnoreCase(isNullable);

                    DbColumnInfo info = new DbColumnInfo(
                            columnName.toLowerCase(), typeName, columnSize, remarks,
                            nullable, isPK, false, columnDef, decimalDigits, autoIncrement);
                    columnInfos.add(info);
                }
                rs.close();
                pstmt.close();

                // 2. 补充主键信息（如果 COLUMN_KEY 未正确标记）
                if (primaryKeys.isEmpty()) {
                    try {
                        rs = connection.getMetaData().getPrimaryKeys(catalog, null, pureTableName);
                        while (rs.next()) {
                            String pkCol = rs.getString("COLUMN_NAME");
                            primaryKeys.add(pkCol);
                        }
                        rs.close();
                        // 回填主键标记
                        for (DbColumnInfo col : columnInfos) {
                            if (primaryKeys.contains(col.getColumnName()) ||
                                primaryKeys.contains(col.getColumnName().toUpperCase())) {
                                col.setParmaryKey(true);
                            }
                        }
                    } catch (SQLException e) {
                        // 忽略
                    }
                }

            } else {
                // 非 MySQL: 使用标准 JDBC metadata
                String catalog = schemaPrefix;
                String schema = null;

                if (isOracle(driverName)) {
                    schema = schemaPrefix != null ? schemaPrefix.toUpperCase() :
                            (dbName != null ? dbName.toUpperCase() : null);
                    catalog = null;
                }

                // 获取列信息
                rs = connection.getMetaData().getColumns(catalog, schema, pureTableName, null);
                while (rs.next()) {
                    String columnName = rs.getString("COLUMN_NAME");
                    String typeName = rs.getString("TYPE_NAME");
                    String columnSize = rs.getString("COLUMN_SIZE");
                    String remarks = rs.getString("REMARKS");
                    if (!StringUtils.isEmpty(remarks)) {
                        remarks = remarks.replace("'", "");
                    } else {
                        remarks = "";
                    }

                    // nullable: Oracle 用 NUMERIC 的 NULLABLE 列(1=可空,0=不可空)
                    // MySQL 用 String 的 IS_NULLABLE("YES"/"NO")
                    Boolean nullable;
                    if (isOracle(driverName)) {
                        nullable = rs.getBoolean("NULLABLE");
                    } else {
                        String isNullableStr = rs.getString("IS_NULLABLE");
                        nullable = "YES".equalsIgnoreCase(isNullableStr);
                    }

                    String decimalDigits = rs.getString("DECIMAL_DIGITS");
                    String columnDef = rs.getString("COLUMN_DEF");
                    if (!StringUtils.isEmpty(columnDef)) {
                        columnDef = columnDef.replace("'", "").trim();
                    }

                    // 自增检测：JDBC 4.0+ 有 IS_AUTOINCREMENT 列
                    boolean autoIncrement = false;
                    try {
                        String isAutoInc = rs.getString("IS_AUTOINCREMENT");
                        autoIncrement = "YES".equalsIgnoreCase(isAutoInc);
                    } catch (SQLException e) {
                        // 旧驱动可能不支持此列
                    }

                    DbColumnInfo info = new DbColumnInfo(columnName, typeName, columnSize, remarks,
                            nullable, false, false, columnDef, decimalDigits, autoIncrement);
                    columnInfos.add(info);
                }
                rs.close();

                // 获取主键信息
                Set<String> pkColumns = new HashSet<>();
                rs = connection.getMetaData().getPrimaryKeys(catalog, schema, pureTableName);
                while (rs.next()) {
                    String primaryKey = rs.getString("COLUMN_NAME");
                    if (primaryKey != null) {
                        pkColumns.add(primaryKey);
                    }
                }
                rs.close();
                // 批量设置主键标记（修复原来的覆盖 bug）
                for (DbColumnInfo col : columnInfos) {
                    if (pkColumns.contains(col.getColumnName())) {
                        col.setParmaryKey(true);
                    }
                }

                // 获取外键信息
                Set<String> fkColumns = new HashSet<>();
                rs = connection.getMetaData().getImportedKeys(catalog, schema, pureTableName);
                while (rs.next()) {
                    String fkCol = rs.getString("FKCOLUMN_NAME");
                    if (fkCol != null) {
                        fkColumns.add(fkCol);
                    }
                }
                rs.close();
                // 批量设置外键标记
                for (DbColumnInfo col : columnInfos) {
                    if (fkColumns.contains(col.getColumnName())) {
                        col.setImportedKey(true);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("获取字段信息的时候失败，请将问题反映到维护人员。" + e.getMessage(), e);
        } finally {
            closeQuietly(rs, null);
        }
        return columnInfos;
    }

    @Override
    public void createTable(Map<String, Object> tableInfo) throws TemplateException, IOException {
        String createTableSql = SqlUtils.getSqlUtils(dbType).getSqlByID("createTable").getContent().trim();
        createTableSql = parseSql(createTableSql, tableInfo).trim();
        executeSql(createTableSql);
    }

    @Override
    public void dropTable(String tableName) {
        String dropSql = SqlUtils.getSqlUtils(dbType).getSqlByID("dropTable").getContent().trim();
        // 对表名进行反引号转义，防止关键字冲突
        String safeTableName = quoteIdentifier(tableName);
        dropSql = dropSql.replaceAll("\\$\\{tablename\\}", safeTableName);
        executeSql(dropSql);
    }

    /**
     * @title: executeSql
     * @description: 执行sql
     * @param sql
     * @return: void
     */
    public void executeSql(String sql) {
        Statement stmt = null;
        try {
            connection.setAutoCommit(false);
            stmt = connection.createStatement();
            String[] sqls = sql.split(";");
            if (sqls.length > 1) {
                for (String sqlItem : sqls) {
                    if (!sqlItem.trim().isEmpty()) {
                        stmt.addBatch(sqlItem);
                    }
                }
                stmt.executeBatch();
            } else {
                stmt.execute(sql);
            }
            connection.commit();
        } catch (Exception ex) {
            ex.printStackTrace();
            try {
                connection.rollback();
            } catch (SQLException e) {
                e.printStackTrace();
            }
            throw new RuntimeException(ex);
        } finally {
            closeQuietly(null, stmt);
        }
    }

    private String parseSql(String sql, Map<String, Object> rootMap) throws TemplateException, IOException {
        String tempname = StringUtils.hashKeyForDisk(sql);
        @SuppressWarnings("deprecation")
        Configuration configuration = new Configuration();
        configuration.setNumberFormat("#");
        StringTemplateLoader stringLoader = new StringTemplateLoader();
        stringLoader.putTemplate(tempname, sql);
        configuration.setTemplateLoader(stringLoader);
        @SuppressWarnings("deprecation")
        freemarker.template.Template template = new freemarker.template.Template(tempname, new StringReader(sql), configuration);
        StringWriter stringWriter = new StringWriter();
        template.process(rootMap, stringWriter);
        sql = stringWriter.toString();
        return sql;
    }

    /**
     * 安全关闭 ResultSet 和 Statement，不关闭 Connection（由调用方管理）
     */
    private void closeQuietly(ResultSet rs, Statement stmt) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                // ignore
            }
        }
        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException e) {
                // ignore
            }
        }
    }
}
