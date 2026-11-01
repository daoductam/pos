# Java JPA / Hibernate Entity Parsing

## Scanning for Entity Files

```python
import pathlib, re

def find_entity_files(root: str) -> list[pathlib.Path]:
    return [p for p in pathlib.Path(root).rglob("*.java")
            if re.search(r'@Entity\b', p.read_text(encoding="utf-8", errors="ignore"))]
```

## Extracting Class / Table Names

```python
CLASS_PATTERN   = re.compile(r'(?:public|protected)?\s*class\s+(\w+)')
TABLE_PATTERN   = re.compile(r'@Table\s*\(\s*(?:[^)]*\s)?name\s*=\s*"([^"]+)"')
SCHEMA_PATTERN  = re.compile(r'@Table\s*\(\s*(?:[^)]*\s)?schema\s*=\s*"([^"]+)"')

def extract_class_info(src: str) -> dict:
    cls   = CLASS_PATTERN.search(src)
    table = TABLE_PATTERN.search(src)
    return {
        "entity_name": cls.group(1) if cls else "Unknown",
        "table_name":  table.group(1) if table else camel_to_snake(cls.group(1)) if cls else "unknown"
    }

def camel_to_snake(name: str) -> str:
    s = re.sub(r'(?<=[a-z0-9])([A-Z])', r'_\1', name)
    return s.lower()
```

## Extracting Fields

Parse field blocks. A field block looks like:
```
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
@Column(name = "user_id", nullable = false, length = 36)
private Long id;
```

```python
FIELD_BLOCK_PATTERN = re.compile(
    r'((?:@\w+(?:\([^)]*\))?\s*)+)'   # annotations (group 1)
    r'(?:private|protected|public)\s+'
    r'([\w<>,\s]+?)\s+'               # type (group 2)
    r'(\w+)\s*;',                      # field name (group 3)
    re.MULTILINE
)

COLUMN_ATTR = re.compile(r'(\w+)\s*=\s*(?:"([^"]*)"|(\w+))')

def parse_field(annotations: str, java_type: str, field_name: str) -> dict:
    anns = annotations.strip()
    col_match = re.search(r'@Column\(([^)]*)\)', anns)
    col_attrs = {}
    if col_match:
        for m in COLUMN_ATTR.finditer(col_match.group(1)):
            col_attrs[m.group(1)] = m.group(2) or m.group(3)

    return {
        "field_name":   field_name,
        "column_name":  col_attrs.get("name", camel_to_snake(field_name)),
        "code_type":    java_type.strip(),
        "sql_type":     java_type_to_sql(java_type.strip()),
        "pk":           bool(re.search(r'@Id\b', anns)),
        "nullable":     col_attrs.get("nullable", "true").lower() != "false",
        "unique":       col_attrs.get("unique", "false").lower() == "true",
        "length":       col_attrs.get("length", ""),
        "precision":    col_attrs.get("precision", ""),
        "scale":        col_attrs.get("scale", ""),
        "default":      col_attrs.get("columnDefinition", ""),
        "validation":   extract_validations(anns),
        "relation":     extract_relation(anns),
        "description":  "",   # filled from Javadoc separately
    }
```

## Java → SQL Type Mapping

```python
JAVA_SQL_MAP = {
    "Long": "BIGINT", "long": "BIGINT",
    "Integer": "INT", "int": "INT",
    "Short": "SMALLINT", "short": "SMALLINT",
    "Boolean": "BOOLEAN", "boolean": "BOOLEAN",
    "Double": "DOUBLE", "double": "DOUBLE",
    "Float": "FLOAT", "float": "FLOAT",
    "BigDecimal": "DECIMAL",
    "String": "VARCHAR",
    "char": "CHAR",
    "Date": "DATE",
    "LocalDate": "DATE",
    "LocalDateTime": "TIMESTAMP",
    "ZonedDateTime": "TIMESTAMP WITH TIME ZONE",
    "Instant": "TIMESTAMP",
    "byte[]": "BLOB",
    "UUID": "VARCHAR(36)",
}

def java_type_to_sql(java_type: str) -> str:
    base = java_type.split("<")[0].split(".")[-1]
    return JAVA_SQL_MAP.get(base, java_type)
```

## Validation Annotations

```python
VALIDATION_ANNOTATIONS = [
    "@NotNull", "@NotBlank", "@NotEmpty",
    "@Size", "@Min", "@Max",
    "@Pattern", "@Email", "@Positive",
    "@PositiveOrZero", "@Negative", "@NegativeOrZero",
    "@Past", "@PastOrPresent", "@Future", "@FutureOrPresent",
    "@DecimalMin", "@DecimalMax", "@Digits",
]

def extract_validations(annotations: str) -> str:
    found = []
    for ann in VALIDATION_ANNOTATIONS:
        m = re.search(re.escape(ann) + r'(?:\([^)]*\))?', annotations)
        if m:
            found.append(m.group(0))
    return ", ".join(found)
```

## Relationship Annotations

```python
REL_PATTERN = re.compile(
    r'(@(?:OneToOne|OneToMany|ManyToOne|ManyToMany)(?:\([^)]*\))?)'
    r'.*?(?:@JoinColumn\((?:[^)]*name\s*=\s*"([^"]+)")?[^)]*\))?'
    r'.*?(?:private|protected|public)\s+(?:List<|Set<|Optional<)?(\w+)',
    re.DOTALL
)

def extract_relation(annotations: str) -> str:
    m = REL_PATTERN.search(annotations)
    if m:
        return f"{m.group(1)} → {m.group(3) or ''}"
    return ""
```

## Javadoc Comments

```python
JAVADOC_PATTERN = re.compile(r'/\*\*(.*?)\*/', re.DOTALL)

def extract_javadoc_before(src: str, field_pos: int) -> str:
    """Find the last Javadoc comment before field_pos."""
    docs = [(m.start(), m.group(1)) for m in JAVADOC_PATTERN.finditer(src) if m.end() <= field_pos]
    if docs:
        text = docs[-1][1]
        # Strip leading * from each line
        return " ".join(line.strip().lstrip("*").strip() for line in text.splitlines()).strip()
    return ""
```

## Hibernate XML Mapping (.hbm.xml)

```python
import xml.etree.ElementTree as ET

def parse_hbm_xml(path: str) -> list[dict]:
    tree = ET.parse(path)
    root = tree.getroot()
    ns = {"hbm": "urn:nhibernate-mapping-2.2"}  # adjust if needed

    entities = []
    for cls_el in root.iter("class"):
        entity = {
            "entity_name": cls_el.get("name", "").split(".")[-1],
            "table_name":  cls_el.get("table", ""),
            "fields": []
        }
        # ID
        id_el = cls_el.find("id")
        if id_el is not None:
            entity["fields"].append({
                "field_name": id_el.get("name"), "pk": True,
                "column_name": id_el.get("column", id_el.get("name")),
                "code_type": id_el.get("type", ""),
            })
        # Properties
        for prop in cls_el.iter("property"):
            entity["fields"].append({
                "field_name":  prop.get("name"),
                "column_name": prop.get("column", prop.get("name")),
                "code_type":   prop.get("type", ""),
                "nullable":    prop.get("not-null", "false") == "false",
            })
        entities.append(entity)
    return entities
```
