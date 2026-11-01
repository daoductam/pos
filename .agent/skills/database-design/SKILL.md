---
name: database-design
description: "Expert database design skill for Google Antigravity IDE. Analyzes business requirements, designs optimal database schemas for SQL (Oracle 12c+, MySQL, PostgreSQL) and NoSQL (MongoDB, Cassandra, CouchDB). Supports multiple ID strategies and database migration tools."
version: 1.0.0
author: ductm
date_created: "2026-03-20"
---

# Database Design Skill

> **Analyze business requirements first, design database later.**

## 🎯 When to Use This Skill

- Analyzing business requirements and logic to design databases
- Selecting appropriate database technology (SQL or NoSQL)
- Designing schema, indexes, relationships
- **Analyzing and proposing partitioning strategies based on context**
- Creating database migration scripts (Liquibase, Flyway)
- Query optimization and performance tuning
- Re-architecting existing database systems

## ⚠️ Do Not Use When

- Only simple query tuning is needed
- You do not have permission to change the data model
- Only handling application-level logic

---

## 📋 Workflow

### Step 1: Analyze Business Requirements
```
Before designing the database, YOU MUST:
├── Collect and analyze business requirements
├── Identify entities and relationships
├── Define access patterns (read/write)
├── Estimate scale targets (rows, QPS, retention)
├── Determine consistency requirements
└── Understand the deployment environment
```

### Step 2: Select Database Technology
```
Based on requirements, choose:
├── SQL Databases
│   ├── Oracle (≥12c) → Enterprise, complex transactions
│   ├── PostgreSQL → Full features, extensions
│   └── MySQL → Web applications, read-heavy workloads
│
└── NoSQL Databases
    ├── MongoDB → Document store, flexible schema
    ├── Cassandra → Wide-column, high write throughput
    └── CouchDB → Document store, offline-first, sync
```

### Step 3: Design Schema

**MANDATORY COLUMN REMARKS (CHÚ THÍCH):**
- You MUST automatically create descriptive remarks (comments/descriptions) for EVERY newly created column in the database schema.
- If the purpose or meaning of a column is unclear from the business requirements, YOU MUST ASK THE USER for clarification.
- DO NOT guess or make assumptions about column remarks.

### Step 4: Analyze Partitioning Strategy
```
Based on context, evaluate:
├── Current and projected table size
│   ├── < 10M rows → Usually NO partition needed
│   ├── 10-100M rows → Consider if query patterns fit
│   └── > 100M rows → SHOULD partition
│
├── Query patterns
│   ├── Filter by time range? → RANGE partition
│   ├── Filter by category/region? → LIST partition
│   └── Need even distribution? → HASH partition
│
├── Data lifecycle requirements
│   ├── Retention policy? → RANGE partition (easy DROP)
│   └── Archive old data? → RANGE + tablespace move
│
└── Propose options for user confirmation
```

### Step 5: Implement Database Migration

---

## 🗄️ SQL Databases Support

### Oracle Database (≥12c)

| Feature | Guidance |
|---------|----------|
| **Version** | Oracle 12c or later |
| **Data Types** | NUMBER, VARCHAR2, CLOB, BLOB, TIMESTAMP WITH TIME ZONE |
| **IDs** | Sequence + Trigger or Identity Column (12c+) |
| **Partitioning** | Range, List, Hash, Composite |
| **Indexes** | B-tree, Bitmap, Function-based, Domain |

```sql
-- Oracle Identity Column (12c+)
CREATE TABLE users (
    user_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email VARCHAR2(255) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP
);

-- Oracle Sequence + Trigger (traditional)
CREATE SEQUENCE users_seq START WITH 1 INCREMENT BY 1;
```

### PostgreSQL

| Feature | Guidance |
|---------|----------|
| **IDs** | BIGINT GENERATED ALWAYS AS IDENTITY (preferred), UUID with gen_random_uuid() |
| **Strings** | TEXT (do not use VARCHAR(n)) |
| **Money** | NUMERIC(p,s) (do not use money type) |
| **Time** | TIMESTAMPTZ (do not use TIMESTAMP) |
| **JSON** | JSONB with GIN index |

```sql
-- PostgreSQL Best Practice
CREATE TABLE users (
    user_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email TEXT NOT NULL UNIQUE,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX users_email_lower ON users (LOWER(email));
CREATE INDEX users_metadata_gin ON users USING GIN (metadata);
```

### MySQL

| Feature | Guidance |
|---------|----------|
| **Engine** | InnoDB (default) |
| **IDs** | BIGINT AUTO_INCREMENT or UUID with BINARY(16) |
| **Strings** | VARCHAR with utf8mb4 charset |
| **Time** | DATETIME(6) or TIMESTAMP |
| **JSON** | JSON type with generated columns |

```sql
-- MySQL Best Practice
CREATE TABLE users (
    user_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    metadata JSON,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## 📄 NoSQL Databases Support

### MongoDB

```javascript
// Collection Design with Validation
db.createCollection("users", {
    validator: {
        $jsonSchema: {
            bsonType: "object",
            required: ["email", "createdAt"],
            properties: {
                email: { bsonType: "string" },
                profile: { bsonType: "object" },
                createdAt: { bsonType: "date" }
            }
        }
    }
});

// Index Strategy
db.users.createIndex({ email: 1 }, { unique: true });
db.users.createIndex({ "profile.country": 1, createdAt: -1 });
```

### Apache Cassandra

```cql
-- Cassandra: Query-First Design
-- Primary Key: ((Partition Key), Clustering Columns)
CREATE TABLE users_by_email (
    email TEXT,
    user_id UUID,
    name TEXT,
    created_at TIMESTAMP,
    PRIMARY KEY (email)
);

CREATE TABLE orders_by_user (
    user_id UUID,
    order_date TIMESTAMP,
    order_id UUID,
    total DECIMAL,
    PRIMARY KEY ((user_id), order_date, order_id)
) WITH CLUSTERING ORDER BY (order_date DESC, order_id ASC);
```

### CouchDB

```json
// CouchDB Document Design
{
    "_id": "user:uuid-here",
    "type": "user",
    "email": "user@example.com",
    "profile": {
        "name": "John Doe",
        "country": "VN"
    },
    "createdAt": "2026-03-20T00:00:00Z"
}

// Design Document with View
{
    "_id": "_design/users",
    "views": {
        "by_email": {
            "map": "function(doc) { if(doc.type === 'user') emit(doc.email, doc); }"
        }
    }
}
```

---

## 🔑 ID Strategy Selection

| Strategy | Use When | Pros | Cons |
|----------|----------|------|------|
| **Snowflake ID** | Distributed systems, high throughput | Sortable, unique, 64-bit | Requires coordination |
| **UUID v4** | Distributed, no coordination needed | Simple, universally unique | Not sortable, 128-bit |
| **UUID v7** | Modern distributed systems | Sortable + unique | Newer standard |
| **ULID** | Need sorted UUID alternative | Sortable, URL-safe | Less common |
| **Auto Increment** | Single database, simple apps | Simple, compact | Not distributed-friendly |

### Snowflake ID Implementation

```sql
-- PostgreSQL Snowflake-like ID Function
CREATE OR REPLACE FUNCTION generate_snowflake_id()
RETURNS BIGINT AS $$
DECLARE
    epoch BIGINT := 1609459200000; -- Custom epoch (2021-01-01)
    seq_id BIGINT;
    now_millis BIGINT;
    shard_id INT := 1; -- Configure per shard
    result BIGINT;
BEGIN
    SELECT nextval('snowflake_seq') % 4096 INTO seq_id;
    SELECT FLOOR(EXTRACT(EPOCH FROM clock_timestamp()) * 1000) INTO now_millis;
    result := ((now_millis - epoch) << 22) | (shard_id << 12) | seq_id;
    RETURN result;
END;
$$ LANGUAGE plpgsql;
```

```java
// Java Snowflake Implementation
public class SnowflakeIdGenerator {
    private final long epoch = 1609459200000L;
    private final long datacenterIdBits = 5L;
    private final long workerIdBits = 5L;
    private final long sequenceBits = 12L;
    
    private long workerId;
    private long datacenterId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;
    
    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            throw new RuntimeException("Clock moved backwards");
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & 4095;
            if (sequence == 0) {
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - epoch) << 22) | (datacenterId << 17) | (workerId << 12) | sequence;
    }
}
```

---

## 🔄 Database Migration Strategy

### Check Codebase

When starting, check the existing codebase to identify:
1. Is there a migration tool? (Liquibase, Flyway, Prisma, Alembic, ...)
2. Current project structure and conventions
3. Database connection configuration

**If NO migration tool:**
- Ask user if they want to implement database migration
- Propose a solution suitable for the current stack

### Liquibase (Recommended for Enterprise)

```xml
<!-- changelog-master.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd">
    
    <include file="changelogs/001-create-users.xml"/>
    <include file="changelogs/002-create-orders.xml"/>
</databaseChangeLog>

<!-- changelogs/001-create-users.xml -->
<changeSet id="001-create-users" author="developer">
    <preConditions onFail="MARK_RAN">
        <not><tableExists tableName="users"/></not>
    </preConditions>
    
    <createTable tableName="users">
        <column name="user_id" type="BIGINT" autoIncrement="true">
            <constraints primaryKey="true" nullable="false"/>
        </column>
        <column name="email" type="VARCHAR(255)">
            <constraints nullable="false" unique="true"/>
        </column>
        <column name="created_at" type="TIMESTAMP WITH TIME ZONE" defaultValueComputed="CURRENT_TIMESTAMP">
            <constraints nullable="false"/>
        </column>
    </createTable>
    
    <createIndex tableName="users" indexName="idx_users_email">
        <column name="email"/>
    </createIndex>
    
    <rollback>
        <dropTable tableName="users"/>
    </rollback>
</changeSet>
```

### Flyway (Simple & Effective)

```sql
-- V1__Create_users_table.sql
CREATE TABLE users (
    user_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON users (email);

-- V2__Add_user_status.sql
ALTER TABLE users ADD COLUMN status VARCHAR(20) DEFAULT 'ACTIVE';
ALTER TABLE users ADD CONSTRAINT chk_user_status 
    CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED'));
```

### Migration Tool Selection Guide

| Tool | Best For | Database Support |
|------|----------|------------------|
| **Liquibase** | Enterprise, multi-DB, XML/YAML | Oracle, PostgreSQL, MySQL, ... |
| **Flyway** | Simple, SQL-based | PostgreSQL, MySQL, Oracle, ... |
| **Prisma Migrate** | Node.js/TypeScript projects | PostgreSQL, MySQL, SQLite |
| **Alembic** | Python/SQLAlchemy projects | All SQLAlchemy supported |
| **golang-migrate** | Go projects | PostgreSQL, MySQL, ... |

---

## 📐 Schema Design Principles

### Naming Conventions
- **Tables and Columns**: Use `snake_case` (e.g., `user_accounts`, `created_at`).
- **Table Names**: Use plural forms (or singular, but MUST be strictly consistent across the database). Plural is preferred (e.g., `users`, `orders`).
- **Primary Keys**: Always use `id` or `<table_name>_id` (e.g., `user_id`).
- **Foreign Keys**: Name columns as `<referenced_table>_id`. Name constraints with `fk_` prefix.
- **Indexes & Constraints**: Prefix indexes with `idx_`, unique constraints with `uq_`, and check constraints with `chk_`.

### Standard Columns (Audit & Soft Delete)
- **Audit Columns**: Always consider adding `created_at`, `updated_at`, `created_by`, `updated_by` to core entity tables for traceability.
- **Soft Deletes**: For critical data that should not be physically deleted, propose a `deleted_at` (TIMESTAMP) or `is_deleted` (BOOLEAN) column.

### Data Types Best Practices
- **Currency/Financial Data**: NEVER use `FLOAT`, `REAL`, or `DOUBLE` due to floating-point precision issues. ALWAYS use `DECIMAL(p,s)` or `NUMERIC(p,s)` (e.g., `DECIMAL(10,2)` perfectly handles exact monetary values like `$2.56`), or store as integers representing the smallest unit (e.g., cents).
- **Strings**: Avoid fixed-length `CHAR` unless the data is strictly fixed length (e.g., ISO country codes). Use `VARCHAR` or `TEXT`.

### Security & PII Data (Personally Identifiable Information)
- **Identify PII**: Explicitly mark sensitive columns (e.g., email, phone numbers, national IDs).
- **Passwords**: Never design a system to store plain-text passwords. Ensure password columns are designed for hashes (e.g., `VARCHAR(255)`).
- **Encryption**: Consider proposing data masking or encryption for highly sensitive PII.

### Normalization Decision

```
When to normalize (separate tables):
├── Data is repeated across rows
├── Updates would need multiple changes
├── Relationships are clear
└── Query patterns benefit

When to denormalize (embed/duplicate):
├── Read performance critical
├── Data rarely changes
├── Always fetched together
└── Simpler queries needed
```

### Relationship Types

| Type | When | Implementation |
|------|------|----------------|
| **One-to-One** | Extension data | Separate table with FK |
| **One-to-Many** | Parent-children | FK on child table |
| **Many-to-Many** | Both sides have many | Junction table |

### Foreign Key ON DELETE

```
├── CASCADE → Delete children with parent
├── SET NULL → Children become orphans
├── RESTRICT → Prevent delete if children exist
└── SET DEFAULT → Children get default value
```

---

## 📊 Indexing Strategy

### When to Create Indexes

```
Index these:
├── Columns in WHERE clauses
├── Columns in JOIN conditions
├── Columns in ORDER BY
├── Foreign key columns (IMPORTANT: PostgreSQL doesn't auto-index FKs!)
└── Unique constraints

Don't over-index:
├── Write-heavy tables (slower inserts)
├── Low-cardinality columns
├── Columns rarely queried
```

### Index Type Selection

| Type | Use For | Databases |
|------|---------|-----------|
| **B-tree** | General purpose, equality & range | All |
| **Hash** | Equality only, faster | PostgreSQL, MySQL |
| **GIN** | JSONB, arrays, full-text | PostgreSQL |
| **GiST** | Geometric, range types | PostgreSQL |
| **Bitmap** | Low cardinality, data warehouse | Oracle |
| **Full-text** | Text search | All |

### Composite Index Order

```
Order matters for composite indexes:
├── Equality columns first
├── Range columns last
├── Most selective first
└── Match query pattern
```

---

## ⚡ Query Optimization

### N+1 Problem

```
What is N+1?
├── 1 query to get parent records
├── N queries to get related records
└── Very slow!

Solutions:
├── JOIN → Single query with all data
├── Eager loading → ORM handles JOIN
├── DataLoader → Batch and cache (GraphQL)
└── Subquery → Fetch related in one query
```

### Optimization Priorities

1. **Add missing indexes** (most common issue)
2. **Select only needed columns** (not SELECT *)
3. **Use proper JOINs** (avoid subqueries when possible)
4. **Limit early** (pagination at database level)
5. **Use EXPLAIN ANALYZE** to understand query plan

---

## ✅ Design Checklist

Before finishing design:

- [ ] Have business requirements been fully analyzed?
- [ ] Have you asked the user about database preference?
- [ ] Have you chosen a database suitable for the context?
- [ ] Have you considered the deployment environment?
- [ ] Have you planned the index strategy?
- [ ] Have you defined relationship types?
- [ ] Have you selected an appropriate ID strategy?
- [ ] **Have you added remarks to all new columns, asking the user if unsure?**
- [ ] **Have you analyzed partitioning needs?**
- [ ] **If partition is needed: have you chosen partition key and type?**
- [ ] Have you set up a database migration tool?
- [ ] Do you have a rollback plan?

---

## ❌ Anti-Patterns

- ❌ Designing database before understanding business requirements
- ❌ Defaulting to PostgreSQL for every application (SQLite might suffice)
- ❌ Ignoring indexing strategy
- ❌ Using SELECT * in production
- ❌ Storing JSON when structured data is better
- ❌ Ignoring N+1 queries
- ❌ No migration rollback plan
- ❌ Oracle version < 12c
- ❌ NoSQL: Designing by entity instead of query patterns
- ❌ Partitioning small tables (< 10M rows) - overhead > benefit
- ❌ Partition key does not match query patterns

---

## 🔐 Safety Guidelines

- Avoid destructive changes without backup and rollback plan
- Validate migration plans in staging before production
- Use transactions when possible
- Test migrations on data copy first
- Never execute breaking changes in a single step

---

## 📚 Output Format

When designing database, provide:

1. **Business Analysis Summary**: Summary of analyzed business requirements
2. **Technology Recommendation**: Database selection with rationale
3. **Schema Design**: Tables/Collections, relationships, constraints
4. **ID Strategy**: Selected ID type and reason
5. **Index Strategy**: Specific indexes and rationale
6. **Partitioning Strategy**: If applicable - partition type, key, maintenance plan
7. **Migration Scripts**: Liquibase/Flyway scripts ready to use
8. **ERD Diagram**: Mermaid syntax (when requested)
9. **Rollback Plan**: How to rollback if issues occur
