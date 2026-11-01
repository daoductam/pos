# Java Spring JPA / Hibernate Annotations Reference

## Key Annotations to Parse

### Entity-level
| Annotation | Meaning | Extract |
|------------|---------|---------|
| `@Entity` | Marks class as JPA entity | entity name = class name |
| `@Table(name="...")` | Override table name | table_name |
| `@Table(schema="...")` | Schema prefix | note in description |
| `@Inheritance(strategy=...)` | Inheritance mapping | note in description |
| `@DiscriminatorColumn` | Discriminator for inheritance | note |

### Field-level
| Annotation | Meaning | Extract |
|------------|---------|---------|
| `@Id` | Primary key | `primary_key = true` |
| `@GeneratedValue` | Auto-generated PK | note strategy in constraints |
| `@Column(name="...", nullable=..., length=..., unique=..., precision=..., scale=...)` | Column mapping | all attributes |
| `@Column(columnDefinition="...")` | Raw SQL type | `sql_type` |
| `@Basic(optional=...)` | Nullable override | `nullable` |
| `@Lob` | Large object | `data_type = CLOB/BLOB` |
| `@Enumerated` | Enum type | `data_type = ENUM`, list values from Java enum |
| `@Temporal(TemporalType.DATE/TIME/TIMESTAMP)` | Date type | map to `DATE`/`TIME`/`TIMESTAMP` |
| `@Transient` | Not persisted | skip this field |
| `@Version` | Optimistic lock version | note in constraints |
| `@CreationTimestamp`, `@UpdateTimestamp` | Audit fields | note in description |

### Relationship annotations
| Annotation | type | Attributes to extract |
|------------|------|-----------------------|
| `@OneToMany` | OneToMany | `mappedBy`, `cascade`, `fetch` |
| `@ManyToOne` | ManyToOne | `optional` (nullable), `fetch` |
| `@OneToOne` | OneToOne | `mappedBy`, `cascade`, `fetch` |
| `@ManyToMany` | ManyToMany | `mappedBy`, `cascade` |
| `@JoinColumn(name="...")` | FK column name | `column_name` on relationship field |
| `@JoinTable` | Join table for M2M | note join table name and columns |

### Validation (Bean Validation / Hibernate Validator)
Capture these as `validation` field:
`@NotNull`, `@NotBlank`, `@NotEmpty`, `@Size(min=,max=)`, `@Min`, `@Max`,
`@Email`, `@Pattern`, `@Positive`, `@PositiveOrZero`, `@DecimalMin`, `@DecimalMax`

### Index annotations
```java
@Table(indexes = {
    @Index(name = "idx_email", columnList = "email", unique = true)
})
```
Extract: index name, column list, unique flag.

---

## Java Type → SQL Type Mapping

| Java Type | SQL Type |
|-----------|----------|
| `String` | `VARCHAR(length)` or `TEXT` |
| `Long` / `long` | `BIGINT` |
| `Integer` / `int` | `INTEGER` |
| `Double` / `double` | `DOUBLE` |
| `BigDecimal` | `DECIMAL(precision, scale)` |
| `Boolean` / `boolean` | `BOOLEAN` |
| `LocalDate` | `DATE` |
| `LocalDateTime` / `Date` | `TIMESTAMP` |
| `LocalTime` | `TIME` |
| `byte[]` | `BLOB` |
| `UUID` | `UUID` / `VARCHAR(36)` |
| Enum | `VARCHAR` or `INTEGER` depending on `@Enumerated` |

---

## Parsing Example

```java
@Entity
@Table(name = "customer",
       indexes = @Index(name="idx_email", columnList="email", unique=true))
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 200)
    @NotBlank
    private String fullName;

    @Column(unique = true, nullable = false, length = 320)
    @Email
    private String email;

    @Column(name = "created_at")
    @CreationTimestamp
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Order> orders;
}
```

Extract:
- Entity: `Customer` → table: `customer`
- Fields: `id` (PK, BIGINT, auto), `fullName` (VARCHAR(200), NOT NULL), `email` (VARCHAR(320), NOT NULL, UNIQUE), `createdAt` (TIMESTAMP, auto)
- Relationship: `orders` → OneToMany → `Order`, cascade=ALL, fetch=LAZY
- Index: `idx_email` on `email` UNIQUE
