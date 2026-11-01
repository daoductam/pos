# SQL DDL Parsing

## Approach: Use `sqlparse` (preferred) or regex fallback

### With sqlparse

```python
import sqlparse
from sqlparse.sql import Statement, Parenthesis, Identifier
from sqlparse.tokens import Keyword, DDL

def parse_ddl(sql_text: str) -> list[dict]:
    entities = []
    statements = sqlparse.parse(sql_text)
    for stmt in statements:
        if stmt.get_type() != "CREATE":
            continue
        flat = [t for t in stmt.flatten()]
        tokens = [t for t in stmt.tokens if not t.is_whitespace]
        # Find table name
        table_name = None
        for i, tok in enumerate(tokens):
            if tok.ttype is DDL and tok.normalized.upper() == "CREATE":
                # Look for TABLE keyword then name
                for j in range(i+1, len(tokens)):
                    if tokens[j].ttype is Keyword and tokens[j].normalized.upper() == "TABLE":
                        if j+1 < len(tokens):
                            table_name = str(tokens[j+1]).strip().strip('"').strip('`')
                        break
                break
        if not table_name:
            continue
        # Find column definitions in parentheses
        for tok in stmt.tokens:
            if isinstance(tok, Parenthesis):
                fields = parse_column_defs(str(tok)[1:-1])
                entities.append({"entity_name": table_name, "table_name": table_name, "fields": fields})
    return entities
```

### Regex Fallback (no sqlparse)

```python
import re

CREATE_TABLE_RE = re.compile(
    r'CREATE\s+(?:TEMPORARY\s+)?TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?'
    r'(?:`?[\w.]+`?\.)?`?(\w+)`?\s*\(([^;]+?)\)\s*'
    r'(?:ENGINE|DEFAULT|COMMENT|;|$)',
    re.IGNORECASE | re.DOTALL
)

def parse_ddl_regex(sql_text: str) -> list[dict]:
    entities = []
    for m in CREATE_TABLE_RE.finditer(sql_text):
        table_name = m.group(1)
        body       = m.group(2)
        fields     = parse_column_defs(body)
        entities.append({"entity_name": table_name, "table_name": table_name, "fields": fields})
    return entities
```

### Column Definition Parser

```python
COL_DEF_RE = re.compile(
    r'^\s*`?(\w+)`?\s+'              # column name
    r'(\w+)'                          # data type keyword
    r'(?:\(([^)]+)\))?'              # optional (length) or (precision,scale)
    r'(.*?)$',                        # rest of definition
    re.IGNORECASE | re.MULTILINE
)

SQL_KEYWORDS = {"PRIMARY", "UNIQUE", "INDEX", "KEY", "CONSTRAINT", "FOREIGN",
                "CHECK", "FULLTEXT", "SPATIAL"}

def parse_column_defs(body: str) -> list[dict]:
    # Split on commas not inside parens
    lines = split_columns(body)
    fields = []
    pks = set()

    for line in lines:
        line = line.strip()
        if not line:
            continue
        upper = line.upper().lstrip()

        # Table constraints
        if any(upper.startswith(k) for k in SQL_KEYWORDS):
            if "PRIMARY KEY" in upper:
                pk_cols = re.findall(r'`?(\w+)`?', line.split("(", 1)[-1].rstrip(")"))
                pks.update(pk_cols)
            continue

        m = COL_DEF_RE.match(line)
        if not m:
            continue
        col_name = m.group(1)
        sql_type = m.group(2).upper()
        size_str = m.group(3) or ""
        rest     = m.group(4) or ""

        precision, scale, length = "", "", ""
        if "," in size_str:
            parts = size_str.split(",")
            precision, scale = parts[0].strip(), parts[1].strip()
        elif size_str:
            length = size_str.strip()
            sql_type = f"{sql_type}({length})"

        # Comment
        comment_m = re.search(r"COMMENT\s+'([^']*)'", rest, re.IGNORECASE)

        default_m = re.search(r"DEFAULT\s+((?:'[^']*'|\S+))", rest, re.IGNORECASE)

        fk_target = ""  # filled in second pass from FOREIGN KEY constraints

        fields.append({
            "field_name":  col_name,
            "column_name": col_name,
            "code_type":   sql_type,
            "sql_type":    sql_type + (f"({precision},{scale})" if precision else ""),
            "pk":          "PRIMARY KEY" in rest.upper(),
            "nullable":    "NOT NULL" not in rest.upper(),
            "unique":      "UNIQUE" in rest.upper(),
            "length":      length,
            "precision":   precision,
            "scale":       scale,
            "default":     default_m.group(1).strip("'") if default_m else "",
            "relation":    fk_target,
            "auto_increment": "AUTO_INCREMENT" in rest.upper() or "AUTOINCREMENT" in rest.upper(),
            "validation":  "",
            "description": comment_m.group(1) if comment_m else "",
        })

    # Apply table-level PRIMARY KEY
    for f in fields:
        if f["field_name"] in pks:
            f["pk"] = True

    return fields


def split_columns(body: str) -> list[str]:
    """Split column definitions on commas, ignoring commas inside parentheses."""
    parts, depth, current = [], 0, []
    for ch in body:
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
        if ch == "," and depth == 0:
            parts.append("".join(current).strip())
            current = []
        else:
            current.append(ch)
    if current:
        parts.append("".join(current).strip())
    return parts
```

## Multi-dialect Notes

| Dialect | Auto-increment syntax | Quote char |
|---|---|---|
| MySQL / MariaDB | `AUTO_INCREMENT` | backtick `` ` `` |
| PostgreSQL | `SERIAL` / `BIGSERIAL` or `GENERATED ALWAYS AS IDENTITY` | `"double quotes"` |
| SQLite | `AUTOINCREMENT` | none / `"` |
| SQL Server | `IDENTITY(1,1)` | `[square brackets]` |
| Oracle | `GENERATED BY DEFAULT AS IDENTITY` | `"double quotes"` |

Strip `[`, `]`, `` ` `` and `"` from identifiers before storing.

## FOREIGN KEY Resolution

```python
FK_CONSTRAINT_RE = re.compile(
    r'FOREIGN\s+KEY\s*\(`?(\w+)`?\)\s*REFERENCES\s+`?(\w+)`?\s*\(`?(\w+)`?\)',
    re.IGNORECASE
)

def resolve_fks(fields: list[dict], body: str) -> list[dict]:
    fk_map = {}
    for m in FK_CONSTRAINT_RE.finditer(body):
        fk_map[m.group(1)] = f"FK → {m.group(2)}.{m.group(3)}"
    for f in fields:
        if f["field_name"] in fk_map:
            f["relation"] = fk_map[f["field_name"]]
    return fields
```
