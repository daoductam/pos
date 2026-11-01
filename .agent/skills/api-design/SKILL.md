---
name: api-design
description: "Comprehensive API Design skill for AI Agents - REST/GraphQL/tRPC patterns, security best practices, error handling, pagination, versioning. Ask user about API-first approach before implementation."
version: "1.0.0"
category: "Backend Development"
date_created: "2026-03-11"
---

# API Design Skill

> **Skill for AI Agents** (Claude Code, Google Antigravity, etc.)
> Comprehensive API design patterns and best practices for building consistent, secure, maintainable APIs.

---

## 🎯 Skill Objective

Guide AI agents in designing and implementing production-ready APIs with:
- Consistent resource naming and HTTP conventions
- Robust error handling and validation
- Secure authentication and authorization
- Scalable pagination and filtering
- Clear versioning strategy
- Comprehensive documentation

---

## ⚡ Quick Start (Agent Instructions)

### IMPORTANT: Confirmation Rules

**Before implementing any API, ALWAYS ask the user about the approach:**

1. **Business Logic & Requirements?**
   - **ACTION:** You MUST fully understand the business requirements and the domain logic before designing the API.
   - **CONFIRM:** If any part of the business process or data relationship is unclear, you MUST ask the user to provide more details. Do not make assumptions about business rules.

2. **API-First vs Code-First?**
   - **Yes (API-First):** Design OpenAPI spec FIRST (Recommended for teams/public APIs)
   - **No (Code-First):** Start coding immediately (Faster for prototypes)

3. **Pagination Strategy?** (ONLY ask if implementing **List/Collection** endpoints)
   - **Offset-based:** Best for Data Grids, Admin panels (Supports jumping to pages)
   - **Cursor-based:** Best for Real-time, Social feeds, Infinite scroll (High performance)

4. **Authentication Method?**
   - **ACTION:** Proactively scan the codebase (check middlewares, security configs, `package.json`) to identify the current auth method (JWT, API Keys, Session, Keycloak, etc.).
   - **CONFIRM:** State your findings and ask the user to confirm.
   - **IF UNKNOWN:** You MUST explicitly ask the user what authentication method should be used before writing auth-related code.

5. **Caching Strategy?**
   - **ACTION:** Analyze the endpoint's purpose. If it serves frequently requested, rarely changed data (e.g., avatars, config, public profiles), propose a caching strategy (e.g., ETag, Cache-Control, Redis).
   - **CONFIRM:** Ask the user if they want to apply caching and which method to use.

---

## 📑 Content Map

| Topic | When to Reference |
|-------|------------------|
| [API Style Selection](#api-style-selection) | Choosing REST vs GraphQL vs tRPC |
| [REST Conventions](#rest-conventions) | Designing RESTful endpoints |
| [Error Handling](#error-handling) | Structuring error responses |
| [Pagination](#pagination) | List endpoints, large datasets |
| [Authentication](#authentication) | Auth pattern selection |
| [Rate Limiting](#rate-limiting) | API protection |
| [Caching Strategy](#caching-strategy) | Performance optimization |
| [Versioning](#versioning) | API evolution |
| [Security Best Practices](#security-best-practices) | Securing APIs |
| [Documentation](#documentation) | OpenAPI/Swagger |
| [Checklist](#api-design-checklist) | Pre-launch validation |

---

## 🔀 API Style Selection

### Decision Tree

```
Who are the API consumers?
│
├── Public API / Multiple platforms
│   └── REST + OpenAPI (widest compatibility)
│
├── Complex data needs / Multiple frontends
│   └── GraphQL (flexible queries)
│
├── TypeScript frontend + backend (monorepo)
│   └── tRPC (end-to-end type safety)
│
├── Real-time / Event-driven
│   └── WebSocket + AsyncAPI
│
└── Internal microservices
    └── gRPC (performance) or REST (simplicity)
```

### Comparison

| Factor | REST | GraphQL | tRPC |
|--------|------|---------|------|
| **Best for** | Public APIs | Complex apps | TS monorepos |
| **Learning curve** | Low | Medium | Low (if TS) |
| **Over/under fetching** | Common | Solved | Solved |
| **Type safety** | Manual (OpenAPI) | Schema-based | Automatic |
| **Caching** | HTTP native | Complex | Client-based |

### GraphQL - When to Use

```
✅ Good fit:
├── Complex, interconnected data
├── Multiple frontend platforms
├── Clients need flexible queries
├── Evolving data requirements
└── Reducing over-fetching matters

❌ Poor fit:
├── Simple CRUD operations
├── File upload heavy
├── HTTP caching important
└── Team unfamiliar with GraphQL
```

### tRPC - When to Use

```
✅ Perfect fit:
├── TypeScript on both ends
├── Monorepo structure
├── Internal tools
├── Rapid development
└── Type safety critical

❌ Poor fit:
├── Non-TypeScript clients
├── Public API
├── Need REST conventions
└── Multiple language backends
```

---

## 🏗️ REST Conventions

### Resource Naming Rules

```
Principles:
├── Use NOUNS, not verbs (resources, not actions)
├── Use PLURAL forms (/users not /user)
├── Use lowercase with hyphens (/user-profiles)
├── Nest for relationships (/users/123/posts)
└── Keep shallow (max 3 levels deep)
```

**Examples:**
```
# GOOD: Nouns, plural, lowercase, hyphenated
GET    /users
GET    /users/{id}
GET    /users/{id}/posts
GET    /blog-posts
GET    /api/v1/user-preferences

# BAD: Verbs, singular, mixed case, underscores
GET    /getUser
GET    /user/{id}
GET    /User/{id}/getPosts
GET    /blog_posts
```

### HTTP Methods

| Method | Purpose | Idempotent | Safe | Request Body |
|--------|---------|------------|------|--------------|
| GET | Read resource | Yes | Yes | No |
| POST | Create resource | No | No | Yes |
| PUT | Replace resource | Yes | No | Yes |
| PATCH | Partial update | No* | No | Yes |
| DELETE | Remove resource | Yes | No | No |

*PATCH is idempotent if you apply the same patch

### Status Codes

| Code | Meaning | When to Use |
|------|---------|-------------|
| 200 | OK | Successful GET, PUT, PATCH |
| 201 | Created | Successful POST that creates |
| 204 | No Content | Successful DELETE |
| 400 | Bad Request | Invalid input, validation error |
| 401 | Unauthorized | Missing/invalid authentication |
| 403 | Forbidden | Authenticated but not authorized |
| 404 | Not Found | Resource doesn't exist |
| 409 | Conflict | Duplicate, state conflict |
| 422 | Unprocessable | Validation failed (alternative to 400) |
| 429 | Too Many Requests | Rate limited |
| 500 | Server Error | Unexpected server error |

### CRUD Operations

```
# Collection operations
GET    /users           # List all users
POST   /users           # Create a user

# Single resource operations
GET    /users/{id}      # Get one user
PUT    /users/{id}      # Replace user
PATCH  /users/{id}      # Update user fields
DELETE /users/{id}      # Delete user

# Nested resources
GET    /users/{id}/posts       # User's posts
POST   /users/{id}/posts       # Create post for user
```

### Actions (Non-CRUD Operations)

```
# When you need actions, use verbs as sub-resources
POST   /users/{id}/activate
POST   /users/{id}/deactivate
POST   /orders/{id}/cancel
POST   /invoices/{id}/send
POST   /auth/login
POST   /auth/logout
POST   /auth/refresh
```

### Resource Design: Flat vs Nested

```
# Flat (preferred for independent resources)
GET /posts
GET /posts/{postId}
GET /comments
GET /comments/{commentId}

# Nested (for dependent resources)
GET /posts/{postId}/comments
GET /users/{userId}/settings

# TOO DEEP - avoid this
GET /companies/{id}/departments/{id}/employees/{id}/tasks

# BETTER - flatten with filters
GET /tasks?employee_id={id}
GET /employees/{id}/tasks
```

---

## ❌ Error Handling

### Standard Error Format

```typescript
interface ApiError {
  error: {
    code: string;           // Machine-readable, snake_case
    message: string;        // Human-readable description
    details?: ErrorDetail[];// Field-level errors (validation)
    requestId?: string;     // Correlation ID for debugging
  };
}

interface ErrorDetail {
  field: string;            // Field path (e.g., "user.email")
  message: string;          // Human-readable error
  code: string;             // Machine-readable code
}
```

### Error Codes Reference

```typescript
// Authentication errors
'unauthorized'              // Missing or invalid token
'token_expired'             // Token has expired
'invalid_credentials'       // Wrong email/password

// Authorization errors
'forbidden'                 // Not allowed to access
'insufficient_permissions'  // Missing required permission

// Resource errors
'not_found'                 // Resource doesn't exist
'already_exists'            // Duplicate resource (409)
'conflict'                  // State conflict

// Validation errors
'validation_error'          // Input validation failed
'invalid_format'            // Wrong format (email, date)
'required_field'            // Missing required field
'invalid_value'             // Value out of range or invalid

// Rate limiting
'rate_limited'              // Too many requests

// Server errors
'internal_error'            // Unexpected server error
'service_unavailable'       // External service down
```

### Example Error Responses

```json
// 400 Bad Request - Validation error
{
  "error": {
    "code": "validation_error",
    "message": "Invalid request parameters",
    "details": [
      { "field": "email", "message": "Invalid email format", "code": "invalid_format" },
      { "field": "age", "message": "Must be at least 13", "code": "min_value" }
    ],
    "requestId": "req_abc123"
  }
}

// 401 Unauthorized
{
  "error": {
    "code": "unauthorized",
    "message": "Invalid or expired authentication token",
    "requestId": "req_abc123"
  }
}

// 404 Not Found
{
  "error": {
    "code": "not_found",
    "message": "User with ID 'usr_123' not found",
    "requestId": "req_abc123"
  }
}

// 429 Rate Limited
{
  "error": {
    "code": "rate_limited",
    "message": "Too many requests. Please retry after 60 seconds",
    "requestId": "req_abc123"
  }
}
```

### Error Classes Implementation

```typescript
class ApiError extends Error {
  constructor(
    public statusCode: number,
    public code: string,
    message: string,
    public details?: ErrorDetail[]
  ) {
    super(message);
  }
}

class NotFoundError extends ApiError {
  constructor(resource: string, id: string) {
    super(404, 'not_found', `${resource} with ID '${id}' not found`);
  }
}

class ValidationError extends ApiError {
  constructor(details: ErrorDetail[]) {
    super(422, 'validation_error', 'Validation failed', details);
  }
}

class UnauthorizedError extends ApiError {
  constructor(message = 'Authentication required') {
    super(401, 'unauthorized', message);
  }
}
```

---

## 📄 Pagination

### Selecting a Strategy

| Method | Best For | Pros | Cons |
|--------|----------|------|------|
| Cursor | Real-time data, infinite scroll | Consistent, fast | Can't jump to page |
| Offset | Static data, page numbers | Simple, can jump | Slow on large data |
| Keyset | Large datasets | Very fast | Requires sortable key |

### Cursor-Based Pattern

```typescript
// Request
GET /posts?limit=20&cursor=eyJpZCI6MTAwfQ

// Response
{
  "data": [...],
  "pagination": {
    "hasMore": true,
    "nextCursor": "eyJpZCI6MTIwfQ",
    "prevCursor": "eyJpZCI6MTAwfQ"
  }
}
```

### Offset-Based

```typescript
// Request
GET /posts?page=2&limit=20

// Response
{
  "data": [...],
  "pagination": {
    "page": 2,
    "limit": 20,
    "total": 150,
    "totalPages": 8
  }
}
```

### Implementation (Cursor)

```typescript
function encodeCursor(data: object): string {
  return Buffer.from(JSON.stringify(data)).toString('base64url');
}

function decodeCursor(cursor: string): object {
  return JSON.parse(Buffer.from(cursor, 'base64url').toString());
}

async function getPosts(limit: number, cursor?: string) {
  const where: any = {};

  if (cursor) {
    const { id } = decodeCursor(cursor);
    where.id = { lt: id };
  }

  const posts = await db.post.findMany({
    where,
    orderBy: { id: 'desc' },
    take: limit + 1, // Fetch one extra to check hasMore
  });

  const hasMore = posts.length > limit;
  const data = hasMore ? posts.slice(0, -1) : posts;

  return {
    data,
    pagination: {
      hasMore,
      nextCursor: hasMore ? encodeCursor({ id: data[data.length - 1].id }) : null,
    },
  };
}
```

---

## 🔐 Authentication

### Agent Instruction: Auth Discovery
1. **Scan:** Search the codebase for `jsonwebtoken`, `passport`, `next-auth`, API key middlewares, or session configs.
2. **Confirm:** "I see you are using [Method]. Should I use this for the new endpoint?"
3. **Ask:** If no method is found, prompt the user for their preferred approach.

### Pattern Selection Guide

| Pattern | Best For |
|---------|----------|
| **JWT** | Stateless, microservices |
| **Session** | Traditional web, simple |
| **OAuth 2.0** | Third-party integration |
| **API Keys** | Server-to-server, public APIs |
| **Passkey** | Modern passwordless (2025+) |

### JWT Best Practices

```
Important:
├── Always verify signature
├── Check expiration
├── Include minimal claims
├── Use short expiry + refresh tokens
└── Never store sensitive data in JWT
```

### JWT Implementation

```typescript
// Login endpoint
app.post('/api/auth/login', async (req, res) => {
  const { email, password } = req.body;
  
  const user = await db.user.findUnique({ where: { email } });
  
  if (!user || !(await bcrypt.compare(password, user.passwordHash))) {
    return res.status(401).json({ 
      error: { code: 'invalid_credentials', message: 'Invalid credentials' }
    });
  }
  
  const token = jwt.sign(
    { userId: user.id, email: user.email, role: user.role },
    process.env.JWT_SECRET,
    { expiresIn: '1h', issuer: 'your-app' }
  );
  
  const refreshToken = jwt.sign(
    { userId: user.id },
    process.env.JWT_REFRESH_SECRET,
    { expiresIn: '7d' }
  );
  
  res.json({ token, refreshToken, expiresIn: 3600 });
});

// Auth middleware
function authenticateToken(req, res, next) {
  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.split(' ')[1];
  
  if (!token) {
    return res.status(401).json({ error: { code: 'unauthorized', message: 'Access token required' }});
  }
  
  jwt.verify(token, process.env.JWT_SECRET, (err, user) => {
    if (err) {
      return res.status(403).json({ error: { code: 'invalid_token', message: 'Invalid token' }});
    }
    req.user = user;
    next();
  });
}
```

---

## ⏱️ Rate Limiting

### Response Headers

```
X-RateLimit-Limit: 100        # Max requests per window
X-RateLimit-Remaining: 95     # Requests remaining
X-RateLimit-Reset: 1640000000 # Unix timestamp when limit resets
Retry-After: 60               # Seconds until can retry (on 429)
```

### Tier Definitions

| Tier | Limit | Window |
|------|-------|--------|
| Anonymous | 60 req | 1 hour |
| Free | 100 req | 1 minute |
| Pro | 1000 req | 1 minute |
| Enterprise | 10000 req | 1 minute |

### Strategy Selection

| Type | How | When |
|------|-----|------|
| **Token bucket** | Burst allowed, refills over time | Most APIs |
| **Sliding window** | Smooth distribution | Strict limits |
| **Fixed window** | Simple counters per window | Basic needs |

### Implementation

```typescript
import { Ratelimit } from '@upstash/ratelimit';

const ratelimit = new Ratelimit({
  redis,
  limiter: Ratelimit.slidingWindow(100, '1 m'),
});

async function rateLimitMiddleware(req: Request) {
  const identifier = req.headers.get('authorization') || req.ip;
  const { success, limit, remaining, reset } = await ratelimit.limit(identifier);

  const headers = {
    'X-RateLimit-Limit': limit.toString(),
    'X-RateLimit-Remaining': remaining.toString(),
    'X-RateLimit-Reset': reset.toString(),
  };

  if (!success) {
    return new Response(JSON.stringify({
      error: { code: 'rate_limited', message: 'Too many requests' }
    }), {
      status: 429,
      headers: { ...headers, 'Retry-After': Math.ceil((reset - Date.now()) / 1000).toString() }
    });
  }

  return { headers };
}
```

---

## 🚀 Caching Strategy

### Agent Instruction: Proactive Caching
Always evaluate `GET` endpoints for caching opportunities. If applicable, propose a method and **ask the user for confirmation** before implementation.

### Caching Methods

| Method | Best For | Implementation |
|--------|----------|----------------|
| **Client-Side (Cache-Control)** | Static assets, public data | `Cache-Control: public, max-age=3600` |
| **Conditional (ETag)** | Avatars, documents, profiles | Return `ETag` header, check `If-None-Match` |
| **Server-Side (Redis/Memcached)** | Expensive DB queries | Store serialized JSON in Redis with TTL |

### ETag Implementation Pattern

```typescript
app.get('/api/users/:id/avatar', async (req, res) => {
  const user = await db.user.findUnique({ where: { id: req.params.id } });
  
  if (!user || !user.avatarData) {
    return res.status(404).json({ error: { code: 'not_found' } });
  }
  
  // Generate ETag based on last update timestamp or content hash
  const etag = `W/"${user.avatarUpdatedAt.getTime()}"`; 
  
  if (req.headers['if-none-match'] === etag) {
    return res.status(304).end(); // Not Modified
  }
  
  res.setHeader('ETag', etag);
  res.setHeader('Cache-Control', 'public, max-age=86400');
  res.send(user.avatarData);
});
```

---

## 🔢 Versioning

### Strategy Selection

| Strategy | Implementation | Trade-offs |
|----------|---------------|------------|
| **URI** (Recommended) | /v1/users | Clear, easy caching |
| **Header** | Accept-Version: 1 | Cleaner URLs, harder discovery |
| **Query** | ?version=1 | Easy to add, messy |

### When to Version

**Create new version for:**
- Removing fields from responses
- Changing field types or formats
- Removing endpoints
- Changing authentication

**Don't create new version for:**
- Adding new optional fields
- Adding new endpoints
- Adding new optional parameters
- Bug fixes

### Implementation Pattern

```typescript
// Route organization
src/
├── api/
│   ├── v1/
│   │   └── users/route.ts
│   └── v2/
│       └── users/route.ts

// Version transformers
function toV1User(user: UserInternal) {
  return {
    id: user.id,
    user_name: user.name,  // snake_case in v1
    email: user.email,
  };
}

function toV2User(user: UserInternal) {
  return {
    id: user.id,
    userName: user.name,   // camelCase in v2
    // email intentionally omitted
  };
}
```

### Deprecation Headers

```typescript
res.setHeader('Deprecation', 'true');
res.setHeader('Sunset', 'Sat, 1 Jan 2025 00:00:00 GMT');
res.setHeader('Link', '</api/v2/users>; rel="successor-version"');
```

---

## 🔒 Security Best Practices

### OWASP API Security Top 10

| Vulnerability | Test Focus |
|---------------|------------|
| **API1: BOLA** | Access other users' resources |
| **API2: Broken Auth** | JWT, session, credentials |
| **API3: Property Auth** | Mass assignment, data exposure |
| **API4: Resource Consumption** | Rate limiting, DoS |
| **API5: Function Auth** | Admin endpoints, role bypass |
| **API6: Business Flow** | Logic abuse, automation |
| **API7: SSRF** | Internal network access |
| **API8: Misconfiguration** | Debug endpoints, CORS |
| **API9: Inventory** | Shadow APIs, old versions |
| **API10: Unsafe Consumption** | Third-party API trust |

### Security Checklist

#### ✅ DO:
- Use HTTPS everywhere
- Implement authentication for protected endpoints
- Validate ALL inputs (never trust user input)
- Use parameterized queries (prevent SQL injection)
- Implement rate limiting
- Hash passwords (bcrypt, salt rounds >= 10)
- Use short-lived tokens (JWT < 1 hour)
- Implement CORS properly
- Log security events
- Keep dependencies updated
- Use security headers (Helmet.js)
- Sanitize error messages

#### ❌ DON'T:
- Store passwords in plain text
- Use weak secrets
- Trust user input
- Expose stack traces
- Use string concatenation for SQL
- Store sensitive data in JWT
- Ignore security updates
- Use default credentials
- Disable CORS completely
- Log sensitive data

### Input Validation with Zod

```typescript
const createUserSchema = z.object({
  email: z.string().email('Invalid email format'),
  password: z.string()
    .min(8, 'Password must be at least 8 characters')
    .regex(/[A-Z]/, 'Must contain uppercase letter')
    .regex(/[a-z]/, 'Must contain lowercase letter')
    .regex(/[0-9]/, 'Must contain number'),
  name: z.string().min(2).max(100),
});

function validateRequest(schema) {
  return (req, res, next) => {
    try {
      schema.parse(req.body);
      next();
    } catch (error) {
      res.status(400).json({
        error: { code: 'validation_error', details: error.errors }
      });
    }
  };
}
```

### Authorization Check Pattern

```typescript
// ❌ Bad: Only checks authentication
app.delete('/api/posts/:id', authenticateToken, async (req, res) => {
  await prisma.post.delete({ where: { id: req.params.id } });
});

// ✅ Good: Checks both authentication and authorization
app.delete('/api/posts/:id', authenticateToken, async (req, res) => {
  const post = await prisma.post.findUnique({ where: { id: req.params.id } });
  
  if (!post) {
    return res.status(404).json({ error: { code: 'not_found' } });
  }
  
  // Check if user owns the post or is admin
  if (post.userId !== req.user.userId && req.user.role !== 'admin') {
    return res.status(403).json({ error: { code: 'forbidden' } });
  }
  
  await prisma.post.delete({ where: { id: req.params.id } });
  res.status(204).end();
});
```

---

## 📚 Documentation

### OpenAPI Structure

```yaml
openapi: 3.1.0
info:
  title: My API
  version: 1.0.0

servers:
  - url: https://api.example.com/v1
    description: Production

paths:
  /users:
    get:
      summary: List all users
      operationId: listUsers
      tags: [Users]
      security:
        - bearerAuth: []
      parameters:
        - name: limit
          in: query
          schema:
            type: integer
            maximum: 100
            default: 20
      responses:
        '200':
          description: Successful response
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/UserListResponse'

components:
  schemas:
    User:
      type: object
      required: [id, name, email]
      properties:
        id:
          type: string
          example: usr_123
        name:
          type: string
        email:
          type: string
          format: email
  
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
```

### Documentation Best Practices

```
Include:
├── All endpoints with examples
├── Request/response schemas
├── Authentication requirements
├── Error response formats
├── Rate limiting info
├── Quick start guide
└── Changelog
```

---

## ✅ API Design Checklist

### Pre-Implementation

- [ ] **Fully understood business requirements and domain logic?**
- [ ] **Asked user about API-First approach?**
- [ ] **Confirmed pagination strategy?** (Offset vs Cursor)
- [ ] **Scanned codebase for Auth method and confirmed with user?**
- [ ] **Analyzed endpoint for caching opportunities and proposed strategy?**
- [ ] **Chosen API style?** (REST/GraphQL/tRPC)
- [ ] **Defined consistent response format?**
- [ ] **Planned versioning strategy?**
- [ ] **Planned rate limiting?**

### Endpoints
- [ ] Resource names are plural nouns
- [ ] Consistent naming convention (kebab-case)
- [ ] Appropriate HTTP methods
- [ ] Correct status codes

### Responses
- [ ] Consistent response format
- [ ] Consistent error format with code, message, details
- [ ] Includes requestId for debugging

### Pagination
- [ ] List endpoints are paginated
- [ ] Strategy matches use-case (Offset for Admin, Cursor for Feeds)
- [ ] Includes required metadata (hasMore or totalCount)

### Security
- [ ] Authentication required where needed
- [ ] Authorization checks on resources
- [ ] Rate limiting implemented
- [ ] Input validation on all endpoints
- [ ] CORS configured correctly

### Documentation
- [ ] OpenAPI spec exists
- [ ] Examples for all endpoints
- [ ] Error codes documented
- [ ] Rate limits documented

---

## ❌ Anti-Patterns

**DON'T:**
- Default to REST for everything
- Use verbs in REST endpoints (/getUsers)
- Return inconsistent response formats
- Expose internal errors to clients
- Skip rate limiting
- Ignore input validation
- Store secrets in code

**DO:**
- Choose API style based on context
- Ask about client requirements
- Document thoroughly
- Use appropriate status codes
- Implement proper error handling
- Validate all inputs

---

## 📜 Core Principles

1. **Consistency** - Same patterns everywhere (naming, errors, pagination)
2. **Predictability** - Developers can guess how things work
3. **Simplicity** - Easy cases should be easy, complex cases possible
4. **Backwards Compatibility** - Don't break existing clients
5. **Self-documenting** - Clear naming, helpful error messages
6. **Security First** - Validate inputs, authenticate users, authorize access

> **Remember:** Good API design makes the right thing easy and the wrong thing hard.
