# MO Database Description Template — Format Reference

This is the user's standard template format (`MO-Mô_tả_database.xlsx`).
When the user says "fill my template" or "add to my DB description file", use this spec exactly.

---

## Workbook Structure

| Sheet | Purpose |
|---|---|
| `Changelog` | History of changes — columns: Thời gian, Người chỉnh sửa, Loại thay đổi, Nội dung |
| `Mục lục` | Index / table of contents — one row per table |
| `<table_name>` | One sheet per database table, named exactly after the table |

---

## Per-Table Sheet Layout

```
Row 0:  [col0="Table name"] [col1=empty] [col2="public.<table_name>"] [rest=empty]
Row 1:  (empty)
Row 2:  (empty)   ← some sheets skip rows 1-2, use row 2 directly as header
Row 3 (HEADER):
  col0="No"  col1="Name"  col2="Type"  col3="Null (Not/null)"  col4="Key (PK/FK)"
  col5=empty  col6="Data default"  col7="Description"  col8="Note"
Row 4 (sub-header for Key col):
  col4="Key"  col5="Table link"  (rest empty)
Row 5+: DATA ROWS — one row per field
```

### Column Mapping (0-indexed)

| Col | Header | Maps to |
|---|---|---|
| 0 | `No` | Row number (sequential integer, or blank for sub-fields like JSON keys) |
| 1 | `Name` | Column/field name |
| 2 | `Type` | SQL data type (e.g. `int8`, `varchar(255)`, `timestamp`, `bool`, `text`) |
| 3 | `Null (Not/null)` | `NOT` or `Not null` if NOT NULL; blank if nullable |
| 4 | `Key (PK/FK)` | `PK` if primary key; `FK` if foreign key; blank otherwise |
| 5 | *(Table link)* | Referenced table name for FK (e.g. `public.merchant`); blank if not FK |
| 6 | `Data default` | Default value (e.g. `False`, `now()`, `nextval('...')`, `9999-12-31 00:00:00.000`) |
| 7 | `Description` | Vietnamese description of the field |
| 8 | `Note` | Additional notes (UNIQUE, INDEX, deprecation warnings, enum values, etc.) |

### Examples from real sheets

```
No | Name          | Type         | Null    | Key | Table link      | Default | Description              | Note
 1 | id            | int8         | NOT     | PK  |                 |         |                          |
 2 | merchant_id   | int8         | NOT     | FK  | public.merchant |         | Id của merchant          |
 3 | status        | int8         | NOT     |     |                 |         | Trạng thái: 0-inactive, 1-active |
 4 | created_at    | timestamp    |         |     |                 |         | Ngày tạo                 |
 5 | is_deleted    | bool         |         |     |                 | False   | Trạng thái xoá           | true: Xóa mềm\nfalse: Active
```

### Null value convention
- NOT NULL → write `NOT` in col3 (some sheets use `Not null` — match the existing style in the sheet)
- Nullable → leave col3 blank (NaN)

### Key conventions
- PK: col4=`PK`, col5=blank
- FK: col4=`FK`, col5=referenced table (e.g. `public.merchant(id)` or just `public.merchant`)
- Unique index only (no FK/PK): col4=blank, col8=`UNIQUE` or `UNIQUE INDEX USING BTREE`
- No constraint: col4=blank, col5=blank

---

## "Mục lục" Sheet Columns

```
col0: sequential number
col1: TABLE_NAME
col2: TABLE_ID
col3: DESCRIPTION  (Vietnamese table description)
col4: NOTE  (e.g. "Không sử dụng", ticket reference)
col5: TABLE_PATH
col6: SOURCE
col7: SOURCE_SERVER_NAME
col8: SOURCE_SERVER_SCHEMA
... (remaining columns mostly blank for new tables)
```

When adding a new table, append a row to Mục lục with at minimum: number, TABLE_NAME, DESCRIPTION.

---

## Changelog Sheet Columns

```
col0: Thời gian (datetime)
col1: Người chỉnh sửa (editor name/code)
col2: Loại thay đổi (A=Add, M=Modify)
col3: Nội dung (change description in Vietnamese)
```

When adding new tables or fields, append a row to Changelog.

---

## Common SQL Types Used in This Template

| PostgreSQL type | Used as |
|---|---|
| `int4` / `int` | Integer 32-bit |
| `int8` | Integer 64-bit (BIGINT) |
| `serial4` | Auto-increment int (PK) |
| `bigserial` | Auto-increment bigint (PK) |
| `varchar(n)` | Variable string with length |
| `varchar` | Variable string, no limit |
| `text` | Unlimited text |
| `bool` / `boolean` | Boolean |
| `timestamp` | Timestamp without timezone |
| `timestamptz` / `timestamp with time zone` | Timestamp with timezone |
| `json` / `jsonb` | JSON |
| `numeric(p,s)` | Decimal |
| `_int4` | Integer array |

---

## Formatting Rules (match existing sheets exactly)

- No special cell background colors in data rows (plain white)
- Row 0 (table name row): plain, no formatting required
- Header row (row 3): plain — no bold, no color in original
- Column widths: vary per sheet, auto-fit is acceptable
- No borders required on data rows (original has no borders)
- Vietnamese text in Description/Note columns — preserve as-is

---

## Notes on Special Rows

Some sheets include sub-field rows for JSON columns (e.g. `person_info text` field followed by
indented sub-rows for `name`, `birthDay`, `identificationType` etc.). These have `NaN` in the
`No` column (blank row number) and no type/key/null. Include them after the parent field row.

Some sheets have the table name in row 0 col0 as `Table name`, others omit it.
Always write `Table name` in col0, row 0 to match the majority format.
