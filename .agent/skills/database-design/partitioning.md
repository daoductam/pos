# Database Partitioning Strategy

> Analyze context to decide when and how to partition database.

## When to Partition?

### Decision Tree

```
Assess partitioning needs:
│
├── Current or projected table size
│   ├── < 10 million rows → Usually NO partition needed
│   ├── 10-100 million rows → Consider partition if query patterns fit
│   └── > 100 million rows → SHOULD partition
│
├── Query patterns
│   ├── Queries always filter by time range? → RANGE partition by date
│   ├── Queries filter by category/region? → LIST partition
│   └── Need to distribute evenly, no natural key? → HASH partition
│
├── Data lifecycle
│   ├── Need to periodically delete old data? → RANGE partition (DROP PARTITION)
│   ├── Need to archive old data? → RANGE partition + tablespace move
│   └── Data retention policy? → RANGE partition
│
└── Performance requirements
    ├── Query only needs subset of data? → Partition pruning benefits
    ├── Bulk load data in batches? → Partition exchange
    └── Parallel query needed? → Partition helps parallelism
```

## Context Analysis Questions

Before proposing partitioning, ASK or ANALYZE:

| Question | Impact on Decision |
|----------|-------------------|
| How many rows currently? Projected growth? | Size threshold |
| What are the main query patterns? | Partition key selection |
| Need to delete/archive old data? | RANGE by time |
| Does data have natural grouping? | LIST partition |
| Read-heavy or write-heavy? | Partition type |
| Need global unique constraints? | PK must include partition key |

---

## Partitioning Types

### RANGE Partitioning

**When to use:**
- Time-series data (logs, events, transactions)
- Data with lifecycle (retention, archival)
- Queries filter by date ranges

```sql
-- PostgreSQL
CREATE TABLE orders (
    order_id BIGINT GENERATED ALWAYS AS IDENTITY,
    user_id BIGINT NOT NULL,
    order_date DATE NOT NULL,
    total NUMERIC(12,2) NOT NULL,
    PRIMARY KEY (order_id, order_date)
) PARTITION BY RANGE (order_date);

CREATE TABLE orders_2024_q1 PARTITION OF orders
    FOR VALUES FROM ('2024-01-01') TO ('2024-04-01');
CREATE TABLE orders_2024_q2 PARTITION OF orders
    FOR VALUES FROM ('2024-04-01') TO ('2024-07-01');
CREATE TABLE orders_2024_q3 PARTITION OF orders
    FOR VALUES FROM ('2024-07-01') TO ('2024-10-01');
CREATE TABLE orders_2024_q4 PARTITION OF orders
    FOR VALUES FROM ('2024-10-01') TO ('2025-01-01');

-- Auto-create future partitions (pg_partman extension)
```

```sql
-- Oracle (≥12c)
CREATE TABLE orders (
    order_id NUMBER GENERATED ALWAYS AS IDENTITY,
    user_id NUMBER NOT NULL,
    order_date DATE NOT NULL,
    total NUMBER(12,2) NOT NULL
)
PARTITION BY RANGE (order_date)
INTERVAL (NUMTOYMINTERVAL(1, 'MONTH'))
(
    PARTITION p_initial VALUES LESS THAN (TO_DATE('2024-01-01', 'YYYY-MM-DD'))
);
-- Oracle automatically creates new partitions with INTERVAL
```

```sql
-- MySQL
CREATE TABLE orders (
    order_id BIGINT AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    order_date DATE NOT NULL,
    total DECIMAL(12,2) NOT NULL,
    PRIMARY KEY (order_id, order_date)
)
PARTITION BY RANGE (YEAR(order_date) * 100 + MONTH(order_date)) (
    PARTITION p202401 VALUES LESS THAN (202402),
    PARTITION p202402 VALUES LESS THAN (202403),
    PARTITION p202403 VALUES LESS THAN (202404),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);
```

### LIST Partitioning

**When to use:**
- Data has discrete categories (region, status, type)
- Queries filter by category
- Different data handling per category

```sql
-- PostgreSQL
CREATE TABLE customers (
    customer_id BIGINT GENERATED ALWAYS AS IDENTITY,
    name TEXT NOT NULL,
    region TEXT NOT NULL,
    PRIMARY KEY (customer_id, region)
) PARTITION BY LIST (region);

CREATE TABLE customers_apac PARTITION OF customers
    FOR VALUES IN ('VN', 'TH', 'SG', 'MY', 'ID', 'PH');
CREATE TABLE customers_emea PARTITION OF customers
    FOR VALUES IN ('UK', 'DE', 'FR', 'IT', 'ES');
CREATE TABLE customers_americas PARTITION OF customers
    FOR VALUES IN ('US', 'CA', 'MX', 'BR');
```

```sql
-- Oracle
CREATE TABLE customers (
    customer_id NUMBER GENERATED ALWAYS AS IDENTITY,
    name VARCHAR2(255) NOT NULL,
    region VARCHAR2(10) NOT NULL
)
PARTITION BY LIST (region) (
    PARTITION p_apac VALUES ('VN', 'TH', 'SG', 'MY', 'ID', 'PH'),
    PARTITION p_emea VALUES ('UK', 'DE', 'FR', 'IT', 'ES'),
    PARTITION p_americas VALUES ('US', 'CA', 'MX', 'BR'),
    PARTITION p_default VALUES (DEFAULT)
);
```

### HASH Partitioning

**When to use:**
- No natural partition key
- Need to distribute data evenly
- Avoid hot spots

```sql
-- PostgreSQL
CREATE TABLE user_sessions (
    session_id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL,
    data JSONB,
    created_at TIMESTAMPTZ DEFAULT now()
) PARTITION BY HASH (session_id);

CREATE TABLE user_sessions_p0 PARTITION OF user_sessions
    FOR VALUES WITH (MODULUS 4, REMAINDER 0);
CREATE TABLE user_sessions_p1 PARTITION OF user_sessions
    FOR VALUES WITH (MODULUS 4, REMAINDER 1);
CREATE TABLE user_sessions_p2 PARTITION OF user_sessions
    FOR VALUES WITH (MODULUS 4, REMAINDER 2);
CREATE TABLE user_sessions_p3 PARTITION OF user_sessions
    FOR VALUES WITH (MODULUS 4, REMAINDER 3);
```

```sql
-- Oracle
CREATE TABLE user_sessions (
    session_id RAW(16) DEFAULT SYS_GUID() PRIMARY KEY,
    user_id NUMBER NOT NULL,
    data CLOB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP
)
PARTITION BY HASH (session_id)
PARTITIONS 8;
```

### Composite (Sub-partitioning)

**When to use:**
- Need to combine multiple criteria
- Large tables with complex access patterns
- Range-List or Range-Hash are most common

```sql
-- Oracle: Range-List
CREATE TABLE sales (
    sale_id NUMBER GENERATED ALWAYS AS IDENTITY,
    sale_date DATE NOT NULL,
    region VARCHAR2(10) NOT NULL,
    amount NUMBER(12,2) NOT NULL
)
PARTITION BY RANGE (sale_date)
SUBPARTITION BY LIST (region)
(
    PARTITION p_2024_q1 VALUES LESS THAN (TO_DATE('2024-04-01', 'YYYY-MM-DD'))
    (
        SUBPARTITION p_2024_q1_apac VALUES ('VN', 'TH', 'SG'),
        SUBPARTITION p_2024_q1_emea VALUES ('UK', 'DE', 'FR'),
        SUBPARTITION p_2024_q1_americas VALUES ('US', 'CA', 'MX')
    ),
    PARTITION p_2024_q2 VALUES LESS THAN (TO_DATE('2024-07-01', 'YYYY-MM-DD'))
    (
        SUBPARTITION p_2024_q2_apac VALUES ('VN', 'TH', 'SG'),
        SUBPARTITION p_2024_q2_emea VALUES ('UK', 'DE', 'FR'),
        SUBPARTITION p_2024_q2_americas VALUES ('US', 'CA', 'MX')
    )
);
```

```sql
-- PostgreSQL: Range-Hash (multi-level)
CREATE TABLE events (
    event_id BIGINT GENERATED ALWAYS AS IDENTITY,
    event_date DATE NOT NULL,
    user_id BIGINT NOT NULL,
    data JSONB,
    PRIMARY KEY (event_id, event_date, user_id)
) PARTITION BY RANGE (event_date);

CREATE TABLE events_2024_01 PARTITION OF events
    FOR VALUES FROM ('2024-01-01') TO ('2024-02-01')
    PARTITION BY HASH (user_id);

CREATE TABLE events_2024_01_p0 PARTITION OF events_2024_01
    FOR VALUES WITH (MODULUS 4, REMAINDER 0);
-- ... more hash partitions
```

---

## Partition Key Selection

### Best Practices

| Criteria | Good | Bad |
|----------|------|-----|
| **Cardinality** | High (many unique values) | Low (few values = hot partition) |
| **Query alignment** | Key used in WHERE clause | Key not in queries |
| **Distribution** | Even data distribution | Skewed (90% in one partition) |
| **Stability** | Values don't change | Frequently updated |

### Common Partition Keys by Use Case

| Use Case | Recommended Key | Partition Type |
|----------|-----------------|----------------|
| **Transaction logs** | transaction_date | RANGE (monthly/daily) |
| **User activity** | activity_date + user_id | RANGE + HASH |
| **Multi-tenant SaaS** | tenant_id | LIST or HASH |
| **IoT sensor data** | timestamp | RANGE (hourly/daily) |
| **E-commerce orders** | order_date | RANGE (monthly) |
| **Geographic data** | region/country | LIST |
| **Session data** | session_id | HASH |

---

## Partition Maintenance

### Data Lifecycle Operations

```sql
-- PostgreSQL: Drop old partition
DROP TABLE orders_2023_q1;

-- Oracle: Drop partition
ALTER TABLE orders DROP PARTITION p_2023_q1;

-- Oracle: Truncate partition (faster than DELETE)
ALTER TABLE orders TRUNCATE PARTITION p_2023_q1;

-- Oracle: Move partition to archive tablespace
ALTER TABLE orders MOVE PARTITION p_2023_q1 
    TABLESPACE archive_ts;
```

### Adding New Partitions

```sql
-- PostgreSQL: Create new partition
CREATE TABLE orders_2025_q1 PARTITION OF orders
    FOR VALUES FROM ('2025-01-01') TO ('2025-04-01');

-- Oracle with INTERVAL: Automatic
-- Oracle manual:
ALTER TABLE orders ADD PARTITION p_2025_q1
    VALUES LESS THAN (TO_DATE('2025-04-01', 'YYYY-MM-DD'));

-- MySQL: Reorganize MAXVALUE partition
ALTER TABLE orders REORGANIZE PARTITION p_future INTO (
    PARTITION p202501 VALUES LESS THAN (202502),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);
```

### Partition Exchange (Fast bulk load)

```sql
-- Oracle: Exchange partition with staging table
CREATE TABLE orders_staging AS SELECT * FROM orders WHERE 1=0;
-- Load data into orders_staging
ALTER TABLE orders EXCHANGE PARTITION p_2024_q1 
    WITH TABLE orders_staging;
```

---

## Constraints and Limitations

### Primary Key Must Include Partition Key

```sql
-- ❌ WRONG: PK does not include partition key
CREATE TABLE orders (
    order_id BIGINT PRIMARY KEY,  -- Error!
    order_date DATE NOT NULL
) PARTITION BY RANGE (order_date);

-- ✅ CORRECT: PK includes partition key
CREATE TABLE orders (
    order_id BIGINT,
    order_date DATE NOT NULL,
    PRIMARY KEY (order_id, order_date)
) PARTITION BY RANGE (order_date);
```

### Foreign Key Limitations

| Database | FK from Partitioned | FK to Partitioned |
|----------|---------------------|-------------------|
| PostgreSQL 12+ | ✅ Yes | ✅ Yes |
| Oracle | ✅ Yes | ✅ Yes |
| MySQL | ❌ No | ❌ No |

### Unique Constraints

```sql
-- Unique constraint must include partition key
CREATE TABLE users (
    user_id BIGINT,
    email TEXT NOT NULL,
    region TEXT NOT NULL,
    PRIMARY KEY (user_id, region),
    UNIQUE (email, region)  -- Must include region
) PARTITION BY LIST (region);
```

---

## Context-Based Recommendations

### Scenario 1: Time-series logs (100M+ rows/month)

```
Analysis:
├── Data: High volume, append-only
├── Queries: Always filter by time range
├── Retention: Keep 12 months, delete older
└── Recommendation: RANGE by month + automated partition management

Partition Strategy:
├── Partition key: log_date (RANGE monthly)
├── Index: (log_date, log_level) within partition
├── Maintenance: Drop partitions > 12 months
└── Tool: pg_partman (PostgreSQL) or INTERVAL (Oracle)
```

### Scenario 2: Multi-tenant SaaS

```
Analysis:
├── Data: Isolated per tenant
├── Queries: Always include tenant_id
├── Scale: Varies greatly per tenant
└── Recommendation: LIST by tenant_id (if <100 tenants) or HASH

Partition Strategy:
├── Small tenants: Shared partition
├── Large tenants: Dedicated partition
├── Queries: Partition pruning on tenant_id
└── Consider: Schema-per-tenant for very large tenants
```

### Scenario 3: E-commerce orders

```
Analysis:
├── Data: Orders grow over time
├── Queries: Recent orders frequent, old orders rare
├── Retention: Keep all, but archive old
└── Recommendation: RANGE by order_date (monthly)

Partition Strategy:
├── Hot partitions: Current + last 3 months on fast storage
├── Cold partitions: Older data on archive storage
├── Indexes: order_id, user_id, status per partition
└── Maintenance: Monthly partition creation automation
```

---

## Checklist Before Partitioning

- [ ] Does table have > 10 million rows or will grow to that?
- [ ] Is there a clear partition key suitable for query patterns?
- [ ] Have limitations (PK, FK, Unique constraints) been understood?
- [ ] Is there a partition maintenance plan (add/drop)?
- [ ] Has performance been tested with EXPLAIN ANALYZE?
- [ ] Is there automation for partition management?

---

## Anti-Patterns

- ❌ Partitioning small table (< 1 million rows) - overhead > benefit
- ❌ Partition key not in WHERE clause - no pruning
- ❌ Too many partitions (> 1000) - planning overhead
- ❌ Partition key with low cardinality - hot partitions
- ❌ No partition maintenance plan - partition sprawl
- ❌ Ignoring partition key in queries - full table scan
