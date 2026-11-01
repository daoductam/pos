# ORM Frameworks — Parsing Reference

## TypeORM (TypeScript)

### Annotations
| Decorator | Meaning |
|-----------|---------|
| `@Entity('table_name')` | Entity + table name |
| `@PrimaryGeneratedColumn()` | Auto PK |
| `@PrimaryColumn()` | Manual PK |
| `@Column({ type, nullable, default, length, unique })` | Field mapping |
| `@CreateDateColumn` / `@UpdateDateColumn` | Audit timestamps |
| `@OneToMany(() => Target, t => t.field)` | Relationship |
| `@ManyToOne(() => Target)` | Relationship |
| `@ManyToMany(() => Target)` | Relationship |
| `@JoinColumn({ name })` | FK column name |
| `@JoinTable` | Join table for M2M |
| `@Index(['col1', 'col2'], { unique })` | Index |

### TypeScript → SQL Mapping
| TS Type | SQL Type |
|---------|----------|
| `string` | `VARCHAR` |
| `number` | `INT` or `FLOAT` |
| `boolean` | `BOOLEAN` |
| `Date` | `TIMESTAMP` |
| `'uuid'` column type | `UUID` |
| `'text'` column type | `TEXT` |
| `'decimal'` | `DECIMAL` |
| `'enum'` | `ENUM` |

### Example
```typescript
@Entity('product')
@Index(['sku'], { unique: true })
export class Product {
  @PrimaryGeneratedColumn()
  id: number;

  @Column({ length: 100, nullable: false })
  name: string;

  @Column({ type: 'decimal', precision: 10, scale: 2 })
  price: number;

  @ManyToOne(() => Category, cat => cat.products)
  @JoinColumn({ name: 'category_id' })
  category: Category;
}
```

---

## Django ORM (Python)

### Field Types → SQL Mapping
| Django Field | SQL Type |
|-------------|----------|
| `CharField(max_length=N)` | `VARCHAR(N)` |
| `TextField()` | `TEXT` |
| `IntegerField()` | `INTEGER` |
| `BigIntegerField()` / `BigAutoField` | `BIGINT` |
| `FloatField()` | `FLOAT` |
| `DecimalField(max_digits, decimal_places)` | `DECIMAL` |
| `BooleanField()` | `BOOLEAN` |
| `DateField()` | `DATE` |
| `DateTimeField()` | `TIMESTAMP` |
| `EmailField()` | `VARCHAR(254)` |
| `UUIDField()` | `UUID` |
| `JSONField()` | `JSON` / `JSONB` |
| `FileField()` / `ImageField()` | `VARCHAR` (path) |
| `ForeignKey(Model, on_delete=...)` | FK (BIGINT ref) |
| `ManyToManyField(Model)` | Join table |
| `OneToOneField(Model)` | FK + UNIQUE |

### Key kwargs to extract
- `null=True` → `nullable = true`
- `blank=True` → application-level (note in validation)
- `default=...` → default value
- `unique=True` → unique constraint
- `db_column='...'` → column name override
- `db_index=True` → creates index
- `choices=[...]` → enum-like, list values in constraints
- `verbose_name='...'` → description
- `help_text='...'` → description

### Meta class
```python
class Meta:
    db_table = 'my_table'           # → table_name
    unique_together = [['a','b']]   # → composite unique
    indexes = [models.Index(fields=['name'])]
```

### Example
```python
class Order(models.Model):
    customer = models.ForeignKey(Customer, on_delete=models.CASCADE, related_name='orders')
    status = models.CharField(max_length=20, choices=[('PENDING','Pending'),('DONE','Done')])
    total = models.DecimalField(max_digits=10, decimal_places=2)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = 'orders'
```

---

## SQL DDL (CREATE TABLE)

Parse raw SQL. Support:
- `CREATE TABLE [IF NOT EXISTS] schema.table_name ( ... );`
- `column_name data_type [NOT NULL] [DEFAULT val] [UNIQUE] [PRIMARY KEY]`
- `CONSTRAINT name PRIMARY KEY (cols)`
- `CONSTRAINT name UNIQUE (cols)`
- `CONSTRAINT name FOREIGN KEY (col) REFERENCES other_table(col) [ON DELETE ...]`
- `CREATE [UNIQUE] INDEX name ON table (cols);`
- Comments: `-- comment` or `/* comment */` above column defs as description
- `COMMENT ON COLUMN table.col IS '...'` (PostgreSQL)
- `COMMENT '...'` (MySQL inline)

SQL Type pass-through: keep the raw SQL type as-is.

---

## Prisma Schema

### Keywords
| Keyword | Meaning |
|---------|---------|
| `model ModelName { }` | Entity; table name = snake_case of ModelName |
| `@@map("table_name")` | Explicit table name |
| `@id` | Primary key |
| `@default(...)` | Default value |
| `@unique` | Unique constraint |
| `@map("col_name")` | Column name override |
| `@relation(...)` | Relationship |
| `@@unique([...])` | Composite unique |
| `@@index([...])` | Index |

### Prisma → SQL Type Mapping
| Prisma | SQL |
|--------|-----|
| `String` | `VARCHAR` / `TEXT` |
| `Int` | `INTEGER` |
| `BigInt` | `BIGINT` |
| `Float` | `FLOAT` |
| `Decimal` | `DECIMAL` |
| `Boolean` | `BOOLEAN` |
| `DateTime` | `TIMESTAMP` |
| `Json` | `JSON` |
| `Bytes` | `BYTEA` / `BLOB` |
| `@db.VarChar(N)` | `VARCHAR(N)` |
| `field?` (trailing ?) | nullable = true |

---

## SQLAlchemy (Python)

### Column definitions
```python
Column('name', String(100), nullable=False, unique=True, default='x', comment='...')
Column(Integer, primary_key=True, autoincrement=True)
Column(ForeignKey('other_table.id'))
```

### Relationship
```python
relationship('Target', back_populates='...', cascade='all, delete')
```

### Table name
- `__tablename__ = 'table_name'` on the model class

---

## ActiveRecord / Rails

Parse `db/schema.rb`:
```ruby
create_table "users", force: :cascade do |t|
  t.string "email", null: false
  t.integer "age"
  t.timestamps
  t.index ["email"], name: "index_users_on_email", unique: true
end
```

Parse migration files as fallback if `schema.rb` not present.
Ruby type → SQL: `string`→VARCHAR, `integer`→INTEGER, `text`→TEXT, `boolean`→BOOLEAN,
`datetime`→TIMESTAMP, `decimal`→DECIMAL, `float`→FLOAT, `binary`→BLOB.

---

## Hibernate XML Mappings

```xml
<class name="com.example.Customer" table="customer">
  <id name="id" type="long"><generator class="native"/></id>
  <property name="fullName" column="full_name" type="string" not-null="true" length="200"/>
  <many-to-one name="category" class="Category" column="category_id" not-null="true"/>
  <bag name="orders" inverse="true" cascade="all">
    <key column="customer_id"/>
    <one-to-many class="Order"/>
  </bag>
</class>
```

Extract: class name → entity, table attr → table_name, property elements → fields,
many-to-one → ManyToOne relationship, bag/set/list with one-to-many → OneToMany.
