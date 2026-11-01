# TypeScript ORM Parsing — TypeORM, Prisma, Sequelize

---

## TypeORM

### Entity Pattern

```typescript
@Entity("orders")
export class Order {
    @PrimaryGeneratedColumn("uuid")
    id: string;

    @Column({ type: "varchar", length: 100, nullable: false })
    name: string;

    @Column({ type: "decimal", precision: 10, scale: 2, default: 0 })
    total: number;

    @ManyToOne(() => Customer, customer => customer.orders)
    @JoinColumn({ name: "customer_id" })
    customer: Customer;
}
```

### Python Parsing (regex)

```python
import re

ENTITY_RE   = re.compile(r'@Entity\s*\(\s*(?:["\']([^"\']+)["\'])?\s*\)')
CLASS_RE    = re.compile(r'export\s+class\s+(\w+)')
COLUMN_RE   = re.compile(
    r'(@(?:Primary(?:Generated)?Column|Column|CreateDateColumn|UpdateDateColumn|DeleteDateColumn)'
    r'(?:\([^)]*(?:\([^)]*\)[^)]*)*\))?)\s*\n\s*(\w+)\s*:\s*([\w<>\[\]|]+)',
    re.MULTILINE
)
REL_RE      = re.compile(
    r'(@(?:OneToOne|OneToMany|ManyToOne|ManyToMany)\s*\([^)]+\))\s*'
    r'(?:@JoinColumn\s*\(\s*\{[^}]*name\s*:\s*["\']([^"\']+)["\'][^}]*\}\s*\))?\s*'
    r'\n\s*(\w+)\s*:\s*([\w<>\[\]]+)',
    re.MULTILINE
)

TYPEORM_TYPE_MAP = {
    "string": "VARCHAR", "number": "INT", "boolean": "BOOLEAN",
    "Date": "TIMESTAMP", "Buffer": "BLOB",
}
TS_EXPLICIT = {
    "varchar": "VARCHAR", "text": "TEXT", "char": "CHAR",
    "int": "INT", "integer": "INT", "bigint": "BIGINT", "smallint": "SMALLINT",
    "float": "FLOAT", "double": "DOUBLE", "decimal": "DECIMAL", "numeric": "DECIMAL",
    "boolean": "BOOLEAN", "bool": "BOOLEAN",
    "date": "DATE", "datetime": "DATETIME", "timestamp": "TIMESTAMP",
    "time": "TIME", "json": "JSON", "jsonb": "JSONB", "uuid": "UUID",
    "blob": "BLOB", "bytea": "BYTEA", "enum": "ENUM",
}

def parse_typeorm_file(src: str) -> list[dict]:
    entities = []
    entity_m  = ENTITY_RE.search(src)
    class_m   = CLASS_RE.search(src)
    if not entity_m or not class_m:
        return []

    table_name = entity_m.group(1) or camel_to_snake(class_m.group(1))

    fields = []
    for col_m in COLUMN_RE.finditer(src):
        ann, fname, ts_type = col_m.group(1), col_m.group(2), col_m.group(3)
        # Extract explicit type from annotation options
        type_opt = re.search(r'type\s*:\s*["\']([^"\']+)["\']', ann)
        sql_type = TS_EXPLICIT.get(type_opt.group(1), type_opt.group(1).upper()) if type_opt \
                   else TYPEORM_TYPE_MAP.get(ts_type, ts_type)
        length   = re.search(r'length\s*:\s*(\d+)', ann)
        prec     = re.search(r'precision\s*:\s*(\d+)', ann)
        scale    = re.search(r'scale\s*:\s*(\d+)', ann)
        fields.append({
            "field_name":  fname,
            "column_name": re.search(r'name\s*:\s*["\']([^"\']+)["\']', ann) and
                           re.search(r'name\s*:\s*["\']([^"\']+)["\']', ann).group(1) or camel_to_snake(fname),
            "code_type":   ts_type,
            "sql_type":    sql_type,
            "pk":          "PrimaryGeneratedColumn" in ann or "PrimaryColumn" in ann,
            "nullable":    "nullable: true" in ann,
            "unique":      "unique: true" in ann,
            "length":      length.group(1) if length else "",
            "precision":   prec.group(1) if prec else "",
            "scale":       scale.group(1) if scale else "",
            "default":     re.search(r'default\s*:\s*([^,}]+)', ann).group(1).strip()
                           if re.search(r'default\s*:\s*([^,}]+)', ann) else "",
            "relation":    "",
            "validation":  "",
            "description": "",
        })
    for rel_m in REL_RE.finditer(src):
        fields.append({
            "field_name":  rel_m.group(3),
            "column_name": rel_m.group(2) or camel_to_snake(rel_m.group(3)) + "_id",
            "code_type":   rel_m.group(4),
            "sql_type":    "(relation — no column)" if "OneToMany" in rel_m.group(1) or "ManyToMany" in rel_m.group(1) else "INT (FK)",
            "pk": False, "nullable": True, "unique": False,
            "length": "", "precision": "", "scale": "", "default": "",
            "relation":    f"{rel_m.group(1).split('(')[0].strip()} → {rel_m.group(4)}",
            "validation": "", "description": "",
        })

    entities.append({"entity_name": class_m.group(1), "table_name": table_name, "fields": fields})
    return entities
```

---

## Prisma

### Schema Pattern

```prisma
model User {
  id        Int      @id @default(autoincrement())
  email     String   @unique @db.VarChar(255)
  name      String?
  createdAt DateTime @default(now()) @map("created_at")
  posts     Post[]

  @@map("users")
}
```

### Python Parsing

```python
MODEL_RE  = re.compile(r'model\s+(\w+)\s*\{([^}]+)\}', re.DOTALL)
MAP_RE    = re.compile(r'@@map\s*\(\s*"([^"]+)"\s*\)')
FIELD_RE  = re.compile(
    r'^\s*(\w+)\s+([\w\[\]]+)(\?)?(.*)$',
    re.MULTILINE
)

PRISMA_TYPE_MAP = {
    "Int": "INT", "BigInt": "BIGINT", "Float": "FLOAT", "Decimal": "DECIMAL",
    "Boolean": "BOOLEAN", "String": "VARCHAR", "DateTime": "TIMESTAMP",
    "Json": "JSON", "Bytes": "BLOB",
}

def parse_prisma(src: str) -> list[dict]:
    entities = []
    for model_m in MODEL_RE.finditer(src):
        model_name = model_m.group(1)
        body       = model_m.group(2)
        map_m      = MAP_RE.search(body)
        table_name = map_m.group(1) if map_m else camel_to_snake(model_name)

        fields = []
        for f in FIELD_RE.finditer(body):
            fname    = f.group(1)
            ftype    = f.group(2).rstrip("[]")
            optional = bool(f.group(3))  # "?" suffix
            attrs    = f.group(4) or ""

            if fname.startswith("@@") or fname.startswith("//"):
                continue

            is_list = "[]" in f.group(2)
            db_attr = re.search(r'@db\.(\w+)(?:\((\d+)(?:,\s*(\d+))?\))?', attrs)
            sql_type = (db_attr.group(1) + (f"({db_attr.group(2)}" + (f",{db_attr.group(3)})" if db_attr.group(3) else ")") if db_attr.group(2) else ""))
                       if db_attr else PRISMA_TYPE_MAP.get(ftype, ftype))

            col_map = re.search(r'@map\s*\(\s*"([^"]+)"\s*\)', attrs)
            default = re.search(r'@default\s*\(([^)]+)\)', attrs)

            fields.append({
                "field_name":  fname,
                "column_name": col_map.group(1) if col_map else camel_to_snake(fname),
                "code_type":   f.group(2),
                "sql_type":    "(relation — no column)" if is_list else sql_type,
                "pk":          "@id" in attrs,
                "nullable":    optional,
                "unique":      "@unique" in attrs,
                "length":      db_attr.group(2) if db_attr and db_attr.group(2) else "",
                "precision":   "",
                "scale":       db_attr.group(3) if db_attr and db_attr.group(3) else "",
                "default":     default.group(1) if default else "",
                "relation":    f"→ {ftype}" if is_list or (ftype[0].isupper() and ftype not in PRISMA_TYPE_MAP) else "",
                "validation":  "",
                "description": "",
            })
        entities.append({"entity_name": model_name, "table_name": table_name, "fields": fields})
    return entities
```

---

## Sequelize

### Model Pattern (JS/TS)

```javascript
const User = sequelize.define('User', {
  id:    { type: DataTypes.INTEGER, primaryKey: true, autoIncrement: true },
  email: { type: DataTypes.STRING(255), allowNull: false, unique: true },
  score: { type: DataTypes.DECIMAL(10, 2), defaultValue: 0 },
}, { tableName: 'users' });
```

### Python Parsing

```python
SEQ_MODEL_RE = re.compile(
    r"(?:sequelize\.define|new\s+Sequelize\.Model)\s*\(\s*['\"](\w+)['\"].*?tableName['\"]?\s*:\s*['\"]([^'\"]+)['\"]",
    re.DOTALL
)
SEQ_FIELD_RE = re.compile(
    r"(\w+)\s*:\s*\{([^}]+)\}",
    re.MULTILINE
)
DATATYPE_RE = re.compile(r"DataTypes\.(\w+)(?:\(([^)]+)\))?")

SEQUELIZE_TYPE_MAP = {
    "STRING": "VARCHAR", "TEXT": "TEXT", "CITEXT": "CITEXT",
    "INTEGER": "INT", "BIGINT": "BIGINT", "FLOAT": "FLOAT",
    "DOUBLE": "DOUBLE", "DECIMAL": "DECIMAL", "BOOLEAN": "BOOLEAN",
    "DATE": "TIMESTAMP", "DATEONLY": "DATE", "TIME": "TIME",
    "BLOB": "BLOB", "UUID": "UUID", "JSON": "JSON", "JSONB": "JSONB",
    "ENUM": "ENUM", "ARRAY": "ARRAY",
}
```
