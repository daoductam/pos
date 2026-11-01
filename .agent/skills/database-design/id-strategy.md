# ID Strategy Guide

> Choose ID strategy based on architecture and requirements.

## Comparison Matrix

| Strategy | Size | Sortable | Distributed | URL-safe | Use Case |
|----------|------|----------|-------------|----------|----------|
| **Auto Increment** | 4-8 bytes | ✅ Yes | ❌ No | ✅ Yes | Single DB, simple apps |
| **UUID v4** | 16 bytes | ❌ No | ✅ Yes | ❌ No | Distributed, random |
| **UUID v7** | 16 bytes | ✅ Yes | ✅ Yes | ❌ No | Modern distributed |
| **ULID** | 16 bytes | ✅ Yes | ✅ Yes | ✅ Yes | URL-friendly distributed |
| **Snowflake** | 8 bytes | ✅ Yes | ✅ Yes | ✅ Yes | High-throughput systems |
| **NanoID** | Variable | ❌ No | ✅ Yes | ✅ Yes | Short URLs, tokens |

---

## Auto Increment

### When to use:
- Single database instance
- Simple applications
- Sequential ordering is important
- Compact storage is needed

### Implementations:

```sql
-- PostgreSQL (Recommended)
CREATE TABLE users (
    user_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email TEXT NOT NULL
);

-- MySQL
CREATE TABLE users (
    user_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL
) ENGINE=InnoDB;

-- Oracle (12c+)
CREATE TABLE users (
    user_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email VARCHAR2(255) NOT NULL
);

-- Oracle (Traditional)
CREATE SEQUENCE users_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE users (
    user_id NUMBER PRIMARY KEY,
    email VARCHAR2(255) NOT NULL
);
```

### Caveats:
- ⚠️ Exposes record count
- ⚠️ Not suitable for distributed systems
- ⚠️ Gaps happen (rollbacks, deletes) - don't try to fix

---

## UUID v4

### When to use:
- Distributed systems without coordination
- Security: Do not expose patterns
- Merging data from multiple sources
- API identifiers

### Implementations:

```sql
-- PostgreSQL
CREATE TABLE users (
    user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email TEXT NOT NULL
);

-- MySQL (Store as BINARY for efficiency)
CREATE TABLE users (
    user_id BINARY(16) PRIMARY KEY DEFAULT (UUID_TO_BIN(UUID())),
    email VARCHAR(255) NOT NULL
);

-- Oracle
CREATE TABLE users (
    user_id RAW(16) DEFAULT SYS_GUID() PRIMARY KEY,
    email VARCHAR2(255) NOT NULL
);

-- MongoDB (default _id)
// MongoDB uses ObjectId by default, but you can use UUID
db.users.insertOne({
    _id: UUID(),
    email: "user@example.com"
});
```

### Caveats:
- ⚠️ 128-bit = larger storage
- ⚠️ Poor index locality (random)
- ⚠️ Not sortable by time

---

## UUID v7 (Recommended for new projects)

### When to use:
- Modern distributed systems
- Need time-sortable IDs
- Better index performance than v4
- PostgreSQL 18+ native support

### Implementations:

```sql
-- PostgreSQL 18+ (Native)
CREATE TABLE users (
    user_id UUID PRIMARY KEY DEFAULT uuidv7(),
    email TEXT NOT NULL
);

-- PostgreSQL (Custom function for older versions)
CREATE OR REPLACE FUNCTION uuid_generate_v7()
RETURNS UUID AS $$
DECLARE
    unix_ts_ms BYTEA;
    uuid_bytes BYTEA;
BEGIN
    unix_ts_ms = substring(int8send(floor(extract(epoch FROM clock_timestamp()) * 1000)::BIGINT) FROM 3);
    uuid_bytes = unix_ts_ms || gen_random_bytes(10);
    uuid_bytes = set_byte(uuid_bytes, 6, (get_byte(uuid_bytes, 6) & 15) | 112); -- version 7
    uuid_bytes = set_byte(uuid_bytes, 8, (get_byte(uuid_bytes, 8) & 63) | 128); -- variant
    RETURN encode(uuid_bytes, 'hex')::UUID;
END;
$$ LANGUAGE plpgsql VOLATILE;

-- Java (using uuid-creator library)
// com.github.f4b6a3:uuid-creator
UUID uuid = UuidCreator.getTimeOrderedEpoch();
```

---

## ULID (Universally Unique Lexicographically Sortable Identifier)

### When to use:
- URL-safe identifiers needed
- Time-sortable requirement
- Case-insensitive environments
- Better human readability than UUID

### Format:
```
 01ARZ3NDEKTSV4RRFFQ69G5FAV
 |-------||-------------|
 Timestamp   Randomness
 (48 bits)   (80 bits)
```

### Implementations:

```java
// Java (using ulid-creator)
// com.github.f4b6a3:ulid-creator
String ulid = UlidCreator.getUlid().toString();
// Output: 01ARZ3NDEKTSV4RRFFQ69G5FAV

// Store in database
// PostgreSQL: TEXT or VARCHAR(26)
// MySQL: VARCHAR(26) or BINARY(16) for efficiency
```

```javascript
// JavaScript (using ulid package)
import { ulid } from 'ulid';
const id = ulid(); // 01ARZ3NDEKTSV4RRFFQ69G5FAV
```

```sql
-- PostgreSQL storage
CREATE TABLE users (
    user_id VARCHAR(26) PRIMARY KEY,
    email TEXT NOT NULL
);
```

---

## Snowflake ID

### When to use:
- High-throughput systems (Twitter, Discord scale)
- 64-bit integer requirement
- Time-sortable
- Need machine/datacenter identification

### Structure:
```
 | 1 bit | 41 bits       | 5 bits      | 5 bits    | 12 bits  |
 | sign  | timestamp     | datacenter  | worker    | sequence |
 |-------|---------------|-------------|-----------|----------|
 | 0     | milliseconds  | 0-31        | 0-31      | 0-4095   |
```

### Implementations:

```java
// Java Implementation
public class SnowflakeIdGenerator {
    private static final long EPOCH = 1609459200000L; // 2021-01-01
    private static final long DATACENTER_BITS = 5L;
    private static final long WORKER_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;
    
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_BITS);
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_BITS);
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);
    
    private static final long WORKER_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_SHIFT = SEQUENCE_BITS + WORKER_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_BITS + DATACENTER_BITS;
    
    private final long datacenterId;
    private final long workerId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;
    
    public SnowflakeIdGenerator(long datacenterId, long workerId) {
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException("Datacenter ID out of range");
        }
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException("Worker ID out of range");
        }
        this.datacenterId = datacenterId;
        this.workerId = workerId;
    }
    
    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();
        
        if (timestamp < lastTimestamp) {
            throw new RuntimeException("Clock moved backwards");
        }
        
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        
        lastTimestamp = timestamp;
        
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_SHIFT)
                | (workerId << WORKER_SHIFT)
                | sequence;
    }
    
    private long waitNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}
```

```sql
-- PostgreSQL Function
CREATE SEQUENCE snowflake_seq;

CREATE OR REPLACE FUNCTION generate_snowflake_id()
RETURNS BIGINT AS $$
DECLARE
    epoch BIGINT := 1609459200000; -- 2021-01-01
    seq_id BIGINT;
    now_millis BIGINT;
    shard_id INT := 1; -- Configure per instance
    result BIGINT;
BEGIN
    SELECT nextval('snowflake_seq') % 4096 INTO seq_id;
    SELECT FLOOR(EXTRACT(EPOCH FROM clock_timestamp()) * 1000) INTO now_millis;
    result := ((now_millis - epoch) << 22) | (shard_id << 12) | seq_id;
    RETURN result;
END;
$$ LANGUAGE plpgsql VOLATILE;

-- Usage
CREATE TABLE users (
    user_id BIGINT PRIMARY KEY DEFAULT generate_snowflake_id(),
    email TEXT NOT NULL
);
```

---

## Decision Guide

### Choose Auto Increment when:
- ✅ Single database
- ✅ Simple CRUD application
- ✅ Storage optimization is important
- ❌ Do not expose ID to public API

### Choose UUID v4 when:
- ✅ Distributed systems
- ✅ Security (unpredictable)
- ✅ Merging data from multiple sources
- ❌ Sort by time is not needed

### Choose UUID v7 when:
- ✅ Modern distributed systems
- ✅ Need time-sortable
- ✅ Better index performance
- ✅ PostgreSQL 18+ or library support available

### Choose ULID when:
- ✅ URL-safe IDs are needed
- ✅ More human-readable than UUID
- ✅ Time-sortable
- ✅ Case-insensitive environment

### Choose Snowflake when:
- ✅ High-throughput (>10K IDs/second)
- ✅ 64-bit integer requirement
- ✅ Need machine identification
- ✅ Twitter/Discord scale systems

---

## Migration Between Strategies

### From Auto Increment to UUID

```sql
-- Step 1: Add UUID column
ALTER TABLE users ADD COLUMN uuid UUID;

-- Step 2: Populate UUIDs
UPDATE users SET uuid = gen_random_uuid();

-- Step 3: Add NOT NULL constraint
ALTER TABLE users ALTER COLUMN uuid SET NOT NULL;

-- Step 4: Create unique index
CREATE UNIQUE INDEX idx_users_uuid ON users (uuid);

-- Step 5: Update application to use UUID
-- Step 6: Drop old ID (optional, careful with FKs)
```
