# Python ORM Parsing — SQLAlchemy & Django

---

## SQLAlchemy (Core / ORM)

### Declarative Base Pattern

```python
class User(Base):
    __tablename__ = "users"
    __table_args__ = {"schema": "public"}

    id = Column(Integer, primary_key=True)
    email = Column(String(255), nullable=False, unique=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    role_id = Column(Integer, ForeignKey("roles.id"), nullable=True)
```

### Parsing Strategy (regex-based, no import needed)

```python
import re, pathlib

TABLE_NAME_RE  = re.compile(r'__tablename__\s*=\s*["\']([^"\']+)["\']')
CLASS_RE       = re.compile(r'^class\s+(\w+)\s*\(', re.MULTILINE)
COLUMN_RE      = re.compile(
    r'(\w+)\s*=\s*(?:mapped_column|Column)\s*\(([^)]*(?:\([^)]*\)[^)]*)*)\)',
    re.MULTILINE
)
FK_RE          = re.compile(r'ForeignKey\s*\(\s*["\']([^"\']+)["\']')

SQLALCHEMY_TYPE_MAP = {
    "Integer": "INT", "BigInteger": "BIGINT", "SmallInteger": "SMALLINT",
    "Float": "FLOAT", "Numeric": "DECIMAL", "Double": "DOUBLE",
    "String": "VARCHAR", "Text": "TEXT", "Unicode": "NVARCHAR",
    "Boolean": "BOOLEAN", "Date": "DATE", "DateTime": "TIMESTAMP",
    "Time": "TIME", "Interval": "INTERVAL", "LargeBinary": "BLOB",
    "JSON": "JSON", "UUID": "UUID", "Enum": "ENUM",
}

def parse_sqlalchemy_file(src: str) -> list[dict]:
    entities = []
    classes = list(CLASS_RE.finditer(src))
    for i, cls_match in enumerate(classes):
        cls_name = cls_match.group(1)
        # Get the class body (up to next class or EOF)
        body_start = cls_match.end()
        body_end   = classes[i + 1].start() if i + 1 < len(classes) else len(src)
        body       = src[body_start:body_end]

        table_name_m = TABLE_NAME_RE.search(body)
        if not table_name_m:
            continue  # Not a model class

        fields = []
        for col_m in COLUMN_RE.finditer(body):
            field_name = col_m.group(1)
            args_str   = col_m.group(2)

            # Extract type
            type_m = re.search(r'\b([A-Z]\w+)\s*(?:\((\d+)(?:,\s*(\d+))?\))?', args_str)
            code_type  = type_m.group(1) if type_m else "Unknown"
            length     = type_m.group(2) if type_m and type_m.group(2) else ""
            scale      = type_m.group(3) if type_m and type_m.group(3) else ""
            sql_type   = SQLALCHEMY_TYPE_MAP.get(code_type, code_type)
            if length:
                sql_type = f"{sql_type}({length}" + (f",{scale})" if scale else ")")

            fk_m = FK_RE.search(args_str)

            fields.append({
                "field_name":  field_name,
                "column_name": re.search(r'name\s*=\s*["\']([^"\']+)["\']', args_str) and
                               re.search(r'name\s*=\s*["\']([^"\']+)["\']', args_str).group(1) or field_name,
                "code_type":   code_type,
                "sql_type":    sql_type,
                "pk":          "primary_key=True" in args_str,
                "nullable":    "nullable=False" not in args_str,
                "unique":      "unique=True" in args_str,
                "length":      length,
                "scale":       scale,
                "default":     re.search(r'default\s*=\s*([^,)]+)', args_str).group(1).strip()
                               if re.search(r'default\s*=\s*([^,)]+)', args_str) else "",
                "relation":    f"FK → {fk_m.group(1)}" if fk_m else "",
                "validation":  "",
                "description": "",
            })

        entities.append({
            "entity_name": cls_name,
            "table_name":  table_name_m.group(1),
            "fields":      fields,
        })
    return entities
```

### SQLAlchemy 2.x `mapped_column` / `Mapped[...]`

Same regex works. Additionally scan:
```python
MAPPED_RE = re.compile(r'(\w+)\s*:\s*Mapped\[([^\]]+)\]\s*=\s*mapped_column\(([^)]*)\)')
```
- Type comes from the `Mapped[T]` annotation — strip `Optional[...]` wrapper to get base type.

---

## Django ORM

### Model Pattern

```python
class Product(models.Model):
    class Meta:
        db_table = "products"

    name        = models.CharField(max_length=200)
    price       = models.DecimalField(max_digits=10, decimal_places=2)
    is_active   = models.BooleanField(default=True)
    category    = models.ForeignKey("Category", on_delete=models.CASCADE, null=True)
    created_at  = models.DateTimeField(auto_now_add=True)
```

### Parsing

```python
DB_TABLE_RE = re.compile(r'db_table\s*=\s*["\']([^"\']+)["\']')
FIELD_RE    = re.compile(
    r'(\w+)\s*=\s*models\.(\w+)\s*\(([^)]*(?:\([^)]*\)[^)]*)*)\)',
    re.MULTILINE
)

DJANGO_TYPE_MAP = {
    "AutoField": "INT AUTO_INCREMENT", "BigAutoField": "BIGINT AUTO_INCREMENT",
    "IntegerField": "INT", "BigIntegerField": "BIGINT",
    "SmallIntegerField": "SMALLINT", "PositiveIntegerField": "INT UNSIGNED",
    "FloatField": "FLOAT", "DecimalField": "DECIMAL",
    "CharField": "VARCHAR", "TextField": "TEXT",
    "BooleanField": "BOOLEAN", "NullBooleanField": "BOOLEAN",
    "DateField": "DATE", "DateTimeField": "TIMESTAMP", "TimeField": "TIME",
    "DurationField": "INTERVAL",
    "BinaryField": "BLOB", "ImageField": "VARCHAR", "FileField": "VARCHAR",
    "JSONField": "JSON", "UUIDField": "UUID",
    "EmailField": "VARCHAR(254)", "URLField": "VARCHAR(200)",
    "SlugField": "VARCHAR(50)",
    "ForeignKey": "INT (FK)", "OneToOneField": "INT (FK)",
    "ManyToManyField": "(relation — join table)",
}

def parse_django_file(src: str) -> list[dict]:
    entities = []
    classes  = list(CLASS_RE.finditer(src))  # reuse CLASS_RE from above
    for i, cls_m in enumerate(classes):
        cls_name   = cls_m.group(1)
        body_start = cls_m.end()
        body_end   = classes[i + 1].start() if i + 1 < len(classes) else len(src)
        body       = src[body_start:body_end]

        if "models.Model" not in src[cls_m.start():body_end]:
            continue

        table_m = DB_TABLE_RE.search(body)
        table_name = table_m.group(1) if table_m else f"{cls_name.lower()}s"

        fields = []
        for f in FIELD_RE.finditer(body):
            fname, ftype, fargs = f.group(1), f.group(2), f.group(3)
            if fname.startswith("_") or ftype == "Meta":
                continue

            mx = re.search(r'max_length\s*=\s*(\d+)', fargs)
            dp = re.search(r'decimal_places\s*=\s*(\d+)', fargs)
            md = re.search(r'max_digits\s*=\s*(\d+)', fargs)
            to = re.search(r'^["\']?(\w+)["\']?', fargs)
            fk_target = to.group(1) if ftype in ("ForeignKey", "OneToOneField", "ManyToManyField") and to else ""

            sql = DJANGO_TYPE_MAP.get(ftype, ftype)
            if mx:
                sql = f"VARCHAR({mx.group(1)})"
            elif md and dp:
                sql = f"DECIMAL({md.group(1)},{dp.group(1)})"

            fields.append({
                "field_name":  fname,
                "column_name": fname if not fk_target else fname + "_id",
                "code_type":   ftype,
                "sql_type":    sql,
                "pk":          ftype in ("AutoField", "BigAutoField"),
                "nullable":    "null=True" in fargs,
                "unique":      "unique=True" in fargs,
                "length":      mx.group(1) if mx else "",
                "default":     re.search(r'default\s*=\s*([^,)]+)', fargs).group(1).strip()
                               if re.search(r'default\s*=\s*([^,)]+)', fargs) else "",
                "relation":    f"FK → {fk_target}" if fk_target else "",
                "validation":  "",
                "description": "",
            })

        entities.append({"entity_name": cls_name, "table_name": table_name, "fields": fields})
    return entities
```
