# unshare.in — Signaling Backend

Backend for a peer-to-peer file-sharing platform (ToffeeShare-style). A sender
picks files in the browser, gets a share link, and a receiver opens that link
to establish a direct WebRTC connection. **File bytes never touch this
backend** — it only creates/tracks share sessions, relays WebRTC
offer/answer/ICE messages between the two browsers, and expires stale
sessions.

This is step 2 of a larger build (20 planned steps):

- **Step 1** added the REST skeleton and the WebSocket signaling relay.
- **Step 2** (this step) replaced the persistence layer with a proper
  token/transfer/file schema, explicit state machines for session and
  transfer lifecycles, and Flyway-migration-only schema management.

This document covers what exists so far.

## 1. Architecture

Modular monolith, one deployable, partitioned internally so a piece (e.g. the
WebSocket signaling relay) could be extracted into its own service later
without a rewrite.

```
API layer        controller/ + websocket/     -> talks in DTOs only
Domain layer      service/                     -> session lifecycle, tokens, rate limiting
Persistence layer entity/ + repository/        -> Flyway-versioned Postgres schema, metadata only
```

The backend's only contact with a "file" is its name/size/MIME type, stored
as metadata so a receiver's UI can show what's incoming before the WebRTC
data channel opens.

## 2. Package structure

```
in.unshare
├── config          AppProperties, WebSocketConfig, ClockConfig
├── security        SecurityConfig (stateless, CORS, actuator lockdown)
├── controller      ShareSessionController, HealthController
├── service         ShareSessionService, TokenGeneratorService,
│                   RateLimiterService, SessionExpiryScheduler
├── repository      ShareSessionRepository, TransferSessionRepository,
│                   SharedFileRepository
├── entity          ShareSession, TransferSession, SharedFile,
│                   SessionStatus, TransferStatus, ConnectionState
├── dto             request/response records
├── mapper          ShareSessionMapper (entity -> DTO)
├── exception       domain exceptions + GlobalExceptionHandler
└── websocket       SignalingWebSocketHandler, SignalingSessionRegistry,
                    SignalingHandshakeInterceptor, SignalingMessage, PeerRole
```

## 3. Database design (step 2)

Three tables, each mapped to its own JPA entity. Nothing here ever stores
file bytes, file paths, local/device paths, or more PII than a share needs.

### `share_session`

The token-addressable, temporary sharing session — the thing a link points
at.

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID` PK | Never exposed externally; internal identity only |
| `token` | `VARCHAR(64)` | Public, URL-safe, unique. See §7 for entropy |
| `status` | `VARCHAR(30)` | See state machine below |
| `sender_state` / `receiver_state` | `VARCHAR(20)` | `NOT_CONNECTED, CONNECTING, CONNECTED, DISCONNECTED` — coordination state, not a raw WebSocket/session id (those stay in-memory in `SignalingSessionRegistry` and are never persisted) |
| `max_receivers` | `INTEGER` | How many receivers this link allows |
| `creator_label` | `VARCHAR(255)`, nullable | Optional sender-supplied label (e.g. "My Laptop") — no IP or device fingerprint is stored |
| `created_at` / `expires_at` / `last_activity_at` | `TIMESTAMPTZ` | UTC throughout |
| `version` | `BIGINT` | Optimistic lock |

Constraints: unique `token`; `max_receivers > 0`; `expires_at > created_at`;
`status`/`sender_state`/`receiver_state` each restricted to their enum's
values via `CHECK`.

### `transfer_session`

One attempt to move a declared batch of files for a share session. Modeled
separately from `share_session` so a link can support more than one transfer
attempt (e.g. a retry) without mutating share-level history.

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID` PK | |
| `share_session_id` | `UUID` FK | `ON DELETE CASCADE` |
| `status` | `VARCHAR(20)` | `PENDING, IN_PROGRESS, COMPLETED, FAILED, CANCELLED` |
| `started_at` | `TIMESTAMPTZ`, nullable | Set when status moves to `IN_PROGRESS` |
| `completed_at` | `TIMESTAMPTZ`, nullable | Set when status reaches any terminal state |
| `last_activity_at` | `TIMESTAMPTZ` | |
| `version` | `BIGINT` | Optimistic lock |

Constraint: `completed_at >= started_at` whenever both are set.

### `shared_file`

Per-file metadata only — no content, no storage path.

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID` PK | Internal identity; never returned to clients |
| `transfer_session_id` | `UUID` FK | `ON DELETE CASCADE` |
| `file_identifier` | `UUID` | Client-supplied correlation id used to match this row to the actual WebRTC data-channel transfer. This is what the API exposes externally — **not** the internal `id` |
| `original_filename` | `VARCHAR(255)` | |
| `mime_type` | `VARCHAR(127)`, nullable | |
| `file_size` | `BIGINT` | `> 0` |
| `sequence_order` | `INTEGER` | Unique per `transfer_session_id` — gap-free ordering assigned server-side regardless of client input order |

Relationships are unidirectional `@ManyToOne` (child → parent) with **no**
back-collections on the parent side. Reads go through repository queries
(`TransferSessionRepository`, `SharedFileRepository`) instead of JPA
collection navigation — avoids N+1/lazy-loading pitfalls and keeps each
repository's methods limited to what the service layer actually calls.

### Indexes

| Index | Why |
|---|---|
| `uq_share_session_token` (unique constraint) | The hot lookup path — every `GET/POST /shares/{token}` request resolves by token. The unique constraint already gives Postgres a btree index, so no separate index was added |
| `idx_share_session_status` | `SessionExpiryScheduler` and any status-filtered query |
| `idx_share_session_expires_at` | The expiry sweep filters `expires_at < now` |
| `idx_share_session_last_activity_at` | Future idle-session cleanup / activity queries |
| `idx_transfer_session_share_session_id` | FK lookup — parent → children, and cascade-delete performance |
| `idx_transfer_session_status`, `idx_transfer_session_last_activity_at` | Same rationale as the share-session equivalents, scoped to transfers |
| `idx_shared_file_transfer_session_id` | FK lookup — fetching a transfer's file list |
| `uq_shared_file_transfer_sequence` | Enforces gap-free, unique ordering per transfer; doubles as an index for ordered reads |

Schema is defined in
[`V1__create_schema.sql`](src/main/resources/db/migration/V1__create_schema.sql).

### State machines

Both `SessionStatus` and `TransferStatus` reject any transition not listed
below (`InvalidShareStateException`) — there is no code path that can push a
row into an arbitrary state.

```
ShareSession:
CREATED --------------> WAITING_FOR_RECEIVER --------------> CONNECTING
   |                            |                                |  |
   v                            v                                v  v
CANCELLED                   CANCELLED                    CONNECTED  WAITING_FOR_RECEIVER
   |                            |                                |  (retry)
   v                            v                                v
EXPIRED                     EXPIRED                    TRANSFERRING --> COMPLETED
                                                              |     |--> CANCELLED
                                                              |     |--> FAILED
                                                              +---> EXPIRED
```

`COMPLETED`, `EXPIRED`, `CANCELLED`, `FAILED` are terminal — no transitions
out.

```
TransferSession:
PENDING --> IN_PROGRESS --> COMPLETED
   |             |
   |             +--------> FAILED
   +--------------------->  CANCELLED
```

## 4. REST endpoints

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/shares` | Sender creates a session (`files`, optional `ttlMinutes`, optional `maxReceivers`) → creates the `ShareSession` **and** its initial `TransferSession` + `SharedFile` rows, returns token + share URL |
| `GET` | `/api/v1/shares/{token}` | Fetch session info + current file list |
| `POST` | `/api/v1/shares/{token}/join` | Receiver claims the session (`WAITING_FOR_RECEIVER` → `CONNECTING` → `CONNECTED`) |
| `DELETE` | `/api/v1/shares/{token}` | Sender cancels |
| `GET` | `/api/v1/health` | Liveness |
| `GET` | `/actuator/health` | Actuator health (only endpoint exposed) |

`ShareSessionResponse` now includes `senderState`, `receiverState`,
`maxReceivers`, and `lastActivityAt` alongside the existing `token`,
`shareUrl`, `status`, `createdAt`, `expiresAt`, `files`. Each file in the
response carries a `fileIdentifier` (client correlation id), not the
internal database id.

All error responses use a single shape (`ApiErrorResponse`) produced by
`GlobalExceptionHandler`.

**Scope note:** step 2 stayed within the persistence layer as specified —
there are intentionally no new endpoints yet for explicitly starting/
completing a transfer (`TransferSession` lifecycle beyond the initial
`PENDING` row). Wiring that up alongside the WebRTC data-channel handshake
is a natural candidate for a later step.

## 5. WebSocket signaling protocol

`TextWebSocketHandler` at `/ws/signaling/{token}?role=SENDER|RECEIVER`. JSON
envelope:

```json
{ "type": "OFFER", "payload": { ... } }
```

Types: `JOIN, OFFER, ANSWER, ICE_CANDIDATE, LEAVE, PEER_JOINED, PEER_LEFT, ERROR`.

The handshake interceptor (`SignalingHandshakeInterceptor`) validates the
token exists and isn't expired before the socket is accepted. The registry
(`SignalingSessionRegistry`) pairs the two sockets by token and relays
`OFFER`/`ANSWER`/`ICE_CANDIDATE` payloads verbatim — the backend never parses
SDP content, only routes on `type`. This registry is purely in-memory and
independent of the `sender_state`/`receiver_state` columns above.

## 6. Session lifecycle

See the state machine diagrams in §3. In short:
`CREATED` → `WAITING_FOR_RECEIVER` (link is shareable) → `CONNECTING` →
`CONNECTED` → `TRANSFERRING` → `COMPLETED` | `FAILED` | `CANCELLED` |
`EXPIRED`. `SessionExpiryScheduler` runs every 60s
(`app.share.expiry-sweep-interval-ms`) and bulk-flips any row in a
non-terminal status past `expires_at` to `EXPIRED`
(`SessionStatus.nonTerminalStatuses()` drives which statuses are eligible,
so adding a new non-terminal status doesn't require touching the scheduler).

## 7. Security model

Stateless (no login — link possession is the auth), CSRF disabled, CORS
locked to `app.cors.allowed-origins`. Tokens are 24 random bytes
(`SecureRandom`, base64url — 192 bits of entropy). Per-IP rate limiting on
session creation (in-memory, single-instance — see §9). Actuator exposes
only `health` publicly; every other path is denied by the security filter
chain by default (`anyRequest().denyAll()`). All secrets come from
environment variables (see `.env.example`), never hardcoded. Internal
database primary keys (`ShareSession.id`, `SharedFile.id`, etc.) are never
returned by the API — external clients only ever see the share `token` and
each file's client-supplied `fileIdentifier`.

## 8. Concurrency

`@Version` optimistic locking on `share_session`, `transfer_session`, and
`shared_file` guards races between the expiry sweep, a receiver's join, and
any future transfer-lifecycle writes. Foreign keys cascade at the database
level (`ON DELETE CASCADE`) rather than through JPA-level cascade
configuration, so a share/transfer deletion removes its children atomically
in Postgres regardless of what's loaded in a given persistence context.
`SignalingSessionRegistry` is a `ConcurrentHashMap` with per-entry
`synchronized` pairing logic. The rate limiter uses a `ConcurrentHashMap`
with atomic per-key window updates.

## 9. Scalability

REST/DB layer is stateless and scales horizontally. The **in-memory
WebSocket peer registry and the in-memory rate limiter are the two things
tying signaling/rate-limiting to a single instance** — intentionally, per
the "no premature infra" constraint. A later step can swap in a shared
pub/sub (e.g. Redis) for both without touching REST/domain code.

## 10. Failure modes

- No receiver ever joins → swept to `EXPIRED`.
- WebRTC/ICE fails after both peers connect (NAT/firewall, no TURN) →
  invisible to the backend today; mapping this to `TransferSession.FAILED`
  is part of wiring up the transfer-lifecycle endpoints in a later step.
- Backend restart drops the in-memory WS registry; DB rows survive but a
  stale `CONNECTED` session with no live socket needs client-side re-join.
- Joining a session not in `WAITING_FOR_RECEIVER` is rejected
  (`InvalidShareStateException`, 409) — covers double-join and joining an
  already-connected/terminal session.
- Token brute-forcing is mitigated by entropy + rate limiting.

## 11. Assumptions

Single backend instance for now; no user accounts (the link is the access
control); TURN credential issuance is a later step; TLS/WSS is terminated
by a reverse proxy in front of the app, not by Spring itself; a share
session's initial `TransferSession` represents "the files offered at share
creation" — explicit multi-attempt transfer orchestration is deferred to a
later step.

## Directory structure

```
unshare/
├── mvnw, mvnw.cmd, .mvn/wrapper/         Maven Wrapper (no local mvn install needed)
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── .dockerignore
├── .env.example
├── src/main/java/in/unshare/…            (see package structure above)
├── src/main/resources/
│   ├── application.yml, application-{dev,test,prod}.yml
│   └── db/migration/V1__create_schema.sql
└── src/test/java/in/unshare/…
    ├── ShareApplicationTests.java                     full context load, Testcontainers Postgres
    ├── controller/                                    @WebMvcTest
    ├── service/                                       Mockito unit tests
    ├── repository/
    │   ├── ShareSessionRepositoryTest.java             @DataJpaTest + Testcontainers
    │   ├── TransferSessionRepositoryTest.java          relationships + cascade delete
    │   ├── SharedFileRepositoryTest.java                constraints + cascade delete
    │   └── SchemaIntegrationTest.java                  verifies indexes/FKs/migration via raw JDBC
    ├── websocket/                                      unit tests
    └── support/
        ├── AbstractIntegrationTest.java                 Testcontainers Postgres (singleton container)
        └── ShareSessionFixtures.java                    test entity factories
```

## Running locally

**Requirements**: JDK 21 (see note below — not 25), Docker (for Postgres and
for Testcontainers-based tests). No local Maven install is required — the
Maven Wrapper is checked in.

1. Copy `.env.example` to `.env` and fill in `DB_PASSWORD`.
2. Start Postgres + backend:
   ```
   docker compose --env-file .env up --build
   ```
   The backend runs on `http://localhost:8080`, `GET /api/v1/health` should
   return `{"status":"UP", ...}`.
3. To run the backend directly against a local Postgres instead of Docker,
   set `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` env vars and run:
   ```
   ./mvnw spring-boot:run
   ```

## Database migrations

Flyway runs automatically on startup (`spring.flyway.enabled: true`),
applying versioned SQL files from `src/main/resources/db/migration` in
order (`V1__create_schema.sql` is the first, covering `share_session`,
`transfer_session`, and `shared_file`). `spring.jpa.hibernate.ddl-auto` is
set to `validate` — Hibernate never auto-generates schema; every schema
change must go through a new `V{n}__description.sql` migration file.

## Build and test commands

```
./mvnw compile              # compile main sources
./mvnw test-compile         # compile main + test sources
./mvnw test                 # run full suite (unit + Testcontainers integration tests; needs Docker running)
./mvnw package              # build the runnable jar (target/unshare-*.jar)
```

## Environment note (this dev machine)

This sandbox didn't have Maven or a JDK 21 install on `PATH` at the time
this project was scaffolded — only a JRE 8 and JDK 17/25. The Maven Wrapper
was added so the build is self-contained, and a JDK 21 was downloaded to
`C:\tools\jdk21` for compiling/running with the exact runtime the spec
calls for. **Use JDK 21 specifically, not 25** — Mockito's inline mock maker
(byte-buddy) can't instrument classes under JDK 25 in the version pinned by
this Spring Boot release, which breaks every Mockito-based unit test; JDK 21
has no such issue. If you open this project in an IDE or terminal without
`JAVA_HOME` set to a JDK 21, point it there.

Testcontainers note: `AbstractIntegrationTest` starts its shared Postgres
container in a static initializer rather than via `@Testcontainers`/
`@Container`. Those annotations stop the container at the end of *each* test
class, and since the container instance is shared (singleton pattern) across
every `@DataJpaTest`/`@SpringBootTest` class in the suite, that per-class
stop/restart raced against reuse and made every class after the first time
out waiting for a connection. Starting it once for the JVM's lifetime (Ryuk
still cleans it up on exit) fixed it — keep this in mind if you're tempted
to re-add `@Container` here.
