# JAVA + SPRING BOOT INTERVIEW QUESTION BANK
## For 2 Years of Experience — Explained Using My Existing Project

**Project:** Finance AI Automation Platform  
**Stack in this repo:** Java 17, Spring Boot 4.1.0, Gradle multi-module, PostgreSQL, Flyway, JWT, BCrypt, JPA/Hibernate, Testcontainers  
**Architecture:** Modular monolith (`platform-app`, `platform-core`, `module-auth`, `module-finance`, `module-ai`, `module-reporting`)

**How to speak in interviews**
- If it exists in this repo: “In our project we use…”
- If it does not: “This does not appear to be used in the current project, so this is general interview knowledge.”
- Do not say “I personally implemented…” unless you actually did.

**How to read each answer**
- **General explanation:** what the concept means in Java / Spring (definition, why it exists, how it works)
- **In our project:** how the same idea shows up in the Finance AI Automation Platform (classes, modules, flows)
- Tip: learn the general idea first, then practice saying “In our project we…”

---

# Part 1 — Core Java Interview Questions and Answers

### Q1. What is Java?

**General explanation:**  
Java is a high-level, object-oriented language. Source compiles to bytecode that runs on the JVM, so the same program can run on different operating systems.

**In our project:**  
We use Java 17 with Spring Boot for the Finance AI Automation Platform backend because of its ecosystem, typing, and strong libraries for APIs, JPA, and security. The Gradle toolchain is set to Java 17 in `backend/build.gradle`.

**Why do we use this?** Mature ecosystem, strong typing, and excellent Spring/JPA/security libraries for enterprise APIs.

**Possible follow-up:** Why Java 17 specifically? (LTS; records, text blocks, stronger APIs we use for DTOs.)

---

### Q2. Why is Java platform independent?

**General explanation:**  
Java is platform independent because source compiles to bytecode that a JVM executes on the host OS. Write once, run anywhere as long as a compatible JVM exists.

**In our project:**  
We build a JAR and run it with Temurin JRE 17 in Docker (`backend/Dockerfile`), so the same artifact runs locally and in containers.

---

### Q3. JDK vs JRE vs JVM?

**General explanation:**  
The JVM runs bytecode. The JRE is the JVM plus libraries needed to run applications. The JDK adds the compiler and build tools.

**In our project:**  
Our multi-stage Docker build uses a JDK image to compile with Gradle and a JRE image to run the Spring Boot JAR for the Finance AI Automation Platform. Multi-stage Docker uses `eclipse-temurin:17-jdk-alpine` to build and `eclipse-temurin:17-jre-alpine` to run — build needs JDK, runtime only needs JRE.

---

### Q4. What is bytecode?

**General explanation:**  
Bytecode is the platform-neutral intermediate instruction set produced by javac into .class files. The JVM interprets or JITs that bytecode for the host machine.

**In our project:**  
Our Gradle modules compile Java sources to bytecode before packaging the Spring Boot executable JAR.

---

### Q5. What happens when Java code is compiled and run?

**General explanation:**  
Java compilation and runtime go from .java to javac producing .class files, then classloading, bytecode verification, interpreter or JIT execution, and garbage collection.

**In our project:**  
Gradle compiles modules and packages `platform-app` as an executable Spring Boot JAR (`bootJar`).

---

### Q6. What is a class?

**General explanation:**  
A class is a blueprint that defines fields and methods. At runtime you create instances from it.

**In our project:**  
`Expense`, `User`, `IncomeController`, `AuthenticationService` are classes. Example entity:

```java
@Entity
@Table(name = "expenses", ...)
public class Expense extends TenantAwareEntity { ... }
```

---

### Q7. What is an object?

**General explanation:**  
An object is a runtime instance of a class, typically allocated on the heap.

**In our project:**  
When `IncomeService` creates an income, Hibernate persists an `Income` object; listings return entity/DTO objects as JSON. `AuthenticationService` works with `User` objects loaded from PostgreSQL via JPA.

---

### Q8. What is a constructor?

**General explanation:**  
A constructor is a special method that initializes a new object when it is created.

**In our project:**  
We use constructor injection so services receive AuthenticationManager, repositories, and JwtService as final fields. Lombok `@RequiredArgsConstructor` generates constructors for services. Explicit constructors appear too, e.g. `JwtTokenProvider(JwtProperties)`. Entities use `@NoArgsConstructor` / `@AllArgsConstructor` for JPA/Lombok.

**Why?** JPA needs a no-arg constructor; services use constructor injection for required dependencies.

---

### Q9. What is `this`?

**General explanation:**  
this is a reference to the current object instance. It distinguishes instance members from static or outer-class context when needed.

**In our project:**  
Method references like `this::toResponse` in `ClientService` (`clients.stream().map(this::toResponse)`).

---

### Q10. What is `super`?

**General explanation:**  
super refers to the parent class constructor or members. Explicit constructors would call super when wiring those shared fields.

**In our project:**  
`Expense extends TenantAwareEntity` which extends `BaseEntity`. Child entities inherit `id`, `createdAt`, `firmId` from parents — classic inheritance where `super` would be used if we wrote explicit constructors.

---

### Q11. What are the four OOP principles?

**General explanation:**  
The four OOP principles are encapsulation, abstraction, inheritance, and polymorphism.

**In our project:**  
They guide how we structure domain entities, service APIs, and pluggable checks in the Finance AI Automation Platform Spring Boot backend. Those ideas shape how we build Expense, User, and CloseCheck pieces.

---

### Q12. What is encapsulation? Where in our project?

**General explanation:**  
Encapsulation hides internal state and exposes controlled access through private fields and methods.

**In our project:**  
ClientService keeps repositories private final; User stores passwordHash privately; Expense.approve encapsulates valid status transitions. That prevents invalid states like approving a non-DRAFT expense.

```java
// ClientService
private final ClientJpaRepository clientRepository;
```
Fields are `private`. Entities use Lombok `@Getter`/`@Setter` with private fields (`User.email`, `User.passwordHash`). `BusinessException` keeps `errorCode` private with a getter. Domain methods like `Expense.approve(User)` encapsulate status transition rules instead of letting callers mutate status freely.

**Why?** Prevents invalid states (e.g. approving a non-DRAFT expense).

**Possible follow-up:** Why not public fields? (Breaks invariants; hard to change later.)

---

### Q13. What is abstraction?

**General explanation:**  
Abstraction exposes what an API does while hiding how it works. Controllers talk to services, not repositories, for business flows in our modular Spring Boot monolith.

**In our project:**  
Interfaces like `EmailService`, `FileStorageService`, `CloseCheck`, `UserFacade`. Controllers depend on services, not repositories directly for business flows.

---

### Q14. Why do we use interfaces in our project?

**General explanation:**  
We use interfaces to depend on contracts, swap implementations, and keep Gradle modules decoupled. EmailService maps to SmtpEmailService or LoggingEmailService; FileStorageService to local or S3; CloseCheck has many implementations; facades like UserFacade define module APIs.

**In our project:**  
- `EmailService` → `SmtpEmailService` or `LoggingEmailService`

- `FileStorageService` → local or S3 via `StorageConfig`
- `CloseCheck` → many period-close checks
- Facades (`UserFacade`, `ReportingFacade`) define module APIs

**Why not concrete classes everywhere?** Changing SMTP vs log email, or local vs S3, would force edits across services.

**Follow-up:** Interface vs abstract class? DIP? Strategy pattern?

---

### Q15. What is inheritance?

**General explanation:**  
Inheritance lets a class acquire state and behavior from a parent.

**In our project:**  
BaseEntity and TenantAwareEntity are abstract mapped superclasses; Expense extends TenantAwareEntity so JPA entities share identity, auditing, and multi-tenant firmId without duplication.

```java
@MappedSuperclass
public abstract class BaseEntity { UUID id; Instant createdAt; ... }

public abstract class TenantAwareEntity extends BaseEntity { UUID firmId; }

public class Expense extends TenantAwareEntity { ... }
```
Shared identity/auditing/tenant fields without duplicating columns on every entity class.

**Why not copy-paste fields?** DRY; consistent auditing.

**Why not always inherit?** Prefer composition when “is-a” is weak.

---

### Q16. What is polymorphism?

**General explanation:**  
Polymorphism means the same interface yields different runtime behavior.

**In our project:**  
`FileStorageService` store/load differs for local vs S3. `CloseReadinessService` injects `List<CloseCheck>` and calls `check.evaluate(context)` — each check implementation behaves differently.

---

### Q17. Overloading vs overriding?

**General explanation:**  
Overloading is the same method name with different parameters, resolved at compile time. Overriding redefines a parent method for runtime dispatch. Our domain favors distinct names like approve and voidTransaction; classic overrides are less common than interface implementations.

**In our project:**  
Overriding is less explicit because we use interfaces more than deep class hierarchies. Overloading appears in Java APIs we call; domain methods are mostly distinct names (`approve`, `voidTransaction`).

**This does not appear heavily as classic override examples**, but interviewers still expect the definition.

---

### Q18. What is an interface?

**General explanation:**  
An interface is a contract of methods a class must implement, and it may include default or static methods.

**In our project:**  
`CloseCheck` has `evaluate(...)` plus default `enabled()`. Spring injects all CloseCheck beans as a List for period-close readiness.

---

### Q19. What is an abstract class?

**General explanation:**  
An abstract class may mix concrete and abstract members and cannot be instantiated.

**In our project:**  
`BaseEntity` / `TenantAwareEntity` are abstract mapped superclasses — shared JPA fields, not meant to be tables themselves.

---

### Q20. Interface vs abstract class?

**General explanation:**  
Interfaces allow multiple implementation contracts with limited state; abstract classes provide a single shared base with full fields.

**In our project:**  
Pluggable behavior → interfaces (`EmailService`, `CloseCheck`).

---

### Q21. Composition vs inheritance?

**General explanation:**  
Composition models has-a relationships; inheritance models is-a.

**In our project:**  
`AuthenticationService` *has* `AuthenticationManager`, `JwtService`, `SessionService` (composition via DI). `Expense` *has* `Set<Receipt>` (composition/association). `Expense` *is a* `TenantAwareEntity` (inheritance for shared persistence fields).

---

### Q22. What is `static`?

**General explanation:**  
static members belong to the class rather than an instance. Constants shared by all instances do not need per-object state.

**In our project:**  
JwtAuthenticationFilter defines a private static final BEARER_PREFIX, and FirmController keeps a static Set of allowed currencies. `JwtAuthenticationFilter` has `private static final String BEARER_PREFIX = "Bearer ";`. `FirmController` has `private static final Set<String> ALLOWED_CURRENCIES`.

**Why?** Constants shared by all instances; no need for object state.

---

### Q23. What is `final`?

**General explanation:**  
final on a variable prevents reassignment, on a method prevents override, and on a class prevents subclassing. That fits constructor injection and immutable collaborator design.

**In our project:**  
Services use private final dependencies such as AuthenticationManager and UserJpaRepository. Constructor-injected dependencies:

```java
private final AuthenticationManager authenticationManager;
private final UserJpaRepository userRepository;
```
Also `private final String errorCode` on `BusinessException`.

**Why `final` on DI fields?** Immutable dependencies after construction; clearer design; works with constructor injection.

---

### Q24. `final` vs `finally` vs `finalize`?

**General explanation:**  
final restricts mutation or inheritance; finally always runs after try or catch; finalize is a deprecated Object GC hook and should not be used.

**In our project:**  
`finally` in `JwtAuthenticationFilter.doFilterInternal` clears `SecurityContextHolder` and `TenantContextHolder` even if the request fails — critical to avoid ThreadLocal leaks.

---

### Q25. `==` vs `.equals()`?

**General explanation:**  
The == operator compares references for objects or values for primitives; equals compares logical equality when overridden. We compare UUIDs and strings with equals, for example user.getFirmId().equals(claims.firmId()) during JWT authentication checks.

**In our project:**  
UUID/string comparisons use `.equals` (e.g. firm id check: `user.getFirmId().equals(claims.firmId())`). Never compare strings with `==`.

---

### Q26. `equals()` and `hashCode()`?

**General explanation:**  
The equals and hashCode contract requires equal objects to share the same hashCode, which HashMap and HashSet rely on. Entities typically use UUID identity; mutable business-field equality is a JPA pitfall. Records used as DTOs generate consistent equals and hashCode.

**In our project:**  
Entities typically rely on identity (`UUID id`). Be careful: using mutable business fields in equals/hashCode for JPA entities is a common pitfall. DTOs are often records (auto equals/hashCode).

**Follow-up:** Why are JPA entity equals hard? (proxy, transient vs persisted id)

---

### Q27. String immutability?

**General explanation:**  
String is immutable; operations that seem to modify create new String instances. Immutability aids safe sharing across threads.

**In our project:**  
We store BCrypt password hashes on User and pass JWT bearer tokens as strings without mutating them in place.

**Why useful?** Thread-safety for sharing; security for things like password handling patterns (though we store BCrypt hashes, not raw passwords in entities).

---

### Q28. What is the String pool?

**General explanation:**  
The String pool is the JVM’s interned storage for string literals so identical literals can be reused. Application strings still allocate normally when built dynamically.

**In our project:**  
It is general JVM knowledge rather than something we configure in the Finance AI Automation Platform.

**General knowledge** — not something we configure in the project.

---

### Q29. StringBuilder?

**General explanation:**  
StringBuilder is a mutable character sequence for efficient concatenation without synchronization. Prefer it over repeated String + in loops. In API code we mostly use it for building messages or intermediate text rather than domain entities.

**In our project:**  
We build messages and logs with normal String APIs; no special StringBuilder framework pattern beyond ordinary Java.

---

### Q30. StringBuffer?

**General explanation:**  
StringBuffer is a synchronized mutable string builder. Prefer StringBuilder for single-threaded use because synchronization adds cost. Modern Spring Boot request handling rarely needs StringBuffer for string assembly.

**In our project:**  
We build messages and logs with normal String APIs; no special StringBuilder framework pattern beyond ordinary Java.

---

### Q31. String vs StringBuilder vs StringBuffer?

**General explanation:**  
Use String for immutable text, StringBuilder for efficient single-threaded concatenation, and StringBuffer only when legacy synchronized builders are required.

**In our project:**  
In our backend, fixed messages and JWT-related strings stay as String; builders appear only when assembling text.

---

### Q32. Primitive vs wrapper classes?

**General explanation:**  
Primitives such as int and boolean differ from wrappers like Integer and Boolean, which can be null and work with generics. Collections need wrappers. Our domain uses BigDecimal for money amounts and UUID for identifiers rather than primitive doubles for currency.

**In our project:**  
Money uses `BigDecimal` (not `double`). IDs use `UUID`. Amounts are never primitive `double` for currency.

---

### Q33. Autoboxing / unboxing?

**General explanation:**  
Autoboxing and unboxing convert automatically between primitives and wrappers, such as int and Integer. Unboxing a null wrapper throws NullPointerException. Watch Optional empty cases and nullable JPA columns when mixing primitives and wrappers.

**In our project:**  
Shows up indirectly with nullable wrapper fields on entities/DTOs (for example BigDecimal amounts and optional Integers) where null unboxing would NPE.

---

### Q34. Is Java pass-by-value?

**General explanation:**  
Java is pass-by-value. For objects the reference value is copied, so callees can mutate the object but reassigning the parameter does not change the caller’s reference. That applies to services mutating entity state before JPA flush.

**In our project:**  
Service methods receive entity references by value; they can mutate managed entities inside a transaction, but rebinding a parameter does not change the caller’s variable.

---

### Q35. Heap vs stack?

**General explanation:**  
The stack holds method frames and local primitives or references; the heap holds objects and arrays.

**In our project:**  
Each Spring MVC request runs on a servlet thread with its own stack; JPA entities and DTOs are heap objects managed with PostgreSQL persistence.

---

### Q36. What is garbage collection?

**General explanation:**  
Garbage collection automatically reclaims unreachable heap objects. Developers must still avoid retaining unused references through static caches or uncleared ThreadLocals. Our JWT filter clears request-scoped ThreadLocals so GC and thread pools stay healthy.

**In our project:**  
Runtime is a Spring Boot JAR in Docker (Temurin JRE 17). We do not tune GC specially in-repo; leaks would show as growing heap under load/tests.

---

### Q37. Can Java have memory leaks?

**General explanation:**  
Java can have logical memory leaks from growing static collections, uncleared ThreadLocals, lingering listeners, or oversized caches.

**In our project:**  
JwtAuthenticationFilter clears SecurityContextHolder and TenantContextHolder in finally so request data does not leak across pooled threads. We explicitly clear ThreadLocals in the JWT filter `finally` block to avoid request-scoped data leaking across pooled threads.

---

### Q38. Access modifiers?

**General explanation:**  
Java access modifiers are private, package-private by default, protected, and public. Service collaborators are private final; controller and repository types are public Spring components. That balances encapsulation with framework visibility.

**In our project:**  
Service fields are `private final`; controllers/services are `public` classes; repository interfaces are public API for Spring Data.

---

### Q39. What is a POJO?

**General explanation:**  
A POJO is a Plain Old Java Object without a mandatory framework base class. Domain entities are annotated POJOs for JPA and Hibernate. Request and response DTOs are often Java records, which keep the same plain-data spirit with less boilerplate.

**In our project:**  
Domain entities are POJOs with JPA annotations. DTOs are often records (modern POJO-like).

---

### Q40. What is an enum?

**General explanation:**  
An enum is a type-safe fixed set of constants.

**In our project:**  
We use RoleCode with ADMIN, ACCOUNTANT, AUDITOR, and BUSINESS_OWNER, plus TransactionStatus values like DRAFT, APPROVED, and VOID, typically persisted with @Enumerated on JPA entities.

```java
public enum RoleCode { ADMIN, ACCOUNTANT, AUDITOR, BUSINESS_OWNER }
```
Also `TransactionStatus` (`DRAFT`, `APPROVED`, `VOID`), period statuses, receipt statuses, match statuses — stored with `@Enumerated`.

**Why?** Prevents invalid string statuses like `"aproved"`.

---

# Part 2 — Java Collections Interview Questions and Answers

### Q41. What is the Collection Framework?

**General explanation:**  
The Collection Framework is the java.util API for grouping objects via List, Set, Queue, and separately Map.

**In our project:**  
We return page content as lists, inject List of CloseCheck, store receipts in sets, and build status count maps for reporting.

---

### Q42. What is a List?

**General explanation:**  
A List is an ordered collection that allows duplicates and index access.

**In our project:**  
Page content, close findings, injected `List<CloseCheck>`, stream `.toList()` results.

---

### Q43. What is a Set?

**General explanation:**  
A Set stores unique elements according to equals and hashCode.

**In our project:**  
Expense and Income initialize Set of Receipt with HashSet. JwtAuthenticationFilter collects accessible client IDs into a Set of UUID for TenantContext authorization checks.

```java
private Set<Receipt> receipts = new HashSet<>();
```
on `Expense` / `Income`. Also `Set<UUID>` for accessible client IDs in tenant context.

**Why Set for receipts?** Linking the same receipt twice should not create duplicate join rows conceptually.

---

### Q44. What is a Map?

**General explanation:**  
A Map associates keys with values.

**In our project:**  
Audit `afterState` maps, `BusinessException` properties, reporting `Map<String, Long> statusCounts`, field-error maps in exception handler (`LinkedHashMap`).

---

### Q45. What is a Queue?

**General explanation:**  
A Queue is typically a FIFO collection for ordered processing. We do not center domain APIs on Queue, but executors and infrastructure use queues internally.

**In our project:**  
Prefer List and Set for most Finance AI Automation Platform service code.

**This does not appear to be a primary structure in business code**, but useful general knowledge (and thread pools use queues internally).

---

### Q46. ArrayList?

**General explanation:**  
ArrayList is a resizable-array List with fast random access and amortized constant-time append, but costly mid-list inserts. It is the default list choice for service responses and intermediate results in our Spring Boot modules.

**In our project:**  
Default list choice for responses and intermediate results.

---

### Q47. LinkedList?

**General explanation:**  
LinkedList is a doubly linked list with cheaper mid insert or delete but poor random access. We rarely need it in controllers or services; ArrayList is preferred unless a specific queue-like access pattern requires otherwise.

**In our project:**  
**Rarely needed in our API layer**; prefer ArrayList unless profiling says otherwise.

---

### Q48. ArrayList vs LinkedList?

**General explanation:**  
Prefer ArrayList for typical backend and API work. Choose LinkedList mainly for heavy head or tail queue operations. In our modular monolith, response lists and stream materialization use ArrayList-style lists by default.

**In our project:**  
Services and controllers use standard JDK lists/collections for in-memory work; persistence stays in JPA entities and repositories.

---

### Q49. HashSet?

**General explanation:**  
HashSet is a Set backed by HashMap offering unordered unique elements with average constant-time add and contains.

**In our project:**  
Entity collections initialize with `new HashSet<>()`.

---

### Q50. HashMap?

**General explanation:**  
HashMap is a key-value store with average constant-time get and put and a single allowed null key. It underpins many java.util structures and is the usual unsynchronized map choice in service and reporting code.

**In our project:**  
We use ordinary `java.util` maps inside services and reporting helpers; nothing custom beyond standard JDK collections.

---

### Q51. HashMap internals (bucket, hashCode, equals, collision)?

**General explanation:**  
HashMap places entries into buckets using the key’s hashCode, then uses equals to resolve collisions within a bucket. Large bins may treeify on modern JDKs. Correct equals and hashCode are essential for HashMap and HashSet keys.

**In our project:**  
We use ordinary `java.util` maps inside services and reporting helpers; nothing custom beyond standard JDK collections.

**Interview tip:** Good `hashCode`/`equals` matter for correctness and performance.

---

### Q52. What is treeification / load factor / resizing?

**General explanation:**  
Load factor, default 0.75, triggers resize when size over capacity exceeds the threshold, rebuilding the table. Large collision bins may treeify for faster lookup. We rely on JDK defaults rather than custom HashMap tuning in the project.

**In our project:**  
We do not tune load factor in our app; defaults are fine.

**General knowledge** — we don’t tune HashMap internals in the project.

---

### Q53. HashMap vs Hashtable?

**General explanation:**  
Hashtable is a legacy synchronized map; HashMap is unsynchronized and preferred. For concurrent shared maps use ConcurrentHashMap or other concurrency utilities. Our request-scoped service code typically uses HashMap or LinkedHashMap.

**In our project:**  
We use ordinary `java.util` maps inside services and reporting helpers; nothing custom beyond standard JDK collections.

---

### Q54. HashMap vs ConcurrentHashMap?

**General explanation:**  
ConcurrentHashMap supports concurrent reads and writes without locking the entire map.

**In our project:**  
Core Finance AI Automation Platform services are largely thread-confined per request, so we do not rely on it in domain code, but it is the right tool for shared caches. Request handling is mostly thread-confined; we don’t rely on ConcurrentHashMap in core domain services. Useful if interviewers ask about shared caches.

---

### Q55. HashSet internals?

**General explanation:**  
HashSet is implemented as a HashMap where elements are keys and a shared dummy object is the value. Uniqueness and performance therefore follow HashMap’s hashing and equals contract. That is why Set behavior mirrors map key behavior.

**In our project:**  
We build messages and logs with normal String APIs; no special StringBuilder framework pattern beyond ordinary Java.

---

### Q56. LinkedHashMap?

**General explanation:**  
LinkedHashMap is a HashMap that preserves insertion order.

**In our project:**  
`GlobalExceptionHandler` and audit/business extras often use `LinkedHashMap` so JSON property order is stable/readable. `ReportingQueryRepository.statusCounts` builds a `LinkedHashMap`.

---

### Q57. TreeMap?

**General explanation:**  
TreeMap is a sorted map backed by a red-black tree with logarithmic operations; keys must be Comparable or you supply a Comparator. It is general knowledge rather than a primary structure in our Spring services.

**In our project:**  
**Not a primary structure in our services** — general knowledge.

---

### Q58. Comparable vs Comparator?

**General explanation:**  
Comparable defines natural order via compareTo on the class itself. Comparator is an external ordering strategy, often as a lambda. Coding interviews and service sorting commonly use Comparator for expenses by amount or clients by name.

**In our project:**  
Entity identity is usually the UUID `id`; we rely on JPA/Hibernate equality by primary key rather than hand-rolled domain equals for most entities.

**Coding interviews:** sort expenses by amount, clients by name — use Comparator lambdas.

---

### Q59. Where do we use Collections in a real flow?

**General explanation:**  
Downstream services use that collection to enforce which clients the authenticated User may access.

**In our project:**  
JwtAuthenticationFilter loads client access rows and streams them into a Set of UUID with Collectors.toSet, then stores that set in TenantContext.

```java
var clientIds = clientAccessRepository.findByUser_Id(user.getId()).stream()
    .map(access -> access.getClientId())
    .collect(Collectors.toSet());
```
That `Set<UUID>` goes into `TenantContext` so later services know which clients the user may touch.

---

### Q60. Unmodifiable collections?

**General explanation:**  
Unmodifiable collections are wrappers or views that reject mutation.

**In our project:**  
FirmController uses Collectors.toUnmodifiableSet for allowed currency codes so the constant set cannot be changed after construction during request handling. `FirmController` builds `Collectors.toUnmodifiableSet()` for allowed currency codes — constant set should not be mutated at runtime.

---

# Part 3 — Java 8+ / Streams / Optional

### Q61. What are lambda expressions?

**General explanation:**  
A lambda is a concise implementation of a functional interface, such as x goes to x greater than zero.

**In our project:**  
We use lambdas in stream pipelines, Specification builders, and Optional filters in JWT authentication flows across the Spring Boot services.

---

### Q62. What is a functional interface?

**General explanation:**  
A functional interface has a single abstract method and may be annotated with @FunctionalInterface. Examples include Predicate, Function, Consumer, and Supplier. Lambdas and method references target these types in stream and Optional APIs.

**In our project:**  
Services use streams/Optionals for mapping entities to DTOs (for example `ClientService` with `this::toResponse`).

---

### Q63. Predicate / Function / Consumer / Supplier?

**General explanation:**  
Predicate tests a value to boolean, Function transforms T to R, Consumer performs a side effect, and Supplier provides a value. Stream filter and map use Predicate and Function; services commonly call orElseThrow with a Supplier of ResourceNotFoundException.

**In our project:**  
Streams use Function/Predicate under the hood (`map`, `filter`). `orElseThrow(Supplier)`.

---

### Q64. What are Streams?

**General explanation:**  
Streams provide a declarative pipeline from a source through intermediate operations to a terminal operation. We map entities to DTOs, collect client IDs in JWT auth, and sample CSV import preview rows with stream pipelines in the platform.

**In our project:**  
Mapping entities to DTOs, collecting client IDs, CSV import preview sampling.

---

### Q65. `filter`?

**General explanation:**  
filter retains elements that match a Predicate.

**In our project:**  
JwtAuthenticationFilter chains Optional filter with isActiveUser and a firmId equals check against JWT claims so only valid tenant users proceed into SecurityContext.

```java
userRepository.findDetailedById(claims.userId())
    .filter(this::isActiveUser)
    .filter(user -> user.getFirmId().equals(claims.firmId()))

```

---

### Q66. `map`?

**General explanation:**  
map transforms each element one-to-one.

**In our project:**  
`clients.stream().map(this::toResponse).toList()` in `ClientService`. The same pattern appears wherever we expose JPA entities through REST controllers.

---

### Q67. `flatMap`?

**General explanation:**  
flatMap maps each element to a stream and flattens the results, supporting one-to-many expansion. It avoids nested collections in pipelines.

**In our project:**  
It is a core Stream interview topic for nested lists even when used sparingly in our services.

**General knowledge / occasional use** — classic interview topic for nested lists.

---

### Q68. `reduce`?

**General explanation:**  
reduce aggregates a stream into a single value such as a sum or concatenation. For building collections we usually prefer collect with Collectors. reduce remains useful for custom aggregation interview questions and numeric folds.

**In our project:**  
Possible in reporting/aggregation helpers; we more often push aggregates to SQL/JPA queries than to stream reduce.

---

### Q69. `collect`?

**General explanation:**  
collect performs a mutable reduction into List, Set, or Map via Collectors.

**In our project:**  
`Collectors.toSet()`, `toUnmodifiableSet()`, `.toList()`.

---

### Q70. `sorted` / `distinct`?

**General explanation:**  
sorted orders stream elements; distinct removes duplicates based on equals and hashCode. They are intermediate operations before a terminal collect. Useful when preparing ordered unique views for reporting or preview endpoints.

**In our project:**  
Used occasionally when shaping in-memory collections before DTO mapping; heavy sorting/filtering belongs in the database query.

---

### Q71. `groupingBy`?

**General explanation:**  
groupingBy collects elements into a Map from classifier key to List of values.

**In our project:**  
A natural domain example is grouping expenses by category name or TransactionStatus for reporting-style summaries in service or interview code.

**Coding angle for our domain:** group expenses by category name / status.

---

### Q72. Method references?

**General explanation:**  
Method references such as this::toResponse or User::getId are shorthand for lambdas that call an existing method.

**In our project:**  
`map(this::toResponse)`, `filter(this::isActiveUser)`.

---

### Q73. What is Optional?

**General explanation:**  
Optional is a container that may or may not hold a value and encourages explicit empty handling. Spring Data repositories return Optional of User or Client, and JWT bearer resolution returns Optional of String before authentication proceeds.

**In our project:**  
Repositories return `Optional<User>`, `Optional<Client>`, etc. JWT bearer resolution returns `Optional<String>`.

---

### Q74. `orElse` vs `orElseGet` vs `orElseThrow`?

**General explanation:**  
orElse always evaluates the default, orElseGet lazily supplies a default, and orElseThrow fails if empty. Services commonly call orElseThrow with a Supplier of ResourceNotFoundException so missing Client or Income becomes a clear 404 via our exception handler.

**In our project:**  
Services commonly `orElseThrow(() -> new ResourceNotFoundException(...))` when an entity must exist.

**Why orElseThrow?** Missing client/income should become a clear 404 via our exception handler — not a NullPointerException.

---

### Q75. Optional `map` vs `flatMap`?

**General explanation:**  
Optional map wraps a non-Optional return in Optional; flatMap is used when the mapping function already returns Optional to avoid Optional of Optional. That pattern appears when chaining repository lookups or claim extractions.

**In our project:**  
Services use streams/Optionals for mapping entities to DTOs (for example `ClientService` with `this::toResponse`).

---

### Q76. Stream vs Collection?

**General explanation:**  
A Collection stores data for repeated access; a Stream processes data with lazy intermediate operations and is typically single-use. We keep entities in lists or sets and use streams to map, filter, and collect into DTOs or ID sets.

**In our project:**  
Services use streams/Optionals for mapping entities to DTOs (for example `ClientService` with `this::toResponse`).

---

### Q77. Parallel streams?

**General explanation:**  
Parallel streams can use multiple threads but need careful benchmarking and avoid shared mutable state or transactional JPA work.

**In our project:**  
The Finance AI Automation Platform uses sequential streams and @Async for AI processing rather than parallelStream. For background AI work we use Spring @Async instead.

**This does not appear to be used in the current project** — we use normal sequential streams and `@Async` for AI processing instead.

---

### Q78. Where do streams help readability in our project?

**General explanation:**  
Streams improve readability for declarative pipelines such as GenericBankStatementCsvImporter preview, which uses stream, limit, map, and toList to sample transformed CSV rows more clearly than equivalent manual loops.

**In our project:**  
Services use streams/Optionals for mapping entities to DTOs (for example `ClientService` with `this::toResponse`).

---

### Q79. Why not replace every loop with streams?

**General explanation:**  
Do not replace every loop with streams. Complex multi-step mutable logic, early exits with many locals, or micro-optimized loops can be clearer as for-loops. Use streams when transformation pipelines are the natural shape of the code.

**In our project:**  
Services use streams/Optionals for mapping entities to DTOs (for example `ClientService` with `this::toResponse`).

---

### Q80. Java records — how do they relate to Java 8+ style?

**General explanation:**  
Java records, available since Java 16 and used on Java 17, are immutable data carriers with generated accessors, equals, and hashCode.

**In our project:**  
`IncomeRequest`, `LoginRequest`, `LoginResponse`.

---

# Part 4 — Exception Handling

### Q81. Exception hierarchy?

**General explanation:**  
In Java, Throwable splits into Error and Exception. Exception further splits into checked exceptions and RuntimeException (unchecked). Errors are JVM-level failures you rarely catch.

**In our project:**  
In the Finance AI Automation Platform we throw custom RuntimeExceptions so services stay clean and Spring REST maps them through GlobalExceptionHandler.

---

### Q82. Checked vs unchecked?

**General explanation:**  
Checked exceptions must be declared or handled; unchecked ones extend RuntimeException and need not. Spring REST APIs usually prefer unchecked so service signatures stay clean and @Transactional rolls back by default on runtime failures. Our BusinessException hierarchy extends RuntimeException for that reason.

**In our project:**  
Custom business exceptions extend `RuntimeException` via `BusinessException` — typical for Spring REST APIs so service signatures stay clean and `@Transactional` rolls back by default on runtime exceptions.

---

### Q83. Error vs Exception?

**General explanation:**  
Error represents serious JVM problems such as OutOfMemoryError; Exception covers application and recoverable conditions. Catching Errors casually is discouraged because the JVM may already be unstable.

**In our project:**  
Our GlobalExceptionHandler maps application Exceptions to ProblemDetail JSON, not JVM Errors.

---

### Q84. try / catch / finally?

**General explanation:**  
try/catch handles failures; finally guarantees cleanup whether or not an exception occurred. That keeps our multi-tenant finance API safe under concurrent load.

**In our project:**  
In JwtAuthenticationFilter we clear SecurityContext and TenantContext in finally so request-scoped auth state never leaks across threads. JWT filter `finally` clears security/tenant contexts.

---

### Q85. `throw` vs `throws`?

**General explanation:**  
throw raises an exception instance at runtime; throws declares checked exceptions a method may propagate. Unchecked RuntimeExceptions do not require throws.

**In our project:**  
Services throw BusinessException and friends directly, and GlobalExceptionHandler turns them into HTTP responses.

---

### Q86. Custom exceptions in our project?

**General explanation:**  
Our custom hierarchy is led by BusinessException with errorCode and properties, plus ResourceNotFoundException, ValidationException, DuplicateResourceException, and DuplicateDocumentException. Period close throws BusinessException with ErrorCodes.PERIOD_NOT_READY_TO_CLOSE and blocker details.

**In our project:**  
GlobalExceptionHandler maps each type to the right HTTP status and ProblemDetail body.

---

### Q87. Multiple catch blocks?

**General explanation:**  
Specific catch blocks should appear before general ones; Java multi-catch uses catch (A | B ex). That maps Spring Security auth failures into our API error shape without nested try blocks.

**In our project:**  
`AuthenticationService.login` catches `BadCredentialsException | DisabledException | LockedException`.

---

### Q88. Global exception handling?

**General explanation:**  
Global exception handling centralizes mapping from exceptions to HTTP responses.

**In our project:**  
Controllers stay thin while Angular gets consistent ProblemDetail JSON for not found, validation, conflicts, and business rule failures. `GlobalExceptionHandler` with `@RestControllerAdvice`.

---

### Q89. `@ExceptionHandler`?

**General explanation:**  
@ExceptionHandler marks a method that handles a given exception type inside advice or a controller. A catch-all Exception handler returns a generic 500 ProblemDetail.

**In our project:**  
In GlobalExceptionHandler, ResourceNotFoundException becomes 404, validation 400, duplicates 409, BusinessException 422, access denied 403, and MaxUploadSizeExceededException 413.

---

### Q90. `@ControllerAdvice` vs `@RestControllerAdvice`?

**General explanation:**  
A missing specific handler falls through to the catch-all Exception mapping.

**In our project:**  
@RestControllerAdvice equals @ControllerAdvice plus @ResponseBody, so return values become response bodies. Controllers stay thin and the Angular client always receives a consistent error envelope. We use it on GlobalExceptionHandler to emit ProblemDetail JSON. So we use @RestControllerAdvice on GlobalExceptionHandler.

**Why global advice?** Controllers stay thin; consistent error JSON for the Angular client.

**Follow-up:** What if we forget a handler? Catch-all `Exception` handler returns a generic 500 ProblemDetail.

---

### Q91. Why not try/catch in every controller method?

**General explanation:**  
Per-method try/catch duplicates mapping logic, produces inconsistent status codes, and hurts testability.

**In our project:**  
We let exceptions bubble from services through controllers to GlobalExceptionHandler. That keeps IncomeController and peers focused on HTTP mapping while error policy stays in one class. GlobalExceptionHandler owns the mapping.

---

### Q92. How does an exception travel in our API?

**General explanation:**  
The exception leaves the service and controller untouched.

**In our project:**  
PeriodCloseService.close finds blockers and throws BusinessException. The Angular client surfaces why the period was not ready to close. GlobalExceptionHandler.handleBusiness maps it to HTTP 422 ProblemDetail with errorCode and blockers. CloseCheck blockers ride along in the error properties.

---

# Part 5 — Multithreading (2-year level)
### Q93. Process vs thread?

**General explanation:**  
A process is an isolated program with its own memory; threads share that process memory. Our Spring Boot finance platform runs as one JVM process with a pool of Tomcat request threads. Concurrent uploads and API calls share the same ApplicationContext and connection pool carefully.

**In our project:**  
Request work is mostly per-request threads from Tomcat; AI extraction uses `@Async` after commit. We avoid shared mutable request state beyond `TenantContext` ThreadLocal.

---

### Q94. Creating threads / Runnable?

**General explanation:**  
new Thread(runnable).start() is low-level and hard to manage at scale. In this project async AI work uses @Async backed by a Spring task executor rather than manually spawning threads per upload.

**In our project:**  
Prefer ExecutorService or Spring's task executors. Request work is mostly per-request threads from Tomcat; AI extraction uses `@Async` after commit. We avoid shared mutable request state beyond `TenantContext` ThreadLocal.

**General knowledge** — production code should use pools.

---

### Q95. Callable vs Runnable?

**General explanation:**  
Runnable.run returns void and cannot throw checked exceptions; Callable.call returns a value and may throw checked exceptions. Our document AI path uses Spring @Async methods rather than raw Callable, but the distinction is standard interview knowledge.

**In our project:**  
ExecutorService.submit accepts both. Request work is mostly per-request threads from Tomcat; AI extraction uses `@Async` after commit. We avoid shared mutable request state beyond `TenantContext` ThreadLocal.

---

### Q96. ExecutorService?

**General explanation:**  
SubscriptionQuotaConcurrencyIntegrationTest uses it to simulate concurrent quota checks. Application async work for DocumentUploadedEvent uses Spring @Async, which is backed by a configured TaskExecutor.

**In our project:**  
ExecutorService manages a thread pool and task submission lifecycle. Used in `SubscriptionQuotaConcurrencyIntegrationTest` to simulate concurrent quota checks. App async work uses Spring `@Async` (backed by a task executor).

---

### Q97. Synchronization / race condition?

**General explanation:**  
A race condition means concurrent updates produce timing-dependent outcomes. Near subscription plan limits, two users creating clients can race the quota. We rely on transactional guards, locking strategies, and unique constraints, covered by concurrency tests in the platform.

**In our project:**  
Two users creating clients near plan limits → quota race. Tests cover this; services use transactional guards / locking strategies for subscription quotas.

**Follow-up:** How fix? DB unique constraints, pessimistic locks, atomic updates.

---

### Q98. Deadlock?

**General explanation:**  
Deadlock happens when threads wait circularly for each other's locks. Mitigate with consistent lock ordering and short critical sections.

**In our project:**  
Our services prefer transactional DB constraints and brief critical work over nested application-level lock graphs.

---

### Q99. `volatile`?

**General explanation:**  
volatile ensures visibility of a field across threads but does not make compound actions like increment atomic. We keep singletons thread-safe by avoiding mutable request state on bean fields.

**In our project:**  
It is general interview knowledge here, not a central pattern in our services.

---

### Q100. Atomic operations?

**General explanation:**  
AtomicInteger and related types provide lock-free updates for single variables. They help counters and flags but do not replace database transactions for business invariants.

**In our project:**  
Subscription quotas in our platform are enforced with transactional and DB-level protections, not only in-memory atomics.

---

### Q101. CompletableFuture?

**General explanation:**  
CompletableFuture is Java's async composition API with thenApply, thenCompose, and exceptionally. It does not appear central in the current project. Document AI uses @Async plus @TransactionalEventListener(AFTER_COMMIT) on DocumentUploadedEvent so processing starts only after a successful commit.

**In our project:**  
We use @Async after the upload commits instead.

**This does not appear to be used in the current project.** We use `@Async` + `@TransactionalEventListener(AFTER_COMMIT)` for document AI processing in `DocumentUploadedListener`.

---

### Q102. How does async AI processing work here?

**General explanation:**  
Async processing runs work after the HTTP request path so latency and failure modes stay separate from the upload transaction.

**In our project:**  
Upload commits a document and publishes DocumentUploadedEvent. After commit, DocumentUploadedListener.onDocumentUploaded runs @Async and calls DocumentAiProcessor. That keeps HTTP upload latency free of AI cost and ensures AI never runs on rolled-back data.

---

# Part 6 — Spring Core
### Q103. What is Spring?

**General explanation:**  
Spring is a framework for building Java applications. It provides IoC, dependency injection, AOP, data access, and web modules. Those pieces integrate across modular packages under com.finance.platform.

**In our project:**  
The Finance AI Automation Platform uses Spring for DI, MVC, Security, and JPA.

---

### Q104. Why Spring?

**General explanation:**  
Spring reduces boilerplate wiring and integrates security, JPA, and transactions in one ecosystem. Manual new across filters, services, and repositories does not scale.

**In our project:**  
AuthenticationService receives AuthenticationManager, repositories, JwtService, and SessionService via DI instead of constructing them itself. Spring plugs JwtService into AuthenticationService for you.

**Why Spring instead of plain Java `new`?** Manual wiring doesn’t scale across dozens of services/repos/filters.

---

### Q105. What is IoC?

**General explanation:**  
Inversion of Control means the framework controls object creation and lifecycle instead of application code calling new for every dependency. Spring's ApplicationContext creates and manages beans for our modular monolith. Controllers and services declare needs; the container satisfies them.

**In our project:**  
Spring wires `AuthenticationService`, `JwtService`, repositories, and other beans via constructor injection across modules.

---

### Q106. What is Dependency Injection?

**General explanation:**  
Dependency Injection supplies collaborators from outside via constructor, setter, or field.

**In our project:**  
AuthenticationService uses constructor injection with @RequiredArgsConstructor for AuthenticationManager, UserJpaRepository, JwtService, and SessionService. That improves testability and avoids hard-wired new JwtService() calls.

```java
@Service
@RequiredArgsConstructor
public class AuthenticationService {
    private final AuthenticationManager authenticationManager;
    private final UserJpaRepository userRepository;
    private final JwtService jwtService;
    private final SessionService sessionService;
    // ... }
```
Spring creates `AuthenticationService` and injects beans into the constructor (via Lombok-generated ctor).

**Why DI instead of `new JwtService()`?** Hard to test, hard to swap implementations, duplicates configuration.

**Follow-ups:** Who creates objects? What is a bean? Constructor vs field injection? Circular dependency?

---

### Q107. Where do we use DI in our project?

**General explanation:**  
DI appears on nearly every @Service, @RestController, @Component filter, and @Configuration class.

**In our project:**  
IncomeController receives IncomeService and DocumentService. JwtAuthenticationFilter receives JwtTokenProvider and related repositories through constructor injection. IncomeController is a concrete example. JwtAuthenticationFilter is another.

---

### Q108. What is a Spring Bean?

**General explanation:**  
A Spring Bean is an object managed by the IoC container — created, wired, and destroyed by Spring. Default singleton scope means they must remain thread-safe regarding instance fields.

**In our project:**  
Our services, REST controllers, CloseCheck components, and JwtAuthenticationFilter are beans.

---

### Q109. ApplicationContext / IoC container?

**General explanation:**  
ApplicationContext is the IoC container holding bean definitions and instances. Component scan and auto-configuration populate beans before the embedded server accepts traffic.

**In our project:**  
SpringApplication.run(FinancePlatformApplication.class, args) creates it at startup.

---

### Q110. Component scanning?

**General explanation:**  
Component scanning discovers stereotype-annotated classes under configured base packages. @SpringBootApplication(scanBasePackages = "com.finance.platform") scans our modular packages.

**In our project:**  
That registers services and controllers from module-auth, module-finance, module-ai, and related modules into one context.

```java
@SpringBootApplication(scanBasePackages = "com.finance.platform")

```
scans all modules under that package.

---

### Q111. `@Component` / `@Service` / `@Repository` / `@Controller`?

**General explanation:**  
Stereotypes guide component scanning with clear intent. @Service marks business logic and @Repository marks persistence with exception translation. We follow that split across the platform modules.

**In our project:**  
@RestController marks HTTP JSON APIs. @Component covers general beans like JwtAuthenticationFilter and CloseCheck.

---

### Q112. `@Configuration` + `@Bean`?

**General explanation:**  
@Configuration classes define @Bean methods Spring invokes to create objects. AuthModuleConfig exposes PasswordEncoder as BCryptPasswordEncoder.

**In our project:**  
StorageConfig chooses FileStorageService local vs S3. AuthSecurityConfig defines SecurityFilterChain and AuthenticationManager beans.

```java
@Configuration
public class AuthModuleConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```
`StorageConfig` `@Bean FileStorageService` chooses local vs S3. `AuthSecurityConfig` defines `SecurityFilterChain` and `AuthenticationManager`.

---

### Q113. Constructor vs field vs setter injection?

**General explanation:**  
Constructor injection is preferred because required dependencies are immutable and easy to unit-test. Field @Autowired hides dependencies; setters suit optional collaborators.

**In our project:**  
Dominant pattern is constructor injection with `@RequiredArgsConstructor` + `private final`.

**Why constructor injection?** Fail fast if dependency missing; `final` fields; obvious unit-test construction.

**Why not field injection?** Harder to test without Spring; mutable; unclear required deps.

---

### Q114. `@Autowired`?

**General explanation:**  
@Autowired marks an injection point. On a single constructor, Spring Boot can autowire without an explicit annotation.

**In our project:**  
Combined with @RequiredArgsConstructor, our services receive beans through constructors without field @Autowired clutter.

---

### Q115. `@Qualifier` / `@Primary`?

**General explanation:**  
@Primary marks the default bean among multiples; @Qualifier selects by name.

**In our project:**  
Multiple EmailService or storage implementations can collide without disambiguation. StorageConfig often returns one FileStorageService based on properties, which avoids ambiguous injection at the call site. Multiple implementations exist for interfaces like `EmailService` / storage — often selected via `@Bean` method logic rather than many competing component scans. Interviewers still ask Qualifier/Primary. **If two beans implement the same interface without Qualifier/Primary:** startup fails with NoUniqueBeanDefinitionException (unless only one is injected as List).

---

### Q116. Multiple implementations pattern here?

**General explanation:**  
That shows both exclusive @Bean selection and multi-bean list injection in one codebase.

**In our project:**  
StorageConfig returns one FileStorageService bean based on provider settings. CloseReadinessService injects List<CloseCheck> so every close-check bean participates — a Strategy pattern. CloseCheck is the list-of-strategies story.

---

### Q117. Bean scopes?

**General explanation:**  
Default scope is singleton: one instance per container. Other scopes include prototype, request, and session.

**In our project:**  
Services/repos/controllers are singletons — must be thread-safe regarding fields (request data goes in method params / SecurityContext / TenantContext, not mutable instance fields).

---

### Q118. Circular dependency?

**General explanation:**  
A circular dependency exists when A needs B and B needs A. Prefer extracting a third service or using events; constructor cycles fail by default in modern Spring. Lazy injection is a last resort.

**In our project:**  
Our modular design and event-driven AI flow help avoid tight cycles.

**Mitigation:** extract third service, events, or lazy injection (last resort).

---

### Q119. Bean lifecycle / `@PostConstruct` / `@PreDestroy`?

**General explanation:**  
Bean lifecycle is instantiation, injection, init callbacks, ready state, then destroy callbacks. @PostConstruct and @PreDestroy hook init and cleanup. We validate critical configuration such as production JWT secrets after properties bind so misconfig fails fast at startup.

**In our project:**  
Services, controllers, filters (for example `JwtAuthenticationFilter`), and config classes are Spring beans in the modular monolith.

**General knowledge**; use init for validation after properties bind (we also use validators like production JWT secret checks).

---

### Q120. Why service layer instead of controller → repository?

**General explanation:**  
Controllers handle HTTP concerns like path variables, status codes, and @PreAuthorize. Services own business rules and @Transactional boundaries. Repositories own JPA access.

**In our project:**  
IncomeController → IncomeService → IncomeJpaRepository → Hibernate → PostgreSQL keeps rules reusable and testable.

**Flow:**
```
Client → IncomeController → IncomeService → IncomeJpaRepository → Hibernate → PostgreSQL
```

---

### Q121. Why not `new` for repositories?

**General explanation:**  
Spring Data creates repository proxy implementations at runtime; you do not instantiate them with new. Dependency injection provides the proxy to services.

**In our project:**  
That is why IncomeService receives IncomeJpaRepository from the container rather than constructing it.

---

# Part 7 — Spring Boot
### Q122. Spring vs Spring Boot?

**General explanation:**  
Spring is the broader framework ecosystem. Spring Boot auto-configures and packages applications with starters, an embedded server, and opinionated defaults. It wires the modular monolith quickly without heavy XML setup.

**In our project:**  
FinancePlatformApplication is our Spring Boot entry point. Our platform-app starts with Boot.

---

### Q123. Why Spring Boot?

**General explanation:**  
Spring Boot reduces XML and Java config for common stacks. It embeds Tomcat and supports production-ready packaging. That avoids deploying a WAR to external Tomcat and simplifies the finance platform ops story.

**In our project:**  
We run locally and in Docker with java -jar.

**Why Boot instead of plain Spring MVC WAR on external Tomcat?** Faster local run, simpler Docker (`java -jar`), consistent defaults.

---

### Q124. What is `@SpringBootApplication`?

**General explanation:**  
@SpringBootApplication is a composite annotation. It enables configuration, component scan, and auto-configuration. It also uses @EntityScan, @EnableJpaRepositories, and @EnableScheduling, then calls SpringApplication.run in main.

**In our project:**  
FinancePlatformApplication sets scanBasePackages to com.finance.platform.

```java
@SpringBootApplication(scanBasePackages = "com.finance.platform")
@EntityScan(basePackages = "com.finance.platform")
@EnableJpaRepositories(basePackages = "com.finance.platform")
@EnableScheduling
public class FinancePlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(FinancePlatformApplication.class, args);
    }
}
```

---

### Q125. What does `@SpringBootApplication` contain?

**General explanation:**  
@SpringBootApplication combines @SpringBootConfiguration, @EnableAutoConfiguration, and @ComponentScan. Version-specific meta-annotations may appear as well. That single annotation boots scanning and auto-config together.

**In our project:**  
FinancePlatformApplication relies on it instead of declaring each concern separately.

---

### Q126. Auto-configuration?

**General explanation:**  
Auto-configuration creates beans based on classpath and properties. JDBC on the classpath can bring a DataSource automatically. Our JPA, Security, web, and validation starters drive much of that setup.

**In our project:**  
AuthSecurityConfig still customizes SecurityFilterChain for JWT stateless APIs. JPA, Security, web MVC, validation starters drive auto-config; we customize security explicitly in `AuthSecurityConfig`.

---

### Q127. Starter dependencies?

**General explanation:**  
Starter dependencies are curated sets such as spring-boot-starter-web, data-jpa, security, validation, and test. They align versions and transitive libraries for you. That avoids manual BOM juggling for each jar.

**In our project:**  
Our Gradle multi-module backend depends on those coherent stacks.

---

### Q128. Embedded server?

**General explanation:**  
With spring-boot-starter-web, Tomcat runs embedded inside the executable JAR by default. No external application server is required.

**In our project:**  
Our Docker image runs the same JAR and exposes the HTTP port for the finance API.

---

### Q129. `application.properties` / `application.yml`?

**General explanation:**  
application.properties or application.yml externalize configuration. Production overrides secrets with environment variables.

**In our project:**  
`application.yml` plus profiles `application-local.yml`, `application-prod.yml`. DB URL, JWT secret, CORS, storage, Flyway settings live there / in env vars.

---

### Q130. Spring Profiles?

**General explanation:**  
Spring Profiles activate environment-specific configuration such as local, prod, and test. That separation keeps the finance platform safe across environments.

**In our project:**  
Local uses localhost PostgreSQL and development JWT defaults; production requires env-injected secrets and often S3 storage.

**Why?** Local uses localhost Postgres + default JWT for dev; prod requires env secrets and often S3 storage.

---

### Q131. `@Value` vs `@ConfigurationProperties`?

**General explanation:**  
@Value injects a single property. @ConfigurationProperties binds a typed prefix object. Groups like JwtProperties, StorageProperties, and SubscriptionProperties are preferred for related settings. They are typed, nestable, and easier to validate than scattered @Value fields.

**In our project:**  
We use both patterns where they fit.

---

### Q132. Environment variables?

**General explanation:**  
Environment variables override externalized config at runtime without rebuilding. Production injects values like SPRING_DATASOURCE_URL and JWT secrets into containers. Secrets must not be committed; the finance platform relies on env overrides in prod profiles.

**In our project:**  
Not a primary custom pattern in this repo — say so honestly, then tie back to everyday Java/Spring usage in our services if the interviewer presses.

**Why?** Secrets must not be committed; containers inject env at runtime.

---

### Q133. Actuator?

**General explanation:**  
Spring Boot Actuator exposes production endpoints for health, metrics, and info. It does not appear central in the current project.

**In our project:**  
We expose custom HealthController routes at /api/v1/health and /api/v1/health/ready used by Docker HEALTHCHECK. We expose custom health endpoints instead.

**This does not appear to be used in the current project.** We expose custom `HealthController` at `/api/v1/health` and `/api/v1/health/ready` (used by Docker HEALTHCHECK).

---

### Q134. What happens when the Spring Boot application starts?

**General explanation:**  
main calls SpringApplication.run and Boot creates the ApplicationContext. It scans com.finance.platform and auto-configures DataSource, JPA, MVC, and Security. Embedded Tomcat then starts.

**In our project:**  
Flyway runs when enabled and beans such as JwtAuthenticationFilter are wired. Flyway failure aborts startup rather than serving with a wrong schema. Flyway updates PostgreSQL schema. If Flyway fails, startup fails on purpose.

**Follow-up:** What if Flyway fails? Startup fails — safer than running with wrong schema.

---

### Q135. Why `@EntityScan` / `@EnableJpaRepositories` with base packages?

**General explanation:**  
@EntityScan and @EnableJpaRepositories set basePackages to com.finance.platform. That ensures Boot discovers entities and repos across modules.

**In our project:**  
In a multi-module layout, entities and repositories live in module-auth, module-finance, and siblings. FinancePlatformApplication lives in platform-app. The Boot app sits in platform-app.

---

### Q136. Why Spring Boot instead of “just Java”?

**General explanation:**  
Building JWT filters, DI, transaction proxies, JSON mapping, validation, and connection pools from scratch would be slow and error-prone. Spring Boot provides a standard enterprise stack instead.

**In our project:**  
The Finance AI Automation Platform can focus on domain rules. CloseCheck readiness and AI document flows sit on that foundation.

---

# Part 8 — REST API / Spring MVC
### Q137. What is REST?

**General explanation:**  
REST is an architectural style using HTTP resources and methods. Communication is typically stateless with JSON representations. Our versioned API lives under /api/v1.

**In our project:**  
`/api/v1/clients/{clientId}/income`, `/api/v1/auth/login`. Versioned REST API under `/api/v1/...` — e.g.

---

### Q138. What is `@RestController`?

**General explanation:**  
It combines @Controller and @ResponseBody.

**In our project:**  
@RestController marks a Spring MVC controller whose return values become the HTTP body, typically JSON via Jackson. IncomeController maps /api/v1/clients/{clientId}/income and returns DTOs for the Angular client rather than server-rendered views.

```java
@RestController
@RequestMapping("/api/v1/clients/{clientId}/income")
@RequiredArgsConstructor
public class IncomeController {
    private final IncomeService incomeService;
    ... }
```
Also `LoginController` at `/api/v1/auth`, `ClientController`, `DocumentController`, etc. (26 REST controllers).

**Why `@RestController`?** We build a JSON API for an Angular frontend, not server-rendered HTML.

**Why not `@Controller`?** `@Controller` is for view names / HTML unless every method adds `@ResponseBody`.

**Possible follow-ups:** What is DispatcherServlet? How is JSON produced? Difference from `@Controller`?

---

### Q139. `@Controller` vs `@RestController`?

**General explanation:**  
@Controller typically resolves view names for HTML.

**In our project:**  
@RestController writes data to the response body instead. All HTTP APIs use `@RestController`. GlobalExceptionHandler uses @RestControllerAdvice for ProblemDetail JSON errors. Error mapping uses `@RestControllerAdvice` (`GlobalExceptionHandler`).

---

### Q140. What is `@RequestMapping`?

**General explanation:**  
@RequestMapping maps requests by path, method, and other conditions on a class or method.

**In our project:**  
Class-level `@RequestMapping("/api/v1/auth")` on `LoginController`; method-level `@PostMapping("/login")` builds full path `/api/v1/auth/login`. IncomeController uses the same pattern under clients and income.

---

### Q141. `@GetMapping` / `@PostMapping` / `@PutMapping` / `@PatchMapping` / `@DeleteMapping`?

**General explanation:**  
GetMapping, PostMapping, PutMapping, PatchMapping, and DeleteMapping are verb-specific RequestMapping shortcuts. Updates use PUT and deletes use DELETE. Approve and void use POST as pragmatic action endpoints for state transitions.

**In our project:**  
IncomeController lists with GET and creates with POST returning 201. - `GET /` list (paged)

- `GET /{incomeId}` get one
- `POST /` create (`201 CREATED`)
- `PUT /{incomeId}` update
- `DELETE /{incomeId}` delete
- `POST /{incomeId}/approve` / `void` for actions

**Why POST for approve instead of PUT?** Approve is an action/state transition, not full resource replacement — common REST pragmatic style.

---

### Q142. `@RequestBody`?

**General explanation:**  
@RequestBody deserializes the HTTP JSON body into a Java type via Jackson.

**In our project:**  
LoginController.login uses @Valid @RequestBody LoginRequest and delegates to AuthenticationService. Income create endpoints bind IncomeRequest the same way before calling IncomeService.

```java
@PostMapping("/login")
public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    return authenticationService.login(request);
}
```

---

### Q143. `@PathVariable`?

**General explanation:**  
@PathVariable binds a URI template variable to a method argument.

**In our project:**  
`@PathVariable UUID clientId`, `@PathVariable UUID incomeId`. Those identifiers flow into IncomeService for authorization checks and PostgreSQL lookups.

---

### Q144. `@RequestParam`?

**General explanation:**  
@RequestParam binds query parameters. Income list endpoints accept status, categoryId, from, to, page, and size with defaults like page=0 and size=20. That supports PageResponse pagination without loading full tables into memory.

**In our project:**  
Income list filters: `status`, `categoryId`, `from`, `to`, `page`, `size` (defaults `page=0`, `size=20`).

---

### Q145. `ResponseEntity`?

**General explanation:**  
Many of our endpoints return DTOs directly and annotate creates with @ResponseStatus(HttpStatus.CREATED).

**In our project:**  
ResponseEntity explicitly sets status, headers, and body. ResponseEntity remains useful when custom headers or dynamic statuses are required. Many endpoints return DTOs directly and use `@ResponseStatus(HttpStatus.CREATED)` for creates. `ResponseEntity` is still important interview knowledge for custom headers/status.

---

### Q146. Common HTTP status codes?

**General explanation:**  
Common codes include 200, 201, 204, 400, 401, 403, 404, 409, 422, and 500. Creates use 201 in controllers like income create.

**In our project:**  
GlobalExceptionHandler maps not found to 404, validation to 400, duplicates to 409, BusinessException to 422, and access denied to 403. Creates use 201.

---

### Q147. 200 vs 201?

**General explanation:**  
200 OK means a successful response. 201 Created means a new resource was created.

**In our project:**  
Angular can then distinguish create success from ordinary reads and updates. `@ResponseStatus(HttpStatus.CREATED)` on income/expense create endpoints.

---

### Q148. 401 vs 403?

**General explanation:**  
401 Unauthorized means not authenticated, such as a missing or invalid JWT. 403 Forbidden means authenticated but not allowed, for example @PreAuthorize denying an auditor from approve.

**In our project:**  
JwtAuthenticationFilter establishes identity; method security enforces finer role rules. JwtAuthenticationFilter handles the identity step first.

---

### Q149. PUT vs PATCH?

**General explanation:**  
PUT typically replaces a full resource representation; PATCH applies a partial update. Our update endpoints often use @PutMapping with request DTOs containing fields we allow to change. Semantics should stay consistent with that DTO contract for clients.

**In our project:**  
Updates often use `@PutMapping` with request DTOs containing the fields we allow to change. Full vs partial semantics should stay consistent with the DTO shape.

---

### Q150. POST vs PUT?

**General explanation:**  
POST commonly creates resources or triggers actions; PUT is an idempotent replace at a known URI.

**In our project:**  
Income approve and void use POST as state-transition actions. Document upload guards duplicates with checksum detection so accidental double POSTs do not silently multiply documents.

---

### Q151. What is idempotency?

**General explanation:**  
Idempotency means repeating the same request has the same effect as doing it once. GET is idempotent; PUT usually is; POST create often is not. Document upload uses checksum duplicate detection via DuplicateDocumentException, and DB unique constraints protect quotas and uniqueness. **Examples:** GET is idempotent; PUT usually is; POST create often is not (double-click can create duplicates unless guarded).

**In our project:**  
Document upload uses checksum/duplicate detection (`DuplicateDocumentException`). Unique DB constraints and transactional checks protect quotas and uniqueness.

---

### Q152. What does stateless mean?

**General explanation:**  
Stateless means the server does not keep client session state between requests; each request carries authentication. AuthSecurityConfig sets SessionCreationPolicy.STATELESS. Refresh tokens may be stored hashed for rotation, but API authorization is JWT-based rather than HTTP session based.

**In our project:**  
```java
.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

```
Refresh tokens are stored hashed in DB for rotation/revocation, but access authorization for APIs is JWT-based, not HTTP session.

---

### Q153. Pagination / sorting?

**General explanation:**  
Pagination returns pages instead of full tables; sorting orders results.

**In our project:**  
That keeps PostgreSQL queries bounded and protects memory for large finance datasets. `PageResponse<T>` with `page`/`size` request params. Reporting and lists avoid loading entire ledgers into memory.

---

### Q154. API versioning?

**General explanation:**  
API versioning lets APIs evolve without breaking existing clients.

**In our project:**  
That gives a clear contract for the Angular frontend and future v2 migrations. URL prefix `/api/v1/...`.

---

### Q155. What is `DispatcherServlet`?

**General explanation:**  
DispatcherServlet is Spring MVC's front controller. After the security filter chain, it maps the request to a handler method and uses a HandlerAdapter to invoke it.

**In our project:**  
IncomeController methods are reached through that dispatch after JwtAuthenticationFilter validates the bearer token.

---

### Q156. What happens when an HTTP request reaches our Spring Boot app?

**General explanation:**  
Tomcat accepts the connection and Spring Security runs first. Jackson then writes the DTO response and filters clear contexts.

**In our project:**  
JwtAuthenticationFilter validates the Bearer token and sets SecurityContext plus TenantContext. DispatcherServlet maps to IncomeController while Jackson and @Valid bind the body. IncomeService runs transactional rules through repositories to PostgreSQL.

```
Client
 → Security filters (JWT)
 → DispatcherServlet
 → IncomeController
 → IncomeRequest DTO + @Valid
 → IncomeService
 → Repository
 → Hibernate
 → PostgreSQL
 → IncomeResponse JSON
``` DispatcherServlet finds IncomeController.

**Why this layering?** HTTP concerns stay in controller; business + transactions in service; SQL in persistence.

---

### Q157. Why `@PreAuthorize` on controller methods?

**General explanation:**  
@PreAuthorize adds method-level authorization beyond authenticated-only URL rules. Income list may allow AUDITOR while approve requires ADMIN or ACCOUNTANT. That fine-grained control differs per operation even on the same resource path family.

**In our project:**  
List income allows AUDITOR; approve requires `ADMIN` or `ACCOUNTANT` only.

**Why not only URL matchers?** Fine-grained role rules differ per operation on the same resource path family.

---

### Q158. Multipart uploads?

**General explanation:**  
multipart/form-data carries file uploads.

**In our project:**  
`DocumentController` upload endpoints; max size configured (~15MB); `MaxUploadSizeExceededException` → 413 in `GlobalExceptionHandler`. MaxUploadSizeExceededException is mapped to HTTP 413 in GlobalExceptionHandler so clients get a clear ProblemDetail.

---

### Q159. Filters vs interceptors?

**General explanation:**  
Servlet filters wrap the entire chain and run early; Spring MVC interceptors hook handler execution. Auth therefore lives in the filter chain, not as a controller interceptor.

**In our project:**  
JwtAuthenticationFilter is a security filter that validates JWT before controllers run.

---

### Q160. How does Spring choose the controller method?

**General explanation:**  
HandlerMapping matches path, HTTP method, and conditions to a controller method. HandlerAdapter then invokes that method. Argument resolvers fill @PathVariable, @RequestBody, and similar annotations.

**In our project:**  
That is how POST /api/v1/clients/{clientId}/income reaches the create method on IncomeController.

---

### Q161. Why REST instead of SOAP / raw TCP?

**General explanation:**  
We chose REST over SOAP and raw TCP because HTTP plus JSON is native for browsers and SPAs, has broad tooling, and is easy to cache or proxy. SOAP adds XML/WS-* ceremony we do not need for a modular Spring Boot API. Raw TCP would force us to reinvent framing, auth, and client libraries. Interview tip: say REST for resource APIs, and mention gRPC only if internal service-to-service latency matters later.

**In our project:**  
Our Angular app already consumes REST JSON endpoints. That is why the Finance AI API is REST-shaped.

---

# Part 9 — DTO + Validation
### Q162. What is a DTO?

**General explanation:**  
A DTO is a Data Transfer Object — the shape of data crossing an API boundary. Most are Java records. They decouple the HTTP contract from JPA entities. That keeps serialization predictable and avoids leaking internal fields.

**In our project:**  
`IncomeRequest`, `IncomeResponse`, `LoginRequest`, `LoginResponse`, `ClientResponse`, etc. Mostly Java **records**.

---

### Q163. Why DTOs? Why not expose Entity?

**General explanation:**  
Bidirectional relations can also cause infinite JSON recursion if entities are returned directly. Request DTOs also host Bean Validation cleanly.

**In our project:**  
We use DTOs instead of entities to hide internals like passwordHash and lazy collections, keep a stable API while the schema evolves, and avoid LazyInitializationException when serializing. Exposing Expense or User as JSON would couple the UI to the DB model and risk leaking secrets.

**Why not Entity as JSON?** Risk of leaking password hashes, infinite recursion on bidirectional relations, and coupling UI to DB model.

---

### Q164. Request DTO vs Response DTO?

**General explanation:**  
Request DTOs carry writable inputs; response DTOs carry what clients may read, including ids, audit fields, and computed values. Login uses LoginRequest versus LoginResponse with tokens and profile info. Separating them lets validation rules stay on inputs without constraining outputs.

**In our project:**  
`IncomeRequest` (create/update input) vs `IncomeResponse` (API output). Login: `LoginRequest` vs `LoginResponse` (tokens + profile info).

---

### Q165. How do we map Entity ↔ DTO?

**General explanation:**  
Entity to DTO mapping is manual via private toResponse methods or static from factories. MapStruct does not appear in the current codebase. Manual mapping gives explicit control and fewer generated surprises. MapStruct becomes attractive when mapping volume explodes.

**In our project:**  
`BankAccountResponse.from(BankAccount)`, `ClientService.toResponse(Client)`.

**MapStruct?** This does not appear to be used in the current project — mapping is manual.

**Why manual?** Explicit control; fewer generated surprises; fine for current size. MapStruct helps when mappings explode in number.

---

### Q166. `@Valid`?

**General explanation:**  
@Valid triggers Jakarta Bean Validation on the annotated argument. Without @Valid, constraint annotations on the DTO are not enforced by Spring MVC. Failures become MethodArgumentNotValidException handled globally as HTTP 400.

**In our project:**  
`@Valid @RequestBody LoginRequest`, `@Valid` on income create body, nested `@Valid` on lists in user access requests. **Trick:** Without `@Valid`, annotations on the DTO are ignored.

---

### Q167. `@NotNull` vs `@NotBlank` vs `@NotEmpty`?

**General explanation:**  
@NotNull rejects null only, so a String may still be empty. @NotBlank rejects null, empty, and whitespace for CharSequence. @NotEmpty rejects null or empty collections and strings. Choosing the right constraint prevents fake-empty money fields.

**In our project:**  
On IncomeRequest we use @NotNull @PastOrPresent for transactionDate, @NotNull @DecimalMin("0.01") for amount, and @NotBlank @Size(max = 200) for customerName.

```java
@NotNull @PastOrPresent LocalDate transactionDate,
@NotNull @DecimalMin("0.01") BigDecimal amount,
@NotBlank @Size(max = 200) String customerName,
```

---

### Q168. `@Email` / `@Size` / `@DecimalMin` / `@PastOrPresent`?

**General explanation:**  
Constraint annotations encode format and range rules on DTOs. @Email appears on user and auth DTOs, @Size bounds strings, @DecimalMin guards money minimums, and @PastOrPresent restricts dates. They run as part of Bean Validation when @Valid is present. Business rules like period-open still live in services. This split keeps cheap declarative checks at the edge.

**In our project:**  
Not a primary custom pattern in this repo — say so honestly, then tie back to everyday Java/Spring usage in our services if the interviewer presses.

---

### Q169. What happens on validation failure?

**General explanation:**  
Validation failure raises MethodArgumentNotValidException. Controllers do not need try/catch for every DTO. Clients see which fields failed and why. This is the standard Spring MVC plus Bean Validation path in our API.

**In our project:**  
GlobalExceptionHandler maps it to HTTP 400 ProblemDetail including field errors. Our GlobalExceptionHandler catches it.

---

### Q170. Business validation vs request validation?

**General explanation:**  
Request validation covers format and presence with annotations like @NotNull and @Email. Business validation covers domain rules such as period must be open, quota exceeded, or only DRAFT can approve, usually throwing BusinessException. Interviewers like hearing both layers.

**In our project:**  
Bean Validation on DTO; `Expense.approve` enforces DRAFT; `PeriodCloseService.close` checks readiness; `SubscriptionAccessService` asserts quotas.

---

### Q171. Why records for DTOs?

**General explanation:**  
Java records are immutable, compact carriers with generated accessors, equals, and hashCode. Immutability reduces accidental mutation across layers. They also read clearly in controllers and tests. Our API DTOs are mostly records for that reason.

**In our project:**  
They fit DTOs that are just data, such as IncomeRequest.

---

# Part 10 — JPA + Hibernate
### Q172. What is JPA?

**General explanation:**  
JPA is the Jakarta Persistence API — the standard ORM interfaces and annotations. We annotate entities and repositories against JPA, not a vendor-only API. Spring Data JPA sits on top of that model. In interviews, separate the spec from the provider.

**In our project:**  
Our Finance AI platform uses JPA annotations throughout the persistence layer.

---

### Q173. What is Hibernate?

**General explanation:**  
Hibernate is the leading JPA implementation: it generates SQL and manages the persistence context. Reporting sometimes uses EntityManager for native SQL while CRUD stays on repositories. Knowing Hibernate explains dirty checking, lazy loading, and flush behavior interviewers ask about.

**In our project:**  
Spring Data JPA uses Hibernate as the Boot auto-configured provider.

---

### Q174. JPA vs Hibernate?

**General explanation:**  
JPA is the specification; Hibernate is an implementation. Application code should prefer JPA annotations and EntityManager-style APIs. Hibernate provides the runtime engine and extras. Spring Boot’s spring-boot-starter-data-jpa defaults to Hibernate.

**In our project:**  
In our stack that means JPA-shaped entities with Hibernate under the hood.

---

### Q175. What is ORM?

**General explanation:**  
ORM is Object-Relational Mapping — mapping classes to tables and rows. It reduces boilerplate for CRUD and associations. We still drop to native SQL for analytics when entity graphs are awkward. That pragmatic mix is a strong interview answer.

**In our project:**  
Our Expense, Income, and User entities are ORM-mapped types.

---

### Q176. What is an Entity?

**General explanation:**  
An entity is a class mapped to a database table row. We map entities to DTOs before returning from services.

**In our project:**  
Many extend TenantAwareEntity for firm_id. `Expense`, `Income`, `User`, `Client`, `Receipt`, `AccountingPeriod`, etc. Expense is annotated with @Entity and @Table(name = "expenses") plus indexes on client_id with transaction_date and status.

```java
@Entity
@Table(name = "expenses", indexes = {
    @Index(columnList = "client_id, transaction_date"),
    @Index(columnList = "client_id, status")
})
public class Expense extends TenantAwareEntity { ... }
```

---

### Q177. `@Entity` / `@Table`?

**General explanation:**  
@Entity declares a persistent type; @Table sets the table name and optional indexes. Defaults can derive table names from class names, but explicit names help migrations.

**In our project:**  
Expense uses @Table(name = "expenses", indexes = …) for query-friendly indexes. Flyway owns the real schema while annotations describe the mapping Hibernate validates. Expense uses both clearly.

---

### Q178. `@Id` / `@GeneratedValue`?

**General explanation:**  
@Id marks the primary key; @GeneratedValue chooses how it is produced. UUIDs are distributed-friendly and avoid leaking insert volume through sequential ids. They also fit multi-tenant SaaS URLs well. Repositories then findById with UUID keys.

**In our project:**  
In BaseEntity we use @GeneratedValue(strategy = GenerationType.UUID) on a UUID id.

```java
@Id
@GeneratedValue(strategy = GenerationType.UUID)
private UUID id;
```

**Why UUID?** Distributed-friendly IDs, no sequential leak of volume; good for multi-tenant SaaS URLs.

---

### Q179. Generation strategies?

**General explanation:**  
ID generation strategies control how primary keys are assigned (identity, sequence, UUID, and so on).

**In our project:**  
Common strategies are AUTO, IDENTITY, SEQUENCE, and UUID. Our project standardizes on UUID via GenerationType.UUID. That avoids sequence/identity tuning for most tables and keeps ids opaque. Interviewers may ask trade-offs: UUIDs are larger and less sequential for B-tree locality than bigserial. For our SaaS API the opacity and simplicity win.

---

### Q180. `@Column`?

**General explanation:**  
@Column customizes column mapping such as nullable, length, unique, and columnDefinition. Combined with Bean Validation on DTOs, @Column enforces persistence-level constraints. Interview tip: validation and DB constraints are complementary.

**In our project:**  
It keeps entity fields aligned with Flyway columns under ddl-auto validate. Overusing columnDefinition can reduce portability, so we prefer portable attributes.

---

### Q181. `JpaRepository` / `CrudRepository`?

**General explanation:**  
Services call repositories inside transactions. This is the backbone of our persistence access.

**In our project:**  
JpaRepository and CrudRepository are Spring Data interfaces providing CRUD, paging, and derived queries. `UserJpaRepository`, `ClientJpaRepository`, `ExpenseJpaRepository`, `IncomeJpaRepository`, etc. JpaRepository adds JPA-specific helpers on top of CrudRepository.

---

### Q182. What does a repository provide?

**General explanation:**  
Repositories provide save, findById, findAll, delete, and paging without boilerplate SQL. They also host derived query methods and @Query declarations. Spring Data executes them through the EntityManager. That keeps HTTP controllers thin.

**In our project:**  
In our architecture services orchestrate repositories under @Transactional.

---

### Q183. How does Spring Data create repository implementations?

**General explanation:**  
Spring Data creates a runtime proxy implementing your repository interface. The proxy is backed by EntityManager and query execution infrastructure. Method names and @Query annotations drive the generated behavior. You declare the interface; Boot wires the bean. Understanding proxies also helps explain transaction self-invocation issues elsewhere.

**In our project:**  
Not a primary custom pattern in this repo — say so honestly, then tie back to everyday Java/Spring usage in our services if the interviewer presses.

---

### Q184. Derived query methods?

**General explanation:**  
Derived query methods translate method names into queries, such as findByEmailAndDeletedAtIsNull. Naming conventions cover And/Or, IgnoreCase, OrderBy, and more. They are great for simple filters. Complex joins or projections usually move to @Query or Specifications.

**In our project:**  
Our UserJpaRepository uses this pattern so soft-deleted users cannot authenticate. Used heavily in auth (`UserJpaRepository`) and elsewhere.

---

### Q185. `@Query` / JPQL?

**General explanation:**  
@Query declares an explicit query; JPQL operates on entities and fields, not tables. Parameters should be bound, never concatenated. JPQL stays closer to the domain model. When SQL dialect features matter, we consider native queries instead.

**In our project:**  
`DocumentRequestJpaRepository` search/overdue queries; notification repositories, etc.

---

### Q186. JPQL vs SQL?

**General explanation:**  
JPQL uses entity and field names; SQL uses tables and columns. JPQL fits entity-centric CRUD and filters. Native SQL shines for aggregations and Postgres-specific features. Interviewers like hearing when you choose each.

**In our project:**  
ReportingQueryRepository prefers native SQL for analytics clarity. We use both on purpose.

---

### Q187. Native query?

**General explanation:**  
Native queries are raw SQL executed via EntityManager.createNativeQuery or @Query(nativeQuery = true). Aggregations, performance, and clarity drive that choice versus forcing everything through entities. We still bind named parameters like :firmId. That is the pragmatic ORM-plus-SQL approach.

**In our project:**  
`ReportingQueryRepository` uses `EntityManager.createNativeQuery(...)` for sums, category totals, trends, status counts — analytics-friendly SQL against ledger tables.

**Why native for reports?** Aggregations/performance/clarity for reporting vs forcing everything through entity graphs.

---

### Q188. `@OneToOne` / `@OneToMany` / `@ManyToOne` / `@ManyToMany`?

**General explanation:**  
These annotations declare relationship cardinalities. @JoinTable names that join table and join columns. Cardinality mistakes cause duplicate rows or missing associations. Prefer LAZY on collections unless a use case needs otherwise.

**In our project:**  
- `User` `@ManyToOne` `Role`

- `User` `@OneToMany` `UserClientAccess`
- `Expense` `@ManyToOne` `Client`, `Category`
- `Expense` `@ManyToMany` `Receipt` via join table `expense_receipts`

```java
@ManyToMany
@JoinTable(
    name = "expense_receipts",
    joinColumns = @JoinColumn(name = "expense_id"),
    inverseJoinColumns = @JoinColumn(name = "receipt_id")
)
private Set<Receipt> receipts = new HashSet<>();
```

---

### Q189. `mappedBy` / owning side / `@JoinColumn`?

**General explanation:**  
The owning side controls the FK or join table; mappedBy marks the inverse side. UserClientAccess owns the user foreign key with @ManyToOne, while User uses mappedBy on the collection. Always update the owning side when linking entities. This is a classic JPA interview trap.

**In our project:**  
Expense owns the expense↔receipt join table via `@JoinTable`. UserClientAccess owns user FK with `@ManyToOne`; User uses `mappedBy` on the collection side.

---

### Q190. Cascade / `orphanRemoval`?

**General explanation:**  
Cascade propagates persist, merge, and remove; orphanRemoval deletes children removed from collections. Aggressive CascadeType.ALL can delete more than intended. Our project prefers explicit service-layer operations over broad cascading. Match cascade to true lifecycle ownership only. Interview tip: explain a concrete risk, such as cascading delete from parent to shared children. **Why not `CascadeType.ALL` everywhere?** Dangerous — deleting a client could wipe unrelated graphs; cascades must match real lifecycle ownership.

**In our project:**  
Prefer explicit service operations over aggressive cascading.

---

### Q191. Lazy vs Eager?

**General explanation:**  
FetchType LAZY loads associations when accessed; EAGER loads them with the parent. Lazy defaults avoid loading huge graphs on every query. Eager-always causes performance issues, N+1 patterns, and Cartesian products. With open-in-view false, we fetch what we need inside transactional services before mapping to DTOs.

**In our project:**  
Associations commonly `FetchType.LAZY` (e.g. `Expense.client`).

**Why lazy default for many associations?** Avoid loading huge graphs on every query.

**Why not eager always?** Performance disaster / N+1 / Cartesian products.

---

### Q192. N+1 problem?

**General explanation:**  
The N+1 problem is one query for parents plus N queries for each child’s association. Listing expenses and calling getCategory().getName() under LAZY is a typical risk. Mitigations include join fetch, EntityGraph, DTO projections, and batch size. Our config emphasizes open-in-view false and controlled fetching. That forces honest fetch planning in services instead of accidental lazy loads in views. **Mitigations:** join fetch, entity graphs, DTO queries, batch size, careful service-layer fetching.

**In our project:**  
**Example risk:** List expenses then calling `expense.getCategory().getName()` per row with LAZY and open session. `open-in-view` is typically disabled in serious apps (this project’s config emphasizes validate + controlled fetching) — don’t rely on views to lazy-load.

---

### Q193. Fetch Join / EntityGraph?

**General explanation:**  
JOIN FETCH and EntityGraph fetch associations in one query when needed. They are the main tools against N+1 for known access patterns. Apply them per use case rather than globally. Overusing join fetch can multiply rows on collections.

**In our project:**  
In our services we fetch what DTO mapping requires inside the transaction.

**General + project:** Use when a use case truly needs related data; don’t join-fetch everything always.

---

### Q194. Persistence Context / EntityManager?

**General explanation:**  
The persistence context is the first-level cache of managed entities for a unit of work. EntityManager is the JPA API Hibernate implements. Spring Data repositories use EntityManager internally. Understanding this explains dirty checking, flush, and LazyInitializationException.

**In our project:**  
Our ReportingQueryRepository goes through EntityManager for native analytics SQL. Reporting native queries go through `EntityManager`; Spring Data repos use it under the hood.

---

### Q195. Dirty checking?

**General explanation:**  
Dirty checking tracks changes to managed entities and issues UPDATEs on flush or commit. Within a transaction, mutating a loaded entity can be enough for persistence. Teams still often call repository.save for clarity and merge cases. Flush timing matters when constraints or later queries must see SQL early. This is core Hibernate behavior interviewers expect.

**In our project:**  
Not a primary custom pattern in this repo — say so honestly, then tie back to everyday Java/Spring usage in our services if the interviewer presses.

---

### Q196. Entity lifecycle?

**General explanation:**  
The entity lifecycle is transient, managed, detached, and removed. save moves transient entities to managed. Closing the persistence context detaches instances. remove schedules deletion. Knowing lifecycle states explains why lazy loads fail after the session ends and why merge exists for detached graphs.

**In our project:**  
Not a primary custom pattern in this repo — say so honestly, then tie back to everyday Java/Spring usage in our services if the interviewer presses.

---

### Q197. First-level cache?

**General explanation:**  
The first-level cache is the persistence context cache: the same id returns the same instance within the transaction or session. It is per persistence context, not a cluster-wide cache. It reduces duplicate loads during a use case. Do not confuse it with HTTP caching or Redis.

**In our project:**  
Our services rely on it naturally under @Transactional.

---

### Q198. `save()` / `flush()`?

**General explanation:**  
repository.save persists or merges; flush synchronizes SQL to the database before commit while remaining transactional. Commit finalizes the unit of work; rollback undoes it. Explicit flush helps when constraints or subsequent native queries need early visibility. Unnecessary flushes can hurt performance.

**In our project:**  
In services we usually save and rely on transaction commit.

---

### Q199. `LazyInitializationException`?

**General explanation:**  
LazyInitializationException occurs when accessing a lazy association after the persistence context is closed. Controllers must not serialize JPA entities. With open-in-view typically disabled, views cannot quietly lazy-load. That is intentional for predictable performance.

**In our project:**  
We avoid it by mapping to DTOs inside @Transactional service methods and fetching required associations first.

**How we avoid:** Map to DTOs inside `@Transactional` service methods; fetch what we need before returning; don’t serialize entities in controllers.

---

### Q200. `@Embedded` example?

**General explanation:**  
@Embedded maps a value object’s fields into the owning entity’s table columns. It models cohesive groups without a separate identity. Prefer embeddables for value objects, entities for things with identity. That distinction is a common interview point.

**In our project:**  
Receipt AI extraction metadata uses `@Embedded` style metadata fields for AI results.

---

### Q201. Soft delete?

**General explanation:**  
Soft delete marks a row deleted instead of physically removing it. It preserves audit history and enables recovery. You must remember filters on every query path. For finance data, soft delete often beats hard delete.

**In our project:**  
`User.deletedAt` — queries use `DeletedAtIsNull` patterns so soft-deleted users cannot authenticate.

---

### Q202. `JpaSpecificationExecutor`?

**General explanation:**  
JpaSpecificationExecutor enables dynamic Criteria predicates for filtering. Optional filters compose cleanly without dozens of derived method variants. Specifications stay type-safe relative to string SQL. They pair well with paging repositories.

**In our project:**  
Expense, income, and receipt list filters build Specifications for status, date, and category combinations.

---

### Q203. Why JPA instead of raw JDBC everywhere?

**General explanation:**  
Reporting still uses native SQL where aggregations need clarity and performance. That pragmatic mix fits a finance ledger platform. Raw JDBC everywhere would cost speed of delivery. Interviewers like hearing intentional boundaries, not dogma.

**In our project:**  
We use JPA for productivity on CRUD and relationships, reducing boilerplate. We use both instead of picking a religion.

---

# Part 11 — Transactions
### Q204. What is a transaction?

**General explanation:**  
A transaction is a unit of work that commits or rolls back as a whole. Accounting flows like period close need multiple writes to succeed together. Spring’s @Transactional demarcates those boundaries in services. Without transactions, failures leave inconsistent state.

**In our project:**  
This is foundational for our PostgreSQL-backed ledger.

---

### Q205. What is ACID?

**General explanation:**  
ACID means Atomicity, Consistency, Isolation, and Durability. Atomicity is all-or-nothing; consistency preserves invariants; isolation limits concurrent interference; durability keeps committed data after crashes. Our period close and quota flows rely on transactional guarantees. Be ready to give a one-line example for each letter.

**In our project:**  
Relational PostgreSQL fits ledger needs better than eventual-only stores.

---

### Q206. What does `@Transactional` do?

**General explanation:**  
@Transactional tells Spring to open a transaction around the method via AOP proxy. Success commits; runtime exceptions roll back by default. Controllers stay outside that boundary. Understanding the proxy model explains self-invocation pitfalls.

**In our project:**  
We place it on service methods that form a business unit of work.

---

### Q207. Where is it used in our project? Why?

**General explanation:**  
Multiple DB writes must succeed or fail together. Be ready to discuss after-commit notification design nuances. The why is atomic business use cases.

**In our project:**  
PeriodCloseService close validates readiness, sets CLOSED, saves, audits, and notifies inside one transactional boundary. We use @Transactional on services such as authentication login, client create, period close, and session refresh.

**Example — period close must be atomic:**
```java
@Transactional
public PeriodResponse close(UUID clientId, UUID periodId, String closeNote) {
    // validate readiness
    period.setStatus(CLOSED);
    periodRepository.save(period);
    auditLogger.record(...);
    workflowNotificationService.periodClosed(...);
    return toResponse(...);
}
```
If audit/save fails mid-way, we don’t want a half-closed period without consistent audit/notification side effects (notification may be after-commit depending on design — be ready to discuss).

**Why required?** Multiple DB writes must succeed or fail together.

---

### Q208. What if one repository operation fails?

**General explanation:**  
A runtime failure in one repository operation rolls back the active transaction. Prior inserts and updates in that unit of work are undone. Spring propagates the exception to the caller after rollback. Partial commits of a single business use case should not remain.

**In our project:**  
This behavior depends on correct @Transactional boundaries.

---

### Q209. Commit / Rollback?

**General explanation:**  
Spring transactions wrap a unit of work so multiple DB operations commit together or roll back together.

**In our project:**  
Commit persists all changes of the unit of work; rollback undoes them. Spring commits when the proxied @Transactional method completes normally. Unchecked exceptions trigger rollback by default. Checked exceptions may still commit unless rollbackFor is set. Our BusinessException is runtime, so it rolls back naturally.

---

### Q210. Checked vs unchecked rollback?

**General explanation:**  
Spring rolls back by default on unchecked RuntimeException and Error, not on checked exceptions unless rollbackFor is specified. Our BusinessException is a runtime type, so transactional methods roll back on business failures. If you throw checked exceptions, set rollbackFor explicitly. This default is a frequent interview question. Align exception types with intended transactional behavior.

**In our project:**  
We use runtime `BusinessException` — natural rollback behavior.

---

### Q211. Transaction propagation?

**General explanation:**  
Propagation controls nesting: REQUIRED is the default join-or-create behavior; REQUIRES_NEW suspends and starts a new transaction. That isolates long or failure-prone AI side effects. Choose propagation deliberately per use case. Interviewers often ask for REQUIRED versus REQUIRES_NEW examples.

**In our project:**  
AI persistence uses `@Transactional(REQUIRES_NEW)` in `DocumentAiPersistenceService` so AI status updates can commit independently of outer flows.

---

### Q212. Isolation levels?

**General explanation:**  
Common isolation levels are READ_UNCOMMITTED, READ_COMMITTED, REPEATABLE_READ, and SERIALIZABLE. They trade consistency against concurrency. Ledger conflicts may need explicit locking or optimistic versions beyond defaults. Mention anomalies like dirty read and non-repeatable read if asked to go deeper.

**In our project:**  
PostgreSQL’s default is typically READ_COMMITTED.

---

### Q213. Spring transaction proxy / self-invocation problem?

**General explanation:**  
Self-invocation via this.method() bypasses the proxy, so the annotation is ignored. Fixes include splitting into another bean or careful self-injection, preferring a separate bean. This affects propagation and readOnly settings too. Always design transactional entry points as public service APIs.

**In our project:**  
@Transactional applies when callers go through the Spring proxy.

**Fix:** Move method to another bean, or inject self carefully (prefer split beans).

---

### Q214. `readOnly = true`?

**General explanation:**  
@Transactional(readOnly = true) marks read paths and can enable provider optimizations. It documents that the use case should not flush writes. Accidental writes in readOnly transactions are a smell to fix. Use it on query services, not on period close.

**In our project:**  
List/get methods often `@Transactional(readOnly = true)` (`ClientService.listAccessibleClients`, reporting facade methods).

---

### Q215. Optimistic vs pessimistic locking / `@Version`?

**General explanation:**  
Optimistic locking uses a version column and fails on concurrent update conflicts; pessimistic locking uses SELECT FOR UPDATE style locks. Subscription quota concurrency is covered by tests such as SubscriptionQuotaConcurrencyIntegrationTest. Not every entity may carry @Version, but the concept matters for ledger edits. Choose optimistic for contention-light paths and pessimistic for critical scarce resources. Explain lost update with a concrete money example.

**In our project:**  
Subscription quota concurrency uses locking strategies / tests (`SubscriptionQuotaConcurrencyIntegrationTest`). Be ready to discuss lost updates on income edits — optimistic `@Version` is general knowledge if not on every entity.

---

### Q216. Why not `@Transactional` on every controller?

**General explanation:**  
Transactions belong with business use cases in services, not controllers. Controllers may orchestrate multiple services incorrectly inside one web-layer transaction. Holding a TX open during HTTP serialization is unnecessary risk. Thin controllers plus transactional services is the Spring norm we follow.

**In our project:**  
Our PeriodCloseService and auth services own the boundaries.

---

### Q217. Why not `@Transactional` on every tiny method?

**General explanation:**  
Annotating every tiny method creates noisy boundaries and can commit partial business operations. Design transactions around use cases such as login, create client, or close period. Keep them as short as the use case allows to reduce lock time. Balance atomicity with duration.

**In our project:**  
HikariCP pool exhaustion often traces to long transactions.

---

# Part 12 — SQL + Database
### Q218. What relational DB do we use?

**General explanation:**  
Strong consistency, foreign keys, unique constraints, and transactions suit accounting. Client→expenses→categories→receipts relations fit SQL naturally. MongoDB is a poor primary for multi-row ledger transactions. That rationale is interview-ready.

**In our project:**  
Our relational database is PostgreSQL, configured via application-local.yml and production env, with Flyway migrations and JPA ddl-auto validate. We use PostgreSQL, a solid table database good at careful money data. Flyway migrates the schema.

**Why relational?** Strong consistency for ledger/period close, FKs, unique constraints, transactions — accounting needs ACID.

**Why not MongoDB as primary?** Financial relations (client→expenses→categories→receipts) and multi-row transactions fit SQL better.

---

### Q219. Primary key / foreign key / unique?

**General explanation:**  
Primary keys identify rows; foreign keys reference other tables; unique constraints enforce uniqueness. Unique constraints cover firm name patterns, (firm_id, name) on clients, and user email. These constraints protect multi-tenant integrity beyond application checks.

**In our project:**  
UUID PKs; FKs like `client_id` on expenses; unique constraints e.g. Flyway encodes them in migrations. firm name, `(firm_id, name)` on clients, unique user email.

---

### Q220. Indexes?

**General explanation:**  
Indexes speed lookups, sorts, and joins at some write and storage cost. They help avoid full table scans. Too many indexes slow inserts/updates and can confuse the planner. Tie indexes to real query patterns in interviews.

**In our project:**  
Expense indexes on `(client_id, transaction_date)` and `(client_id, status)` — match common list filters.

**Why indexes improve reads?** Avoid full table scans.

**Why too many indexes are bad?** Slower writes, more storage, planner confusion.

---

### Q221. Composite index?

**General explanation:**  
A composite index indexes multiple columns for filters that use them together. Planners can use leftmost prefixes depending on the query. Design composites from actual WHERE and ORDER BY clauses. Random composite indexes without query evidence waste write cost.

**In our project:**  
Our expense indexes on client_id with transaction_date or status are composite examples.

---

### Q222. Normalization?

**General explanation:**  
Normalization organizes tables to reduce duplication, such as separate clients, categories, and expenses. Foreign keys enforce relationships. Analytical copies may denormalize later, but OLTP stays normalized.

**In our project:**  
Repeating client names on every expense row would invite inconsistency. Our schema follows that relational discipline via Flyway. Clients, categories, and expenses stay separate instead of repeating client names on every expense.

---

### Q223. INNER JOIN vs LEFT JOIN?

**General explanation:**  
INNER JOIN returns matching rows only; LEFT JOIN returns all left rows plus matches, with nulls when none exist. Reporting joins must respect tenant filters too. Wrong join type is a common SQL interview failure. State both definitions and one example.

**In our project:**  
Clients without expenses is LEFT JOIN expenses then WHERE expense.id IS NULL.

**Interview SQL:** clients without expenses → LEFT JOIN + `WHERE expense.id IS NULL`.

---

### Q224. GROUP BY / HAVING / WHERE vs HAVING?

**General explanation:**  
WHERE filters rows before grouping; GROUP BY forms aggregate buckets; HAVING filters aggregates afterward. Status counts are GROUP BY status with COUNT(*). You cannot put aggregate conditions in WHERE. This trio appears in reporting SQL and interviews constantly.

**In our project:**  
Our ReportingQueryRepository status counts follow the same shape.

---

### Q225. DELETE vs TRUNCATE vs DROP?

**General explanation:**  
DELETE removes rows and can be conditional and transactional. TRUNCATE empties a table quickly, often with less per-row overhead. DROP removes the table definition. In interviews, contrast recoverability and transactional behavior of each.

**In our project:**  
For users we prefer soft delete via deletedAt rather than hard DELETE. Soft delete in our app is a status/timestamp, not these DDL/DML verbs.

---

### Q226. Connection pooling / HikariCP?

**General explanation:**  
Connection pooling reuses DB connections; Boot defaults to HikariCP. Exhaustion symptoms are hangs or failures under load from a pool too small or connections held too long. Long @Transactional methods and leaks are common root causes. Monitor active connections in production. Pair pooling knowledge with transaction design answers.

**In our project:**  
**Symptom of exhaustion:** requests hang/fail under load — pool too small or connections leaked (long TX).

---

### Q227. Database migrations / Flyway?

**General explanation:**  
That avoids surprise schema drift from ddl-auto update in production. Migrations are code-reviewed like application code.

**In our project:**  
Flyway on (`classpath:db/migration` V1–V24). `ddl-auto: validate` — Hibernate checks mapping matches DB; Flyway owns changes. Mentioning validate plus Flyway is a strong project-specific answer.

**Why Flyway instead of `ddl-auto: update`?** Controlled, reviewable migrations for prod; no surprise schema drift.

**Liquibase?** Not used here.

---

### Q228. Query optimization / explain plans?

**General explanation:**  
Use EXPLAIN ANALYZE, verify indexes, avoid SELECT *, reduce N+1, and paginate large lists. Our heavy analytics go through tuned native SQL in reporting repositories. Optimization starts from real query plans, not folklore. Tenant filters should remain index-friendly. Interviewers like a practical checklist more than buzzwords.

**In our project:**  
Heavy analytics go through tuned native SQL in reporting repositories.

---

### Q229. SQL exercise — second-highest expense amount (per client)?

**General explanation:**  
One approach is SELECT DISTINCT amount FROM expenses WHERE client_id = :clientId AND status = 'APPROVED' ORDER BY amount DESC OFFSET 1 LIMIT 1. Window functions like DENSE_RANK are portable interview variants. Always scope by tenant/client in our multi-tenant model. Discuss ties if amounts can duplicate. Clarity of approach matters as much as exact dialect.

**In our project:**  
Finding the second-highest expense is like finding the second-tallest kid.

---

### Q230. SQL — count expenses by status?

**General explanation:**  
SELECT status, COUNT(*) FROM expenses WHERE firm_id = :firmId AND client_id = :clientId GROUP BY status. Always include tenant predicates. Aggregations like this are why native SQL is comfortable for reports. Be ready to add date filters in follow-ups.

**In our project:**  
This mirrors ReportingQueryRepository.statusCounts style analytics.

---

### Q231. SQL — clients without expenses?

**General explanation:**  
This anti-join finds clients without expenses. NOT EXISTS is an equivalent style many prefer. Tenant filtering on firm_id is mandatory in our shape. Mention both LEFT JOIN and NOT EXISTS if you want bonus points.

**In our project:**  
Use a LEFT JOIN from clients to expenses scoped by firm_id and keep rows where the expense id is null.

---

### Q232. SQL — latest income per client?

**General explanation:**  
Filter by firm_id for tenancy. Window functions like ROW_NUMBER are the portable alternative. Our incomes table is the source. State dialect assumptions when using DISTINCT ON in interviews.

**In our project:**  
PostgreSQL DISTINCT ON (client_id) with ORDER BY client_id, transaction_date DESC, created_at DESC returns latest income per client.

---

### Q233. SQL — duplicates by checksum/name?

**General explanation:**  
GROUP BY checksum HAVING COUNT(*) > 1 finds duplicate fingerprints. The same pattern applies to duplicate names with different HAVING keys. Always consider tenant scope so firms do not collide. HAVING is required because the filter is on the aggregate.

**In our project:**  
It relates to our duplicate document detection concept.

---

### Q234. Pagination in SQL?

**General explanation:**  
Pagination uses ORDER BY plus LIMIT and OFFSET, for example expenses by transaction_date DESC. Spring Data exposes this via Pageable on repositories. Stable ordering is required for consistent pages. Deep OFFSET can degrade; keyset pagination is an advanced follow-up.

**In our project:**  
List endpoints in our API rely on paging for scalability.

---

### Q235. Why `BigDecimal` in Java for money mapped to numeric SQL?

**General explanation:**  
binary floating point double is unsafe for currency. Scale and rounding mode matter in calculations. Entity amount fields and DTO validation like @DecimalMin align with that choice. Saying never use double for money is interview gold.

**In our project:**  
We use BigDecimal in Java for money mapped to numeric SQL types for exact decimal arithmetic.

---

### Q236. Multi-tenant data shape?

**General explanation:**  
Client-supplied firm ids are not authoritative. Leaking cross-tenant data is a critical failure in a finance SaaS.

**In our project:**  
Rows carry firm_id via TenantAwareEntity. Services and queries must scope by tenant; JWT and TenantContext establish the firm for the request. Mention TenantContext together with JWT claims for a complete answer.

---

# Part 13 — Spring Security / JWT
### Q237. Authentication vs Authorization?

**General explanation:**  
Authentication answers who you are; authorization answers what you can do. Login authenticates credentials and issues tokens. @PreAuthorize plus client-access checks authorize operations afterward. Confusing the two is a common junior mistake.

**In our project:**  
Our SecurityFilterChain and method security cover both layers. Login authenticates; `@PreAuthorize` + client access checks authorize.

---

### Q238. What is Spring Security here?

**General explanation:**  
Spring Security provides the filter chain and method security protecting our APIs. Configuration lives in AuthSecurityConfig. Stateless JWT sessions fit our SPA client. Explain it as infrastructure around every request, not only login.

**In our project:**  
It integrates UserDetailsService, PasswordEncoder, JWT filter, CORS, and authorization rules. It is central to the Finance AI platform.

---

### Q239. `SecurityFilterChain`?

**General explanation:**  
That ordering is a key talking point.

**In our project:**  
SecurityFilterChain defines ordered servlet filters for security. CSRF disabled (stateless JWT API), CORS enabled, STATELESS sessions, permit login/register/refresh/health, everything else authenticated, JWT filter before `UsernamePasswordAuthenticationFilter`. JwtAuthenticationFilter runs before UsernamePasswordAuthenticationFilter.

---

### Q240. `AuthenticationManager`?

**General explanation:**  
AuthenticationManager authenticates credentials. On success we issue JWT and refresh tokens. On failure we map to auth errors without leaking extra user details.

**In our project:**  
`ProviderManager` + `DaoAuthenticationProvider` with `UserDetailsService` + `PasswordEncoder` bean. AuthenticationService.login calls it during login.

---

### Q241. `UserDetailsService`?

**General explanation:**  
Soft-deleted users cannot authenticate. This is the custom user lookup hook in our security setup.

**In our project:**  
UserDetailsService loads a user by username for authentication. `UserDetailsServiceImpl.loadUserByUsername` loads by email (`findByEmailAndDeletedAtIsNull`) → `SecurityUser`. DaoAuthenticationProvider then checks the password hash.

---

### Q242. `PasswordEncoder` / BCrypt?

**General explanation:**  
PasswordEncoder with BCryptPasswordEncoder stores one-way salted password hashes. Plaintext or reversible encryption is unacceptable for password storage. That rationale belongs in every security interview answer.

**In our project:**  
BCrypt’s work factor slows brute force. We use it on register, change, and reset password flows, and matches runs during login via DaoAuthenticationProvider and UserService.changePassword.

```java
@Bean
PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```
Used on register/change/reset password; `matches` on login via DaoAuthenticationProvider / `UserService.changePassword`.

**Why BCrypt instead of plaintext or reversible encryption?** DB leak should not reveal passwords.

---

### Q243. Roles / authorities?

**General explanation:**  
Roles map to RoleCode values: ADMIN, ACCOUNTANT, AUDITOR, BUSINESS_OWNER. Authorities are typically ROLE_ADMIN style strings for hasRole. Method security and JWT claims carry role information. Authorization combines roles with client-access rules. Naming RoleCode in the interview shows project fluency.

**In our project:**  
Not a primary custom pattern in this repo — say so honestly, then tie back to everyday Java/Spring usage in our services if the interviewer presses.

---

### Q244. `@PreAuthorize`?

**General explanation:**  
Examples include restricting income approve to ADMIN or ACCOUNTANT while auditors remain read-only. It runs after authentication and complements URL rules. Client-scoped checks still apply in services. Prefer method security for business operations, not only HTTP matchers.

**In our project:**  
@PreAuthorize is SpEL-based method security under @EnableMethodSecurity.

**Examples:** income approve ADMIN/ACCOUNTANT; auditors can read but not approve.

---

### Q245. CORS?

**General explanation:**  
CORS defines browser cross-origin access rules. It is enforced by browsers, not a substitute for authentication. APIs still require JWT on protected routes. Explain CORS separately from CSRF in interviews.

**In our project:**  
We configure allowed origins through application CORS properties, including local Angular at localhost:4200.

---

### Q246. CSRF?

**General explanation:**  
CSRF is Cross-Site Request Forgery against cookie-authenticated browser sessions. That is appropriate only because we are not relying on browser cookie session auth for API mutations. Cookie-based session apps need CSRF tokens. State that distinction clearly.

**In our project:**  
Our API is stateless JWT, typically via Authorization header, so CSRF protection is disabled in SecurityFilterChain.

**Why disable CSRF here?** Not using browser cookie session auth for API calls in the classic sense.

---

### Q247. What is JWT?

**General explanation:**  
JWT is a JSON Web Token: a compact signed set of claims shaped as header, payload, and signature in Base64url. The payload is readable and not encrypted by default, so secrets must not live in claims. Signature verification proves integrity and authenticity with our secret. That foundation leads into our filter and claims design.

**In our project:**  
We use JWTs as short-lived access credentials for the API.

**Important:** Payload is **not encrypted** by default — don’t put secrets/passwords in claims.

---

### Q248. JWT claims in our project?

**General explanation:**  
Those claims rebuild security and tenant context per request. Avoid putting sensitive secrets in the payload. Claim design is a frequent follow-up question after “what is JWT”.

**In our project:**  
JWT claims in our project include user id, firmId, role, email, and expiry/issuer/jti-style fields via JwtTokenProvider and JwtClaimNames. firmId supports multi-tenant scoping with TenantContext. TenantContext uses firm information from the token path.

---

### Q249. How is JWT generated / validated?

**General explanation:**  
Validation uses parseToken to verify signature, expiry, and issuer. Failed validation leaves the request unauthenticated for protected routes. Mentioning issue versus validate shows end-to-end understanding.

**In our project:**  
Generation goes through JwtService issueAccessToken for a User via JwtTokenProvider generateAccessToken using JJWT and a secret. JwtAuthenticationFilter applies that validation on each request.

---

### Q250. Access token vs refresh token?

**General explanation:**  
Hashing refresh tokens protects against database leaks. Short access TTL limits damage if an access token leaks. This split is our session strategy with stateless API auth. Be ready to walk login, refresh, and logout briefly.

**In our project:**  
Access JWTs are short-lived and sent on each API call; refresh tokens are longer-lived, stored hashed via SessionService, rotated on refresh, and revoked on logout or password reset.

**Why hash refresh tokens?** DB leak shouldn’t yield usable refresh tokens.

---

### Q251. JWT filter?

**General explanation:**  
Controllers and @PreAuthorize run with that context. A finally block clears both contexts so ThreadLocal state does not leak across requests.

**In our project:**  
It reads Authorization Bearer, validates via JwtService, loads the user, and checks active status plus firm claim match. JwtAuthenticationFilter extends OncePerRequestFilter and runs once per HTTP request. It then sets SecurityContextHolder and TenantContext including accessible client ids.

---

### Q252. Full authentication flow (story — must know)?

**General explanation:**  
Finally blocks clear contexts after the request.

**In our project:**  
AuthenticationManager and DaoAuthenticationProvider authenticate via UserDetailsServiceImpl and BCrypt. JwtService issues the access JWT and SessionService persists a hashed refresh token; LoginResponse returns both. Login is POST /api/v1/auth/login to LoginController then AuthenticationService.login with rate limiting. On later calls JwtAuthenticationFilter validates the Bearer token, sets SecurityContext and TenantContext, then @PreAuthorize runs.

---

### Q253. Token expiry / invalid JWT?

**General explanation:**  
Expired tokens or invalid signatures leave the request unauthenticated in the filter. Protected endpoints then hit the authentication entry point and return 401. Clients should use the refresh flow or re-login. Refresh revocation storage helps kill longer-lived sessions without relying on server HTTP sessions.

**In our project:**  
We keep access JWT TTL short to limit stolen-token window.

---

### Q254. Why JWT instead of server sessions?

**General explanation:**  
Any instance can verify the signature with the shared secret. Trade-off: access-token revocation is harder than destroying a server session. ProductionJwtSecretValidator-style checks keep the signing secret out of insecure defaults.

**In our project:**  
Mitigation is short access TTL plus refresh token hashing and revocation storage in SessionService. We prefer JWT over server sessions for horizontal scaling and SPA or mobile clients without sticky sessions.

---

### Q255. SQL injection prevention?

**General explanation:**  
SQL injection is prevented by bound parameters, never string-concatenating user input into SQL. JPA and Spring Data bind values safely for repository methods. That keeps tenant filters and filters from becoming executable SQL. Interview tip: mention prepared statements and that ORM alone is not enough if you concatenate native SQL.

**In our project:**  
Our reporting native queries use named parameters such as :firmId and :clientId.

---

### Q256. Secrets / environment variables?

**General explanation:**  
JWT secrets and DB credentials come from configuration and environment variables, especially in production. ProductionJwtSecretValidator-style checks refuse insecure defaults at boot. Docker Compose and deployment inject env for datasource and JWT settings. Interviewers want to hear secret rotation and least privilege for DB users as well.

**In our project:**  
We never commit real secrets to the repository.

---

### Q257. 401 vs 403 in security terms (recap)?

**General explanation:**  
401 Unauthorized means authentication failed or is missing — invalid JWT, no Bearer header, or anonymous on a protected route. 403 Forbidden means the principal is authenticated but authorization failed — role or tenant client access insufficient.

**In our project:**  
Our handlers map AuthenticationException-style failures to 401 and AccessDeniedException to 403. @PreAuthorize failures typically surface as 403 after the filter authenticated the user.

---

# Part 14 — Testing
### Q258. Unit vs integration test?

**General explanation:**  
Unit tests isolate one class with mocked collaborators. Integration tests load several layers with Spring context and usually a real database. Cross-cutting security and tenant filters are exactly where mocks hide bugs. We still explain Mockito for interviews even when the suite focuses on integration.

**In our project:**  
Tests are primarily **integration** (`@SpringBootTest`, MockMvc, Testcontainers PostgreSQL).

---

### Q259. JUnit?

**General explanation:**  
JUnit is the primary Java test framework. Nested tests and lifecycle callbacks are available when suites grow.

**In our project:**  
We use JUnit 5 via spring-boot-starter-test with @Test and assertion APIs. Integration tests in the Finance AI Automation Platform are JUnit classes that boot Spring and hit MockMvc or repositories. Knowing JUnit 4 versus 5 differences is useful interview color but we run on 5.

---

### Q260. Mockito (`@Mock`, `@InjectMocks`, `when`, `verify`)?

**General explanation:**  
Mockito is the common mocking library with @Mock, @InjectMocks, when-thenReturn stubbing, and verify. It does not appear to be the focus of our current project suite, which prefers Spring Boot integration tests. That keeps business rules fast and deterministic without Postgres. Prefer integration tests for JWT filter and tenant isolation behavior.

**In our project:**  
For interviews, say you would mock ClientJpaRepository when unit-testing a service method in isolation.

**This does not appear to be used in the current project** (no typical Mockito service unit tests). Still must know for interviews.

**Example (general):** mock `ClientJpaRepository` when unit-testing a service method.

---

### Q261. Mock vs spy?

**General explanation:**  
A mock is a complete fake with no real implementation unless stubbed. A spy wraps a real instance and stubs selected methods while delegating others. Spies risk accidental real side effects if you forget what is stubbed. Mocks are clearer for pure unit isolation of services.

**In our project:**  
Our suite rarely needs spies because we favor full-stack integration paths.

---

### Q262. `@SpringBootTest`?

**General explanation:**  
@SpringBootTest loads the full Spring application context for integration testing. Cost is slower startup than slice tests. Benefit is catching wiring regressions early in a modular monolith.

**In our project:**  
`FinancePlatformApplicationTests`, security and finance lifecycle tests. Combined with Testcontainers Postgres it exercises Flyway, JWT filters, and services together.

---

### Q263. `@WebMvcTest`?

**General explanation:**  
Collaborators outside the web slice are typically mocked. It is not the main testing style in this repository. Mention slice tests as a faster option for pure controller mapping checks.

**In our project:**  
@WebMvcTest is a Spring Boot slice that focuses on the MVC layer with MockMvc. We prefer fuller @SpringBootTest integration covering security filters and persistence. **Not the main style here** — we prefer fuller integration tests.

---

### Q264. MockMvc?

**General explanation:**  
Combined with @SpringBootTest and a real Postgres container it validates filters and controllers together. You assert status, headers, and JSON paths. It is the workhorse for API-level integration tests here.

**In our project:**  
MockMvc simulates HTTP requests against controllers without binding a real server socket. In our project BaseWebIntegrationTest-style support drives login 401 cases, CRUD flows, and tenant isolation assertions. Used in web integration tests (`BaseWebIntegrationTest` support) for login 401s, CRUD, tenant isolation.

---

### Q265. `@DataJpaTest`?

**General explanation:**  
@DataJpaTest loads a JPA slice for repository tests. It is general Spring knowledge but not our primary suite style. That catches tenant filters and constraints H2 might miss. Use @DataJpaTest when you only need fast repository queries in isolation.

**In our project:**  
We prefer full-stack integration with Testcontainers PostgreSQL for truthful SQL and Flyway. We lean on full Postgres Testcontainers instead.

**General knowledge** — our suite leans to full-stack integration with Testcontainers.

---

### Q266. Testcontainers?

**General explanation:**  
Combined with @SpringBootTest it validates the modular monolith against production-like storage. Requires Docker available in the CI or local environment.

**In our project:**  
Testcontainers runs disposable Docker containers as test dependencies. `AbstractPostgresIntegrationTest` pattern + Postgres container for integration suite. That is more confidence than H2-only suites for Flyway, constraints, and SQL dialect.

---

### Q267. What should be mocked?

**General explanation:**  
True unit tests mock repositories, clocks, and external IO to isolate business logic. That choice fits multi-tenant finance where wiring bugs matter most. You might still stub an external AI HttpClient for deterministic upload tests. Interview answer: mock for speed in units; prefer real collaborators for security and tenancy.

**In our project:**  
Our integration tests usually mock little — they exercise JWT security, TenantContext, and the database.

**Why mock repository in a service unit test?** Isolate business logic from DB; fast/deterministic tests.

---

### Q268. How would you test `PeriodCloseService.close`?

**General explanation:**  
Integration approach: seed a client and open period with close blockers, call close, expect 422 BusinessException-style failure. Clear blockers, close again, assert status CLOSED and an audit event. Prefer integration here because close spans bank, ledger, and documents.

**In our project:**  
Unit approach: mock CloseReadinessService or List of CloseCheck, repositories, and audit, then assert transitions and exceptions. That maps directly to PeriodCloseService.close in module-finance.

---

### Q269. Important tests in this repo?

**General explanation:**  
Key suite examples include JwtSecurityIntegrationTest, TenantIsolationIntegrationTest, and FinancialLifecycleIntegrationTest. SubscriptionQuotaConcurrencyIntegrationTest covers race-prone quota gates. FlywayMigrationIntegrationTest, category and client creation tests, and file upload security tests round out critical paths. Together they protect auth, multi-tenancy, ledger flows, and storage. Mentioning them shows project-specific testing depth.

**In our project:**  
Flyway migrations and upload safety also get coverage.

---

### Q270. Why integration tests fit this project?

**General explanation:**  
Regressions often appear only when real Spring wiring and SQL run together. Unit tests alone can green-pass while filters misconfigure. That is why this modular monolith favors integration-heavy coverage.

**In our project:**  
Multi-tenant security, JWT filters, subscription quotas, and Flyway are cross-cutting concerns. Integration tests with MockMvc and Testcontainers catch tenant leakage and auth gaps early.

---

# Part 15 — Microservices / Monolith
### Q271. What is our project’s real architecture?

**General explanation:**  
There is no Feign or Kafka mesh between auth and finance. Mistake to avoid: calling Gradle modules microservices. Modules organize code; deployment stays one process.

**In our project:**  
The Finance AI Automation Platform is a modular monolith, not microservices. Evidence: one @SpringBootApplication FinancePlatformApplication, Gradle modules compiling into one :platform-app bootJar, one PostgreSQL with Flyway, and compose running one backend plus Postgres and frontend. We ship one JAR and talk to one Postgres.

**Mistake to avoid:** “We have microservices because we have modules.” Modules ≠ separately deployed services.

---

### Q272. What is monolithic architecture?

**General explanation:**  
Monolithic architecture means a single deployable backend unit serving all API features in one process. Local transactions and in-process calls stay simple. Operationally you scale by replicating the whole app. The downside is that one hot feature can force scaling everything.

**In our project:**  
Our project starts from this model with modular structure inside.

---

### Q273. What is a modular monolith?

**General explanation:**  
A modular monolith is one deployable application organized into clear modules with boundaries. Facades define cross-module entry points. We still deploy one Spring Boot JAR. That preserves local @Transactional correctness for period close.

**In our project:**  
Ours includes platform-app for HTTP and Flyway, platform-core for tenant audit storage, module-auth for JWT users, module-finance for ledger periods, module-ai for document AI, and module-reporting for analytics SQL.

```
platform-app        → HTTP controllers, wiring, Flyway, boot JAR
platform-core       → exceptions, tenant, audit, notifications, storage
module-auth         → users, JWT, sessions, roles
module-finance      → clients, ledger, documents, bank, periods, subscriptions
module-ai           → document AI processing
module-reporting    → reports / native SQL
```

---

### Q274. What are microservices?

**General explanation:**  
Microservices are independently deployable services that usually communicate over the network and often own separate databases. Benefits include independent scaling and team ownership. Costs include eventual consistency, distributed tracing, and ops overhead. Interviewers expect an honest contrast with our modular monolith.

**In our project:**  
Our platform intentionally avoids that mesh today.

---

### Q275. Monolith vs microservices?

**General explanation:**  
Microservices: many deploys, distributed or saga transactions, harder tracing, scale hot services, higher ops cost. For accounting close and tenant checks, local transactions win early. Extract services when independent scale or team boundaries justify the tax. That comparison is a standard senior-junior interview table.

**In our project:**  
Modular monolith: one deploy, local @Transactional, easier debugging, scale the whole app, lower ops cost.

---

### Q276. Why does our project use a modular monolith?

**General explanation:**  
Local @Transactional and one Postgres schema keep correctness simpler. Network calls, eventual consistency, and DevOps overhead would hurt before we need independent scaling. Modules still give clean ownership via facades. Extract later if AI or reporting needs separate scale.

**In our project:**  
We use a modular monolith because accounting workflows share data: documents link to expenses, period close reads bank ledger and docs, subscriptions gate uploads, and JWT TenantContext spans features.

**Why not microservices from day one?** Network calls, eventual consistency, distributed transactions, and DevOps overhead before the product needs independent scaling/teams.

---

### Q277. Why would you NOT use microservices (yet)?

**General explanation:**  
I would not adopt microservices yet because of team size, shared transactional boundaries, and operational complexity. Distributed debugging and auth propagation add risk. Splitting without clear data ownership creates a distributed monolith. Revisit when scaling or ownership forces it.

**In our project:**  
Period close and tenant isolation need strong consistency that local transactions provide.

---

### Q278. When would microservices become useful?

**General explanation:**  
Microservices become useful for independent scaling such as CPU-heavy AI extraction, separate team ownership, different SLAs, isolating noisy neighbors, or multi-region needs. Reporting read models might also split for replica-friendly loads. Auth propagation and tenant consistency must be designed carefully. Prefer extracting one bounded context first rather than a big-bang rewrite.

**In our project:**  
Our in-process AI async path is the likely first extraction candidate.

---

### Q279. If this project became microservices, how would you split?

**General explanation:**  
A hypothetical split: auth for users and tokens, finance for ledger and periods, documents for uploads, AI workers for extraction, reporting for analytics, notifications for email.

**In our project:**  
A hypothetical split: auth for users and tokens, finance for ledger and periods, documents for uploads, AI workers for extraction, reporting for analytics, notifications for email. Hard parts are period close spanning docs bank and ledger, tenant consistency, and auth propagation. True split needs clear data ownership, not one shared schema with cross-service joins. Replace in-process DocumentUploadedEvent with messaging if AI scales out. Do not claim we already run that architecture.

**Hard parts:** period close spanning docs+bank+ledger; tenant consistency; auth propagation; avoiding distributed monolith.

---

### Q280. “Why did you use microservices?” trap question

**General explanation:**  
We did not use microservices. That keeps transactions simple for period close and tenant checks. If asked why not microservices, cite team size, consistency, and ops cost. Offer when you would extract AI or reporting later. Never invent a service mesh that is not in the repo.

**In our project:**  
We intentionally built a modular monolith with Gradle modules and facades, one bootJar, and one Postgres.

---

### Q281. How do modules communicate today?

**General explanation:**  
Controllers and services call those APIs rather than reaching into another module’s internals. There is no inter-module Feign or REST mesh. Spring DI wires implementations inside one JVM. That preserves local transactions and simple debugging.

**In our project:**  
Modules communicate in-process through facades such as UserFacade, ReportingFacade, AiExtractionFacade, and LedgerQueryFacade. UserFacade and LedgerQueryFacade are example doors.

---

### Q282. Domain events inside the monolith?

**General explanation:**  
That is in-process messaging tied to the publisher’s commit, not Kafka. It avoids processing if the upload transaction rolls back. UI can show processing while extraction continues. For multi-instance scale later, Kafka could replace this path.

**In our project:**  
We use Spring application events such as DocumentUploadedEvent with @TransactionalEventListener(AFTER_COMMIT) and async handling for AI.

---

### Q283. Frontend vs backend?

**General explanation:**  
Frontend and backend separation is normal layered architecture, not backend microservices. Auth, finance, and AI remain inside one Spring Boot process. docker-compose may run frontend, backend, and Postgres side by side. Interview trap: do not call browser-plus-API microservices.

**In our project:**  
The Angular SPA is a separate container or host that calls this single REST API.

---

### Q284. Scaling a modular monolith?

**General explanation:**  
Shared Postgres remains the source of truth. We do not rely on sticky HTTP sessions. Vertically scale the database first; add read replicas for reporting if needed.

**In our project:**  
Scale a modular monolith by running multiple platform-app instances behind a load balancer with stateless JWT. Watch connection pools and Flyway locking during rolling deploys.

---

### Q285. Shared database smell in microservices?

**General explanation:**  
A shared database across supposed microservices creates coupling through schema and transactions. You get network complexity without independent evolution. True splits need clear data ownership and integration via APIs or events.

**In our project:**  
Our one Postgres for the monolith is appropriate today. If we extract AI later, give it owned queues or tables carefully rather than silent cross-joins.

---

### Q286. Explain architecture like a 2-year developer

**General explanation:**  
It is one Spring Boot app split into Gradle modules so auth, finance, AI, and reporting stay organized. That keeps transactions simple for period close and tenant checks. Facades define module doors without HTTP between modules. If AI or reporting needed separate scale later, we could extract those first.

**In our project:**  
We deploy one JAR from platform-app. Auth finance AI and reporting stay organized indoors.

---

# Part 16 — Service Communication
### Q287. Synchronous vs asynchronous communication?

**General explanation:**  
Asynchronous fires work and continues, such as @Async AI processing after DocumentUploadedEvent commits. Sync fits login and CRUD where the client needs definitive success or failure. Async fits slow extraction and notifications.

**In our project:**  
Synchronous communication waits for a response, typically HTTP REST from Angular to our API. Mixing both is intentional in the Finance AI platform. Document AI processing after upload is asynchronous (`@Async` after commit).

---

### Q288. REST as sync communication?

**General explanation:**  
Commands like create income or login need immediate success or error semantics. This is not fire-and-forget messaging. Keep REST for user-facing commands and reserve async for background work.

**In our project:**  
REST here is synchronous client-server communication: the Angular app waits for JSON responses from controllers. HTTP status codes and ProblemDetail bodies carry outcomes.

---

### Q289. WebClient / OpenFeign / RestTemplate?

**General explanation:**  
RestTemplate is the legacy synchronous client, WebClient is the reactive client, and OpenFeign is declarative HTTP for service-to-service calls. This project does not use them for inter-module communication because modules are in-process. AI integration uses the JDK HttpClient in places rather than Feign or WebClient. Mention resilience patterns when calling external providers. Do not invent an internal Feign mesh.

**In our project:**  
Not a primary custom pattern in this repo — say so honestly, then tie back to everyday Java/Spring usage in our services if the interviewer presses.

**This does not appear to be used for inter-service calls** (no microservices). AI integration uses `java.net.http.HttpClient` in places rather than Feign/WebClient.

**General knowledge:** RestTemplate legacy; WebClient reactive; Feign declarative.

---

### Q290. Timeout / retry / circuit breaker / fallback?

**General explanation:**  
Timeouts, retries, circuit breakers, and fallbacks are resilience patterns for remote calls. They matter when calling external OpenAI-like providers. Do not blindly retry non-idempotent money creates. Prefer fail-fast with clear errors for user-facing REST and bounded retries for idempotent reads.

**In our project:**  
Resilience4j does not appear to be a first-class dependency in this project — know the concepts for interviews.

**Resilience4j / circuit breaker:** This does not appear to be a first-class dependency in the project — know the concepts for interviews.

**When useful here?** Calling external OpenAI-like providers — timeouts and retries matter; don’t retry non-idempotent ops blindly.

---

### Q291. Eventual consistency?

**General explanation:**  
Eventual consistency means components converge over time rather than in one atomic commit across systems. The UI should tolerate processing states. Ledger creates remain strongly consistent inside @Transactional boundaries. If we introduced Kafka later, consumers would also face eventual consistency across services.

**In our project:**  
After upload commits, AI may still be processing and document status progresses asynchronously. UI should tolerate “processing” states.

---

### Q292. Why not make every operation asynchronous?

**General explanation:**  
Not every operation should be async because UX and error handling get harder. Login, create income, and period close should return definitive success or failure. Async fits slow side effects such as AI extraction and notifications after commit. Overusing queues for simple CRUD adds operational cost without benefit.

**In our project:**  
Our mix — sync REST plus async document AI — matches those rules.

---

### Q293. In-process events vs Kafka?

**General explanation:**  
In-process Spring events are simple and can participate with the publisher’s transaction via AFTER_COMMIT listeners.

**In our project:**  
In-process Spring events are simple and can participate with the publisher’s transaction via AFTER_COMMIT listeners. Kafka provides durable cross-service streaming, replay, and fan-out at scale — and is not used in this project. Our DocumentUploadedEvent path is in-process, not a broker. Prefer Kafka if AI becomes a separate scaled worker fleet. Until then in-process async is the truthful answer.

---

# Part 17 — Kafka / Messaging

> **Kafka is not used in this project**, but this is useful general backend interview knowledge.
### Q294. What is Kafka?

**General explanation:**  
Apache Kafka is a distributed event streaming platform. Producers write records to topics and consumers read them, often with durability and replay. It is common for microservice integration and high-throughput pipelines. Say that clearly, then explain concepts for interview depth.

**In our project:**  
Kafka is not used in the Finance AI Automation Platform.

---

### Q295. Producer / Consumer / Topic / Partition / Broker?

**General explanation:**  
Producer publishes records; consumer reads them. A topic is a categorized append-only log. Partitions divide a topic for parallelism and ordering within each partition. Brokers are Kafka servers that store and serve partitions.

**In our project:**  
These terms are general knowledge — not mapped to our current runtime because Kafka is absent.

---

### Q296. Consumer group / offset?

**General explanation:**  
A consumer group coordinates consumers so each partition is processed by one member for parallel scale. Offsets track the position within a partition for commits, resume, and replay. At-least-once delivery plus offset commits drives idempotent consumer design. Interviewers still expect these definitions.

**In our project:**  
Again, general Kafka knowledge only — our app uses Spring events instead.

---

### Q297. Ordering?

**General explanation:**  
Kafka guarantees ordering per partition, not globally across a topic’s partitions. Choose a partition key such as documentId when relative order matters for that entity. More partitions increase parallelism but split ordering domains. Mention it when contrasting Kafka with single-threaded async executors.

**In our project:**  
This is general knowledge; our in-process listener does not face partition ordering.

---

### Q298. Why Kafka? When not?

**General explanation:**  
Why Kafka: decouple producers and consumers, buffer load, replay events, and fan-out to many readers. Introduce Kafka if AI workers become separately deployed and multi-instance. Be honest that it is not in the repo now.

**In our project:**  
When not: simple CRUD apps, strong single-DB transactions, small teams — complexity not worth it, which matches our modular monolith today. We already async AI with Spring events after commit.

**Why:** decouple services, buffer load, replay events, fan-out. **When not:** simple CRUD apps, strong single-DB transactions, small team — complexity not worth it (our case today).

---

### Q299. REST vs Kafka?

**General explanation:**  
REST is synchronous request-response suited to user-facing commands and queries. Kafka is asynchronous fire-and-forget or streaming between services. We would consider Kafka for cross-service document events if AI were extracted. Do not replace interactive CRUD with Kafka topics.

**In our project:**  
Our Angular clients use REST against one API.

---

### Q300. Duplicate messages / idempotent consumer?

**General explanation:**  
At-least-once delivery can produce duplicates, so consumers must be idempotent. Techniques include dedupe keys, upserts, and storing processed event ids. For a hypothetical document.uploaded consumer, processing the same upload twice must not create duplicate extractions or side effects. State this as general messaging knowledge, not current Kafka usage.

**In our project:**  
Our current in-process path still benefits from careful status transitions.

---

### Q301. Retries / dead-letter queue?

**General explanation:**  
Consumers retry transient failures with backoff, then route poison messages to a dead-letter queue for investigation. DLQs protect the main topic from infinite retry storms. Combine with alerting on DLQ depth. Apply similar thinking to async AI failures in-process — status fields and admin visibility. Kafka-specific DLQ tooling is general knowledge for this project.

**In our project:**  
Services and controllers use standard JDK lists/collections for in-memory work; persistence stays in JPA entities and repositories.

---

### Q302. How would Kafka fit *if* we extracted AI?

**General explanation:**  
Challenges include idempotency, ordering per document, and tenant-aware payloads. Auth and schema contracts must travel with events.

**In our project:**  
If we extracted AI, a document-uploaded topic could feed AI worker consumers, then document-extracted events would return to finance. That replaces the in-process after-commit listener and @Async path for horizontal worker scale. Today we honestly still use Spring application events inside one JVM.

---

# Part 18 — Redis / Caching

> **Redis / Spring Cache annotations do not appear to be used in this project.** Keep this section shorter.
### Q303. What is caching? Why cache?

**General explanation:**  
Caching stores expensive or frequent read results closer to the app for lower latency and DB load. Benefits are speed and reduced Postgres pressure. Risks are stale data and invalidation bugs. Redis or Spring Cache are common tools — not used as a first-class pattern here. Prefer caching reference data before ledger balances.

**In our project:**  
Not a primary custom pattern in this repo — say so honestly, then tie back to everyday Java/Spring usage in our services if the interviewer presses.

---

### Q304. Redis?

**General explanation:**  
Redis is an in-memory data store commonly used for caching, rate limiting, and session storage. It is not used in this project. We authenticate with JWT and persist refresh tokens via our session storage in the database path, not Redis. Mention Redis as a future option for hot reference reads if needed. Keep answers honest about current architecture.

**In our project:**  
It is not part of our Finance AI stack today. Do not claim Redis sessions when we use JWT.

---

### Q305. `@Cacheable` / `@CacheEvict`?

**General explanation:**  
@Cacheable and @CacheEvict are Spring Cache annotations for declarative caching and eviction. If added later, pair writes with eviction and include firmId in keys for multi-tenant safety. Avoid caching mutable ledger aggregates without strict invalidation. Prefer explicit cache services over surprise annotations on money paths.

**In our project:**  
They are general knowledge and do not appear to be used in this project.

---

### Q306. TTL / invalidation / stale cache?

**General explanation:**  
TTL expires cache entries automatically after a duration. Invalidation removes entries on updates so readers see fresh data. Stale cache serves outdated values — especially dangerous for balances, quotas, and authorization. Multi-tenant keys must include firmId to avoid leakage.

**In our project:**  
Since we do not run Redis caching now, discuss this as design caution for a future addition.

---

### Q307. Redis vs database?

**General explanation:**  
A cache miss should fall back to the database safely. Do not persist critical ledger state only in Redis. Our architecture already treats Postgres as authoritative without a cache tier. Interview answer: cache accelerates reads; DB guarantees durability and consistency.

**In our project:**  
Redis is a fast ephemeral layer; PostgreSQL is the source of truth for our platform.

---

### Q308. Why not cache everything?

**General explanation:**  
Do not cache everything because of memory cost, invalidation complexity, and multi-tenant leakage if keys omit firmId. Ledger and permission data are high-risk for stale reads. What we might cache later: subscription plan catalog, currency lists, and rarely changing reference data with firm-aware keys where needed. Redis and Spring Cache are not in the project today. Correctness beats micro-optimizations for finance APIs.

**In our project:**  
**What might we cache later?** Subscription plan catalog, currency lists, rarely changing reference data — with firm-aware keys where needed.

---

# Part 19 — Design Patterns + SOLID
### Q309. Singleton (Spring)?

**General explanation:**  
Singleton is the default Spring bean scope — one instance per container. They must not store per-request tenant state in instance fields.

**In our project:**  
Prototype or request scopes exist but are uncommon for our services. Our services such as PeriodCloseService and AuthenticationService are singletons. TenantContext and SecurityContext use ThreadLocal cleared per request instead. TenantContext stays on ThreadLocal, not on the singleton’s fields.

---

### Q310. Factory?

**General explanation:**  
The Factory pattern creates objects without exposing concrete classes to callers. That aligns with Dependency Inversion and easier testing. Spring @Bean methods often act as lightweight factories.

**In our project:**  
Callers depend on the FileStorageService abstraction. `StorageConfig.fileStorageService(...)` chooses local vs S3 implementation — factory-like `@Bean` method.

---

### Q311. Builder?

**General explanation:**  
Builder provides fluent construction of complex objects. It keeps required contextual fields explicit at call sites. Prefer builders when many optional fields would create telescoping constructors. Records cover simpler DTO cases without builders.

**In our project:**  
We use Lombok @Builder on entities and fluent AuditEvent construction from tenant context. Lombok `@Builder` on entities; `AuditEvent` builder style (`AuditEvent.fromTenant()...build()`).

---

### Q312. Strategy?

**General explanation:**  
Strategy defines interchangeable algorithms behind an interface. Each check is a @Component focused on one readiness rule. Adding a new close rule extends behavior without editing a giant if/else.

**In our project:**  
`CloseCheck` + implementations; `CloseReadinessService` loops `List<CloseCheck>` and evaluates each. This is Open/Closed in practice inside module-finance. Adding a new close rule = new `@Component` implementing `CloseCheck` (Open/Closed).

---

### Q313. Repository pattern?

**General explanation:**  
Repository abstracts persistence so domain services depend on collection-like interfaces. Custom queries and specifications extend that abstraction when needed. Reporting may use EntityManager for native SQL while CRUD stays repository-based. It keeps SQL details out of controllers.

**In our project:**  
`*JpaRepository` interfaces.

---

### Q314. Adapter?

**General explanation:**  
Adapter converts one interface to another expected by a framework or client. SecurityUser wrapping domain User to implement UserDetails is the interview angle in this project. Adapters isolate third-party APIs from domain code.

**In our project:**  
File storage implementations similarly adapt S3 or local disk to FileStorageService. That keeps module-auth aligned with Spring Security without polluting entities.

**Interview angle:** Security `UserDetails` adapter via `SecurityUser` wrapping domain `User`.

---

### Q315. Observer / events?

**General explanation:**  
Observer or events: publishers emit events and listeners react without tight coupling. DocumentUploadedEvent plus DocumentUploadedListener implement that here with transactional after-commit handling. Upload code stays focused on persistence and HTTP response. AI processing subscribes asynchronously.

**In our project:**  
Prefer this over hard-wiring AiExtractionFacade calls deep inside upload transactions when side effects should wait for commit. `DocumentUploadedEvent` + `DocumentUploadedListener`.

---

### Q316. Facade?

**General explanation:**  
Facade provides a simplified API over a subsystem. Other modules depend on facades instead of reaching into internal services. That supports modular monolith discipline without HTTP. Keep facades thin and stable as contracts.

**In our project:**  
`UserFacade`, `ReportingFacade`, `AiExtractionFacade`, `LedgerQueryFacade` — module boundaries.

---

### Q317. Dependency Injection as a pattern?

**General explanation:**  
Dependency Injection is a pattern where collaborators are provided rather than constructed internally. That enables testing and modular replacement of storage or email.

**In our project:**  
We depend on abstractions like EmailService and repositories; the container wires concretes. Spring implements it via constructor injection across our services and filters. DI underpins Strategy lists such as List<CloseCheck> injection.

---

### Q318. SOLID — Single Responsibility?

**General explanation:**  
SOLID Single Responsibility: a class should have one reason to change. That separation shows up clearly in code reviews and interviews.

**In our project:**  
Controllers do not embed period-close rules — PeriodCloseService does. `JwtTokenProvider` (token crypto) vs `JwtService` (app API) vs `JwtAuthenticationFilter` (HTTP). CloseCheck classes each own one readiness rule.

---

### Q319. Open/Closed?

**General explanation:**  
Open/Closed: open for extension, closed for modification. Avoid editing sprawling if/else when a new rule appears. Strategy plus Spring component scanning makes OCP practical.

**In our project:**  
StorageConfig choosing FileStorageService implementations is another extension point. New CloseCheck @Component implementations extend period-close readiness without rewriting CloseReadinessService’s evaluation loop. Add a new CloseCheck without editing the readiness loop’s core.

---

### Q320. Liskov Substitution?

**General explanation:**  
Liskov Substitution: subtypes must be usable wherever the base type is expected without breaking callers. SecurityUser must behave as a valid UserDetails. Violations force instanceof checks and fragile branches. Design interfaces around real behavioral contracts, not accidental method piles.

**In our project:**  
Any FileStorageService implementation should honor save, load, and delete contracts consistently.

---

### Q321. Interface Segregation?

**General explanation:**  
Interface Segregation: prefer small focused interfaces over fat ones clients do not need. Facades should expose cohesive operations per module concern. Fat interfaces force dummy methods and unnecessary coupling. Spring injection stays cleaner with narrow contracts.

**In our project:**  
A focused EmailService send API beats kitchen-sink service interfaces.

---

### Q322. Dependency Inversion?

**General explanation:**  
Dependency Inversion: high-level modules depend on abstractions, not concretions. Spring DI supplies implementations. This enables testing and environment-specific beans. Combined with Strategy and Facade it shapes our modular monolith boundaries.

**In our project:**  
Services depend on EmailService, SubscriptionQuotaGuard, FileStorageService, and CloseCheck rather than SmtpEmailService or raw S3 clients.

---

### Q323. Why Strategy for close checks instead of one giant if/else?

**General explanation:**  
Close rules grow over time: bank presence, draft documents, pending AI, and more. That design is why we chose Strategy over procedural checks.

**In our project:**  
A giant if/else in PeriodCloseService becomes unreadable and hard to test. CloseReadinessService aggregates results into blockers for 422 responses. Strategy with CloseCheck keeps each rule isolated and independently testable. Many CloseCheck classes keep each rule isolated.

---

# Part 20 — Gradle (our build tool)

> This project uses **Gradle**, not Maven.
### Q324. What is Gradle? Why a build tool?

**General explanation:**  
Without a build tool, classpath and version management become painful. This repository uses Gradle, not Maven.

**In our project:**  
Gradle automates compilation, tests, dependency management, and packaging for the Finance AI Automation Platform. Multi-module settings wire platform-app and feature modules together. The Dockerfile runs ./gradlew :platform-app:bootJar to produce the executable JAR. Docker build calls Gradle to make the bootJar. Maven is the other common Java build tool — we use Gradle.

---

### Q325. Multi-project build?

**General explanation:**  
Gradle multi-project builds include modules via settings while the root build shares Java 17 toolchain and dependency management. Incremental builds can recompile only changed modules. The runtime remains one Spring Boot application. Multi-project structure enforces modular monolith boundaries at compile time.

**In our project:**  
Our modules include platform-app, platform-core, module-auth, module-finance, module-ai, and module-reporting.

---

### Q326. Dependency / transitive dependency?

**General explanation:**  
Direct dependencies are what you declare in build.gradle; transitive dependencies come along from those libraries. Spring Boot’s dependency management BOM aligns versions across the stack. Understanding the tree helps resolve conflicts and CVEs. Prefer implementation over leaking everything on compile classpaths.

**In our project:**  
Our modules declare starters and project dependencies accordingly.

---

### Q327. Plugins?

**General explanation:**  
Gradle plugins extend the build with conventions and tasks. We apply java, Spring Boot, and dependency-management plugins in root or subprojects. Plugins keep build logic consistent across modules. Know plugin application as a standard Gradle interview topic.

**In our project:**  
The Boot plugin provides bootJar for the executable fat JAR used in Docker. The Spring Boot plugin adds bootJar and bootRun.

---

### Q328. Common tasks?

**General explanation:**  
Locally developers run tests separately for feedback. Task graph awareness helps debug slow builds.

**In our project:**  
Common Gradle tasks include clean, compileJava, test, build, and bootJar or bootRun. In our Dockerfile we run ./gradlew :platform-app:bootJar --no-daemon -x test to produce the runtime artifact. Prefer module-scoped tasks like :platform-app:bootJar for clarity.

---

### Q329. Executable JAR?

**General explanation:**  
You run it with java -jar app.jar, which is our container ENTRYPOINT pattern. Layered jars can optimize Docker caching further if adopted. This packaging is why one deployable unit represents the whole modular monolith.

**In our project:**  
Spring Boot’s bootJar creates an executable fat JAR containing the application and dependencies. platform-app is the module that produces that artifact.

---

### Q330. Dependency configurations/scopes?

**General explanation:**  
Gradle configurations include implementation, api, compileOnly, runtimeOnly, and testImplementation. Lombok is typically compileOnly. api is used when a module’s types must be visible to dependents. Choosing the right configuration keeps compile classpaths tight and encapsulation stronger across Gradle modules.

**In our project:**  
Testcontainers and MockMvc support land on testImplementation.

---

### Q331. Why Gradle multi-module?

**General explanation:**  
Gradle multi-module enforces boundaries between auth, finance, AI, and reporting at compile time. It clarifies ownership and can speed incremental builds. Facades plus module dependencies prevent spaghetti imports across domains. That matches modular monolith goals better than a single flat source tree.

**In our project:**  
We still boot one Spring Boot application from platform-app.

---

### Q332. Maven vs Gradle (interview)?

**General explanation:**  
Maven and Gradle solve the same build problems with different models. As general knowledge, Maven’s lifecycle phases like clean install are still asked in interviews. Gradle offers a flexible task graph and strong multi-module support we rely on. Do not claim Maven if the repo is Gradle-based.

**In our project:**  
This repository uses Gradle multi-project builds and Boot’s bootJar.

---

# Part 21 — Docker / Deployment
### Q333. What is Docker? Why Docker?

**General explanation:**  
Docker packages applications and runtimes as images that run as containers for consistent environments. Compose brings Postgres and frontend alongside for local and prod-like runs. Containers isolate dependencies from the host OS. Interviewers expect why Docker — parity, packaging, and deployment repeatability.

**In our project:**  
Our backend image runs the Spring Boot JAR on Temurin JRE 17.

---

### Q334. Image vs container?

**General explanation:**  
A container is a running instance of that image with its own filesystem layer and process. Multiple containers can share one image tag. Understanding the distinction is foundational Docker interview knowledge.

**In our project:**  
An image is an immutable template built from a Dockerfile. We build a backend image and run containers via Compose or orchestrators.

---

### Q335. Explain our backend Dockerfile

**General explanation:**  
That separates build tools from runtime. It matches modular monolith packaging into one artifact.

**In our project:**  
Our backend Dockerfile is multi-stage: eclipse-temurin:17-jdk-alpine AS build copies modules and runs ./gradlew :platform-app:bootJar, then eclipse-temurin:17-jre-alpine copies the JAR as app.jar. We run as a non-root USER app, EXPOSE 8080, HEALTHCHECK against /api/v1/health/ready, and ENTRYPOINT java -jar. Gradle produces the bootJar in the build stage.

---

### Q336. FROM / COPY / RUN / ENTRYPOINT?

**General explanation:**  
FROM selects the base image for a stage. COPY brings source or artifacts into the image. ENTRYPOINT defines the main process, which for us launches the Spring Boot JAR. HEALTHCHECK, USER, and EXPOSE complement these core instructions.

**In our project:**  
Knowing them lets you explain our backend Dockerfile line by line. RUN executes build or setup commands such as Gradle bootJar.

---

### Q337. Environment variables / ports / volumes?

**General explanation:**  
Containers receive configuration via environment variables for datasource and JWT secrets, especially under the prod profile. Port 8080 is exposed for the API. Upload storage can use a prepared path or volume such as /data/uploads. Never bake production secrets into the image layers. Compose and orchestrators inject env and mounts at runtime.

**In our project:**  
Not a primary custom pattern in this repo — say so honestly, then tie back to everyday Java/Spring usage in our services if the interviewer presses.

---

### Q338. Docker Compose?

**General explanation:**  
Docker Compose orchestrates multi-container apps via docker-compose.yml. Compose wires networks, ports, and environment variables. It is the documented local and prod-like path for this repository. Prefer Compose for developer onboarding before introducing Kubernetes complexity.

**In our project:**  
Our stack runs PostgreSQL, the Spring Boot backend, and the Angular frontend together.

---

### Q339. Kubernetes?

**General explanation:**  
Kubernetes is a container orchestrator using pods, services, deployments, configmaps, and secrets. It does not appear as first-class manifests in the core app flow of this project — know basics for interviews. Compose is what the repository documents heavily for running the stack. Say that without inventing in-repo Helm charts that are not there.

**In our project:**  
In K8s you would deploy the bootJar image, configure env secrets, and probe /api/v1/health/ready.

**This does not appear to be defined as first-class K8s manifests in the core app flow** — know basics: pod, service, deployment, configmap/secret. Compose is what the repo documents heavily.

---

### Q340. Why multi-stage builds?

**General explanation:**  
Benefits are smaller images, fewer packaged secrets or sources, faster pulls, and reduced attack surface. Non-root USER and healthchecks complement the lean runtime. This is the standard production packaging story for the modular monolith.

**In our project:**  
Our backend Dockerfile follows exactly that pattern from temurin JDK alpine to JRE alpine. Multi-stage Docker builds use a build stage with JDK and Gradle, then a slim runtime stage with JRE and the bootJar only.

---

### Q341. Healthcheck purpose?

**General explanation:**  
Healthchecks probe readiness so orchestrators do not route traffic until the app can serve safely. In our stack the ready endpoint covers DB connectivity, not just process up. Compose and cloud platforms mark the container unhealthy until /api/v1/health/ready succeeds. Interview tip: separate liveness (process alive) from readiness (dependencies OK).

**In our project:**  
That pairs with Flyway startup and Hikari pool init.

---

### Q342. How would you deploy?

**General explanation:**  
Build a Boot JAR, multi-stage Docker image, then deploy with Compose or a platform alongside Postgres. Set prod profile and secrets for DB URL, JWT, and storage. Document rollback as previous image plus migration discipline.

**In our project:**  
Flyway runs on startup; verify /api/v1/health/ready before smoke tests. Smoke login plus one income or expense API. Flyway migrates when the app boots.

---

# Part 22 — Production / Scenario Questions
### Q343. One API becomes very slow. What do you do?

**General explanation:**  
Start with logs and latency around that endpoint, then split controller versus service versus DB time. Inspect SQL for N+1 and indexes on client_id plus transaction_date. Also watch Hikari exhaustion, GC, and thread pools. Tie the diagnosis to a concrete endpoint so the fix is credible.

**In our project:**  
For P&L check ReportingQueryRepository ranges; for documents check AI HTTP latency.

---

### Q344. Duplicate records when user double-clicks Create?

**General explanation:**  
Double submit creates duplicate rows unless the API is idempotent. Mitigate with UX disable, unique constraints, and transactional duplicate checks. Our document path uses checksums and can raise DuplicateDocumentException. Idempotency keys are ideal for create endpoints under retries. Say which layer you own in the interview: UI, DB constraint, or application check.

**In our project:**  
We are on Java 17; DTOs are mostly Lombok classes today. You can mention records as a possible cleanup without claiming we already use them everywhere.

---

### Q345. Two users update the same income?

**General explanation:**  
Concurrent updates risk lost updates if both read then write without coordination. Optimistic @Version or re-read-and-validate status still DRAFT are common mitigations. Pessimistic locks fit rarer critical rows. Last-write-wins is acceptable only if product consciously chooses it.

**In our project:**  
Relate to IncomeService update paths and status rules.

---

### Q346. Database is down?

**General explanation:**  
DataSource failures surface as 5xx and ready health fails so orchestrators stop routing. Containers may restart or wait for DB recovery. Restore from backup if data is lost; do not silently dual-write elsewhere. Transactional integrity means partial success is worse than clear failure.

**In our project:**  
Mention Hikari connection errors and Flyway unable to migrate on boot.

---

### Q347. External AI service unavailable?

**General explanation:**  
If the external AI is down, log failure and leave processing state retryable. Timeouts and circuit ideas matter so HTTP stays snappy. retryProcessing-style endpoints recover without re-upload. Emphasize never blocking the upload transaction on remote AI.

**In our project:**  
Upload persists the file and Receipt; AI runs after commit via @Async listeners.

---

### Q348. Works locally, fails in production?

**General explanation:**  
Compare the active Spring profile, env vars for DB and JWT, CORS origins, and S3 versus local storage. Network, secrets managers, and TLS differ from a local YAML setup. Reproduce with prod-like Compose before blaming code. Systematic env diff beats random redeploys.

**In our project:**  
Flyway checksum or pending migrations can block boot. Flyway state and firewalls differ from laptop.

---

### Q349. High CPU?

**General explanation:**  
Profile CPU with sampling; look for tight loops, expensive reports, and heavy serialization. GC thrash from allocation can look like CPU pain. Cap retries and pagination on large ranges. Tie findings to metrics before scaling hardware blindly.

**In our project:**  
Document AI workers or native report SQL may dominate.

---

### Q350. Memory leak symptoms?

**General explanation:**  
Symptoms are rising heap, frequent full GC, then OOM. Unbounded caches, static lists, and large multipart buffers are common causes. Heap dumps confirm retained graphs. Interviewers like hearing ThreadLocal discipline in multi-tenant filters.

**In our project:**  
Check ThreadLocal leaks — JwtAuthenticationFilter clears TenantContext in finally. We clear TenantContext in a finally block on purpose.

---

### Q351. DB connection exhaustion?

**General explanation:**  
Hikari max reached means waits or SQLTransientConnectionException. Causes include long transactions, open-in-view style holds, and traffic spikes. Keep TX short; map DTOs inside the service TX then release. Tune pool and Postgres max_connections together.

**In our project:**  
Open-in-view false in our project reduces accidental connection holds.

---

### Q352. `LazyInitializationException` in prod?

**General explanation:**  
Lazy access outside the persistence context throws LazyInitializationException. Fix by DTO mapping inside @Transactional services, entity graphs, or fetch joins. Returning entities to controllers with open-in-view false is a classic trap. That keeps connection lifetime predictable.

**In our project:**  
Our preference is explicit fetch plus DTOs.

---

### Q353. N+1 in prod?

**General explanation:**  
N+1 appears as one parent SELECT then N child SELECTs. Fix with join fetch, @EntityGraph, batch fetching, or projection queries. Specs-based lists with lazy categories are common offenders. Logging SQL in staging proves the pattern. Prefer DTO queries for read-heavy list APIs.

**In our project:**  
Not a primary custom pattern in this repo — say so honestly, then tie back to everyday Java/Spring usage in our services if the interviewer presses.

---

### Q354. Sudden 401s?

**General explanation:**  
Causes include expiry, secret rotation mismatch, malformed header, or user soft-deleted. firmId claim mismatches can also fail downstream auth. Short access TTL makes expiry more visible after deploys. Correlate with auth failure audit logs.

**In our project:**  
401 usually means JwtAuthenticationFilter rejected the Bearer token.

---

### Q355. Sudden 403s?

**General explanation:**  
403 is authorization failure after authentication. Auditors are often read-only by design. Firm-scoped client access removal shows as 403 on finance APIs. Distinguish 401 versus 403 clearly in interviews.

**In our project:**  
RoleCode changes, @PreAuthorize on approve/write, and ClientAccessService denials are primary causes.

---

### Q356. Invalid JWT / token reuse after logout?

**General explanation:**  
Stateless access JWTs remain valid until expiry unless you add a denylist. Clients should discard access and refresh tokens immediately. Short access TTL bounds residual risk. Password reset paths also revoke refresh tokens.

**In our project:**  
Logout revokes stored refresh sessions via SessionService so POST refresh fails.

---

### Q357. Transaction rollback surprises?

**General explanation:**  
Unchecked exceptions roll back the default Spring TX; checked may not unless configured. REQUIRES_NEW nested work can commit independently. Walk the call stack when QA sees partial expectations.

**In our project:**  
Self-invocation bypasses the proxy so @Transactional may not apply. Document AI after-commit listeners only run if the TX commits.

---

### Q358. Concurrent subscription quota exceeded incorrectly?

**General explanation:**  
Seat and client limits race without transactional locking or constraints. Prefer DB-enforced uniqueness or selective locks for hard limits. Soft checks alone are insufficient under parallel requests. Cite SubscriptionQuotaConcurrencyIntegrationTest themes.

**In our project:**  
SubscriptionAccessService asserts quotas inside the write path; concurrency tests cover double-create.

---

### Q359. File upload security incident?

**General explanation:**  
Path traversal and executable content are classic risks. Checksums help integrity and duplicate detection. FileUploadSecurityIntegrationTest themes guide regression coverage.

**In our project:**  
Harden uploads with content-type and size limits, authz via ClientAccessService, and safe storage keys. Quotas via SubscriptionAccessService also gate abuse.

---

### Q360. Tenant data leak fear?

**General explanation:**  
Multi-tenant safety depends on firmId from TenantContextHolder set by the JWT filter, not request body firm ids. Repositories and services must filter by firm and client access. TenantIsolationIntegrationTest guards regressions. Soft deletes and reporting SQL need the same discipline. This is a top interview security question for our SaaS.

**In our project:**  
Always scope by firmId from TenantContext.

---

### Q361. Period close fails with 422?

**General explanation:**  
Blockers surface as BusinessException with properties and HTTP 422. Fix draft ledger items, bank mismatches, or pending docs per checklist. Then retry the close in one transactional mark CLOSED. Interviewers want the readiness-before-close story.

**In our project:**  
PeriodCloseService.close runs CloseReadinessService with CloseCheck strategies. CloseCheck strategies report what failed.

---

### Q362. How do you debug with logs?

**General explanation:**  
Enable SQL logging cautiously in non-prod. Audit events explain approve, close, and auth failures. Avoid logging secrets, refresh tokens, or full card-like payloads. Correlation ids help across async AI listeners.

**In our project:**  
Use structured logs with request time, userId, firmId, and GlobalExceptionHandler errorCode.

---

# Part 23 — Coding Questions (Java)

Use domain types where it helps (`User`, `Expense`, `Client`). Show complexity.
### Q363. Reverse a string

**General explanation:**  
Approach: convert to a mutable buffer and reverse, or swap chars with two pointers. StringBuilder.reverse is idiomatic and O(n) time with O(n) space for the new string. Mention immutability of String in Java. Edge cases: null and empty. Complexity O(n) time, O(n) space for the result.

**In our project:**  
Not a primary custom pattern in this repo — say so honestly, then tie back to everyday Java/Spring usage in our services if the interviewer presses.

---

### Q364. Palindrome

**General explanation:**  
Two-pointer scan from start and end; compare chars and move inward. Early return on mismatch. Handle empty and length-one as true. Optionally normalize case or strip non-alphanumerics if required. Time O(n), space O(1) for the scan.

**In our project:**  
Not a primary custom pattern in this repo — say so honestly, then tie back to everyday Java/Spring usage in our services if the interviewer presses.

---

### Q365. Character frequency

**General explanation:**  
Use a HashMap or stream groupingBy with counting. Mapping each character then Collectors counting is clean. Discuss Unicode and case folding if asked. Time O(n), space O(k) distinct chars.

**In our project:**  
Relate to checksum-like thinking without claiming crypto.

---

### Q366. Word frequency

**General explanation:**  
Normalize case, split on non-word characters, filter blanks, then groupingBy counting. Discuss locale and punctuation rules briefly. Time O(n) over characters. Space O(distinct words).

**In our project:**  
Good bridge to domain tallies like expense counts by category.

---

### Q367. Find duplicates in a list

**General explanation:**  
Single pass with HashSet seen; if seen.add returns false, add to duplicates set. Time O(n), space O(n). Mention sorting plus adjacent compare as an alternative. Clear interview sketch beats clever one-liners.

**In our project:**  
Prefer UUID examples matching our Client ids.

---

### Q368. Remove duplicates

**General explanation:**  
A stream distinct then toList keeps first occurrence order for sequential streams. LinkedHashSet is another stable option. Time O(n), space O(n). Clarify whether order matters before coding. Domain example: unique emails in an invite batch.

**In our project:**  
Not a primary custom pattern in this repo — say so honestly, then tie back to everyday Java/Spring usage in our services if the interviewer presses.

---

### Q369. Second-highest number

**General explanation:**  
One-pass track max and second with careful updates when n > max or between. Distinct second-highest needs skipping equals. Streaming sort is clearer but O(n log n). State edge cases: length less than two. Prefer O(n) loop for whiteboard clarity.

**In our project:**  
Not a primary custom pattern in this repo — say so honestly, then tie back to everyday Java/Spring usage in our services if the interviewer presses.

---

### Q370. Missing number in 1..n

**General explanation:**  
Sum approach: expected - actual in O(n) time O(1) space. Mention overflow and long cast for large n. XOR of indices and values also works. Clarify array contains distinct 1..n with one missing. Good warm-up before domain aggregations.

**In our project:**  
Not a primary custom pattern in this repo — say so honestly, then tie back to everyday Java/Spring usage in our services if the interviewer presses.

---

### Q371. Fibonacci

**General explanation:**  
Iterative DP with two variables a,b is the expected answer. Naive recursion is exponential — call that out. Memoized recursion is O(n) time with O(n) space. Discuss int overflow and use long. Interviewers want iterative by default.

**In our project:**  
Not a primary custom pattern in this repo — say so honestly, then tie back to everyday Java/Spring usage in our services if the interviewer presses.

---

### Q372. Prime check

**General explanation:**  
Trial division to i*i <= n; return false on any divisor. Handle n < 2. Optional 6k±1 optimizations if pressed for speed. Time O(sqrt n). Do not claim cryptographic primality for this sketch.

**In our project:**  
Not a primary custom pattern in this repo — say so honestly, then tie back to everyday Java/Spring usage in our services if the interviewer presses.

---

### Q373. Factorial

**General explanation:**  
Iterative product from 2..n into long. Mention overflow and BigInteger if n is large. Recursive factorial is O(n) stack depth. Time O(n), space O(1) iterative. Keep the sketch short and correct.

**In our project:**  
Not a primary custom pattern in this repo — say so honestly, then tie back to everyday Java/Spring usage in our services if the interviewer presses.

---

### Q374. Two Sum

**General explanation:**  
HashMap from value to index; for each i check target - nums[i]. Return indices when found, else store nums[i]. Time O(n), space O(n). Clarify distinct indices and duplicate values. Classic interview pattern transferable to matching bank amounts.

**In our project:**  
Not a primary custom pattern in this repo — say so honestly, then tie back to everyday Java/Spring usage in our services if the interviewer presses.

---

### Q375. Anagram

**General explanation:**  
Length check then int[26] counts or sorting both strings. Counting is O(n); sorting is O(n log n). State alphabet assumptions. Unicode needs a map instead of 26 slots. Clean early exits impress interviewers.

**In our project:**  
Not a primary custom pattern in this repo — say so honestly, then tie back to everyday Java/Spring usage in our services if the interviewer presses.

---

### Q376. First non-repeated character

**General explanation:**  
Frequency map with LinkedHashMap then find first count == 1. Stream groupingBy with LinkedHashMap supplier preserves order. Time O(n), space O(k). Clarify case sensitivity. Optional Character return type matches Java interviews.

**In our project:**  
Not a primary custom pattern in this repo — say so honestly, then tie back to everyday Java/Spring usage in our services if the interviewer presses.

---

### Q377. Sort expenses by amount (domain)

**General explanation:**  
Mention BigDecimal natural order. Stable sort preserves equal-amount order. Time O(n log n). Note lazy category fields are irrelevant for amount-only sort.

**In our project:**  
Sort expenses with a reversed amount comparator and collect to a list.

---

### Q378. Group expenses by category id

**General explanation:**  
Call out lazy Category access inside a TX or use a DTO query. Time O(n). Alternative: group by category name if already fetched. Tie to open-in-view false discipline.

**In our project:**  
GroupingBy category id builds a map from UUID to expense lists.

---

### Q379. Highest expense amount

**General explanation:**  
Discuss empty list and null amounts. Time O(n), space O(1). Prefer BigDecimal over double for money. Matches finance domain interviews well.

**In our project:**  
Map each expense to amount then take max with natural order, returning Optional of BigDecimal. Highest expense amount means scan and keep the max BigDecimal.

---

### Q380. Second-highest salary → second-highest expense

**General explanation:**  
Stream amounts distinct, reverse sort, skip(1), findFirst. Clarify distinct versus allowing ties. Time O(n log n) with sort; one-pass tracking is O(n). Empty Optional when fewer than two distinct values. Domain rename from salary questions.

**In our project:**  
Second-highest expense amount is the next distinct podium place.

---

### Q381. Highest amount by category

**General explanation:**  
groupingBy categoryId with Collectors.maxBy comparing amount. Mention LazyInitializationException if category unloaded. Time O(n). Interview sketch stays in-memory streams.

**In our project:**  
Could project in SQL via ReportingQueryRepository for large datasets. Per category, find the expense with the biggest amount.

---

### Q382. Convert List\<Client\> to Map\<UUID, Client\>

**General explanation:**  
Collect clients to a map keyed by Client id. Discuss IllegalStateException on duplicate keys and merge functions. Time O(n). Common prelude to enriching DTOs without N+1.

**In our project:**  
Null ids are invalid for our entities.

---

# Part 24 — Project-Specific Interview Questions
### Q383. Explain the application.

**General explanation:**  
Firms manage clients, documents, income/expenses, bank reconciliation, period close, reporting, subscriptions, and AI document extraction. Roles include ADMIN, ACCOUNTANT, AUDITOR, and BUSINESS_OWNER. Emphasize modular monolith, not microservices.

**In our project:**  
Finance AI Automation Platform is a multi-tenant accounting-practice SaaS backend. Architecture is a modular Spring Boot monolith with JWT auth, PostgreSQL, and Flyway.

---

### Q384. Explain the architecture.

**General explanation:**  
Communication is in-process facades and Spring events, not network RPC. JWT filter sets tenant context per request. That story beats claiming microservices incorrectly.

**In our project:**  
Gradle modules compile into one platform-app Boot JAR. Controllers and security wiring live in the app module; domain services sit in feature modules; shared types and TenantContext live in platform-core. Controllers sit in platform-app; features live in auth, finance, AI, and reporting modules.

---

### Q385. What are the modules?

**General explanation:**  
Auth covers login and sessions; finance covers ledger, documents, bank, close; AI covers extraction; reporting covers aggregates. Knowing names shows project fluency. Optional follow-up: package boundaries and dependency direction.

**In our project:**  
The modules are platform-app, platform-core, module-auth, module-finance, module-ai, and module-reporting. platform-app wires HTTP, security, and Boot entrypoint.

---

### Q386. What does `LoginController` do?

**General explanation:**  
Thin controllers keep security orchestration testable in services. Mention rate limiting on login if asked.

**In our project:**  
LoginController maps /api/v1/auth for login, refresh, logout, forgot and reset password. It validates DTOs and delegates to AuthenticationService and SessionService. GlobalExceptionHandler still formats auth failures. LoginController exposes auth URLs under /api/v1/auth. It stays thin and calls AuthenticationService or SessionService.

---

### Q387. Walk through login end-to-end.

**General explanation:**  
Soft-deleted users cannot authenticate. Refresh rotation and logout complete the session story. Cite Q252-level detail without overlong code.

**In our project:**  
Flow: @Valid LoginRequest → rate limit → AuthenticationManager with BCrypt → JwtService access token → SessionService stores hashed refresh → LoginResponse. JwtAuthenticationFilter is not involved on login itself.

---

### Q388. Walk through create income.

**General explanation:**  
open-in-view false means mapping happens in the service TX. Strong end-to-end modular monolith answer.

**In our project:**  
POST /api/v1/clients/{clientId}/income → JWT filter TenantContext → @PreAuthorize → @Valid IncomeRequest → IncomeService client access, period open, SubscriptionAccessService write assert → save DRAFT → IncomeResponse 201. Errors become ProblemDetail via GlobalExceptionHandler. Create income: JWT sets tenant, roles pass @PreAuthorize, form validates, then IncomeService checks access, open period, and subscription write. Firm scope comes from TenantContext.

---

### Q389. Walk through document upload.

**General explanation:**  
Upload TX commits before AI work. Failures leave retryable processing state. Emphasize after-commit async as a design highlight.

**In our project:**  
Multipart hits DocumentController → ClientAccessService and SubscriptionAccessService → FileStorageService store → persist Receipt with checksum duplicate detection → publish DocumentUploadedEvent → after-commit @Async AI listener.

---

### Q390. Walk through period close.

**General explanation:**  
Blockers → BusinessException 422 with properties. Success → mark CLOSED transactionally, audit, notify. Strategy pattern keeps checks extensible. This is a flagship domain walkthrough for interviews.

**In our project:**  
PeriodCloseService.close verifies access and open status, then CloseReadinessService runs all CloseCheck strategies. Period close runs checklist robots called CloseCheck strategies.

---

### Q391. Why does `AuthenticationService` exist?

**General explanation:**  
That keeps security policy in one place. Interviewers like clear service boundaries.

**In our project:**  
Pairs with SessionService for refresh rotation and revocation. AuthenticationService centralizes login orchestration: rate limiting, AuthenticationManager, JWT issuance, refresh session creation, and success/failure audit. LoginController only maps HTTP. AuthenticationService exists so login rules do not live in the controller.

---

### Q392. Why `ClientAccessService`?

**General explanation:**  
Role differences surface here and in @PreAuthorize. Citing it shows you understand authorization beyond JWT presence.

**In our project:**  
ClientAccessService centralizes tenant/client authorization for read, write, and approve. IncomeService, ExpenseService, DocumentService, and close flows reuse it. Combined with TenantContext firmId it enforces isolation.

---

### Q393. Why `Expense` ↔ `Receipt` many-to-many?

**General explanation:**  
Many-to-many via join tables such as expense_receipts models real supporting documents. Keeps document AI outputs reusable across ledger entries. Prefer explicit join entities if you need link metadata later.

**In our project:**  
One expense may attach multiple receipts; one receipt may support multiple lines. Schema is owned by Flyway migrations. Expense and Receipt are many-to-many because one bill can have several docs and one receipt can support several lines.

---

### Q394. Why DTOs like `IncomeRequest`?

**General explanation:**  
Manual mapping in services fits our codebase size. Stable APIs survive schema evolution.

**In our project:**  
IncomeResponse carries readable fields for Angular. DTOs like IncomeRequest decouple HTTP contracts from entities, host Bean Validation, and prevent lazy-graph leakage. Pair with GlobalExceptionHandler 400 on validation failure. IncomeRequest is a validated input backpack, not the JPA entity.

---

### Q395. Why JWT + refresh tokens?

**General explanation:**  
Short-lived JWT access enables horizontal scaling without server sessions. Password reset and logout revoke refresh sessions. This is our standard session design answer.

**In our project:**  
Refresh tokens hashed in DB via SessionService support rotation and revocation. BCrypt protects passwords separately from refresh hashing.

---

### Q396. Why is `PeriodCloseService.close` transactional?

**General explanation:**  
Readiness checks happen before mutating. Runtime failures roll back the close. After-commit listeners only fire if commit succeeds. Stress ACID language for period integrity.

**In our project:**  
PeriodCloseService.close uses @Transactional so CLOSED status, audit, and related side effects commit atomically.

---

### Q397. How is validation handled?

**General explanation:**  
Jakarta Bean Validation on DTOs with @Valid at controllers; domain rules in services and entity methods. MethodArgumentNotValidException → 400; BusinessException often 422. Clean split is a strong interview talking point.

**In our project:**  
Examples: IncomeRequest constraints, Expense.approve DRAFT-only, PeriodCloseService readiness, SubscriptionAccessService quotas. GlobalExceptionHandler maps both styles to HTTP.

---

### Q398. How are exceptions handled?

**General explanation:**  
Covers validation, business rules, auth failures, and not-found styles. Keeps API error contracts stable for the SPA. Avoid leaking stack traces to clients in prod. Central handling is preferred over per-controller catches.

**In our project:**  
GlobalExceptionHandler is @RestControllerAdvice mapping domain and MVC exceptions to ProblemDetail with errorCode. Exceptions bubble to GlobalExceptionHandler which builds consistent ProblemDetail JSON.

---

### Q399. How are DB changes managed?

**General explanation:**  
Deploy path: new image → boot → migrate → ready health. Team reviews SQL migrations like code. Saying validate not update shows production discipline.

**In our project:**  
Flyway owns schema evolution; Hibernate validate ensures entities match migrations. Testcontainers tests exercise real Postgres migrations. Database changes are Flyway numbered SQL scripts.

---

### Q400. How is the project tested?

**General explanation:**  
Notable themes include tenant isolation, subscription quota concurrency, file upload security, and period close readiness. Slice tests exist where useful; critical paths prefer full context. CI runs the suite against containerized Postgres. Mention concrete test class names when you remember them.

**In our project:**  
Stack: JUnit 5, @SpringBootTest, MockMvc, Testcontainers PostgreSQL. Testing is mostly full Spring Boot tests with MockMvc and real Postgres in Docker Testcontainers. Security, tenant isolation, lifecycle, concurrency, and Flyway get coverage.

---

### Q401. How is it built?

**General explanation:**  
Multi-module dependency graph keeps feature modules separate compile units. Same artifact runs locally or in Compose. Knowing the module task name shows build fluency.

**In our project:**  
Dockerfile multi-stage runs the Gradle build then copies the JAR. Gradle :platform-app:bootJar produces the executable Boot JAR. Build with Gradle multi-module; bootJar makes the runnable artifact. Modules compile as dependencies of platform-app.

---

### Q402. How would you deploy it?

**General explanation:**  
Ship multi-stage image, set SPRING_PROFILES_ACTIVE=prod and secrets, run Compose or orchestrator with Postgres. Healthcheck on /api/v1/health/ready gates traffic. Verify migrations applied and run smoke tests. Scale later with more stateless replicas sharing Postgres. Keep secrets out of images and git.

**In our project:**  
Flyway migrates on boot.

---

### Q403. How would you scale it?

**General explanation:**  
Async AI already offloads HTTP; dedicated workers help further under load. Avoid premature microservice split of ledger write paths. Cache only safe reference data with tenant awareness. Measure bottlenecks before splitting modules into services.

**In our project:**  
Horizontal scale platform-app replicas; tune Postgres and consider read replicas for heavy reports.

---

### Q404. What performance problem could occur?

**General explanation:**  
Mitigate with fetch strategies, indexes, pagination, short TXs, and async AI. APM plus SQL logs locate hotspots. Tie each risk to a module when answering.

**In our project:**  
Concrete risks: N+1 on expense/income lists, unindexed client_id/date filters, ReportingQueryRepository wide ranges, Hikari exhaustion, large multiparts, and AI queue depth.

---

### Q405. What would you improve?

**General explanation:**  
Prefer observability before architecture fashion. Shows mature judgment in interviews.

**In our project:**  
Improvements: deeper unit tests for CloseCheck and domain methods; metrics for TX timing and AI failures; careful reference-data caching; MapStruct only if DTO mapping volume hurts; extract AI workers if CPU-bound. Keep Flyway and tenant isolation non-negotiable.

---

### Q406. What is `TenantContext`?

**General explanation:**  
TenantContextHolder holds request-scoped firmId, userId, roles, and clientIds in ThreadLocal. open-in-view false does not replace tenant clearing. Top multi-tenant interview topic.

**In our project:**  
Services and ClientAccessService consume it for scoping. JwtAuthenticationFilter sets it after validating the access JWT and clears it in finally — critical against memory/tenant leaks. TenantContext is sticky notes for this request: firmId, user, role, client access.

---

### Q407. What is soft delete on users?

**General explanation:**  
Soft delete marks a user inactive with a timestamp instead of physically removing the row, so history and foreign keys stay intact. Auth and admin flows must consistently exclude soft-deleted accounts.

**In our project:**  
`User.deletedAt` is the soft-delete marker; authentication queries use `DeletedAtIsNull` so soft-deleted users cannot log in. ADMIN flows typically set `deletedAt`, and refresh sessions should be revoked when a user is disabled.

---

### Q408. What does bank reconciliation do?

**General explanation:**  
Authz still goes through client access rules. CSV parsing and amount/date heuristics are typical details. Strong domain differentiator versus generic CRUD apps.

**In our project:**  
BankReconciliationService imports statements, suggests matches against expenses/incomes, and records confirm/reject/ignore decisions. Supports CloseCheck bank readiness for period close.

---

### Q409. What does subscription gating do?

**General explanation:**  
Integrates with transactional create paths to reduce races. Complements RoleCode permissions with commercial limits. Cite concurrency test coverage when discussing races.

**In our project:**  
SubscriptionAccessService asserts plan limits before costly actions like client create, seat add, document upload, and ledger writes. Failures map through GlobalExceptionHandler as business errors.

---

### Q410. Why `open-in-view` concerns matter?

**General explanation:**  
Disabling open-in-view prevents lazy loads during JSON rendering and avoids holding connections through the MVC stack. Bugs surface as LazyInitializationException instead of silent N+1 in rendering. Fix with service-layer DTO mapping and fetch joins. Production-safe default for APIs. Interviewers often ask why false is preferred.

**In our project:**  
We prefer explicit fetches over hidden magic.

---

### Q411. Purpose of `ReportingQueryRepository`?

**General explanation:**  
Must enforce tenant filters in SQL. Complements JPA CRUD elsewhere in finance. Performance answer often points here for slow report endpoints.

**In our project:**  
ReportingQueryRepository provides native SQL aggregates for P&L, trends, and status metrics without hydrating heavy entity graphs. Keeps reporting efficient versus streaming all Expense entities.

---

### Q412. Purpose of `GlobalExceptionHandler`?

**General explanation:**  
Maps MethodArgumentNotValidException, BusinessException, access denials, and others to correct statuses. Critical for SPA error handling and support triage. Prevents leaking internal exception messages inappropriately. Core cross-cutting piece alongside security filters.

**In our project:**  
GlobalExceptionHandler as @RestControllerAdvice standardizes ProblemDetail responses with errorCode. GlobalExceptionHandler turns exceptions into consistent HTTP JSON for Angular.

---

### Q413. Why BCrypt in `AuthModuleConfig`?

**General explanation:**  
Stores only password hashes on User. Strength factor is a tunable security parameter. Do not replace with plain or weak MD5 in production stories. Pairs with hashed refresh tokens for defense in depth.

**In our project:**  
AuthModuleConfig exposes a BCrypt PasswordEncoder used by user provisioning, Dao authentication, and reset/change password flows.

---

### Q414. What roles exist?

**General explanation:**  
RoleCode values ADMIN, ACCOUNTANT, AUDITOR, BUSINESS_OWNER appear in JWT claims and @PreAuthorize expressions. Auditors typically cannot approve or mutate sensitive writes. BUSINESS_OWNER and ADMIN cover firm administration patterns. Naming roles precisely shows project familiarity.

**In our project:**  
ClientAccessService still applies per-client grants.

---

### Q415. How does refresh work?

**General explanation:**  
Revoked or expired sessions fail cleanly. Complements logout and password-reset revocation. Stateless API auth with revocable refresh is the headline design. Mention hashing so DB leaks are not directly reusable tokens.

**In our project:**  
POST /api/v1/auth/refresh → SessionService.refresh validates the presented token against hashed storage, rotates the refresh session, and issues a new access JWT plus refresh as designed.

---
# Part 25 — Project Explanation Scripts

### 30-second explanation

**General explanation:**  
This is a backend platform for accounting practices: multi-tenant firms manage clients, documents, income/expenses, bank reconciliation, period close, reporting, and AI document extraction in one modular Spring Boot application.

**In our project / spoken pitch:**

“It’s a Spring Boot backend for an accounting-practice SaaS. Firms manage clients, documents, income and expenses, bank reconciliation, period close, reporting, and AI document extraction. It’s a modular monolith with JWT auth and PostgreSQL plus Flyway, so we keep strong consistency for ledger workflows while still organizing code into clear modules.”

### 1-minute explanation

**General explanation:**  
A single Spring Boot process exposes versioned REST APIs, authenticates with JWT, applies business rules in services, persists with JPA to PostgreSQL, and returns JSON to a SPA. Integration tests use MockMvc plus containerized Postgres; delivery is a Boot JAR in Docker Compose.

**In our project / spoken pitch:**

“`FinancePlatformApplication` starts one Spring Boot app scanning `com.finance.platform`. Controllers under `/api/v1` call services like `IncomeService`, `DocumentService`, and `AuthenticationService`. Persistence is Spring Data JPA. `JwtAuthenticationFilter` validates Bearer tokens and sets `TenantContext` for firm isolation. Roles use `@PreAuthorize`. Errors go through `GlobalExceptionHandler`. Tests use MockMvc and Testcontainers PostgreSQL. We deploy a Boot JAR via Docker Compose with Postgres and an Angular frontend.”

### 2-minute explanation

**General explanation:**  
Feature code is split into Gradle modules (auth, finance, AI, reporting) inside one deployable monolith. Security uses BCrypt and hashed refresh tokens; documents use checksums; subscriptions enforce quotas; period close runs pluggable readiness checks; AI runs asynchronously after commit; schema is Flyway-managed. It is intentionally not a microservice mesh.

**In our project / spoken pitch:**

“Add: Gradle modules (`platform-core`, `module-auth`, `module-finance`, `module-ai`, `module-reporting`, `platform-app`); refresh tokens hashed in DB; BCrypt passwords; document checksum duplicates; subscription quotas; period close Strategy checks (`CloseCheck`); AI after-commit `@Async`; reporting via native SQL; Flyway V1–V24; multi-stage Dockerfile with healthcheck on `/api/v1/health/ready`. Emphasize it is **not** microservices — modules talk in-process through facades and Spring events.”

### Detailed technical explanation

**General explanation:**  
Explain layered architecture: security edge, REST API, transactional application services, domain entities, infrastructure (JPA, storage, email), cross-cutting concerns (tenant context, audit, Flyway), and async AI. Emphasize modular monolith for transactional consistency, with an option to extract AI/reporting later if scale requires it.

**In our project / spoken pitch:**

Speak through layers:

1. **Edge:** Tomcat + SecurityFilterChain + JWT filter  
2. **API:** 26 REST controllers, validation, method security  
3. **Application:** transactional services, access guards, workflows  
4. **Domain:** entities (`Expense`, `Income`, `Receipt`, `AccountingPeriod`, `User`…)  
5. **Infra:** JPA repos, native reporting, local/S3 storage, SMTP/log email  
6. **Cross-cutting:** audit log, notifications, tenant ThreadLocal, Flyway  
7. **Async:** document uploaded event → AI processor  
8. **Ops:** profiles, Docker, health endpoints, integration tests  

Close with trade-off: modular monolith chosen for transactional consistency and simpler ops; extract AI/reporting later if scale demands.

---

# Part 26 — Rapid-Fire Round: Top 100 Questions I MUST Know

Short answers for last-minute revision — one general line, one project line.

1. **What is Dependency Injection?**
   - *General:* Dependency Injection means Spring brings your class the helpers it needs instead of the class building them with new.
   - *In our project:* Spring creates and injects dependencies; AuthenticationService gets JwtService and repositories via constructor injection.

2. **What is IoC?**
   - *General:* Inversion of Control: the container builds and manages the object graph instead of manual wiring everywhere.
   - *In our project:* Spring wires `AuthenticationService`, `JwtService`, repositories, and other beans via constructor injection across modules.

3. **What is a Spring Bean?**
   - *General:* A Spring Bean is an object Spring creates, wires, and manages for you.
   - *In our project:* A bean is a container-managed object such as our services, controllers, and JWT filter.

4. **Why constructor injection?**
   - *General:* Constructor injection gives required tools at birth so they can stay final and tests stay easy.
   - *In our project:* We prefer constructor injection with final fields and @RequiredArgsConstructor as the project default.

5. **What is `@RestController`?**
   - *General:* @RestController means this class answers HTTP and returns JSON data, not an HTML page.
   - *In our project:* @RestController returns the response body as JSON — IncomeController and LoginController use it.

6. **`@Controller` vs `@RestController`?**
   - *General:* @Controller typically returns views; @RestController returns data/JSON for our Angular client.
   - *In our project:* @Controller is often for pages; @RestController is for JSON APIs.

7. **What is DispatcherServlet?**
   - *General:* DispatcherServlet is the Spring MVC front controller that dispatches to mapped handler methods.
   - *In our project:* Not a primary custom pattern in this repo — say so honestly, then tie back to everyday Java/Spring usage in our services if the interviewer presses.

8. **Request flow?**
   - *General:* A request usually goes JWT check → controller → service → database → JSON response.
   - *In our project:* Flow: JwtAuthenticationFilter → DispatcherServlet → Controller → Service → Repository → DB → DTO JSON.

9. **What is `@Transactional`?**
   - *General:* @Transactional wraps a service method in a DB transaction via a Spring AOP proxy.
   - *In our project:* Not a primary custom pattern in this repo — say so honestly, then tie back to everyday Java/Spring usage in our services if the interviewer presses.

10. **Self-invocation problem?**
   - *General:* Self-invocation skips the proxy, so @Transactional on the inner method is ignored.
   - *In our project:* Calling this.otherMethod() inside the same class skips Spring’s helper, so @Transactional may not run.

11. **Checked vs unchecked?**
   - *General:* Checked must be declared/handled; unchecked RuntimeException — BusinessException is unchecked so services stay clean.
   - *In our project:* Checked exceptions must be declared; unchecked RuntimeExceptions do not, and our business errors are unchecked.

12. **`@RestControllerAdvice`?**
   - *General:* A global handler turns thrown errors into clear HTTP JSON for the frontend.
   - *In our project:* @RestControllerAdvice GlobalExceptionHandler maps exceptions to consistent ProblemDetail responses.

13. **401 vs 403?**
   - *General:* 401 is unauthenticated; 403 is authenticated but unauthorized.
   - *In our project:* Not a primary custom pattern in this repo — say so honestly, then tie back to everyday Java/Spring usage in our services if the interviewer presses.

14. **JWT structure?**
   - *General:* JWT is header.payload.signature — the payload is not secret; trust comes from the signature.
   - *In our project:* JWT has three parts; the middle is readable, and the signature proves it was not forged.

15. **Login flow?**
   - *General:* Login checks email and password, then hands back a short access JWT and a refresh token.
   - *In our project:* LoginController → AuthenticationManager → UserDetailsService → BCrypt → JWT access token + refresh session.

16. **JWT filter?**
   - *General:* The JWT filter reads the Bearer badge each request and remembers who you are and which firm you belong to.
   - *In our project:* JwtAuthenticationFilter validates the Bearer token and sets SecurityContext plus TenantContext.

17. **Why STATELESS?**
   - *General:* STATELESS means no server session drawer; each request brings its own JWT.
   - *In our project:* We use SessionCreationPolicy.STATELESS — no HTTP session; JWT is presented per request.

18. **Why disable CSRF?**
   - *General:* CSRF is disabled because this is a stateless Bearer API, not cookie-session form posts.
   - *In our project:* CSRF mainly matters for cookie sessions; Bearer-token APIs usually turn it off.

19. **BCrypt?**
   - *General:* BCrypt PasswordEncoder in AuthModuleConfig hashes passwords one-way with salt.
   - *In our project:* BCrypt one-way hashes passwords so we store fingerprints, never plain passwords.

20. **What is JPA?**
   - *General:* JPA is the persistence API; Hibernate is the implementation Spring Boot uses here.
   - *In our project:* JPA is the rulebook for mapping objects to tables; Hibernate is the engine that follows it.

21. **What is an Entity?**
   - *General:* An entity is a Java class that means one database table row, like Expense.
   - *In our project:* An @Entity class maps to a table — Expense is a core ledger entity in our project.

22. **Lazy vs Eager?**
   - *General:* Lazy means load friends later when asked; Eager means bring them now — we prefer Lazy.
   - *In our project:* We prefer FetchType.LAZY on associations to avoid loading huge graphs on every query.

23. **N+1?**
   - *General:* N+1 is 1 + N queries; fix with join fetch, entity graphs, or DTO queries.
   - *In our project:* N+1 is one query for a list plus one extra query per row — slow; fix with smarter fetches.

24. **`LazyInitializationException`?**
   - *General:* Accessing a lazy association after the persistence context closes throws LazyInitializationException.
   - *In our project:* Not a primary custom pattern in this repo — say so honestly, then tie back to everyday Java/Spring usage in our services if the interviewer presses.

25. **DTO why?**
   - *General:* DTOs are backpacks for the API door so we do not expose full database objects and secrets.
   - *In our project:* DTOs keep the API stable and safe — IncomeRequest validates input without exposing entities.

26. **`@Valid`?**
   - *General:* @Valid triggers Bean Validation on the annotated request body or nested objects.
   - *In our project:* Not a primary custom pattern in this repo — say so honestly, then tie back to everyday Java/Spring usage in our services if the interviewer presses.

27. **`@NotNull` vs `@NotBlank`?**
   - *General:* @NotNull forbids null; @NotBlank forbids null/empty/whitespace strings.
   - *In our project:* Not a primary custom pattern in this repo — say so honestly, then tie back to everyday Java/Spring usage in our services if the interviewer presses.

28. **Optional?**
   - *General:* Optional makes empty explicit; services commonly orElseThrow ResourceNotFoundException for 404s.
   - *In our project:* Optional is a maybe-empty box; we often orElseThrow to turn missing rows into clear 404s.

29. **Stream map/filter?**
   - *General:* Stream map reshapes each item; filter keeps only items that pass a yes/no test.
   - *In our project:* map transforms elements and filter keeps matches — used in JWT filter and ClientService mapping.

30. **List vs Set?**
   - *General:* List is ordered and allows duplicates; Set is unique — receipts use Set on expenses/incomes.
   - *In our project:* List keeps order and allows copies; Set keeps unique items only.

31. **HashMap basics?**
   - *General:* hashCode selects a bucket; equals resolves collisions for HashMap keys.
   - *In our project:* HashMap uses hashCode to pick a locker and equals to settle who owns that locker.

32. **`==` vs equals?**
   - *General:* == compares references (or primitive values); equals compares logical equality when overridden.
   - *In our project:* == asks same box in memory; equals asks same meaning or contents.

33. **String immutability?**
   - *General:* String is immutable — modifications create new String objects.
   - *In our project:* Strings cannot change; editing one really creates a new string.

34. **`final` on fields?**
   - *General:* final injected fields cannot be reassigned after construction — our DI default.
   - *In our project:* Not a primary custom pattern in this repo — say so honestly, then tie back to everyday Java/Spring usage in our services if the interviewer presses.

35. **Enum?**
   - *General:* Enums are type-safe constants — RoleCode and TransactionStatus in our domain.
   - *In our project:* An enum is a fixed menu of allowed choices so typos like aproved cannot sneak in.

36. **Interface vs abstract class?**
   - *General:* An interface is a promise of skills; an abstract class is a half-finished parent with shared parts.
   - *In our project:* Interfaces define contracts like CloseCheck; abstract classes share base state like BaseEntity.

37. **Encapsulation?**
   - *General:* Encapsulation hides the messy insides and only opens the safe door methods.
   - *In our project:* Encapsulation keeps state private and exposes controlled methods — Expense.approve enforces rules.

38. **Polymorphism?**
   - *General:* Polymorphism means one command, many behaviors — each CloseCheck does its own evaluate.
   - *In our project:* Same interface, different runtime behavior — List<CloseCheck> in period-close readiness.

39. **Composition vs inheritance?**
   - *General:* Prefer composition via DI for collaborators; inheritance for is-a like TenantAwareEntity.
   - *In our project:* Composition is has-a tools; inheritance is is-a family — we use both carefully.

40. **Pass by value?**
   - *General:* Java is pass-by-value; object parameters receive a copied reference.
   - *In our project:* Java always passes copies; for objects the copy is the address sticker, not a full house clone.

41. **Heap vs stack?**
   - *General:* Objects live on the heap; method frames and local primitives/references live on the stack.
   - *In our project:* Heap is the big toy room for objects; stack is each method’s small desk of locals.

42. **Can Java leak memory?**
   - *General:* Logical leaks happen via static caches or uncleared ThreadLocals — we clear tenant context in finally.
   - *In our project:* Yes — if you keep pointing at junk forever, memory can leak even with garbage collection.

43. **ACID?**
   - *General:* ACID is Atomic, Consistent, Isolated, Durable — required for ledger and period-close updates.
   - *In our project:* Not a primary custom pattern in this repo — say so honestly, then tie back to everyday Java/Spring usage in our services if the interviewer presses.

44. **Flyway?**
   - *General:* Flyway versioned migrations are our schema source of truth (V1–V24 style history).
   - *In our project:* Flyway runs numbered SQL scripts in order so every environment gets the same schema story.

45. **`ddl-auto: validate`?**
   - *General:* spring.jpa.hibernate.ddl-auto=validate verifies entity mappings without auto-altering schema.
   - *In our project:* Not a primary custom pattern in this repo — say so honestly, then tie back to everyday Java/Spring usage in our services if the interviewer presses.

46. **PostgreSQL why?**
   - *General:* PostgreSQL gives relational ACID storage suitable for multi-tenant accounting data.
   - *In our project:* PostgreSQL is our careful table database for money data that needs relations and transactions.

47. **Index why?**
   - *General:* Indexes speed filtered reads such as client_id + transaction_date; excess indexes hurt writes.
   - *In our project:* Indexes make common finds faster, like a book index, but too many slow down writes.

48. **HikariCP?**
   - *General:* HikariCP is Spring Boot’s default JDBC connection pool.
   - *In our project:* HikariCP keeps a pool of ready database connections so each request is not opening a brand-new door.

49. **Monolith or microservices?**
   - *General:* Architecture is a modular monolith — one Spring Boot process and one deployable JAR.
   - *In our project:* We are a modular monolith: one deployable app with neat modules, not many network services.

50. **Why not microservices yet?**
   - *General:* We avoid microservices for now due to shared transactional boundaries, one DB, and lower ops cost.
   - *In our project:* Microservices can wait because shared money workflows need simple local transactions and easier ops.

51. **Facade?**
   - *General:* A facade is a simple front door into a module so others do not dig through every inside class.
   - *In our project:* UserFacade and ReportingFacade expose module APIs without leaking internals.

52. **Strategy?**
   - *General:* Strategy means swap checklist robots behind one interface — CloseCheck implementations.
   - *In our project:* Period close uses the Strategy pattern via multiple CloseCheck beans.

53. **`@Async` here?**
   - *General:* DocumentUploadedListener runs @Async after commit so AI does not block upload or see rolled-back data.
   - *In our project:* Not a primary custom pattern in this repo — say so honestly, then tie back to everyday Java/Spring usage in our services if the interviewer presses.

54. **Kafka used?**
   - *General:* Kafka is not used in this project; know it as general interview knowledge only.
   - *In our project:* Kafka is not a project dependency — answer generally if asked, and say we use in-process events today.

55. **Redis used?**
   - *General:* Redis/Spring Cache are not first-class in this project — general knowledge only.
   - *In our project:* Redis caching is not used here; know the idea for interviews, but do not claim it in this repo.

56. **MapStruct used?**
   - *General:* DTO mapping is manual — MapStruct does not appear in the current project.
   - *In our project:* MapStruct is not used; we map entities to DTOs by hand in toResponse/from methods.

57. **Mockito used?**
   - *General:* Primary tests are integration style; Mockito is interview knowledge more than suite focus.
   - *In our project:* Mockito is not the suite focus; we lean on full-app integration tests instead.

58. **Testcontainers?**
   - *General:* Integration tests use Testcontainers PostgreSQL for realistic schema, SQL, and locking behavior.
   - *In our project:* Testcontainers runs a real PostgreSQL in Docker during tests so results match production better.

59. **MockMvc?**
   - *General:* MockMvc simulates HTTP against controllers in Spring tests for login, CRUD, and tenant checks.
   - *In our project:* MockMvc pretends to call HTTP endpoints without opening a real network port.

60. **`@SpringBootApplication`?**
   - *General:* @SpringBootApplication is the big on-switch: scan packages, auto-configure, and start the app.
   - *In our project:* @SpringBootApplication enables configuration, component scan, and auto-config on FinancePlatformApplication.

61. **Startup story?**
   - *General:* SpringApplication.run builds the context, scans modules, auto-configures, migrates with Flyway, then starts Tomcat.
   - *In our project:* Startup story: run main → build context → scan beans → auto-config → Flyway → Tomcat ready.

62. **Profiles?**
   - *General:* Spring profiles switch local/prod/test configuration without code changes.
   - *In our project:* Profiles are outfits for environments like local, prod, and test with different settings.

63. **Actuator?**
   - *General:* Actuator is unused here; we expose custom health endpoints instead.
   - *In our project:* No Actuator dependency in focus — custom HealthController serves /api/v1/health and /ready.

64. **Gradle?**
   - *General:* Gradle is the build tool that compiles, tests, and packages this multi-module backend.
   - *In our project:* Gradle multi-module build wires platform-app and feature modules into one bootJar.

65. **bootJar?**
   - *General:* bootJar makes a runnable fat JAR with dependencies inside so java -jar can start the app.
   - *In our project:* bootJar produces the executable Spring Boot JAR we run in Docker.

66. **Docker multi-stage?**
   - *General:* Docker multi-stage means build with the full JDK toolkit, then run with a smaller JRE image.
   - *In our project:* Multi-stage Dockerfile: JDK stage builds; JRE stage runs the JAR for a smaller safer image.

67. **Healthcheck?**
   - *General:* Docker HEALTHCHECK hits /api/v1/health/ready to verify readiness.
   - *In our project:* Healthcheck pings /api/v1/health/ready so Docker knows the app is actually ready.

68. **Idempotency?**
   - *General:* Idempotency means repeat-safe behavior; POST create is often not idempotent without keys or unique rules.
   - *In our project:* Not a primary custom pattern in this repo — say so honestly, then tie back to everyday Java/Spring usage in our services if the interviewer presses.

69. **PUT vs PATCH?**
   - *General:* PUT is full replace semantics; PATCH is partial update — keep DTO shape honest.
   - *In our project:* PUT usually replaces the whole resource; PATCH changes only some fields.

70. **POST vs PUT?**
   - *General:* POST creates or triggers actions; PUT is idempotent replace at a known resource URI.
   - *In our project:* POST creates or starts an action; PUT replaces at a known URI and should be safer to retry.

71. **Pagination?**
   - *General:* APIs use page/size query params and return PageResponse wrappers.
   - *In our project:* Not a primary custom pattern in this repo — say so honestly, then tie back to everyday Java/Spring usage in our services if the interviewer presses.

72. **Many-to-many example?**
   - *General:* Expense↔Receipt is @ManyToMany via join table expense_receipts.
   - *In our project:* Expense and Receipt can link many-to-many through a join table so one expense can have many receipts.

73. **Owning side?**
   - *General:* Owning side has @JoinColumn or @JoinTable; mappedBy marks the inverse side.
   - *In our project:* The owning side holds the foreign key or join table that actually controls the relationship.

74. **Cascade ALL everywhere?**
   - *General:* Avoid CascadeType.ALL by default — cascades must match real lifecycle ownership.
   - *In our project:* Cascade ALL everywhere is dangerous because deletes can wipe more data than you meant.

75. **`REQUIRES_NEW`?**
   - *General:* Propagation REQUIRES_NEW suspends the outer TX and starts a fresh one — used for AI persistence style work.
   - *In our project:* REQUIRES_NEW starts a brand-new independent transaction that can commit on its own.

76. **Propagation REQUIRED?**
   - *General:* Propagation REQUIRED is the default: join current TX or create one.
   - *In our project:* REQUIRED joins an existing transaction or creates one — that is the default.

77. **Dirty checking?**
   - *General:* Hibernate dirty checking auto-detects field changes in the persistence context and issues UPDATEs on flush/commit.
   - *In our project:* Not a primary custom pattern in this repo — say so honestly, then tie back to everyday Java/Spring usage in our services if the interviewer presses.

78. **Persistence context?**
   - *General:* The persistence context is the first-level cache of managed entities for a transaction/session.
   - *In our project:* Persistence context is Hibernate’s short-term memory of managed objects for one unit of work.

79. **JPQL vs SQL?**
   - *General:* JPQL operates on the entity model; native SQL uses database tables and columns.
   - *In our project:* JPQL talks in entity and field names; SQL talks in table and column names.

80. **Native query where?**
   - *General:* ReportingQueryRepository uses native SQL aggregates for dashboards and status counts.
   - *In our project:* We use native SQL especially for reporting math that is clearer and faster in SQL.

81. **Soft delete?**
   - *General:* Users use deletedAt soft delete; auth queries ignore soft-deleted users.
   - *In our project:* Not a primary custom pattern in this repo — say so honestly, then tie back to everyday Java/Spring usage in our services if the interviewer presses.

82. **UUID PK?**
   - *General:* UUID primary keys give unique ids that work well in URLs and multi-tenant systems.
   - *In our project:* BaseEntity uses @GeneratedValue UUID strategy for primary keys.

83. **BigDecimal?**
   - *General:* Money fields use BigDecimal mapped to numeric SQL — never double for currency.
   - *In our project:* Use BigDecimal for money because double can lose cents with fuzzy math.

84. **Records?**
   - *General:* Records are short locked data packs — perfect for request and response DTOs.
   - *In our project:* Most DTOs are Java records such as IncomeRequest and LoginResponse.

85. **`@PreAuthorize`?**
   - *General:* @PreAuthorize enforces method security roles beyond merely being logged in.
   - *In our project:* @PreAuthorize is a method bouncer: your role must be allowed before the code runs.

86. **Tenant isolation?**
   - *General:* Tenant isolation means each firm only sees its own data through firmId and client access checks.
   - *In our project:* TenantContext plus firmId filters and client-access checks keep firms isolated.

87. **Refresh token storage?**
   - *General:* Refresh tokens are stored hashed in the database so a DB leak does not give usable tokens.
   - *In our project:* SessionService persists hashed refresh tokens and rotates/validates them on refresh.

88. **Rate limiting login?**
   - *General:* Login is rate-limited so attackers cannot try endless passwords quickly.
   - *In our project:* AuthenticationService applies login rate limiting before expensive credential checks.

89. **ProblemDetail?**
   - *General:* ProblemDetail is a standard-shaped error JSON so the frontend always knows how to show failures.
   - *In our project:* GlobalExceptionHandler returns RFC-style ProblemDetail bodies with error codes.

90. **422 meaning here?**
   - *General:* BusinessException typically maps to HTTP 422 with blockers or rule details.
   - *In our project:* Not a primary custom pattern in this repo — say so honestly, then tie back to everyday Java/Spring usage in our services if the interviewer presses.

91. **409 meaning here?**
   - *General:* DuplicateResourceException / DuplicateDocumentException map to HTTP 409 Conflict.
   - *In our project:* Not a primary custom pattern in this repo — say so honestly, then tie back to everyday Java/Spring usage in our services if the interviewer presses.

92. **Multipart max?**
   - *General:* Max upload size failures map to HTTP 413 Payload Too Large.
   - *In our project:* If an upload is too large, we return 413 so the client knows the file exceeded limits.

93. **S3 vs local storage?**
   - *General:* StorageConfig selects local or S3 FileStorageService based on properties.
   - *In our project:* Storage can be local disk or S3; config picks one FileStorageService implementation.

94. **SOLID DIP example?**
   - *General:* Dependency Inversion means depend on EmailService promises, not SmtpEmailService everywhere.
   - *In our project:* DIP: high-level modules depend on abstractions like EmailService and FileStorageService.

95. **Open/Closed example?**
   - *General:* Open/Closed means add a new CloseCheck without rewriting the close engine.
   - *In our project:* Close readiness is open for extension via new CloseCheck beans without modifying the orchestrator.

96. **Lost update?**
   - *General:* Without @Version or locks, concurrent updates can overwrite each other — last write wins.
   - *In our project:* Lost update is two people saving the same row and the last write silently winning.

97. **Race on quotas?**
   - *General:* Subscription quota concurrency needs transactional guards/locks — covered by concurrency tests.
   - *In our project:* Quota races happen when two creates pass the check at once; need locks or unique constraints.

98. **N+1 detect?**
   - *General:* Detect N+1 via SQL logging/metrics, then fix with fetch joins or DTO projections.
   - *In our project:* N+1 shows up as one query plus many look-alike SELECTs in logs under load.

99. **Prod config issue?**
   - *General:* Compare SPRING_PROFILES_ACTIVE, secrets, datasource URL, and Flyway history when local≠prod.
   - *In our project:* Prod fails when env vars, secrets, profiles, or Flyway differ from what worked locally.

100. **How explain project in 30s?**
   - *General:* In 30 seconds: Spring Boot app for accounting firms — clients, money, docs, bank, close, reports, AI — one modular app with JWT and Postgres.
   - *In our project:* Pitch: modular Spring Boot monolith for multi-tenant accounting SaaS with JWT, PostgreSQL/Flyway, ledger workflows, and AI document extraction.

---

## Quick “Why not?” cheat sheet

| Why not? | Short trade-off |
|----------|-----------------|
| Plain Java instead of Spring Boot | Too much DIY for DI/security/JPA |
| `new` instead of DI | Hard tests, rigid wiring |
| Field injection | Hidden deps, harder tests |
| Controller → Repository | Fat controllers, weak TX/business reuse |
| Entity in API | Leaks, lazy issues, coupling |
| Raw JDBC only | Slow CRUD; we mix JPA + native SQL |
| Mongo as primary | Weak fit for relational ledger TX |
| Sessions instead of JWT | Harder horizontal scale for SPA API |
| Microservices now | Ops + distributed TX cost |
| Kafka for every event | Complexity; in-process events suffice |
| Eager everywhere | Over-fetch / memory |
| CascadeType.ALL | Accidental mass deletes |
| `@Transactional` everywhere | Wrong boundaries, long TX |
| Index every column | Slow writes |
| Cache everything | Stale/wrong tenant data |
| Async everything | Painful error/UX semantics |

---

## Difficult follow-up chains (practice out loud)

**DI →** IoC → Bean → ApplicationContext → constructor vs field → `@Qualifier` → `@Primary` → circular dependency → bean scopes  

**JWT →** structure → claims secret? → filter order → SecurityContext → refresh vs access → logout revoke → 401 path  

**JPA →** EntityManager → dirty checking → lazy → N+1 → open-in-view → DTO mapping → transactions  

**Close period →** Strategy checks → `@Transactional` → 422 blockers → audit → notifications  

**Architecture →** modular monolith evidence → when to split → what splits first (AI/reporting) → data ownership  

---

*End of interview bank. Study Parts 8, 10, 11, 13, 24, 25, and the Top 100 the night before.*
