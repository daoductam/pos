---
name: log-processing
description: Automatically apply logging and refactor Java Spring Boot code - add entry/exit logs for service methods, standardize error handling, and add structured logging with customizable format.
---

# Log-Processing & Refactor Skill

This skill provides detailed instructions for automatically adding logging and refactoring Java Spring Boot code. It is designed to work with **any Java Spring Boot project** and supports **multiple log format options**.

## Goals

1. **Add entry/exit logs** for all public methods in Service layer (DO NOT log in Controllers)
2. **Standardize error handling** with full context logging
3. **Include entity IDs** (userId, profileId, documentId...) in log messages when available in context
4. **Log early returns** - clearly state the reason when a method returns early
5. **Refactor code** to follow logging best practices
6. **Ensure data safety** - never log sensitive fields

---

## 🚨 MANDATORY STEP 0: Ask User Preferences & WAIT 🚨

**BEFORE analyzing any files or applying any logging, you MUST send a message to the user to ask for their preferences and STOP to wait for their reply. You CANNOT SKIP this step.**

Ask the user these two specific questions:
1. **Choose Log Format**: Would they like "Option A (Scan project existing format)" or "Option B (Choose from 3 predefined formats)"?
2. **Additional Sensitive Keys**: Besides default sensitive fields (passwords, tokens, etc.), are there any other specific keys that should NEVER be logged?

**DO NOT PROCEED to modify code until the user has replied.**

---

## Log Format Options (For Question 1)

There are 2 approaches for log formats:

### Option A — Scan & Follow Existing Project Format

Scan the project's existing codebase to detect the dominant log format:

1. **Search for log statements** — scan `src/main/java/` for `log.info`, `log.warn`, `log.error`, `log.debug` patterns
2. **Analyze format patterns** — detect prefix style, separator, key-value format, etc.
3. **Present findings** — show the detected format to the user with examples
4. **Confirm** — ask the user to confirm or adjust before applying

```
Example scan results:
- Detected format: "[methodName] message - key: value, key: value"
- Found in 45/60 service classes
- Recommend following this format for consistency
```

### Option B — Choose from 3 Pre-defined Formats

Present the following 3 format options to the user:

---

#### Format 1: Semantic Action Log ⭐ (Recommended)

```
[methodName] <Start/End> <Action> <Object> <Result> key=value
```

| Component | Description | Values |
|-----------|-------------|--------|
| `[methodName]` | Method name prefix for filtering | `[createDocument]`, `[getUser]`, ... |
| `Start/End` | Mark beginning/end of function | `Start`, `End` |
| `Action` | Action being performed | `Create`, `Update`, `Delete`, `Call`, `Send`, `Receive`, `Validate`, `Query`, `Process` |
| `Object` | Entity being operated on | `Document`, `User`, `Signature`, `Authorization`, ... |
| `Result` | Result (optional, usually at End/Error) | `success`, `failed`, `not_found`, `denied`, `skipped`, `empty` |
| `key=value` | Context data, space-separated | `documentId = 123 userId = 456` |

**Examples:**
```java
// Entry
log.info("[createDocument] Start Create Document templateId = {} userId = {}", templateId, userId);

// Intermediate step — Call external service
log.info("[createDocument] Call SignatureService Document documentId = {} signerCount = {}", docId, signers.size());

// Intermediate step — Validate
log.info("[createDocument] Validate Permissions Document documentId = {} userId = {}", docId, userId);

// Intermediate step — Query
log.info("[getDocDetail] Query Document documentId = {}", documentId);

// Early return — not found
log.warn("[getDocDetail] End Query Document not_found documentId = {}", documentId);

// Early return — empty input
log.info("[getDocsReference] End Query Document skipped statusList is empty");

// Success exit
log.info("[createDocument] End Create Document success documentId = {} userId = {}", doc.getId(), userId);

// Error
log.error("[processPayment] End Process Payment failed orderId = {} error = {}", orderId, e.getMessage(), e);
```

**Action Reference:**

| Action | When to use | Example |
|--------|-------------|---------|
| `Create` | Creating new entity | `Start Create Document` |
| `Update` | Modifying existing entity | `Start Update Signature` |
| `Delete` | Removing entity | `Start Delete Authorization` |
| `Call` | Calling external/internal service | `Call SignatureService Document` |
| `Send` | Sending message/notification/email | `Send Kafka Event` |
| `Receive` | Receiving message/callback | `Receive Webhook Callback` |
| `Validate` | Validating data/permissions | `Validate Permissions Document` |
| `Query` | Querying/fetching data | `Query Document` |
| `Process` | Processing business logic | `Process Payment` |

**Result Reference:**

| Result | When to use |
|--------|-------------|
| `success` | Operation completed normally |
| `failed` | Operation failed (with error) |
| `not_found` | Entity not found |
| `denied` | Permission/access denied |
| `skipped` | Skipped due to condition (e.g., empty input) |
| `empty` | Result set is empty |

---

#### Format 2: Bracket Prefix (Classic)

```
[methodName] MESSAGE - key: value, key: value
```


```java
log.info("[createDocument] START - templateId: {}, userId: {}", templateId, userId);
log.info("[createDocument] Fetching template from DB, templateId: {}", templateId);
log.info("[createDocument] Early return - statusList is empty");
log.info("[createDocument] END - Document created successfully, documentId: {}, userId: {}", docId, userId);
log.error("[createDocument] Error creating document - templateId: {}, error: {}", templateId, e.getMessage(), e);
```

---

#### Format 3: Structured Key-Value (Machine-Friendly)

```
action=ACTION method=methodName object=Object result=Result key=value
```

```java
log.info("action = START method = createDocument object = Document templateId = {} userId = {}", templateId, userId);
log.info("action = QUERY method = createDocument object = Template templateId = {}", templateId);
log.info("action = END method = createDocument object = Document result = success documentId = {} userId = {}", docId, userId);
log.error("action = END method = createDocument object = Document result = failed templateId = {} error = {}", templateId, e.getMessage(), e);
```

> Best suited for projects using log aggregation pipelines (ELK, Splunk) that parse key-value pairs automatically.

---

## Step 1: Discover Project Logging Conventions

After choosing a format, **analyze the project** to understand existing conventions:

1. **Check the logging framework** — Look for `logback.xml`, `logback-spring.xml`, or `log4j2.xml` in `src/main/resources`
2. **Check existing annotations** — Scan for `@Slf4j` (Lombok), manual `LoggerFactory.getLogger()`, or other patterns
3. **Check for structured logging** — Look for `StructuredArguments.kv()`, MDC usage, or JSON encoder config
4. **Check for sensitive field masking** — Look for masking patterns in logging config
5. **Check existing log patterns** — Scan service classes for existing log format conventions

### Common Logging Setups

| Setup | Annotation | Logger | Parameterized |
|-------|------------|--------|---------------|
| Lombok + SLF4J | `@Slf4j` | `log` | `log.info("msg: {}", val)` |
| Manual SLF4J | None | `private static final Logger log = LoggerFactory.getLogger(...)` | `log.info("msg: {}", val)` |
| Log4j2 | `@Log4j2` | `log` | `log.info("msg: {}", val)` |

**Always match the project's existing pattern.** If the project uses `@Slf4j`, use that. If it uses manual logger, follow that convention.

### Parameterized Logging (Required)

Always use parameterized messages instead of string concatenation:
```java
// ✅ CORRECT
log.info("[methodName] Start Create Document documentId = {}", entityId);

// ❌ WRONG - string concatenation is slow and not lazy-evaluated
log.info("[methodName] Start Create Document documentId = " + entityId);
```

### Sensitive Fields — NEVER Log Directly

Common sensitive fields that should **never be logged**:
- Passwords, passcodes, secret keys
- Access tokens, refresh tokens, JWT tokens
- Phone numbers, ID card numbers
- Signature images, certificates
- File content, HTML content
- Any field masked in the project's logging config

> **Important:** Before applying logging, check the project's `logback.xml` or equivalent for masked fields and ensure these are not logged directly.

#### Ask User for Additional Sensitive Keys

This MUST be asked in **MANDATORY STEP 0**. Present the question like this:

```
Besides the default sensitive fields and the fields masked in the logging config,
would you like to add any additional keys that should NEVER be logged?

Please list them in the following format:
- <key name> — <meaning / reason not to log>

Example:
- cardNumber — Bank card number, PCI-DSS sensitive data
- otp — One-time password for authentication, sensitive data
- privateKey — Private key used for digital signing
```

If the user provides additional keys, **add them to the sensitive fields list** and ensure they are excluded from all log statements throughout the session.

## Logging Rules

### 1. Controller Methods — DO NOT LOG

> **Important:** DO NOT add logging in Controller methods. Controllers only serve as request routers to the Service layer. All logging is performed at the Service layer.

### 2. Service Methods

> **Note:** If a different format was chosen in Step 0, adapt the message format accordingly while keeping the same logging rules.

#### a. Log ENTRY with important parameters + Entity IDs
```java
@Override
public DocumentDetailResponse getDocumentDetail(DocumentDetailRequest request) {
    log.info("[getDocumentDetail] Start Query Document documentId = {} userId = {}", 
            request.getDocumentId(), request.getUserId());
    // ... business logic
}
```

#### b. Log important steps in business flow (with entity IDs)
```java
    log.info("[getDocumentDetail] Query Document documentId = {}", id);
    Document document = documentRepository.findById(id).orElseThrow(...);
    
    log.info("[getDocumentDetail] Validate Permissions Document userId = {} documentId = {}", 
            userProfile.getId(), document.getId());
    validateAccess(document);
```
`
#### c. Log EARLY RETURN — clearly state the reason
When a method has early return logic, **the reason for returning must be logged** before the return statement:
```java
@Override
public PageDTO<DocumentResponse> getDocsReference(DocReferenceRequest request) {
    log.info("[getDocsReference] Start Query Document documentId = {}", request.getDocumentId());
    
    if (CollectionUtils.isEmpty(request.getStatusList())) {
        log.info("[getDocsReference] End Query Document skipped statusList is empty");
        return PageDTO.empty();
    }
    
    Document document = documentRepository.findById(request.getDocumentId()).orElse(null);
    if (document == null) {
        log.warn("[getDocsReference] End Query Document not_found documentId = {}", 
                request.getDocumentId());
        return PageDTO.empty();
    }
    
    // ... business logic
}
```

**Early return rules:**
- Use `log.info` if the early return is a normal case (e.g., empty input → `skipped`)
- Use `log.warn` if the early return is an abnormal case (e.g., entity not found → `not_found`)
- The log message must contain the **reason for returning** + relevant **entity IDs**

#### d. Log result before returning (with entity IDs)
```java
    log.info("[getDocumentDetail] End Query Document success documentId = {} userId = {}", 
            document.getId(), userProfile.getId());
    return response;
```

#### e. Log ERROR with full context + entity IDs
```java
try {
    // business logic
} catch (SpecificException e) {
    log.error("[methodName] End Process Entity failed entityId = {} userId = {} error = {}", 
            entityId, userId, e.getMessage(), e);
    throw new BusinessException("User-friendly error description");
}
```

#### f. Service method template:
```java
@Override
public ResponseType methodName(RequestType request) {
    log.info("[methodName] Start <Action> <Object> entityId = {} userId = {}", request.getEntityId(), request.getUserId());
    
    // Early return check
    if (someCondition) {
        log.info("[methodName] End <Action> <Object> skipped reason description entityId = {}", request.getEntityId());
        return defaultValue;
    }
    
    // Step 1 — Query
    log.info("[methodName] Query <Object> entityId = {}", entityId);
    // ... logic
    
    // Step 2 — Call external service
    log.info("[methodName] Call <ServiceName> <Object> entityId = {}", entityId);
    // ... logic
    
    // Step 3 — Validate
    log.info("[methodName] Validate <What> <Object> userId = {}", userId);
    // ... logic
    
    log.info("[methodName] End <Action> <Object> success resultId = {} userId = {}", result.getId(), userId);
    return result;
}
```

### 3. Entity IDs to Include in Logs

When a log message has relevant IDs available in context, **always include them in the message**. Common entity IDs include:

| ID | When available | Common examples |
|----|----------------|-----------------|
| `userId` | Almost always | `userProfile.getId()`, `request.getUserId()`, `currentUser.getId()` |
| Primary entity ID | When operating on main entity | `document.getId()`, `order.getId()`, `product.getId()` |
| `organizationId` | When operating on organizations | `organization.getId()`, `user.getOrganizationId()` |
| `tenantId` | When operating cross-tenant | `request.getTenantId()`, `TenantContext.getCurrentTenant()` |
| Related entity IDs | When referencing related entities | `template.getId()`, `config.getId()`, `batch.getId()` |

> **Tip:** Identify the main entities of the project and always include their IDs when they're available in the method's scope.

### 4. Log Levels

| Level | When to use | Example (Format 1) |
|-------|-------------|---------------------|
| `log.info` | Method entry/exit, important business flow, normal early return | `log.info("[createDoc] Start Create Document docId = {}", id)` |
| `log.warn` | Abnormal early return, recoverable situations | `log.warn("[getDoc] End Query Document not_found docId = {}", id)` |
| `log.error` | Exceptions, critical errors | `log.error("[signDoc] End Process Signature failed docId = {} error = {}", id, e.getMessage(), e)` |
| `log.debug` | Technical details, debugging, loop-level logging | `log.debug("[validate] Process Validation data = {}", data)` |

### 5. Log Level Rules by Layer

| Layer | Entry | Successful exit | Early return | Error exit | Intermediate steps |
|-------|-------|-----------------|-------------|------------|-------------------|
| Controller | — | — | — | — | **No logging** |
| Service | `info` | `info` | `info`/`warn` | `error` | `info` for important steps |
| Repository/DAO | `debug` | `debug` | `debug` | `error` | Not needed |
| Utility/Helper | `debug` | `debug` | `debug` | `warn`/`error` | Not needed |

### 6. Core Logging Constraints

When applying logs, **you must strictly follow these constraints**:
- **No full request/response objects**: Do not log the entire request or response objects. Only log essential entity IDs and specific, necessary fields.
- **No excessive logging in loops**: Avoid placing log statements inside large `for` or `while` loops, which causes log flooding.
- **No large objects**: Do not log excessively large elements such as huge JSON payloads, Base64 encoded files, or raw binary data.
- **No side-effects (Observer Only)**: Log statements must **only observe** state. **NEVER** call APIs, execute DB queries, or modify state variables inside or purely for a log statement.

## Code Refactoring Rules

### 1. Add logging annotation if missing (Service only, NOT Controller)
```java
// BEFORE - missing annotation
@Service
public class OrderServiceImpl implements OrderService {

// AFTER - annotation added
@Slf4j  // or project's preferred logging annotation
@Service
public class OrderServiceImpl implements OrderService {
```

### 2. Use more specific try-catch blocks
```java
// BEFORE - generic catch, no context
try {
    orderService.processOrder(request);
    paymentService.processPayment(paymentRequest);
} catch (Exception e) {
    log.error("Error when processing", e);
}

// AFTER - specific catch + clear logging with context
try {
    log.info("[processOrder] Start Process Order orderId = {} userId = {}", request.getOrderId(), request.getUserId());
    orderService.processOrder(request);
    log.info("[processOrder] Call OrderService Order success orderId = {}", request.getOrderId());
    
    paymentService.processPayment(paymentRequest);
    log.info("[processOrder] Call PaymentService Payment success orderId = {}", request.getOrderId());
} catch (PaymentException e) {
    log.error("[processOrder] End Process Payment failed orderId = {} userId = {} error = {}", 
            request.getOrderId(), request.getUserId(), e.getMessage(), e);
    throw e;
} catch (Exception e) {
    log.error("[processOrder] End Process Order failed orderId = {} userId = {} error = {}", 
            request.getOrderId(), request.getUserId(), e.getMessage(), e);
    throw e;
}
```

### 3. Refactor string concatenation to parameterized logging
```java
// BEFORE
log.info("Service is out of stock by customer id = " + customerId);

// AFTER
log.info("[checkInventory] Query Inventory not_found customerId = {}", customerId);
```

### 4. Add consistent method name prefix
```java
// BEFORE - no prefix, hard to trace
log.info("Start create authorization config");
log.info("Create authorization config success");

// AFTER - clear prefix with semantic format
log.info("[createAuthorizationConfig] Start Create AuthorizationConfig");
log.info("[createAuthorizationConfig] End Create AuthorizationConfig success");
```

### 5. Do not log entire large objects or request/response — log entity IDs instead
```java
// ❌ WRONG - logging entire request/response can be very large, may leak sensitive data, or contain huge objects/base64 files
log.info("Request: {}", request);
log.info("Found entity: {}", entity);

// ✅ CORRECT - log entity IDs and important fields only
log.info("[createDoc] Start Create Document templateId = {} userId = {} signerCount = {}", 
        request.getTemplateId(), userProfile.getId(), request.getSigners().size());
```

### 6. Log the reason for early returns
```java
// ❌ WRONG - returning without logging the reason
if (isNull(request.getAttachmentId())) {
    return;
}

// ✅ CORRECT - clearly log the reason for returning
if (isNull(request.getAttachmentId())) {
    log.info("[updateAttachment] End Update Attachment skipped attachmentId is null userId = {}", userProfile.getId());
    return;
}
```

## Execution Process

When asked to apply logging to a specific file:

### Step 1: Execute MANDATORY STEP 0
1. **STOP and Ask the user** for format preference and sensitive fields.
2. **Wait for user input**. Use the `notify_user` tool with `BlockedOnUser: true`. Do not proceed until they respond.
3. If Option A: scan the project, present findings, confirm with user.
4. If Option B: present 3 formats, let user choose.
5. **Remember the chosen format** and custom sensitive keys for the rest of the session.

### Step 2: Analyze the file
1. Read the entire file to understand its purpose
2. Identify the file type: Controller, Service, Repository, Utility, or other
3. List all public methods
4. Check if logging annotation (`@Slf4j` or equivalent) already exists
5. **If first time in this project:** run Step 1 (Discover Project Conventions)

### Step 3: Identify logging points
1. Entry point of each public method (Service only)
2. Exit point (before return)
3. **Early return points** — log the reason for early return
4. Catch blocks
5. Important steps in business logic
6. **Entity IDs** available in context

### Step 4: Apply logging rules
1. Add `@Slf4j` (or project's annotation) if needed
2. Add entry/exit logs for each method **using the chosen format**
3. Add logging in catch blocks
4. Refactor existing log messages to standard format
5. Verify no sensitive fields are logged

### Step 5: Refactor code
1. Replace string concatenation with parameterized logging
2. Add method name prefix to log messages
3. Split catch blocks into more specific ones if needed
4. Remove redundant/duplicate logs

### Step 6: Review
1. Verify log message format consistency with chosen format
2. Ensure no sensitive data is logged
3. Check log levels are appropriate
4. Verify logging annotation import if newly added

---

## Appendix: Real-World Examples

The following examples demonstrate all 3 formats applied to the same real-world code.

### Example 1: Method with early return

**BEFORE:**
```java
private void updateAttachmentId(CreateAuthorizationConfigRequest request,
                                UserProfile userProfile,
                                AuthorizationConfiguration authorizationConfiguration) {
    log.info("Start update attachment id with user id: {}", userProfile.getId());
    if (isNull(request.getAttachmentId()) || isNull(request.getAttachmentType())) {
        return;
    }
    // ... business logic
    log.info("Update attachment id success");
}
```

**AFTER (Format 1 — Semantic Action Log):**
```java
private void updateAttachmentId(CreateAuthorizationConfigRequest request,
                                UserProfile userProfile,
                                AuthorizationConfiguration authorizationConfiguration) {
    log.info("[updateAttachmentId] Start Update Attachment userId = {} configId = {}", 
            userProfile.getId(), authorizationConfiguration.getId());
    
    if (isNull(request.getAttachmentId()) || isNull(request.getAttachmentType())) {
        log.info("[updateAttachmentId] End Update Attachment skipped attachmentId or attachmentType is null userId = {}", 
                userProfile.getId());
        return;
    }
    
    log.info("[updateAttachmentId] Process Attachment attachmentType = {} userId = {}", 
            request.getAttachmentType(), userProfile.getId());
    // ... business logic
    
    log.info("[updateAttachmentId] End Update Attachment success userId = {} configId = {}", 
            userProfile.getId(), authorizationConfiguration.getId());
}
```

**AFTER (Format 2 — Bracket Prefix):**
```java
private void updateAttachmentId(CreateAuthorizationConfigRequest request,
                                UserProfile userProfile,
                                AuthorizationConfiguration authorizationConfiguration) {
    log.info("[updateAttachmentId] START - userId: {}, configId: {}", 
            userProfile.getId(), authorizationConfiguration.getId());
    
    if (isNull(request.getAttachmentId()) || isNull(request.getAttachmentType())) {
        log.info("[updateAttachmentId] Early return - attachmentId or attachmentType is null, userId: {}", 
                userProfile.getId());
        return;
    }
    
    log.info("[updateAttachmentId] Processing with attachmentType: {}, userId: {}", 
            request.getAttachmentType(), userProfile.getId());
    // ... business logic
    
    log.info("[updateAttachmentId] END - Attachment updated successfully, userId: {}, configId: {}", 
            userProfile.getId(), authorizationConfiguration.getId());
}
```

**AFTER (Format 3 — Structured Key-Value):**
```java
private void updateAttachmentId(CreateAuthorizationConfigRequest request,
                                UserProfile userProfile,
                                AuthorizationConfiguration authorizationConfiguration) {
    log.info("action=START method=updateAttachmentId object=Attachment userId = {} configId = {}", 
            userProfile.getId(), authorizationConfiguration.getId());
    
    if (isNull(request.getAttachmentId()) || isNull(request.getAttachmentType())) {
        log.info("action=END method=updateAttachmentId object=Attachment result=skipped reason=attachmentId_or_type_null userId = {}", 
                userProfile.getId());
        return;
    }
    
    log.info("action=PROCESS method=updateAttachmentId object=Attachment attachmentType = {} userId = {}", 
            request.getAttachmentType(), userProfile.getId());
    // ... business logic
    
    log.info("action=END method=updateAttachmentId object=Attachment result=success userId = {} configId = {}", 
            userProfile.getId(), authorizationConfiguration.getId());
}
```

### Example 2: Verification method with multiple failure cases

**BEFORE:**
```java
private Anonymous verifyAnonymous(String refId, String accessCode, String token) {
    log.info("Verifying document with refId: {}", refId);
    Anonymous anonymous = anonymousRepository.findByRefId(refId).orElse(null);
    log.info("Found anonymous: {}", anonymous);   // ❌ logs entire object
    if (isNull(anonymous)) {
        throw new AnonymousNotFoundException("Anonymous not found");
    }
    if (Strings.isBlank(token) && VerifyType.TOKEN == anonymous.getVerifyType()) {
        throw new BusinessException(Strings.EMPTY, ANONYMOUS_TOKEN_NOT_BLANK);
    }
    boolean matches = ARGON2.matches(accessCode, anonymous.getAccessCode());
    if (!matches) {
        throw new AnonymousVerificationException("Access code is not correct");
    }
    log.info("Get anonymous successfully with id: {}", anonymous.getId());
    return anonymous;
}
```

**AFTER (Format 1 — Semantic Action Log):**
```java
private Anonymous verifyAnonymous(String refId, String accessCode, String token) {
    log.info("[verifyAnonymous] Start Validate Anonymous refId = {}", refId);
    Anonymous anonymous = anonymousRepository.findByRefId(refId).orElse(null);
    if (isNull(anonymous)) {
        log.warn("[verifyAnonymous] End Query Anonymous not_found refId = {}", refId);
        throw new AnonymousNotFoundException("Anonymous not found");
    }
    log.info("[verifyAnonymous] Query Anonymous success anonymousId = {} userId = {} verifyType = {}",
            anonymous.getId(), anonymous.getUserId(), anonymous.getVerifyType());

    if (Strings.isBlank(token) && VerifyType.TOKEN == anonymous.getVerifyType()) {
        log.warn("[verifyAnonymous] End Validate Anonymous failed token is blank but required anonymousId = {}",
                anonymous.getId());
        throw new BusinessException(Strings.EMPTY, ANONYMOUS_TOKEN_NOT_BLANK);
    }
    boolean matches = ARGON2.matches(accessCode, anonymous.getAccessCode());
    if (!matches) {
        log.warn("[verifyAnonymous] End Validate Anonymous denied accessCode mismatch anonymousId = {}",
                anonymous.getId());
        throw new AnonymousVerificationException("Access code is not correct");
    }
    log.info("[verifyAnonymous] End Validate Anonymous success anonymousId = {} userId = {} documentId = {}",
            anonymous.getId(), anonymous.getUserId(), anonymous.getDocumentId());
    return anonymous;
}
```

### Example 3: Permission check with early return

**BEFORE:**
```java
protected void checkPermission(Document document, Long accessUserId, Long organizationId) {
    List<DocSignerFlowConfig> handlers = docSignerFlowRepository.findByDocumentId(document.getId());
    boolean isCreator = document.getOwnerBy().equals(accessUserId);
    boolean isShared = checkDocumentShared(document.getId(), accessUserId, organizationId);
    boolean isReference = documentRefUserRepository.existsByDocumentIdAndUserId(document.getId(), accessUserId);
    log.info("isCreator: {}, isShared: {}, isReference: {}", isCreator, isShared, isReference);
    if (isCreator || isShared || isReference) return;

    boolean isHandler = handlers.stream().map(DocSignerFlowConfig::getUserId).toList().contains(accessUserId);
    if (non(isHandler)) {
        throw new UserHaveNotPermissionException("Request is denied");
    }
}
```

**AFTER (Format 1 — Semantic Action Log):**
```java
protected void checkPermission(Document document, Long accessUserId, Long organizationId) {
    log.info("[checkPermission] Start Validate Permissions documentId = {} accessUserId = {}", document.getId(), accessUserId);
    List<DocSignerFlowConfig> handlers = docSignerFlowRepository.findByDocumentId(document.getId());
    boolean isCreator = document.getOwnerBy().equals(accessUserId);
    boolean isShared = checkDocumentShared(document.getId(), accessUserId, organizationId);
    boolean isReference = documentRefUserRepository.existsByDocumentIdAndUserId(document.getId(), accessUserId);
    log.info("[checkPermission] Validate Permissions documentId = {} accessUserId = {} isCreator = {} isShared = {} isReference = {}",
            document.getId(), accessUserId, isCreator, isShared, isReference);
    if (isCreator || isShared || isReference) {
        log.info("[checkPermission] End Validate Permissions success access granted (creator/shared/reference) documentId = {} accessUserId = {}",
                document.getId(), accessUserId);
        return;
    }

    boolean isHandler = handlers.stream().map(DocSignerFlowConfig::getUserId).toList().contains(accessUserId);
    if (non(isHandler)) {
        log.warn("[checkPermission] End Validate Permissions denied user is not a handler documentId = {} accessUserId = {}",
                document.getId(), accessUserId);
        throw new UserHaveNotPermissionException("Request is denied");
    }
    log.info("[checkPermission] End Validate Permissions success access granted as handler documentId = {} accessUserId = {}",
            document.getId(), accessUserId);
}
```

### Example 4: Method with empty list check (early return)

**BEFORE:**
```java
@Override
public PageDTO<AuthorizationConfigResponse> getAuthorizationConfig(GetAuthorizationConfigRequest request) {
    log.info("Start get list authorization config");
    // ... query
    if (CollectionUtils.isEmpty(page.getContent())) {
        log.info("List authorization config empty");
        return PageDTO.builder().list(new ArrayList<>()).build();
    }
    // ... processing
}
```

**AFTER (Format 1 — Semantic Action Log):**
```java
@Override
public PageDTO<AuthorizationConfigResponse> getAuthorizationConfig(GetAuthorizationConfigRequest request) {
    log.info("[getAuthorizationConfig] Start Query AuthorizationConfig authorizer = {} authorizedPerson = {}", 
            request.getAuthorizer(), request.getAuthorizedPerson());
    // ... query
    if (CollectionUtils.isEmpty(page.getContent())) {
        log.info("[getAuthorizationConfig] End Query AuthorizationConfig empty authorizer = {} authorizedPerson = {}", 
                request.getAuthorizer(), request.getAuthorizedPerson());
        return PageDTO.builder().list(new ArrayList<>()).build();
    }
    // ... processing
    log.info("[getAuthorizationConfig] End Query AuthorizationConfig success totalElements={}", page.getTotalElements());
}
```

## How to Use This Skill

When the user requests to apply logging to a file:

1. **Choose format** — Ask the user to choose Option A (scan project) or Option B (pick from 3 formats).
2. **Discover conventions** (first time only) — run Step 1 to understand the project's logging setup
3. **Read the file** using the `view_file` tool
4. **Analyze** according to the execution process above
5. **Apply logging** following the rules and chosen format
6. **Use** `replace_file_content` or `multi_replace_file_content` to update the file
7. **Explain** the changes to the user

When the user requests to apply logging to an entire package (e.g., "apply logging to all services"):
1. **Choose format first** — same format applies to all files
2. List all files in the package
3. **Skip Controllers** — no logging in controllers
4. Process **one file at a time** (not in parallel to avoid errors)
5. Apply the same rules and format to each file
6. Report a summary of all changes made
