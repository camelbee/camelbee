# How CamelBee works

What actually happens inside your application when CamelBee is enabled, and how the UI turns that
into a topology and a waterfall. Written for people who want to know what they are switching on
before they switch it on in an environment that matters.

Everything below lives in **`camelbee-core`** (`core/shared-core`), the framework-neutral engine.
The Quarkus, Spring Boot and standalone cores add only dependency injection and an HTTP layer on
top of it.

---

## The three moving parts

CamelBee hooks into Camel in three places, and they do different jobs:

| | What it hooks | What it gives you |
|---|---|---|
| **Event notifier** | Camel's `EventNotifier` SPI | *That* an exchange was created, sent, completed or failed |
| **Intercept strategies** | Camel's `InterceptStrategy` SPI | *Where* in the route it currently is |
| **Unit of work** | `UnitOfWorkFactory` | MDC keys on your own log lines |

The notifier alone is not enough. Camel tells you an exchange was sent to `direct:foo`, but not
which node inside the calling route sent it — and without that a trace is a flat list of endpoint
calls rather than a path through your routes. The intercept strategies supply the missing half.

---

## Event notifier — what happened

`CamelBeeEventNotifier` subscribes to five Camel events:

- `ExchangeCreatedEvent` — a route consumer accepted an exchange
- `ExchangeSendingEvent` / `ExchangeSentEvent` — a producer call started / finished
- `ExchangeCompletedEvent` — the exchange finished
- `ExchangeFailedEvent` — it finished by throwing

Sending and Sent become the **request and response halves of one hop**. That pairing is what lets
the UI show a request body and its response side by side, and what gives the waterfall a bar with a
real duration rather than a point in time.

`ExchangeFailedEvent` matters more than it looks. On a route started by a consumer — a timer, a file
poller, a JMS listener — there is no caller for the error to propagate to, so a failure would
otherwise leave no trace at all. Recording the event directly is what makes those visible.

Registered on startup, after the routes exist. Controlled by `camelbee.notifier-enabled`.

---

## Intercept strategies — where it happened

Registered by `camelBeeRouteConfigurer.configureRoute(this)`, which **every `RouteBuilder` must
call as its first statement** on Quarkus and Spring Boot. On standalone, `CamelBee.register(main)`
does the same before the context starts.

The timing is not a style preference. Intercept strategies are consulted while each processor is
*reified* — built from its definition — so a strategy registered after that point sees nothing.
This is why the call has to come before your routes are declared, and why CamelBee will otherwise
start happily, draw the topology, and simply produce incomplete traces.

**`NodeIdInterceptStrategy`** wraps every node and stamps the current node id onto the exchange, so
the notifier's events can be attributed to a position in a route rather than just an endpoint. Two
details are load-bearing:

- It wraps with a `DelegateAsyncProcessor`, not a synchronous lambda. A synchronous wrapper would
  drag every intercepted node onto the caller thread and defeat Camel's asynchronous routing.
- It restores the *enclosing* node id from the async callback rather than a `finally` block. Without
  that, once control returned from a callee route the id would still name the callee's last node,
  and the response half of a hop would be attributed to the wrong edge.

**`PollInterceptStrategy`** covers `poll()` and `pollEnrich()`, which Camel emits no sending/sent
events for. Without it those hops are invisible — which is exactly where a stuck integration often
is, because a poll that times out looks identical to one that returned nothing.

Both are registered **once per CamelContext**, guarded, even though every route builder calls the
configurer.

Controlled by `camelbee.route-configurer-enabled`. It also turns on stream caching, so reading a
body for tracing does not consume it for your route.

---

## Following one request across the exchanges it spawns

`wireTap`, `multicast`, `split`, `recipientList` and `seda` each create a **new exchange** with its
own id. Camel does not relate them to the one that caused them, so by default they arrive as
unrelated traffic.

CamelBee records the causing exchange's id on the spawned one, which is what lets the UI nest
branches under the request that started them, and what makes "one request reads as one flow" true
in the waterfall rather than a dozen disconnected entries.

Redelivery attempts are kept as **separate** hops rather than collapsed, so a retry that eventually
succeeds still shows how many attempts it took and how long the backoff was.

---

## What gets stored, and what never does

`MessageService` holds traced messages in memory, capped by
`camelbee.tracer-max-messages-count` (default 1000). The UI warns when the cap is hit.

Three things happen at the point of capture, not at display time — so an unredacted value never
exists in the buffer, never reaches the browser, and cannot appear in a heap dump of the running
application:

- **Redaction** (`camelbee.masking-enabled`, default on) replaces configured keys.
  Header redaction is exact key matching. **Body redaction is pattern matching** and will not catch
  a field you have not configured — this is the one guarantee that is best-effort.
- **Body capture** can be switched off entirely with `camelbee.tracer-body-enabled=false`. This is
  the only hard guarantee: no body text is read at all.
- **The capture filter** (set at runtime from the UI or `/camelbee/tracer/filter`) records only
  flows containing the string you give it, plus the branches they spawn. Everything else is never
  recorded — it is not filtered at display time.

The tracer is **off** at startup, armed from the UI without a restart, and disarms itself after
`camelbee.tracer-max-idle-time` (default 300000 ms) of inactivity.

---

## How the UI reads it

The UI is a React application served from your own HTTP port. It is a plain client of the same REST
API you can call yourself:

| Endpoint | Used for |
|---|---|
| `GET /camelbee/routes` | The topology — routes, nodes and the edges between them |
| `GET /camelbee/messages?index=&addVersion=&resetVersion=` | Incremental message polling |
| `POST /camelbee/tracer/status` | Arm / disarm tracing |
| `POST /camelbee/tracer/filter` | Set the capture filter |
| `GET /camelbee/auth/status`, `POST /camelbee/auth/login` | Authentication |

**Topology** comes from `RouteContextService`, which reads Camel's *route definitions* — not
traffic. That is why the graph is complete the moment the application starts, before anything has
been traced, and why a route nothing has exercised still appears.

**Messages** are polled incrementally. The client passes the `index` it has reached plus the
`addVersion` and `resetVersion` it last saw; the server returns only what is new. The two version
counters are how the client detects that the buffer was cleared or rolled over underneath it and
resyncs, instead of silently stitching together two unrelated windows.

The **waterfall** is derived entirely client-side from those same messages — each hop's start
timestamp and duration become a bar, and the spawned-exchange links become the nesting. No separate
API, and no extra work in your application.

### Inside the UI

Source: [`ui/`](../ui) — see its [README](../ui/README.md) for local development.

A React 19 + TypeScript application built with Vite, bundled at build time into the core jars and
served as static assets from your application's own HTTP port (`META-INF/resources/camelbee` on
Quarkus and Camel K, `static/camelbee` on Spring Boot, and Vert.x handlers on the camel-main
management server for standalone). Vite is configured with `base: '/camelbee/'`, which is why the
assets resolve wherever the app is mounted.

| Concern | How |
|---|---|
| Server state & polling | **TanStack Query** — messages refetch every 2s while tracing is armed, health and metrics on the intervals you set in Settings, and nothing polls when disabled |
| Topology graph | **@xyflow/react** for rendering, **dagre** for layout (`src/utils/routeGraph.ts` turns the `/camelbee/routes` response into nodes and edges) |
| Charts | **Recharts** for the JVM/CPU/GC series on the metrics page |
| Routing | **react-router-dom**, with a server-side fallback so `/camelbee/settings` can be bookmarked, shared and reloaded |
| Local state | a small store under `src/store` for settings and UI preferences, persisted in the browser |

`src/` is organised as `api/` (one hook per endpoint), `components/`, `pages/`, `hooks/`, `store/`,
`utils/` and `types/`. The metrics page parses the Prometheus exposition format directly in the
browser — the scrape path is a Setting, because it differs per runtime (`/q/metrics` on Quarkus and
Camel K, `/metrics` on the Spring Boot sample, `/observe/metrics` on standalone).

Nothing in the UI is privileged: it uses the same REST API documented above, with the same bearer
token, so anything it shows you can retrieve yourself with `curl`.

---

## What it costs

- Tracing off: the notifier and intercept strategies are registered but do almost nothing —
  `NodeIdInterceptStrategy` checks `TracerService.isActive()` before any per-node work.
- Tracing on: per-hop capture, redaction and an in-memory buffer bounded by the message cap.
- Stream caching is on whenever the route configurer runs, which is what makes bodies safe to read
  twice.

Everything above is switched by the properties in the
[configuration table](../README.md#all-configuration-properties).
