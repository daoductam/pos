# Database Migration Guide

> Safe migration strategy for zero-downtime changes.

## Migration Tool Selection

| Tool | Best For | Language/Framework | Database Support |
|------|----------|-------------------|------------------|
| **Liquibase** | Enterprise, Multi-DB, Version control | Java, XML/YAML/JSON | Oracle, PostgreSQL, MySQL, SQL Server, ... |
| **Flyway** | Simple, SQL-based, Clean history | Java, SQL | PostgreSQL, MySQL, Oracle, SQL Server, ... |
| **Prisma Migrate** | Node.js/TypeScript | TypeScript | PostgreSQL, MySQL, SQLite, SQL Server |
| **Alembic** | Python/SQLAlchemy | Python | All SQLAlchemy supported |
| **golang-migrate** | Go projects | Go | PostgreSQL, MySQL, MongoDB, ... |
| **Knex.js** | Node.js | JavaScript | PostgreSQL, MySQL, SQLite |

## Approach for Existing Codebase

### Step 1: Check existing migration setup
```
Search in project:
├── liquibase.properties, changelog.xml → Liquibase
├── flyway.conf, V*__*.sql → Flyway
├── prisma/migrations/ → Prisma Migrate
├── alembic/, alembic.ini → Alembic
├── migrations/, knexfile.js → Knex.js
└── db/migrate/ → Rails ActiveRecord
```

### Step 2: If NO migration tool
1. Ask user: "Do you want to implement database migration?"
2. Propose tool suitable for the stack
3. Wait for user confirmation before implementing

---

## Liquibase Guide

### Setup Structure
```
project/
├── src/main/resources/
│   ├── liquibase.properties
│   └── db/
│       ├── changelog-master.xml
│       └── changelogs/
│           ├── 001-create-users.xml
│           ├── 002-create-orders.xml
│           └── 003-add-user-status.xml
```

### liquibase.properties
```properties
changeLogFile=db/changelog-master.xml
url=jdbc:postgresql://localhost:5432/mydb
username=myuser
password=mypassword
driver=org.postgresql.Driver
```

### changelog-master.xml
```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd">
    
    <include file="changelogs/001-create-users.xml" relativeToChangelogFile="true"/>
    <include file="changelogs/002-create-orders.xml" relativeToChangelogFile="true"/>
</databaseChangeLog>
```

### Example Changeset with Rollback
```xml
<!-- changelogs/001-create-users.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd">

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
            <column name="name" type="VARCHAR(255)">
                <constraints nullable="false"/>
            </column>
            <column name="status" type="VARCHAR(20)" defaultValue="ACTIVE">
                <constraints nullable="false"/>
            </column>
            <column name="created_at" type="TIMESTAMP WITH TIME ZONE" 
                    defaultValueComputed="CURRENT_TIMESTAMP">
                <constraints nullable="false"/>
            </column>
            <column name="updated_at" type="TIMESTAMP WITH TIME ZONE" 
                    defaultValueComputed="CURRENT_TIMESTAMP">
                <constraints nullable="false"/>
            </column>
        </createTable>
        
        <addCheckConstraint 
            tableName="users" 
            constraintName="chk_users_status"
            constraintBody="status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')"/>
        
        <createIndex tableName="users" indexName="idx_users_email">
            <column name="email"/>
        </createIndex>
        
        <createIndex tableName="users" indexName="idx_users_status">
            <column name="status"/>
        </createIndex>
        
        <rollback>
            <dropTable tableName="users"/>
        </rollback>
    </changeSet>
</databaseChangeLog>
```

### Liquibase Commands
```bash
# Preview changes
liquibase --changeLogFile=changelog-master.xml status

# Apply changes
liquibase --changeLogFile=changelog-master.xml update

# Rollback last N changes
liquibase --changeLogFile=changelog-master.xml rollbackCount 1

# Generate SQL without applying
liquibase --changeLogFile=changelog-master.xml updateSQL > output.sql

# Diff between databases
liquibase diff --referenceUrl=jdbc:postgresql://localhost/prod_db
```

---

## Flyway Guide

### Setup Structure
```
project/
├── flyway.conf
└── sql/
    ├── V1__Create_users_table.sql
    ├── V2__Create_orders_table.sql
    ├── V3__Add_user_status.sql
    └── R__Refresh_views.sql  # Repeatable migration
```

### flyway.conf
```properties
flyway.url=jdbc:postgresql://localhost:5432/mydb
flyway.user=myuser
flyway.password=mypassword
flyway.locations=filesystem:sql
flyway.baselineOnMigrate=true
```

### Migration File Naming
```
V<version>__<description>.sql
├── V1__Create_users_table.sql      # Version 1
├── V1.1__Add_email_index.sql       # Version 1.1
├── V2__Create_orders_table.sql     # Version 2
└── R__Refresh_materialized_views.sql  # Repeatable
```

### Example Migrations

```sql
-- V1__Create_users_table.sql
CREATE TABLE users (
    user_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED'))
);

CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_status ON users (status);
```

```sql
-- V2__Create_orders_table.sql
CREATE TABLE orders (
    order_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    order_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total_amount NUMERIC(12, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    
    CONSTRAINT chk_orders_status CHECK (status IN ('PENDING', 'PAID', 'SHIPPED', 'DELIVERED', 'CANCELLED'))
);

CREATE INDEX idx_orders_user_id ON orders (user_id);
CREATE INDEX idx_orders_status ON orders (status);
CREATE INDEX idx_orders_date ON orders (order_date);
```

```sql
-- V3__Add_user_profile.sql
ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_image VARCHAR(500);
ALTER TABLE users ADD COLUMN IF NOT EXISTS bio TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone VARCHAR(20);
```

### Flyway Commands
```bash
# Check status
flyway info

# Apply migrations
flyway migrate

# Validate migrations
flyway validate

# Clean database (DANGEROUS in production!)
flyway clean

# Repair history table
flyway repair
```

---

## Safe Migration Patterns

### Adding Column (Zero-downtime)
```sql
-- Step 1: Add nullable column
ALTER TABLE users ADD COLUMN phone VARCHAR(20);

-- Step 2: Backfill data (if needed)
UPDATE users SET phone = '' WHERE phone IS NULL;

-- Step 3: Add NOT NULL constraint (after deploy)
ALTER TABLE users ALTER COLUMN phone SET NOT NULL;
```

### Removing Column (Zero-downtime)
```sql
-- Step 1: Stop using column in application code
-- Step 2: Deploy application
-- Step 3: Remove column
ALTER TABLE users DROP COLUMN IF EXISTS old_column;
```

### Renaming Column (Zero-downtime)
```sql
-- Step 1: Add new column
ALTER TABLE users ADD COLUMN new_name VARCHAR(255);

-- Step 2: Copy data
UPDATE users SET new_name = old_name;

-- Step 3: Add NOT NULL if needed
ALTER TABLE users ALTER COLUMN new_name SET NOT NULL;

-- Step 4: Deploy app using new column

-- Step 5: Drop old column
ALTER TABLE users DROP COLUMN old_name;
```

### Adding Index (Non-blocking)
```sql
-- PostgreSQL: CONCURRENTLY prevents table locks
CREATE INDEX CONCURRENTLY idx_users_email ON users (email);

-- Note: Cannot run inside transaction
```

---

## Oracle Migration Considerations

### Oracle-specific Liquibase
```xml
<changeSet id="001-oracle-specific" author="developer" dbms="oracle">
    <createSequence sequenceName="users_seq" startValue="1" incrementBy="1"/>
    
    <createTable tableName="users">
        <column name="user_id" type="NUMBER(19)">
            <constraints primaryKey="true" nullable="false"/>
        </column>
        <column name="email" type="VARCHAR2(255)">
            <constraints nullable="false" unique="true"/>
        </column>
        <column name="created_at" type="TIMESTAMP WITH TIME ZONE" 
                defaultValueComputed="SYSTIMESTAMP"/>
    </createTable>
    
    <sql>
        CREATE OR REPLACE TRIGGER users_bi
        BEFORE INSERT ON users
        FOR EACH ROW
        BEGIN
            IF :NEW.user_id IS NULL THEN
                SELECT users_seq.NEXTVAL INTO :NEW.user_id FROM dual;
            END IF;
        END;
    </sql>
</changeSet>
```

### Oracle Flyway
```sql
-- V1__Create_users_oracle.sql
CREATE SEQUENCE users_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE users (
    user_id NUMBER(19) DEFAULT users_seq.NEXTVAL PRIMARY KEY,
    email VARCHAR2(255) NOT NULL UNIQUE,
    name VARCHAR2(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL
);

-- Oracle 12c+ Identity Column
CREATE TABLE users_v2 (
    user_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email VARCHAR2(255) NOT NULL UNIQUE
);
```

---

## Rollback Strategy

### Always Have Rollback Plan
```xml
<!-- Liquibase: Explicit rollback -->
<changeSet id="add-column" author="dev">
    <addColumn tableName="users">
        <column name="new_col" type="VARCHAR(100)"/>
    </addColumn>
    
    <rollback>
        <dropColumn tableName="users" columnName="new_col"/>
    </rollback>
</changeSet>
```

### Flyway Undo Migrations (Pro/Enterprise)
```sql
-- U1__Undo_create_users.sql
DROP TABLE IF EXISTS users;
```

### Manual Rollback Scripts
```
migrations/
├── V1__Create_users.sql
├── V1__Create_users_rollback.sql  # Keep alongside
├── V2__Add_orders.sql
└── V2__Add_orders_rollback.sql
```

---

## Best Practices Checklist

- [ ] Each migration has a rollback script/section
- [ ] Test migrations on data copy first
- [ ] Never modify an applied migration
- [ ] Use pre-conditions to handle re-runs
- [ ] Split breaking changes into multiple steps
- [ ] Run large data migrations off-hours
- [ ] Backup database before major migrations
- [ ] Version control all migration files
