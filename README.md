# Flagship Project 2: Realtime Collaboration Platform
> **WebSocket-Based Concurrency, Room Scoping, Last-Write-Wins Consensus, and Horizontal Scale Architecture**

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21%20LTS-orange.svg)](https://www.oracle.com/java/)
[![React](https://img.shields.io/badge/React-19%20%2B%20TypeScript-blue.svg)](https://react.dev/)
[![STOMP](https://img.shields.io/badge/Protocol-WebSocket%20%2B%20STOMP-blueviolet.svg)](https://stomp.github.io/)
[![Database](https://img.shields.io/badge/Database-PostgreSQL%20%2F%20H2-336791.svg)](https://www.postgresql.org/)
[![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)](LICENSE)

---

## Section 11: System Design & Real-Time Concurrency Breakdown

### 1. The Core Architectural Muscle: Stateful WebSockets vs Stateless REST
In a traditional stateless CRUD API (Project 1), horizontal scaling is trivially solved by placing a round-robin load balancer in front of N interchangeable, stateless workers. A client request arrives on Node A, modifies a database record under ACID transaction isolation, and completes. The server retains zero client memory after sending the HTTP response.

**Real-Time WebSocket collaboration breaks this assumption fundamentally:**
- **Stateful TCP Sockets:** Each client maintains a persistent full-duplex TCP socket connection open for minutes or hours.
- **In-Memory Channel Subscriptions:** The server maintains an internal registry of which socket session belongs to which tenant/room topic (`/topic/rooms/{roomId}`).
- **Two-Tier State Division:**
  1. **Ephemeral State:** Socket file descriptors, active presence ping/pong heartbeats, live cursor/typing indicators.
  2. **Durable State:** Rooms, board items, audit logs, and version vectors persisted to PostgreSQL.

```
       [ Client A ]          [ Client B ]          [ Client C ]
            |                     |                     |
     (Room Alpha)           (Room Alpha)           (Room Bravo)
            \                     /                     |
    [ WebSocket Full-Duplex TCP Sockets: Port 8080 /ws-collab ]
                                 |
                 [ STOMP Message Broker Engine ]
            +--------------------+--------------------+
            |                    |                    |
   /topic/rooms/alpha   /topic/rooms/bravo      /user/queue/sync
    [ Alice, Bob ]          [ Charlie ]          (Private Sync)
            |                    |
            +----------+---------+
                       |
             [ Board & Conflict Engine ]
             - Optimistic Versioning (v1 -> v2)
             - Deterministic Last-Write-Wins (LWW)
             - Presence Registry (Clean Disconnects)
                       |
            [ PostgreSQL / H2 Storage ]
             - Rooms & Board Items
             - LWW Conflict Audit Records
```

---

### 2. What Breaks First at Scale? (The Physical Limits)

Stateless web services typically degrade under CPU or database connection pool limits. A WebSocket server fails completely differently: **thousands of concurrent connections hit OS kernel and memory thresholds long before CPU business logic even registers 10% utilization.**

```
+-----------------------------------------------------------------------------------+
|                           WHAT BREAKS FIRST AT SCALE                              |
+-----------------------------------------------------------------------------------+
| 1. OS File Descriptors (ulimit -n)                                                |
|    Each TCP socket is an open file descriptor. Default Linux limit is 1024.       |
|    At 1025 connections: `java.io.IOException: Too many open files`.               |
|                                                                                   |
| 2. Kernel & Socket Memory Bloat                                                   |
|    Default Linux TCP buffer (rmem/wmem) allocates 128KB - 256KB per socket.       |
|    10,000 idle connections = 2.5 GB kernel RAM just holding empty sockets.        |
|                                                                                   |
| 3. Thread Pool Starvation & Ping/Pong Heartbeats                                  |
|    If STOMP heartbeats (10s interval) run on standard worker threads, 50,000      |
|    clients generate 5,000 heartbeat packets/sec, saturating event loops.          |
|                                                                                   |
| 4. The "Reconnect Storm" (Thundering Herd on Database)                            |
|    A momentary network blip disconnects 20,000 clients. When network restores,   |
|    20,000 clients simultaneously reconnect and request a full DB catch-up snapshot|
|    instantaneously starving the HikariCP database connection pool.                |
+-----------------------------------------------------------------------------------+
```

#### Detailed Breakdown of Failure Modes:

1. **File Descriptor Table Exhaustion (`ulimit -n`):**
   In Unix systems, every active TCP socket requires a file descriptor. If `ulimit -n` is left at the default (1024), the 1025th client is abruptly rejected with `ECONNRESET`. In production, this requires tuning `/etc/security/limits.conf` (`nofile 65536` or `1048576`).
2. **Buffer Memory Footprint:**
   Holding 100,000 open connections in memory requires:
   $$\text{Memory} = N_{\text{conn}} \times (\text{Socket Buffer}_{\text{rx/tx}} + \text{JVM Session Object} + \text{STOMP Frame Buffer})$$
   At $200\text{ KB}$ per connection, 100,000 connections require **20 GB of RAM** merely to remain idle without any user typing a character.
3. **The Multi-Node Broker Problem (Cross-Node Routing):**
   If User 1 connects to Node A and User 2 connects to Node B, Node A's in-memory Spring SimpleBroker cannot broadcast to User 2. A distributed message broker (such as a **Redis Pub/Sub** bus or **RabbitMQ STOMP Relay**) must sit behind the application cluster so that Node A publishes to `room.{id}` and all cluster nodes fan out the message to their local connected sessions.

---

### 3. Conflict Resolution Engine: Last-Write-Wins (LWW) Trade-Off Analysis

When two remote users modify the exact same board item concurrently without locks, a conflict resolution strategy must decide the final state.

This platform implements **Deterministic Last-Write-Wins (LWW) with Version Tracking and Client Timestamps**.

```
Alice (v1, t=1000) -------[ Submits edit "Alice Title" ]--------> [ Committed as v2 ]
                                                                      |
Bob   (v1, t=1500) -------[ Submits edit "Bob Title"   ]--------> [ Collision Detect ]
                                                                 (Stored v2 > Base v1)
                                                                      |
                                                          Is Bob's t (1500) > Alice (1000)?
                                                                   /         \
                                                            [ YES ]           [ NO ]
                                                               |                 |
                                                        LWW OVERWRITE       LWW REJECT
                                                        Committed as v3     Reject Bob's packet
                                                        Emit Conflict       Send winner state
                                                        Audit Log           to Bob for reconcile
```

#### What Does a User Lose Under Last-Write-Wins? (Explicit Engineering Trade-off)
Many naive implementations treat LWW as a "solved problem." In production, LWW has severe, irreversible trade-offs:

1. **Data Loss (Clobbering):** If Alice writes three paragraphs in a task description, and Bob corrects a typo in the title 50ms later, Bob's packet overwrites the entire record—**Alice's three paragraphs are permanently wiped out**.
2. **Clock Skew Vulnerability:** Client wall-clock timestamps (`Date.now()`) are inherently unreliable across distributed machines. If Bob's laptop clock is running 5 minutes fast (NTP desynchronization), all of Bob's edits will unconditionally overwrite everyone else's edits for the next 5 minutes.
3. **Lack of True Causality:** LWW does not model intent. Unlike **CRDTs (Conflict-free Replicated Data Types)** or **Operational Transformation (OT)**, LWW operates on coarse item-level granularity rather than character-level diff trees.

**Why LWW is Appropriate Here:**
For discrete card/task entities (moving columns, changing assignees, updating titles), LWW provides determinism, $O(1)$ constant-time compute overhead, and low memory overhead without requiring complex distributed dependency graph resolution. To counteract data loss, our implementation **generates an explicit `ConflictRecord` and broadcasts a `CONFLICT_DETECTED` event** so users are immediately alerted if their edit was overwritten or rejected.

---

### 4. Persistence + Reconnect Synchronization ("The Catch-Up Problem")

WebSocket connections are ephemeral and drop frequently (Wi-Fi transitions, laptop sleep, mobile network changes). The persisted document state in PostgreSQL must never be lost.

When a client reconnects, it suffers from the **Catch-Up Dilemma**:
- If it immediately subscribes to live updates, it misses all mutations that occurred while it was offline.
- If it loads from REST asynchronously after subscribing, a race condition occurs where old REST data can overwrite newer WebSocket frames received in between.

#### The Reconnect Protocol:
1. Client establishes WebSocket connection to `/ws-collab`.
2. Client subscribes to `/topic/rooms/{roomId}`.
3. Client immediately dispatches `/app/room/{roomId}/join` or requests `/app/room/{roomId}/sync`.
4. Server loads current database snapshot (Room + all `BoardItem`s + active presence + conflict audit) and transmits a consolidated `ROOM_SYNC` payload.
5. Client reconciles local memory with the snapshot before processing any subsequent delta updates.

---

### 5. Architectural Test Suite & Verification Matrix

The test suite in `server/src/test/java/com/collab/platform/` validates all 4 concurrency pillars:

| Test Class | Focus | Verification Result |
| :--- | :--- | :--- |
| `WebSocketIntegrationTest` | Multi-client broadcast loop | Client 1 creates item; Client 2 receives STOMP frame |
| `RoomIsolationIntegrationTest` | Multi-tenant room isolation | Room Alpha broadcasts never leak to Room Bravo subscribers |
| `ConflictResolutionIntegrationTest` | Deterministic LWW & Audit | Resolves concurrent writes; logs winning/rejected user, versions, and clock deltas |
| `ReconnectSyncIntegrationTest` | Reconnect catch-up sync | Disconnected client misses 2 mutations; reconnects and receives complete updated DB state |

---

### 6. Quick Start & Execution

#### Prerequisites
- Java 21 LTS
- Maven 3.9+
- Node.js 20+ & npm

#### 1. Start Spring Boot Backend
```powershell
cd server
mvn spring-boot:run
```
*Backend starts on `http://localhost:8080` with H2 in-memory DB and seed rooms enabled.*
*To use PostgreSQL:*
```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

#### 2. Run Test Suite
```powershell
cd server
mvn test
```

#### 3. Start React Collaboration Frontend
```powershell
cd client
npm install
npm run dev
```
*Frontend runs on `http://localhost:5173`.*
