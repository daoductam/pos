# SQL DDL Generation — Dialect Reference

## Dialect Quick Reference

| Feature | MySQL/MariaDB | PostgreSQL | SQL Server | Oracle | SQLite | H2 |
|---|---|---|---|---|---|---|
| Quote char | `` ` `` | `"` | `[` `]` | `"` | `"` | `"` |
| Auto-increment | `AUTO_INCREMENT` | `SERIAL` / `GENERATED ALWAYS AS IDENTITY` | `IDENTITY(1,1)` | `GENERATED ALWAYS AS IDENTITY` | `AUTOINCREMENT` | `AUTO_INCREMENT` / `IDENTITY` |
| String type | `VARCHAR(n)` | `VARCHAR(n)` | `NVARCHAR(n)` | `VARCHAR2(n)` | `TEXT` | `VARCHAR(n)` |
| Boolean | `TINYINT(1)` | `BOOLEAN` | `BIT` | `NUMBER(1)` | `INTEGER` | `BOOLEAN` |
| Timestamp | `TIMESTAMP` | `TIMESTAMP` | `DATETIME2` | `TIMESTAMP` | `TEXT` | `TIMESTAMP` |
| Table suffix | `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4` | *(none)* | *(none)* | *(none)* | *(none)* | *(none)* |

---

## Java → SQL Type Map by Dialect

| Java Type | MySQL | PostgreSQL | SQL Server | Oracle |
|---|---|---|---|---|
| `Long` / `long` | `BIGINT` | `BIGINT` | `BIGINT` | `NUMBER(19)` |
| `Integer` / `int` | `INT` | `INTEGER` | `INT` | `NUMBER(10)` |
| `Short` / `short` | `SMALLINT` | `SMALLINT` | `SMALLINT` | `NUMBER(5)` |
| `Boolean` / `boolean` | `TINYINT(1)` | `BOOLEAN` | `BIT` | `NUMBER(1)` |
| `Double` / `double` | `DOUBLE` | `DOUBLE PRECISION` | `FLOAT` | `BINARY_DOUBLE` |
| `Float` / `float` | `FLOAT` | `REAL` | `REAL` | `BINARY_FLOAT` |
| `BigDecimal` | `DECIMAL(p,s)` | `NUMERIC(p,s)` | `DECIMAL(p,s)` | `NUMBER(p,s)` |
| `String` | `VARCHAR(n)` | `VARCHAR(n)` | `NVARCHAR(n)` | `VARCHAR2(n)` |
| `LocalDate` | `DATE` | `DATE` | `DATE` | `DATE` |
| `LocalDateTime` | `DATETIME` | `TIMESTAMP` | `DATETIME2` | `TIMESTAMP` |
| `ZonedDateTime` | `TIMESTAMP` | `TIMESTAMP WITH TIME ZONE` | `DATETIMEOFFSET` | `TIMESTAMP WITH TIME ZONE` |
| `Instant` | `TIMESTAMP` | `TIMESTAMP` | `DATETIME2` | `TIMESTAMP` |
| `UUID` | `VARCHAR(36)` | `UUID` | `UNIQUEIDENTIFIER` | `VARCHAR2(36)` |
| `byte[]` + `@Lob` | `LONGBLOB` | `BYTEA` | `VARBINARY(MAX)` | `BLOB` |
| `String` + `@Lob` | `LONGTEXT` | `TEXT` | `NVARCHAR(MAX)` | `CLOB` |

---

## MySQL / MariaDB Template

```sql
-- ==========================================
-- Table: {table_name}  (Entity: {entity_name})
-- ==========================================
DROP TABLE IF EXISTS `{table_name}`;

CREATE TABLE `{table_name}` (
{column_defs},
    PRIMARY KEY (`{pk_col}`){unique_constraints}{index_defs}
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='{entity_comment}';

{fk_alters}
```

Column definition format:
```
    `{col_name}` {sql_type} [NOT NULL] [DEFAULT {val}] [AUTO_INCREMENT] [COMMENT '{description}']
```

Unique constraint (inline or separate `UNIQUE KEY`):
```sql
    UNIQUE KEY `uq_{table}_{col}` (`{col}`)
```

FK alter (emitted after all CREATE TABLEs):
```sql
ALTER TABLE `{child_table}`
    ADD CONSTRAINT `fk_{child}_{parent}`
    FOREIGN KEY (`{fk_col}`) REFERENCES `{parent_table}` (`{parent_pk}`)
    ON DELETE RESTRICT ON UPDATE CASCADE;
```

---

## PostgreSQL Template

```sql
-- ==========================================
-- Table: {table_name}  (Entity: {entity_name})
-- ==========================================
DROP TABLE IF EXISTS "{table_name}" CASCADE;

CREATE TABLE "{table_name}" (
{column_defs},
    CONSTRAINT "pk_{table_name}" PRIMARY KEY ("{pk_col}"){unique_constraints}
);

COMMENT ON TABLE "{table_name}" IS '{entity_comment}';
{column_comments}
{fk_alters}
```

Column definition:
```
    "{col_name}" {sql_type} [NOT NULL] [DEFAULT {val}]
```

Column comment:
```sql
COMMENT ON COLUMN "{table}"."{col}" IS '{description}';
```

Unique:
```sql
    CONSTRAINT "uq_{table}_{col}" UNIQUE ("{col}")
```

FK:
```sql
ALTER TABLE "{child_table}"
    ADD CONSTRAINT "fk_{child}_{parent}"
    FOREIGN KEY ("{fk_col}") REFERENCES "{parent_table}" ("{parent_pk}");
```

---

## SQL Server Template

```sql
-- ==========================================
-- Table: {table_name}  (Entity: {entity_name})
-- ==========================================
IF OBJECT_ID(N'[dbo].[{table_name}]', N'U') IS NOT NULL
    DROP TABLE [dbo].[{table_name}];
GO

CREATE TABLE [dbo].[{table_name}] (
{column_defs},
    CONSTRAINT [PK_{table_name}] PRIMARY KEY CLUSTERED ([{pk_col}] ASC){unique_constraints}
);
GO
{fk_alters}
```

Column definition:
```
    [{col_name}] {sql_type} [NOT NULL | NULL] [DEFAULT ({val})] [IDENTITY(1,1)]
```

---

## Python Code: DDL Generator

```python
import re

def camel_to_snake(name: str) -> str:
    s = re.sub(r'(?<=[a-z0-9])([A-Z])', r'_\1', name)
    return s.lower()

DIALECT_TYPES = {
    "mysql": {
        "Long": "BIGINT", "Integer": "INT", "Short": "SMALLINT",
        "Boolean": "TINYINT(1)", "Double": "DOUBLE", "Float": "FLOAT",
        "BigDecimal": "DECIMAL({p},{s})", "String": "VARCHAR({n})",
        "LocalDate": "DATE", "LocalDateTime": "DATETIME",
        "ZonedDateTime": "TIMESTAMP", "Instant": "TIMESTAMP",
        "UUID": "VARCHAR(36)", "default": "VARCHAR(255)",
    },
    "postgresql": {
        "Long": "BIGINT", "Integer": "INTEGER", "Short": "SMALLINT",
        "Boolean": "BOOLEAN", "Double": "DOUBLE PRECISION", "Float": "REAL",
        "BigDecimal": "NUMERIC({p},{s})", "String": "VARCHAR({n})",
        "LocalDate": "DATE", "LocalDateTime": "TIMESTAMP",
        "ZonedDateTime": "TIMESTAMP WITH TIME ZONE", "Instant": "TIMESTAMP",
        "UUID": "UUID", "default": "TEXT",
    },
    "sqlserver": {
        "Long": "BIGINT", "Integer": "INT", "Short": "SMALLINT",
        "Boolean": "BIT", "Double": "FLOAT", "Float": "REAL",
        "BigDecimal": "DECIMAL({p},{s})", "String": "NVARCHAR({n})",
        "LocalDate": "DATE", "LocalDateTime": "DATETIME2",
        "ZonedDateTime": "DATETIMEOFFSET", "Instant": "DATETIME2",
        "UUID": "UNIQUEIDENTIFIER", "default": "NVARCHAR(255)",
    },
    "h2": {  # Good for Spring test environments
        "Long": "BIGINT", "Integer": "INT", "Short": "SMALLINT",
        "Boolean": "BOOLEAN", "Double": "DOUBLE", "Float": "FLOAT",
        "BigDecimal": "DECIMAL({p},{s})", "String": "VARCHAR({n})",
        "LocalDate": "DATE", "LocalDateTime": "TIMESTAMP",
        "ZonedDateTime": "TIMESTAMP WITH TIME ZONE", "Instant": "TIMESTAMP",
        "UUID": "UUID", "default": "VARCHAR(255)",
    },
}

def resolve_sql_type(java_type: str, length: str, precision: str, scale: str,
                     dialect: str = "mysql") -> str:
    base = java_type.split("<")[0].split(".")[-1]
    type_map = DIALECT_TYPES.get(dialect, DIALECT_TYPES["mysql"])
    sql = type_map.get(base, type_map["default"])
    
    # Clean up empty strings - use defaults instead of ''
    n = length.strip() if length and length.strip() else "255"
    p = precision.strip() if precision and precision.strip() else "10"
    s = scale.strip() if scale and scale.strip() else "2"
    
    # Only apply formatting if the type template actually needs it
    if "{n}" in sql:
        return sql.format(n=n)
    elif "{p}" in sql and "{s}" in sql:
        return sql.format(p=p, s=s)
    else:
        return sql


def get_quote(dialect: str) -> tuple[str, str]:
    if dialect == "mysql":    return "`", "`"
    if dialect == "sqlserver": return "[", "]"
    return '"', '"'


def generate_ddl(entities: list[dict], dialect: str = "mysql",
                 drop_table: bool = True, include_fk: bool = True,
                 include_index: bool = False) -> str:
    q0, q1 = get_quote(dialect)
    lines = []
    fk_lines = []

    for entity in entities:
        tbl  = entity["table_name"]
        name = entity["entity_name"]
        lines.append(f"-- {'=' * 42}")
        lines.append(f"-- Table: {tbl}  (Entity: {name})")
        lines.append(f"-- {'=' * 42}")

        if drop_table:
            if dialect == "mysql":
                lines.append(f"DROP TABLE IF EXISTS {q0}{tbl}{q1};")
            elif dialect == "postgresql":
                lines.append(f'DROP TABLE IF EXISTS "{tbl}" CASCADE;')
            elif dialect == "sqlserver":
                lines.append(f"IF OBJECT_ID(N'[dbo].[{tbl}]', N'U') IS NOT NULL")
                lines.append(f"    DROP TABLE [dbo].[{tbl}];")
                lines.append("GO")
            else:
                lines.append(f'DROP TABLE IF EXISTS "{tbl}";')

        col_defs = []
        pk_cols  = []
        uq_cols  = []

        for f in entity.get("fields", []):
            if "(relation" in f.get("sql_type", ""):
                continue  # skip virtual relation fields

            col  = f.get("column_name", "")
            if not col or not col.strip():  # Skip fields with no column name
                continue
            
            jtype = f.get("code_type", "String")
            sql_type = resolve_sql_type(
                jtype, 
                f.get("length", ""), 
                f.get("precision", ""),
                f.get("scale", ""), 
                dialect
            )

            parts = [f"    {q0}{col}{q1} {sql_type}"]

            if not f.get("nullable", True):
                parts.append("NOT NULL")

            if f.get("pk") and dialect in ("mysql", "h2"):
                parts.append("AUTO_INCREMENT" if dialect == "mysql" else "AUTO_INCREMENT")

            desc = f.get("description", "")
            if desc and desc.strip() and dialect == "mysql":  # Only add comment if description exists
                safe = desc.replace("'", "''")[:100]
                parts.append(f"COMMENT '{safe}'")

            col_defs.append(" ".join(parts))

            if f.get("pk"):
                pk_cols.append(col)
            if f.get("unique") and not f.get("pk"):
                uq_cols.append(col)

            # FK alter statements
            if include_fk and f.get("relation"):
                rel = f.get("relation", "")
                if "FK →" in rel or "@ManyToOne" in rel or "@OneToOne" in rel:
                    # Extract reference table from relation string
                    ref = rel.replace("FK →", "").replace("@ManyToOne →", "").replace("@OneToOne →", "").strip()
                    if ref and ref.strip():  # Only create FK if reference exists
                        ref_table = camel_to_snake(ref.split(".")[0]) if "." in ref else camel_to_snake(ref)
                        ref_col = ref.split(".")[-1] if "." in ref else "id"
                        
                        if ref_table and ref_table.strip():  # Verify ref_table is not empty
                            fk_lines.append(
                                f"ALTER TABLE {q0}{tbl}{q1}\n"
                                f"    ADD CONSTRAINT {q0}fk_{tbl}_{col}{q1}\n"
                                f"    FOREIGN KEY ({q0}{col}{q1}) REFERENCES {q0}{ref_table}{q1} ({q0}{ref_col}{q1});"
                            )

        if pk_cols:
            pk_str = ", ".join(f"{q0}{c}{q1}" for c in pk_cols)
            col_defs.append(f"    PRIMARY KEY ({pk_str})")

        for uq_col in uq_cols:
            col_defs.append(f"    UNIQUE KEY {q0}uq_{tbl}_{uq_col}{q1} ({q0}{uq_col}{q1})")

        suffix = " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4" if dialect == "mysql" else ""
        lines.append(f"CREATE TABLE {q0}{tbl}{q1} (")
        lines.append(",\n".join(col_defs))
        lines.append(f"){suffix};")
        lines.append("")

        # PostgreSQL column comments
        if dialect == "postgresql":
            for f in entity.get("fields", []):
                desc = f.get("description", "")
                if desc and desc.strip():  # Only add comment if description exists and is not empty
                    safe = desc.replace("'", "''")[:200]
                    col_name = f.get("column_name", "")
                    if col_name and col_name.strip():  # Verify column name exists
                        lines.append(f"COMMENT ON COLUMN \"{tbl}\".\"{col_name}\" IS '{safe}';")
            if any(f.get("description", "").strip() for f in entity.get("fields", [])):
                lines.append("")  # Only add blank line if we added comments

        if dialect == "sqlserver":
            lines.append("GO")
            lines.append("")

    if fk_lines:
        lines.append("-- ==========================================")
        lines.append("-- Foreign Key Constraints")
        lines.append("-- ==========================================")
        lines.extend(fk_lines)

    return "\n".join(lines)
```
