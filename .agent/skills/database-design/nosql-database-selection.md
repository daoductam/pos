# NoSQL Database Selection Guide

> NoSQL: Model queries first, not entities.

## Core Mental Shift

| Feature | SQL (Relational) | NoSQL (Distributed) |
|---------|------------------|---------------------|
| **Data modeling** | Model Entities + Relationships | Model **Queries** (Access Patterns) |
| **Joins** | CPU-intensive, at read time | **Pre-computed** (Denormalized) |
| **Storage cost** | Expensive (minimize duplication) | Cheap (duplicate for read speed) |
| **Consistency** | ACID (Strong) | **BASE (Eventual)** / Tunable |
| **Scalability** | Vertical (Bigger machine) | **Horizontal** (More nodes) |

## Decision Tree

```
What are your requirements?
│
├── Document Store / Flexible Schema
│   ├── General purpose → MongoDB
│   ├── Full-text search → Elasticsearch
│   └── Offline-first / Sync → CouchDB
│
├── Wide-Column / High Write Throughput
│   ├── Time-series / IoT → Cassandra, ScyllaDB
│   └── Analytics → ClickHouse, Bigtable
│
├── Key-Value / Caching
│   ├── In-memory cache → Redis
│   └── Serverless → DynamoDB
│
├── Graph Data
│   └── Neo4j, Amazon Neptune
│
└── Multi-model
    └── ArangoDB, CosmosDB
```

## MongoDB

### When to choose MongoDB:
- Flexible schema / Schema evolution
- Document-oriented data
- Rapid development / Prototyping
- Moderate scale (millions of documents)
- Aggregation pipeline needs

### MongoDB Design Patterns:
```javascript
// Embedding vs Referencing
// EMBED when: Data is always accessed together
{
    _id: ObjectId("..."),
    name: "Order #123",
    items: [
        { product: "Widget", qty: 2, price: 10 },
        { product: "Gadget", qty: 1, price: 25 }
    ]
}

// REFERENCE when: Data is large or frequently updated
{
    _id: ObjectId("..."),
    name: "Order #123",
    items: [ObjectId("item1"), ObjectId("item2")]
}
```

### MongoDB Best Practices:
- Create indexes for query patterns
- Use compound indexes (order matters)
- Avoid unbounded arrays
- Use schema validation
- Consider sharding early for scale

## Apache Cassandra

### When to choose Cassandra:
- Write-heavy workloads (IoT, logs, events)
- Time-series data
- High availability requirement (no single point of failure)
- Geographic distribution
- Linear scalability needed

### Cassandra Design Patterns:

```cql
-- Query-First Design: Table per query pattern
-- Pattern: Get user orders by date
CREATE TABLE orders_by_user (
    user_id UUID,
    order_date TIMESTAMP,
    order_id UUID,
    total DECIMAL,
    PRIMARY KEY ((user_id), order_date, order_id)
) WITH CLUSTERING ORDER BY (order_date DESC);

-- Pattern: Get user by email
CREATE TABLE users_by_email (
    email TEXT PRIMARY KEY,
    user_id UUID,
    name TEXT
);
```

### Cassandra Anti-Patterns:
- ❌ **ALLOW FILTERING** in production (full cluster scan)
- ❌ Low-cardinality partition keys (hot partitions)
- ❌ Relational modeling (joins don't exist)
- ❌ High-velocity deletes (tombstone issues)

## CouchDB

### When to choose CouchDB:
- Offline-first applications
- Multi-master replication / Sync
- Mobile/Edge data sync
- Simple REST API preferred
- Conflict resolution needed

### CouchDB Design Patterns:

```json
// Document with type field
{
    "_id": "user:abc123",
    "type": "user",
    "email": "user@example.com",
    "profile": {
        "name": "John Doe"
    }
}

// Design Document with Views
{
    "_id": "_design/users",
    "views": {
        "by_email": {
            "map": "function(doc) { if(doc.type === 'user') emit(doc.email, null); }"
        },
        "by_type": {
            "map": "function(doc) { emit(doc.type, 1); }",
            "reduce": "_count"
        }
    }
}
```

## DynamoDB

### When to choose DynamoDB:
- AWS ecosystem
- Serverless architecture
- Predictable performance at scale
- Single-digit millisecond latency
- Managed service preferred

### DynamoDB Design Patterns:

```
Single-Table Design (Adjacency List):
┌─────────────────┬─────────────────┬────────────────┐
│ PK (Partition)  │ SK (Sort)       │ Attributes     │
├─────────────────┼─────────────────┼────────────────┤
│ USER#123        │ PROFILE         │ {name, email}  │
│ USER#123        │ ORDER#001       │ {total, status}│
│ USER#123        │ ORDER#002       │ {total, status}│
│ ORDER#001       │ ITEM#1          │ {product, qty} │
└─────────────────┴─────────────────┴────────────────┘

Query PK="USER#123" → Gets profile AND all orders in one request
```

### DynamoDB Best Practices:
- Use GSI for alternative access patterns
- Use TTL for automatic data expiration
- Understand WCU/RCU for capacity planning
- Use single-table design when possible

## Comparison Matrix

| Feature | MongoDB | Cassandra | CouchDB | DynamoDB |
|---------|---------|-----------|---------|----------|
| **Data Model** | Document | Wide-column | Document | Key-value/Document |
| **Query** | Flexible | Limited | MapReduce | Limited |
| **Scale** | Sharding | Linear | Replication | Auto-scaling |
| **Consistency** | Tunable | Tunable | Eventual | Tunable |
| **Best For** | General NoSQL | Time-series/IoT | Offline-first | Serverless |

## Expert Checklist

Before finalizing NoSQL schema:

- [ ] **Access Pattern Coverage**: Does every query pattern have a corresponding table/index?
- [ ] **Cardinality Check**: Does the Partition Key have enough unique values to distribute load?
- [ ] **Partition Size**: Can a single partition grow indefinitely? (>10GB = needs sharding)
- [ ] **Consistency Requirement**: Can the application tolerate eventual consistency?
- [ ] **Denormalization Plan**: Is the data duplication strategy defined?
