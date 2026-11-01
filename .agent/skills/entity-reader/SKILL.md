---
name: entity-reader
description: >
  Use this skill whenever the user works with database entity classes or schema files 
  and wants to extract, document, export, or generate something from them. 
  ALWAYS trigger when: user uploads entity/model files (.java, .py, .ts, .sql); 
  mentions @Entity, @Table, @Column, models.Model, or CREATE TABLE; says 
  "read my entities", "document my schema", "export to Excel/CSV/Word", 
  "fill my template", "generate SQL/DDL", "data dictionary", or similar phrases 
  in any language including Vietnamese ("điền template", "tạo data dictionary"). 
  Primary: Spring Boot/JPA/Hibernate. Also supports: Django, SQLAlchemy, TypeORM, 
  Prisma, Sequelize, and raw SQL DDL. Use even for a single entity file.
---

# Entity / Database Reader Skill

**Primary focus: Spring Boot JPA / Hibernate.**
Also supports: SQLAlchemy, Django, TypeORM, Prisma, Sequelize, raw SQL DDL.

---

## Step 0 — Brainstorming & Requirements Clarification (MANDATORY)

**⛔ HARD GATE — Do not read any files, explore the codebase, write SQL, or connect to any database until this step is complete.**

The purpose of this step is to avoid wasted effort. Even a request that seems fully self-contained often has hidden ambiguity (wrong mode assumed, missing filter conditions, wrong output format). Always confirm before acting.

### Waiver Condition (read carefully before skipping)

You may skip Step 0 **only if** the user's message explicitly contains **ALL** of the following — not implied, not inferred, but literally stated:

| Required item | Explicitly provided means… |
|---|---|
| **Mode** | User said "generate SQL", "document schema", "export data", etc. |
| **Table/entity scope** | Named specific tables, entities, or said "all" |
| **Output format** | Said "SQL query", "Excel", "CSV", "Markdown", etc. |
| **Filter/conditions** *(Mode B only)* | WHERE conditions, status values, ID lists, or "no filter" |
| **Output columns** *(Mode B only)* | Listed columns explicitly or said "all fields" |

If **any single item is missing or ambiguous** → ask. Do not assume. A detailed-sounding request is not the same as an explicitly complete one.

---

### Step 0A — Determine the Mode

If mode is not explicit, ask:

> "To get started, which of these fits your goal?
> - **A — Schema docs**: I'll read your entity/model files and document the table structure (output: Excel, Markdown, Word, etc.)
> - **B — SQL / data export**: I'll write a SQL query or export actual data from a live database
> - **C — Both**: Schema documentation + data export"

- **Mode A: Schema Documentation** → parse entity classes/DDL → produce docs
- **Mode B: SQL / Data Export** → write queries or connect to live DB and pull records
- **Mode C: Both**

---

### Step 0B — Targeted Questions by Mode

**Mode A — Schema Documentation:**

1. **Purpose**: What's this for? (team docs, migration planning, audit, filling a template?)
2. **Scope**: All entities, or specific ones? List them if specific.
3. **Output format**: Excel / Markdown / Word / JSON / SQL DDL?
4. **Template**: Do you have an existing template to fill? (e.g. MO Vietnamese DB format)

**→ ⛔ STOP. Wait for the user to answer before reading any files.**

---

**Mode B — SQL / Data Export:**

First clarify the sub-mode:
- **B1 — SQL query only** (no live DB needed): "I'll explore the codebase schema and write you a SQL query to run yourself."
- **B2 — Live DB export**: "I'll connect to a live database and pull the actual data."

Then ask:

1. **Scope**: Which tables or entities? Be specific.
2. **Filters**: Status conditions, date ranges, specific IDs, or "all records"?
3. **Output columns**: Which columns to include, or "all"?
4. **Output format**: SQL query / Excel / CSV / JSON?
5. *(B2 only)* **Connection**: DB type + host + port + database + credentials

**→ ⛔ STOP. Wait for the user to answer before writing any SQL or connecting to any database.**

---

### Step 0C — Confirm Before Proceeding

Once the user has answered, echo back a short plan and ask for green light:

> "Got it. Here's my plan:
> - **Mode**: [A / B1 / B2 / C]
> - **Scope**: [tables/entities]
> - **Filters**: [conditions or 'none']
> - **Output**: [format]
>
> Ready to proceed — shall I start?"

**→ ⛔ STOP. Only move to Step 1 after the user says yes (or equivalent like "go", "proceed", "yes").**

---

## Step 1 — Ingest Source Files (Mode A: Schema Documentation)

**If user selected Mode B (Data Export) in Step 0, skip to Step 1-B below.**

**If user selected Mode A or Mode C:** Based on the scope defined in Step 0, locate and scan the relevant entity files.

**If the user specified specific tables in Step 0:** Only scan files matching those table/entity names.
**If the user wants all entities:** Scan all files in the provided directory.

Check uploaded files or the specified directory. List what was found and confirm with the user before proceeding.

Detect framework by file content:

| Framework | Key signals |
|---|---|
| **Spring JPA** *(primary)* | `.java` + `@Entity`, `@Table`, `@Column`, `@Id` |
| Spring XML ORM | `.hbm.xml` / `orm.xml` with `<class>` or `<entity>` |
| Python SQLAlchemy | `.py` + `Column(`, `declarative_base`, `mapped_column` |
| Python Django | `.py` + `models.Model`, `models.CharField` |
| TypeORM | `.ts` + `@Entity()`, `@Column()` |
| Prisma | `schema.prisma` + `model` blocks |
| Sequelize | `.js`/`.ts` + `DataTypes.`, `sequelize.define` |
| SQL DDL | `.sql` + `CREATE TABLE` |

For **Spring JPA**, read `references/java-jpa.md` for detailed parsing patterns.
For other frameworks, read the relevant file in `references/`.

---

## Step 1-B — Connect to Database (Mode B: Data Export)

**If user selected Mode A (Schema Documentation), skip this step.**

**If user selected Mode B or Mode C:** Connect to the live database using the credentials provided in Step 0.

1. **Establish connection:**
   ```python
   import psycopg2  # for PostgreSQL
   # or: import mysql.connector  # for MySQL
   # or: import cx_Oracle  # for Oracle
   # etc.
   
   conn = psycopg2.connect(
       host="hostname",
       port=5432,
       database="dbname",
       user="username",
       password="password"
   )
   ```

2. **Verify connection:** Test with a simple query (e.g., `SELECT 1` or `SELECT version()`)

3. **List available tables:** Query system catalogs to show user what tables exist
   - PostgreSQL: `SELECT tablename FROM pg_tables WHERE schemaname = 'public'`
   - MySQL: `SHOW TABLES`
   - Confirm with user if the tables from Step 0 exist

4. **Build filtered queries:** Based on the filtering criteria from Step 0, construct WHERE clauses

**Example query construction:**

```python
# Base query
query = f"SELECT * FROM {table_name}"

# Add WHERE clauses from Step 0 filtering criteria
where_clauses = []

# Specific IDs (e.g., MIDs)
if specific_ids:
    where_clauses.append(f"id IN ({','.join(map(str, specific_ids))})")

# Time range
if time_range:
    where_clauses.append(f"created_at >= '{start_date}' AND created_at < '{end_date}'")

# Status conditions
if status_filter:
    where_clauses.append(f"status = {status_value}")

# Image existence check
if must_have_image:
    where_clauses.append("image_url IS NOT NULL")

# Custom WHERE clause from user
if custom_where:
    where_clauses.append(custom_where)

# Combine all conditions
if where_clauses:
    query += " WHERE " + " AND ".join(where_clauses)

# Add LIMIT if sample requested
if limit:
    query += f" LIMIT {limit}"
```

5. **Preview query:** Show the constructed query to the user for approval before executing

6. **Execute and fetch data:** Run the query and retrieve results

**Security note:** Always use parameterized queries to prevent SQL injection. Never concatenate user input directly into SQL.

---

## Step 1.5 — Review and Confirm Entities (MANDATORY GATE)

⛔ **HARD STOP — After scanning entities but before parsing, you MUST confirm with the user.**

**Why this step exists:** Prevents accidentally documenting wrong tables, test entities, or deprecated tables. Requires user to explicitly approve what will be documented.

### What to show the user:

List all entities found with basic info:

> "I scanned the directory/files and found **{N} entities**:
>
> | # | Entity Name | Table Name | File Path | Fields Count (approx) |
> |---|-------------|------------|-----------|----------------------|
> | 1 | User | users | src/entities/User.java | ~8 |
> | 2 | Order | orders | src/entities/Order.java | ~12 |
> | 3 | Product | products | src/entities/Product.java | ~10 |
> | 4 | UserBackup | users_backup | src/entities/UserBackup.java | ~9 |
> | 5 | TestEntity | test_data | src/test/TestEntity.java | ~3 |
> | ... | ... | ... | ... | ... |
>
> **Before I proceed to document these tables, please confirm:**
>
> **Question 1:** Should I document ALL {N} entities, or do you want to exclude any?
> - If excluding: Which ones should I skip? (provide numbers or names)
>
> **Question 2:** For each table being added to documentation, why is it needed?
> - This helps ensure we're not documenting test data, backups, or deprecated tables by mistake.
>
> Please review the list and tell me:
> - **Which entities to include** (e.g., "all", "1-3, 5", "exclude 4 and 5")
> - **Reason for documenting them** (e.g., "production tables", "active development", "migration requirement")

### User must provide:

1. **Explicit confirmation** of which entities to include
2. **Justification/reason** for why they're being documented (even brief like "production tables" is fine)

### Handle user response:

```python
# Example: User says "Include 1-3, exclude 4-5 because they are backup and test"
entities_to_document = [entity_list[0], entity_list[1], entity_list[2]]
excluded_entities = [entity_list[3], entity_list[4]]
reason = "Production tables for main application"

# Log the decision
print(f"✅ Confirmed: Documenting {len(entities_to_document)} entities")
print(f"   Reason: {reason}")
print(f"❌ Excluded: {len(excluded_entities)} entities")
for e in excluded_entities:
    print(f"   - {e['name']} ({e['table_name']})")
```

**→ Only proceed to Step 2 after receiving explicit confirmation.**

**Exception:** If the user specified exact table names in Step 0 (e.g., "document User and Order tables only"), you can skip asking again since they already specified.

---

## Step 2 — Parse Entity Metadata

**Parse only the entities approved in Step 1.5** (or Step 0 if already specified).

### Step 2A — Extract Metadata

For each entity extract the following per field:

| Column | Source |
|---|---|
| Entity Name | Class name |
| Table Name | `@Table(name=...)` or snake_case of class name |
| Field Name | Java field name |
| Column Name | `@Column(name=...)` or snake_case of field name |
| Java Type | Declared type (`Long`, `String`, `LocalDateTime`, …) |
| SQL Type | Mapped DB type — see `references/java-jpa.md` type map |
| PK | `@Id` present |
| Nullable | `@Column(nullable=false)` → No; default → Yes |
| Unique | `@Column(unique=true)` or `@UniqueConstraint` |
| FK / Relation | `@ManyToOne`, `@OneToMany`, `@JoinColumn(name=...)` |
| Length | `@Column(length=...)` |
| Precision / Scale | `@Column(precision=..., scale=...)` |
| Default Value | `@Column(columnDefinition=...)` default clause |
| Validation | Bean Validation annotations (`@NotNull`, `@Size`, `@Email`, …) |
| Description | Javadoc `/** ... */` on the field |

**Spring-specific extras to capture:**
- `@GeneratedValue(strategy=...)` → note in PK column
- `@Temporal(TemporalType.DATE/TIME/TIMESTAMP)` → refine SQL Type
- `@Enumerated(EnumType.STRING/ORDINAL)` → note in SQL Type
- `@Lob` → SQL Type = `TEXT` or `BLOB`
- `@CreationTimestamp` / `@UpdateTimestamp` → note in Description
- `@Version` → note "optimistic lock" in Description
- Lombok annotations (`@Data`, `@Builder`, etc.) → note in entity header, parse fields normally

---

### Step 2B — Detect Duplicate Table Structures

After parsing all entities, check for duplicate or nearly identical table structures. This prevents cluttering documentation with redundant tables and helps the user identify potential data model issues.

**Use the detection script:** Import and run `scripts/detect_duplicates.py`:

```python
from scripts.detect_duplicates import detect_duplicate_tables, format_duplicate_report

# After parsing all entities into a list
duplicates = detect_duplicate_tables(entities, threshold=90.0)

if duplicates:
    # Found duplicates - need to ask user
    for dup in duplicates:
        print(format_duplicate_report(dup))
```

**What qualifies as a duplicate:**
- **Identical column set**: Same columns (by name) in both tables, even if field names differ
- **High similarity (90%+ match)**: Tables share 90% or more of the same column names
- **Same column types and constraints**: Same columns with matching types, nullability, and key constraints

**When duplicates are detected:**

⛔ **STOP and ask the user for EACH duplicate pair:**

> "I found potential duplicate table structures:
>
> - **`{table1_name}`** ({N} columns) and **`{table2_name}`** ({M} columns) share {X}% of their columns
> - Shared columns: `{list first 5-10 shared column names}`
> {if only-in-table1 exists:} - Only in {table1_name}: `{list them}`
> {if only-in-table2 exists:} - Only in {table2_name}: `{list them}`
>
> This might indicate:
> 1. One is a copy/backup of the other
> 2. They serve similar purposes and could be consolidated  
> 3. They're intentionally similar but serve different contexts
>
> **How would you like me to handle this?**
> - **A** — Include both tables in the documentation
> - **B** — Skip `{table2_name}` (keep `{table1_name}` only)
> - **C** — Skip `{table1_name}` (keep `{table2_name}` only)
> - **D** — Document both but add a note about the duplication
> - **E** — Let me review the entity files first and then decide"

**Apply user's choice using the script:**

```python
from scripts.detect_duplicates import filter_entities_by_user_choice

# Build user_choices dict based on their responses
# Example: {'dup_0': 'keep_second', 'dup_1': 'keep_both_annotated'}
user_choices = {}

for i, dup in enumerate(duplicates):
    choice_key = f"dup_{i}"
    # Map user response A/B/C/D to internal keys
    # A -> 'keep_both'
    # B -> 'keep_first' 
    # C -> 'keep_second'
    # D -> 'keep_both_annotated'
    user_choices[choice_key] = mapped_choice

# Filter entities based on choices
entities = filter_entities_by_user_choice(entities, duplicates, user_choices)
```

If user chooses option D (keep both with annotation), the script adds: `[Note: Similar structure to {other_table_name}]` to the first field's description.

**Example scenario:**

If you detect that `development_unit_backup` has 95% similarity to `development_unit` (sharing columns like `id`, `name`, `code`, `manager_id`), you should:
1. Show the similarity report with shared and unique columns
2. Ask which table to include or whether to annotate both
3. Explain that including both without annotation might confuse readers
4. Apply the user's choice before generating output

**Important:** Don't auto-skip duplicates without asking. The user knows the domain model and may have valid reasons for both tables existing.

---

## Step 3 — Confirm Output Mode (if not already decided in Step 0)

If the user already specified the output format in Step 0, skip this step and proceed directly to Step 4.

Otherwise, after parsing, confirm with the user:

> "I found **N entities** with **M fields** total. 
> 
> Based on your goal from our earlier discussion, would you like me to proceed with [format from Step 0], or would you prefer a different format?"

If they want to change the format, present the three modes:

### Mode A — Generate a new output file
Create a fresh file from the extracted data. Ask which format:
- **Excel (.xlsx)** ← default recommendation
- **CSV**
- **Word document (.docx)**
- **Markdown**
- **JSON**

→ Go to **Step 4A**.

### Mode B — Fill an existing template
The user has an Excel or Word template with placeholders or a pre-defined table structure that should be populated with the entity data.

**The skill supports ANY Excel (.xlsx) or Word (.docx) template format** — not tied to any specific organization's format.

→ Go to **Step 4B**.

### Mode C — Generate SQL (DDL)
Produce `CREATE TABLE` SQL statements from the entity metadata.

Ask:
- Target dialect: **MySQL / MariaDB**, **PostgreSQL**, **SQL Server**, **Oracle**, **SQLite**, **H2** (default: PostgreSQL)
- Include `DROP TABLE IF EXISTS` before each table? (Yes / No)
- Include FK `CONSTRAINT` / `REFERENCES` clauses? (Yes / No)
- Include index definitions from `@Index` annotations? (Yes / No)

→ Go to **Step 4C**.

---

## Step 4A — Generate New Output File

### Excel (.xlsx)

Read `/mnt/skills/public/xlsx/SKILL.md` before writing code.

Use `scripts/entity_to_excel.py` — import `write_excel(entities, output_path)`.

Structure:
- **"Summary" sheet** — one row per entity: name, table, field count, PK field(s)
- **Per-entity sheets** if > 5 entities; single **"All Fields"** sheet if ≤ 5

Columns (in order):
```
Field Name | Column Name | Java Type | SQL Type | PK | Nullable | Unique | FK/Relation | Length | Precision | Scale | Default | Validation | Description
```

Formatting rules:
- Header: bold, `#4472C4` background, white text, frozen row
- PK rows: light green `#E2EFDA`
- FK rows: light yellow `#FFF2CC`
- Alternate row shading `#EBF3FB`
- Auto-fit column widths

### Word (.docx)

Read `/mnt/skills/public/docx/SKILL.md` before writing code.

Structure: Title → Table of Contents → per-entity section (Heading 2 + field table + relation notes).

### CSV / Markdown / JSON

- CSV: `pandas.to_csv()`, one file (with Entity column) or per-entity files
- Markdown: `## EntityName` + `| col | col |` table
- JSON: `{ "entities": [ { "name": "...", "tableName": "...", "fields": [...] } ] }`

---

## Step 4B — Fill Existing Template

The skill intelligently works with **any Excel or Word template**, auto-detecting structure and mapping entity fields automatically.

### Step 4B-1: Inspect the Template

Ask the user to upload or specify the path to their template file (.xlsx or .docx).

Use the `print_detected_structure()` helper to analyze what the skill detected:

```python
from scripts.fill_template import print_detected_structure

print_detected_structure(template_path)
```

This outputs:
- For Excel: header row location, column mapping, data start row
- For Word: format type (table/sections), column mapping if applicable

### Step 4B-2: Confirm or Adjust Mapping

Show the user what was detected and ask if it looks correct:

> "I detected your template structure:
> - **Header row**: Row 3
> - **Columns detected**:
>   - Column A: Field Name
>   - Column B: Data Type
>   - Column C: Nullable
>   - Column D: Description
> 
> Does this look right? If any columns are wrong, let me know and I'll adjust the mapping."

**If user says it's wrong or detection failed:**

Ask them to describe the template structure:
- Which row contains the headers?
- What does each column represent?
- Should data go in one sheet or multiple sheets (one per entity)?

Build a manual `user_column_mapping` dict:

```python
user_column_mapping = {
    1: 'field_name',      # Column A
    2: 'sql_type',        # Column B
    3: 'nullable',        # Column C
    4: 'description',     # Column D
    # ... etc
}
```

### Step 4B-3: Fill the Template

Use `scripts/fill_template.py` to fill the template:

```python
from scripts.fill_template import fill_template

output_path = fill_template(
    entities=entities,
    template_path='path/to/user_template.xlsx',
    output_path='path/to/filled_template.xlsx',
    user_column_mapping=None  # Or pass the manual mapping if needed
)
```

**How it works:**

**For Excel templates:**
1. Auto-detects header row by scanning first 10 rows for recognizable column names
2. Maps template columns to entity fields using fuzzy matching:
   - "Field Name", "Column", "Name" → `field_name`
   - "Type", "Data Type", "SQL Type" → `sql_type`
   - "Null", "Nullable", "Allow Null" → `nullable`
   - "PK", "Primary Key" → `pk`
   - "FK", "Foreign Key", "Relation" → `relation`
   - "Default", "Default Value" → `default`
   - "Description", "Note", "Comment" → `description`
   - (see `scripts/fill_template.py` for full list)
3. For 3 or fewer entities: fills all in one sheet
4. For 4+ entities: creates one sheet per entity (or uses existing sheets)
5. Preserves all existing formatting, formulas, and sheets

**For Word templates:**
1. Detects if template uses a table format or sections format
2. If table: fills rows into the existing table
3. If sections: creates a heading + table for each entity
4. Preserves existing styles and formatting

### Step 4B-4: Handle Special Cases

**Multi-sheet Excel workbooks (like database documentation templates):**

If the template has special sheets (like "Changelog", "Table of Contents", "Index"):
- The skill preserves all existing sheets
- Only modifies data sheets or creates new entity sheets
- Ask user if they want you to update index/changelog sheets

**Custom column requirements:**

If the template has columns the skill doesn't recognize (e.g., "Business Owner", "Approval Status"):
- The skill leaves those columns blank
- Ask user how to fill them: "Some columns like 'Business Owner' aren't in the entity data. Should I leave them blank, or would you like to provide values?"

**Existing data in template:**

If template already has data rows:
- The skill appends new rows after existing data
- Never overwrites existing data
- Ask: "Your template has existing data. Should I append new entities below, or create new sheets for them?"

### Column Matching Logic

The skill uses **fuzzy matching** with these patterns (case-insensitive):

| Field Type | Matches headers containing... |
|---|---|
| `field_name` | field, attribute, property, name, column name |
| `column_name` | column, col name, db column, database column |
| `sql_type` | type, data type, datatype, sql type, db type |
| `pk` | pk, primary, key, primary key, is pk |
| `nullable` | null, nullable, allow null, not null, nullability |
| `unique` | unique, distinct, is unique, unique key |
| `relation` | fk, foreign, relation, table link, reference, foreign key |
| `length` | length, size, max length, maxlength |
| `default` | default, data default, default value, initial value |
| `description` | description, note, comment, remark, notes |
| `validation` | validation, constraint, rule, check |

Match threshold: 60% similarity required.

### Error Handling

If auto-detection fails completely:

```python
raise ValueError(
    "Could not auto-detect template structure. Please describe your template format: "
    "Which row has headers? What do the columns represent?"
)
```

Then build `user_column_mapping` manually based on user's description and retry.

---

## Step 4C — Generate SQL (DDL)

Use `references/sql-generation.md` for dialect-specific syntax.

General structure per entity:

```sql
-- ==========================================
-- Table: users  (Entity: User)
-- ==========================================
DROP TABLE IF EXISTS `users`;

CREATE TABLE `users` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `email`      VARCHAR(255) NOT NULL,
    `created_at` TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_users_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User account entity';
```

Rules:
- Respect the chosen dialect's quoting style (backtick / `"` / `[` `]`)
- Apply `NOT NULL` when `nullable = false`
- Apply `UNIQUE` constraints from `@Column(unique=true)` or `@UniqueConstraint`
- Apply FK `REFERENCES` clauses when requested (and target table is in the extracted set)
- Append `-- [Javadoc description]` inline comment for fields that have descriptions
- Emit all tables first, then FK `ALTER TABLE ... ADD CONSTRAINT ...` statements at the end
  (avoids forward-reference issues)
- Write output to a `.sql` file

---

## Step 5 — Save and Present

1. Print a brief summary:
   - Entities found / processed
   - Total fields
   - Any fields skipped or warnings (unrecognised annotations, missing types, etc.)
2. Save output to `/mnt/user-data/outputs/<descriptive_filename>.<ext>`
3. Call `present_files` with the output path.

---

## Spring JPA Edge Cases

| Situation | Handling |
|---|---|
| `@Inheritance(strategy=JOINED/TABLE_PER_CLASS)` | Note strategy; flatten parent fields into child, mark `[inherited]` |
| `@MappedSuperclass` | Treat as abstract; merge its fields into all subclasses |
| `@Embedded` / `@Embeddable` | Flatten into parent entity; prefix Description with `[embedded: EmbeddedClass]` |
| `@OneToMany` / `@ManyToMany` (no column) | Include row; SQL Type = `(relation — no column)` |
| `@JoinTable` | Note join table name + join columns in FK/Relation column |
| Lombok `@Data` / `@Builder` | Parse normally; note "Lombok" in entity header row |
| No `@Column` annotation | Infer: column name = snake_case(fieldName), nullable = true, no length |
| `@Enumerated(EnumType.STRING)` | SQL Type = `VARCHAR(enum)` |
| `@Enumerated(EnumType.ORDINAL)` | SQL Type = `INT(enum)` |
| `@Lob` on String | SQL Type = `TEXT` / `LONGTEXT` |
| `@Lob` on byte[] | SQL Type = `BLOB` / `LONGBLOB` |
| 50+ entities | Ask: one sheet per entity vs. combined sheet |
