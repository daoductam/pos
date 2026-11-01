# Generic Template Support — Design & Usage

This document explains how the entity-reader skill's generic template filling works.

---

## Philosophy

**Any template, any format** — the skill should work with whatever Excel or Word template the user brings, without requiring them to conform to a specific structure.

Traditional approach (before):
- Hard-coded to specific template format (MO Vietnamese DB template)
- Required exact column names and sheet structure
- Failed if template was different

New approach:
- **Auto-detection**: Scans template to understand structure
- **Fuzzy matching**: Maps columns intelligently using similarity scoring
- **Flexible**: Works with single-sheet, multi-sheet, table-based, or section-based formats
- **Fallback**: Asks user for clarification if auto-detection is uncertain

---

## How Auto-Detection Works

### Excel Templates

**Step 1: Find the header row**

Scans rows 1-10 looking for a row that:
- Has at least 3 non-empty text cells
- At least 2 cells match known column patterns (using fuzzy matching)

**Step 2: Map columns to field types**

For each column header, calculates similarity score against known patterns:
- "Field Name" vs "field_name" patterns → score = 0.85 → match!
- "Type" vs "sql_type" patterns → score = 0.65 → match!
- "Random Column" vs any pattern → score < 0.60 → no match

Threshold: 60% similarity required.

**Step 3: Determine layout**

- 1-3 entities → single-sheet layout (all entities in one sheet)
- 4+ entities → multi-sheet layout (one sheet per entity)

### Word Templates

**Step 1: Check for tables**

Scans all tables in the document:
- Checks first row for recognizable headers
- If found → uses table-based format

**Step 2: Fallback to sections**

If no recognizable table found:
- Uses sections format
- Creates heading + table for each entity

---

## Column Matching Patterns

The skill recognizes these common column header variations:

| What user writes | Skill recognizes as |
|---|---|
| Field Name, Column Name, Attribute, Property | `field_name` |
| Type, Data Type, DataType, SQL Type, DB Type | `sql_type` |
| Null, Nullable, Allow Null, Not Null, Is Null | `nullable` |
| PK, Primary Key, Is Primary, Key (if contains "primary") | `pk` |
| FK, Foreign Key, Relation, Reference, Table Link | `relation` |
| Length, Size, Max Length, MaxLength | `length` |
| Default, Default Value, Initial Value, Data Default | `default` |
| Description, Note, Comment, Remark, Notes, Desc | `description` |
| Validation, Constraint, Rule, Check | `validation` |
| Unique, Distinct, Is Unique, Unique Key | `unique` |

**Case-insensitive, whitespace-tolerant, fuzzy matching.**

Examples that work:
- "field_name", "Field Name", "FIELD NAME", "fieldName", "Field-Name"
- "data_type", "Data Type", "DataType", "TYPE", "Sql Type"
- "Not Null", "not null", "Nullable", "NULLABLE", "Allow Null"

---

## Formatting Preservation

The skill preserves:
- ✅ All existing sheets in workbook
- ✅ Cell formatting (fonts, colors, borders)
- ✅ Column widths
- ✅ Formulas
- ✅ Existing data rows (appends below them)
- ✅ Protected sheets (if not write-protected)
- ✅ Conditional formatting rules
- ✅ Data validation rules

The skill does NOT:
- ❌ Overwrite existing data
- ❌ Delete sheets
- ❌ Modify cells outside the data area
- ❌ Change workbook-level settings

---

## Example: User Brings Custom Template

**User's template: company_db_docs.xlsx**

```
Row 1: [blank]
Row 2: Database Documentation
Row 3: [blank]
Row 4: Column Name | SQL Data Type | Required? | Primary? | Comments
Row 5: [data would go here]
```

**Skill's detection:**

```
Header Row: 4
Column Mapping:
  - Column A (1): "Column Name" → field_name (similarity: 0.72)
  - Column B (2): "SQL Data Type" → sql_type (similarity: 0.81)
  - Column C (3): "Required?" → nullable (similarity: 0.63)
  - Column D (4): "Primary?" → pk (similarity: 0.71)
  - Column E (5): "Comments" → description (similarity: 0.68)
Data Start Row: 5
```

**Skill fills:**

```
Row 5: id | BIGINT | Yes | Yes | Primary key
Row 6: username | VARCHAR(100) | Yes | No | User login name
Row 7: email | VARCHAR(255) | Yes | No | Email address
...
```

**Result:** Template filled correctly without any manual configuration!

---

## When Auto-Detection Fails

**Scenario 1: Ambiguous headers**

Template has: "Info", "Data", "Value" (too generic)

**Solution:** Skill asks user:
> "I found columns 'Info', 'Data', 'Value' but couldn't determine what they represent. 
> Which column should contain:
> - Field names?
> - Data types?
> - Descriptions?"

**Scenario 2: Non-standard layout**

Template uses vertical format (fields in column A, values in column B)

**Solution:** Skill detects this is not a table format and asks:
> "Your template uses a vertical layout which I can't auto-fill. 
> Would you like me to:
> A) Create a new sheet with a standard table format
> B) Generate a new file in your template's style (you'll need to describe the format)"

**Scenario 3: Multiple tables per sheet**

Template has separate tables for different entity groups on same sheet

**Solution:** Skill uses the first recognizable table and notifies user:
> "I found multiple tables in your template. I'll fill the first one (starting at row 4).
> If you need data in other tables, please extract them to separate sheets first."

---

## Advanced: Custom Column Mapping

If user has unique columns the skill doesn't recognize, they can provide a manual mapping:

```python
user_column_mapping = {
    1: 'field_name',        # Column A: "Database Column"
    2: 'sql_type',          # Column B: "Type"
    3: 'nullable',          # Column C: "Mandatory?"
    4: 'description',       # Column D: "Business Description"
    5: 'custom_business_owner',  # Column E: Not in standard fields
    6: 'custom_approval_status'  # Column F: Not in standard fields
}
```

For custom columns not in entity data, skill leaves them blank and asks how to fill them.

---

## Multi-Sheet Workbooks

**Common pattern: DB documentation workbooks**

```
- Sheet: Changelog (tracks changes)
- Sheet: Index (table of contents)
- Sheet: users (entity data)
- Sheet: orders (entity data)
- Sheet: products (entity data)
```

**Skill behavior:**

1. Detects which sheets have data tables vs. metadata sheets
2. Fills or creates entity data sheets
3. **Preserves** Changelog, Index, and other special sheets
4. **Offers** to update Index/Changelog if user wants:
   > "I've filled the entity sheets. Would you like me to also:
   > - Add entries to the Index sheet?
   > - Add a change record to the Changelog?"

---

## Supported Template Patterns

### Pattern 1: Simple Single-Sheet Table
```
| Field | Type | Nullable | Description |
|-------|------|----------|-------------|
| ...   | ...  | ...      | ...         |
```
**Use case:** Small projects, quick documentation

### Pattern 2: Multi-Sheet (One Per Entity)
```
Sheet: users
Sheet: orders  
Sheet: products
```
**Use case:** Large projects with many entities

### Pattern 3: Grouped Sections
```
## Users Entity
[table]

## Orders Entity
[table]
```
**Use case:** Word documents, narrative-style docs

### Pattern 4: Complex Workbook
```
Sheet: Index
Sheet: Changelog
Sheet: entity_name_1
Sheet: entity_name_2
...
```
**Use case:** Enterprise DB documentation, compliance requirements

---

## Migration Guide (MO Template → Generic)

If you previously used the MO Vietnamese template format:

**Old way:**
```python
from scripts.fill_mo_template import fill_mo_template
fill_mo_template(entities, 'MO-Mô_tả_database.xlsx', 'output.xlsx')
```

**New way:**
```python
from scripts.fill_template import fill_template
fill_template(entities, 'ANY_template.xlsx', 'output.xlsx')
```

**Benefits:**
- ✅ Works with any template, not just MO format
- ✅ No need to remember MO-specific column positions
- ✅ Auto-detects structure
- ✅ Same API, more flexible

**Compatibility:**
- MO templates still work (detected as multi-sheet format)
- No need to change your templates
- Skill detects Vietnamese column names automatically

---

## Best Practices

**For users:**
1. Use clear, descriptive column headers ("Field Name" not "Col1")
2. Put headers in first 10 rows of sheet
3. Keep table structure consistent within a sheet
4. For Word: use actual tables, not space-aligned text

**For skill developers:**
1. Always show detected structure to user before filling
2. Provide escape hatch for manual mapping
3. Preserve ALL existing template content
4. Handle missing columns gracefully (leave blank, don't error)

---

## Future Enhancements

Potential improvements:
- Support CSV templates (delimiter auto-detection)
- Support Markdown tables (GitHub-style)
- Learn from user corrections (improve matching algorithm)
- Detect and update related sheets (Index, Changelog) automatically
- Support template validation (check for required columns)
- Template library (common formats users can choose from)
