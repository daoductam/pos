---
name: angular-form-generator
description: "Use this when the user wants to create, generate, or build an Angular form, setup Reactive Forms, or provides a list of fields/specifications for a new UI component. Trigger this for any requests related to 'form generation', 'building a form', or 'creating a form group' with validations."
allowed-tools: Read, Write, Shell, Grep
---

# Objective: Human-Verified & Style-Consistent Form Generation

## Naming Conventions (Strict)
- **English Only**: All form names, field names, and variable names MUST be in English.
- **Translation**: If the user provides specifications or field names in another language (e.g., Vietnamese), the Agent MUST translate them to accurate, professional English camelCase before proposing the table.
- **Format**: 
  - Component Class: `PascalCase` (e.g., `UserRegistrationComponent`)
  - Form Group & Controls: `camelCase` (e.g., `shippingAddress`, `phoneNumber`)

## Step 1: Pattern Discovery (Imitation)
Before proposing any code, the Agent MUST:
1. Use `Grep` to find existing components (`*.component.ts`) containing "FormGroup".
2. Use `Read` to analyze if the project uses a specific initialization pattern (e.g., `initForm()`, `buildForm()`, or direct assignment).
3. If a clear pattern is found, prioritize following that style to maintain consistency.

## Step 2: Mandatory Verification Workflow
You **MUST** present this summary and wait for the user's agreement before writing any files.

### 2.1 Proposal Table (Translated to English)
| Field Name (English) | Original Name | Type | Control Type | Proposed Validators | Proposed Default Value |
| :--- | :--- | :--- | :--- | :--- | :--- |
| [englishName] | [original] | [type] | [FormControl/FormArray] | [list] | [value/null] |

### 2.2 Verification Questions
Directly ask the user:
1. "Are the English translations for the field names accurate?"
2. "Are these validators correct, or should I add/remove any?"
3. "Do you want to set specific default values for any of these fields?"
4. "For custom validators, where should I look for or create the logic? (Search project / Create new / Local)"

## Step 4: Execution & Technical Constraints (Angular 21)
Once confirmed, generate code using these rules:

### 4.1 Code Structure (Fallback Style)
```typescript
public form!: FormGroup;

private fb = inject(FormBuilder).nonNullable;

ngOnInit(): void {
  this.initForm();
}

initForm(): void {
  this.form = this.fb.group({
    // All keys here MUST be in English camelCase
  });
}
```

### Step 4.2: Examples (Reference Pattern)

#### Example 1: Standard FormGroup (Profile Form)
```typescript
public form!: FormGroup;
private fb = inject(FormBuilder).nonNullable;

ngOnInit(): void {
  this.initForm();
}

initForm(): void {
  this.form = this.fb.group({
    firstName: ['', [Validators.required]],
    lastName: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
    birthDate: ['']
  });
}
```

#### Example 2: FormGroup with FormArray (Order Form)
```typescript
public form!: FormGroup;
private fb = inject(FormBuilder).nonNullable;

ngOnInit(): void {
  this.initForm();
}

initForm(): void {
  this.form = this.fb.group({
    customerName: ['', [Validators.required]],
    items: this.fb.array([]),
    totalAmount: ['']
  });
}
```
