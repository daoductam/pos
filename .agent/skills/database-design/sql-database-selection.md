# SQL Database Selection Guide

> Choose database based on context, not default.

## Decision Tree

```
What are your requirements?
│
├── Enterprise, Complex Transactions, High Security
│   └── Oracle Database (≥12c)
│
├── Full relational features, Extensions, Vector Search
│   ├── Self-hosted → PostgreSQL
│   └── Serverless → Neon, Supabase
│
├── Web Applications, Read-heavy, MySQL Ecosystem
│   ├── Self-hosted → MySQL 8.0+
│   └── Serverless → PlanetScale
│
├── Edge deployment / Ultra-low latency
│   └── Turso (edge SQLite)
│
├── Simple / Embedded / Local / Mobile
│   └── SQLite
│
└── Global distribution / NewSQL
    └── CockroachDB, TiDB, YugabyteDB
```

## Comparison Matrix

| Database | Best For | Pros | Cons |
|----------|----------|------|------|
| **Oracle (≥12c)** | Enterprise, Financial, Healthcare | Mature, Feature-rich, Support | Cost, Complexity |
| **PostgreSQL** | General purpose, Complex queries | Free, Extensions, Active community | Needs tuning for scale |
| **MySQL** | Web apps, Read-heavy | Fast reads, Large ecosystem | Less features than PG |
| **SQLite** | Embedded, Local, Mobile | Zero config, Single file | Single writer |
| **CockroachDB** | Global distribution | ACID + Horizontal scale | Complexity |

## Oracle Database (≥12c)

### When to choose Oracle:
- Enterprise applications with high SLA requirements
- Financial systems needing strict ACID compliance
- Healthcare/Government with compliance requirements
- Existing Oracle ecosystem and expertise
- Need Real Application Clusters (RAC) for HA
- Advanced security features (TDE, Data Vault, Audit)

### Oracle-specific Features:
- **Partitioning**: Range, List, Hash, Composite, Interval
- **In-Memory Column Store**: Analytics acceleration
- **Data Guard**: Disaster recovery
- **Flashback**: Point-in-time recovery
- **JSON Support**: Native JSON data type (21c+)

## PostgreSQL

### When to choose PostgreSQL:
- Need full relational features for free
- Complex queries with CTEs, Window functions
- JSONB storage with indexing
- Full-text search with trigram
- Vector search (pgvector)
- Geographic data (PostGIS)

### PostgreSQL-specific Features:
- **Extensions**: pgvector, PostGIS, TimescaleDB
- **JSONB**: Document store within RDBMS
- **Logical Replication**: Selective replication
- **Row-Level Security**: Fine-grained access control

## MySQL

### When to choose MySQL:
- Web applications with read-heavy workload
- LAMP/LEMP stack applications
- WordPress, Drupal, Magento
- Simple to moderate query complexity
- Large MySQL ecosystem tools

### MySQL-specific Features:
- **InnoDB**: ACID-compliant storage engine
- **Group Replication**: Built-in HA
- **MySQL Router**: Load balancing
- **Clone Plugin**: Fast provisioning

## Questions to Ask

1. **Scale**: How many requests/second? Data size?
2. **Complexity**: How complex are the queries?
3. **Budget**: Is there a budget for commercial DB?
4. **Compliance**: Are there compliance requirements?
5. **Team**: Which DB does the team have experience with?
6. **Ecosystem**: Existing tools and integrations?
7. **Deployment**: On-premise or cloud?
