# CamelBee User Guide

## Contents

- [Introduction](#introduction)
- [Debugger Page](#debugger-page)
  - [Route Visualization](#route-visualization)
  - [Message Tracing](#message-tracing)
  - [Message Panel](#message-panel)
  - [Waterfall Panel](#waterfall-panel)
  - [Linking the Waterfall and the Graph](#linking-the-waterfall-and-the-graph)
  - [Health Panel](#health-panel)
  - [Filtering Messages](#filtering-messages)
  - [Resizing the Panels](#resizing-the-panels)
- [Using CamelBee in Production](#using-camelbee-in-production)
  - [Redacting Sensitive Data](#redacting-sensitive-data)
  - [Tracing a Single Transaction](#tracing-a-single-transaction)
  - [Signing In](#signing-in)
  - [Restricting Who Can Reach CamelBee](#restricting-who-can-reach-camelbee)
  - [One More Way Traced Data Leaves the Application](#one-more-way-traced-data-leaves-the-application)
- [Metrics Page](#metrics-page)
  - [Metrics Topology](#metrics-topology)
  - [Metrics Detail Modal](#metrics-detail-modal)
  - [Metrics Charts](#metrics-charts)
- [Settings Page](#settings-page)

---

## Introduction

CamelBee is an Apache Camel library for microservices monitoring and debugging. It provides an **embedded React UI** served directly from your application, offering route visualization, message tracing, debugging, and real-time metrics.

**Two ways to add it.** Use a **starter as your parent POM** for a new project (CamelBee then decides
your Camel/Spring Boot/Quarkus versions), or add the **core as a plain dependency** to a service you
already have (your BOM decides — the core declares its framework dependencies as `provided`).

The core path works on **Camel 4.8+ / Quarkus 3.15 LTS+ / JDK 17+**, so an existing service does not
need to upgrade its stack to run the current CamelBee. Each core README states the exact floor.

To enable your Camel microservices to work with CamelBee, follow the setup instructions in the core library READMEs:

- **Spring Boot:** [CamelBee SpringBoot Core README](https://github.com/camelbee/camelbee/tree/main/core/springboot-core)
- **Quarkus:** [CamelBee Quarkus Core README](https://github.com/camelbee/camelbee/tree/main/core/quarkus-core)
- **Standalone (plain Camel / `camel-main`):** [CamelBee Standalone Core README](https://github.com/camelbee/camelbee/tree/main/core/standalone-core)
- **Camel K:** runs on the Camel Quarkus runtime, but pins an older Camel — use `camelbee-quarkus-core-camelk` and see the [Camel K Sample README](https://github.com/camelbee/camelbee/tree/main/examples/allcomponent-camelk-sample)

Every `RouteBuilder` must also call `camelBeeRouteConfigurer.configureRoute(this)` as its first statement (on Quarkus and Spring Boot) - it installs the intercept strategies behind per-node tracing and `poll()` hops. On standalone, `CamelBee.register(main)` does the same. See the core READMEs.

For how the tracing actually works inside your application - the event notifier, the intercept strategies, and how the UI reads them - see [How CamelBee works](how-it-works.md).

For working examples, see the [camelbee-examples](https://github.com/camelbee/camelbee-examples) repository.

Once your application is running, the CamelBee UI is available at:

`http://localhost:8080/camelbee/`

> For the Standalone core, the UI is served on the separate camel-main management server instead, at `http://localhost:8081/camelbee` by default.

> On Camel K, expose the integration's HTTP port (e.g. `kubectl port-forward svc/your-route 8080:80`) and open `http://localhost:8080/camelbee`.

The UI has three main sections accessible from the top navigation bar: **Debugger**, **Metrics**, and **Settings**.

---

## Debugger Page

The Debugger page is the main workspace of CamelBee. It visualizes the topology of your Camel routes as an interactive graph, showing routes, endpoints, and their interconnections.

![Debugger Page](../images/debugger_page.png)

### Route Visualization

- Routes are displayed as nodes in the graph, color-coded by type (REST, DIRECT, KAFKA, HTTP, CXF, etc.).
- Connections between routes are shown as dashed lines, with colors indicating successful (green) or failed (red) message flow.
- The graph is interactive — you can zoom in/out and pan to explore complex route topologies.
- The health panel on the left displays the application status, context name, framework version, Camel version, JVM, and garbage collector information. Click **View Health Details** to see the full health check JSON response.

### Message Tracing

- Click **Start Tracing** in the toolbar to begin capturing messages exchanged between routes.
- Click **Stop Tracing** to stop capturing.
- Use the **timeline bar** at the bottom of the page to navigate through the captured messages. Move the slider or use the prev/next buttons to step through the message flow.
- The topology graph animates to show which routes and connections were involved at each point in time, with color-coded edges (green for success, red for failure).
- The **Clear** button resets all collected messages for a fresh start.

- Exchanges that end in an **unhandled exception** are traced too, and their closing marker is typed
  as an error. This matters most on routes started by a consumer (timer, file, JMS), where there is
  no caller to report the failure to.

> **Note:** When tracing is enabled, messages exchanged between routes are collected. Before using
> this in production, read [Using CamelBee in Production](#using-camelbee-in-production) — sensitive
> values are redacted by default, and you can restrict tracing to a single transaction.

### Message Panel

Click on any connection badge (the numbered circles on the edges between routes) to open the Message Panel on the right side.

![Message Panel](../images/debugger_messages.png)

The Message Panel displays the details of each message exchanged within that connection:

- **Request headers and body** sent for the interaction.
- **Response headers and body** received for the interaction.
- If an exception occurred, the error message is displayed and the status shows **FAILURE** instead of **SUCCESS**.
- Navigate through messages using the **Prev** and **Next** buttons.
- Click **Go to timeline position** to jump to the exact point in time when this interaction occurred on the global timeline.

### Waterfall Panel

Click **Waterfall** in the toolbar to open a timing view along the bottom of the page. It answers the
question the graph cannot: *where did the time actually go?*

![Waterfall Panel](../images/debugger_waterfall.png)

- Each row is one **hop**, drawn as a bar positioned by when it started and sized by how long it took.
- Rows are grouped into **flows**. A flow is one request together with everything it spawned, so the
  branches created by `wireTap`, `multicast`, `split`, `recipientList` and `seda` handoffs appear
  **indented underneath the exchange that started them** rather than as unrelated entries.
- The flow header shows the total hop count and duration, and is marked **OK** or **Error**.
- Failed hops are drawn in red; a hop still in flight shows a dash instead of a duration.
- A wide parent bar over near-instant children usually means waiting rather than working — retry
  delays and poll timeouts show up exactly this way.
- Click a flow header to collapse or expand it. With more than three flows, only the newest is
  expanded by default.
- The panel follows the **timeline bar**, so it always shows the same slice as the graph.

> Very large flows (for example a `split()` over thousands of items) are capped for rendering, with a
> note saying how many hops are hidden. The slowest hops are always shown, wherever they occur, and
> the flow header always counts and times **all** of them.

### Linking the Waterfall and the Graph

The waterfall and the topology graph are two views of the same data, and selecting in one highlights
the other:

![Waterfall linked to the graph](../images/debugger_waterfall_linked.png)

- **Click a connection badge on the graph** → its hops are highlighted in the waterfall, the flow
  containing them is expanded, and the panel scrolls to them if they are below the fold.
- **Click a bar in the waterfall** → the matching connection is selected on the graph and its Message
  Panel opens. Clicking the same bar again clears the selection.

Bars with no matching connection on the graph are not clickable.

### Health Panel

The Health Panel provides a quick overview of your microservice's health status.

![Health Panel](../images/debugger_health.png)

- The panel on the left shows the application status (UP/DOWN), context name, framework version, Camel version, JVM, and garbage collector information.
- Click **View Health Details** to open a modal displaying the full health check JSON response, including `camel-context`, `camel-routes`, and `camel-consumers` status.

### Filtering Messages

There are **two** filter boxes in the toolbar, and they do different things:

![Toolbar filters](../images/debugger_toolbar_filters.png)

| Box | Colour | What it does |
|-----|--------|--------------|
| **Only trace containing…** | amber | Decides what the server **records at all**. Applied when tracing starts, or on Enter. |
| **Filter messages…** | grey | Hides rows that were **already recorded**, in your browser only. |

For everyday debugging the grey box is enough. For a busy or production application, use the amber
one — see [Tracing a Single Transaction](#tracing-a-single-transaction).

### Resizing the Panels

Both side panels can be resized by dragging the grip on their inner edge:

- The **Waterfall** panel: drag its top edge up or down.
- The **Message** panel: drag its left edge sideways.

The grips also respond to the arrow keys once focused. Sizes are remembered across page reloads.

---

## Using CamelBee in Production

CamelBee's tracer is designed to be safe to switch on outside development: it starts **off**, and it
**switches itself off** after a period of inactivity (`camelbee.tracer-max-idle-time`). Two further
features exist specifically for production use, and the sections after them cover who is allowed to
reach it in the first place.

### Redacting Sensitive Data

Traced headers and bodies are shown in the UI and written to the application log, so anything
captured is disclosed. CamelBee therefore **redacts sensitive values by default** — you do not have
to switch this on.

- Keys such as `password`, `token`, `authorization`, `apikey`, `creditcard`, `cvv`, `iban` and `ssn`
  are replaced with `***`.
- Matching ignores case and separators, so one `apikey` entry also catches `X-Api-Key`.
- Set `camelbee.masked-keys` to your own comma-separated list to replace the defaults.

**Know the limits.** Header redaction is exact, because the key name is known. Body redaction is
best-effort pattern matching over JSON, XML and form-encoded payloads: it cannot redact a field you
did not configure, and a body in some other format is left untouched. If a body must never be
captured under any circumstances, set `camelbee.tracer-body-enabled = false`, which reads no body
text at all. Note that this setting does **not** affect headers — redaction is what protects those.

### Tracing a Single Transaction

In an application handling hundreds of exchanges a second, capturing everything is neither readable
nor safe. Type a value into the amber **Only trace containing…** box before starting tracing, and
CamelBee records **only the flow that contains it**:

- Type an order id, a customer reference, a correlation id — anything that appears in the body or the
  headers of the request you are chasing.
- Once any message of an exchange matches, the **whole** exchange is kept, and so are the branches it
  spawns. You get the complete flow, not fragments of it.
- Everything else is never recorded at all — not merely hidden from view.
- Matching is case-insensitive, and runs against the text **after** redaction, so a value that
  masking removes cannot be searched for.

Press **Enter** to apply the filter while tracing is already running; it is also applied
automatically when you press **Start Tracing**. Changing it starts a fresh investigation.

### Signing In

From 4.0, CamelBee asks for a username and password before showing you anything. Authentication is
**on by default** (`camelbee.auth-enabled`), so opening the UI on a protected application presents a
login form rather than the debugger.

- The default user name is **`camelbee`**.
- If the application has no password configured, **one is generated when it starts and written to the
  application log**, next to a line telling you to set your own. On your own machine that is where to
  look for it.
- Your session lasts as long as you are using it. Each request extends it, and it ends after two
  minutes of inactivity (`camelbee.session-timeout`) — so a tab left open on a shared screen stops
  being a way in.
- The session belongs to the browser tab. Closing the tab signs you out.

If an application runs with `camelbee.auth-enabled: false` — as the CamelBee samples do — no login
form appears and the debugger opens directly.

**Why the login exists even though CamelBee is a debugging tool.** The API is not read-only: anyone
who can reach it can *switch tracing on* and then read the traffic that flows through the
application. Redaction decides what is recorded; the login decides who can read it.

### Restricting Who Can Reach CamelBee

Signing in is one layer, and it is not a reason to publish a debugging interface to the internet.
Two things remain worth knowing:

- The **topology** is available to anyone who signs in, even when tracing has never been switched on.
  It lists every route and endpoint URI, which typically includes internal hostnames and queue names.
  Credentials inside a URI are redacted.
- **One shared credential is a gate, not an identity.** There is no per-user audit, and no way to
  revoke a session before it expires. That is the right weight for a debugging tool, not for a system
  of record.

> **Keep CamelBee inside your network perimeter.** Reach it over an internal address, a VPN or a
> port-forward, with the login as the second line of defence rather than the only one. And serve it
> over HTTPS — the password and the session token are readable in transit otherwise.

If your organisation needs single sign-on or an audit trail, turn CamelBee's own login off
(`camelbee.auth-enabled: false`) and put your framework's security in front of `/camelbee` instead.
The configuration for each runtime is in the **Securing the CamelBee endpoints** section of the core
README for [Quarkus](../core/quarkus-core/README.md#securing-the-camelbee-endpoints),
[Spring Boot](../core/springboot-core/README.md#securing-the-camelbee-endpoints) and
[Standalone](../core/standalone-core/README.md#securing-the-camelbee-endpoints).

### One More Way Traced Data Leaves the Application

Everything above is about the HTTP endpoints. There is a second path, and it is easy to overlook:

`camelbee.logging-enabled` writes every traced message — body and headers — into the **application
log**. Redaction is applied first, so the same masking protects it. But none of the tracer's other
safety mechanisms do:

- it is **not** affected by the **Start Tracing** / **Stop Tracing** toggle in the UI;
- it is **not** affected by `camelbee.tracer-max-idle-time`, so it never switches itself off;
- it is **not** affected by the **Only trace containing…** capture filter, so narrowing an
  investigation in the UI does not narrow what is logged.

If it is switched on, every message is written for as long as the application runs — and in most
production setups logs are shipped to a central platform with a broader audience and a much longer
retention than a debugging session. Leave `camelbee.logging-enabled` off in production unless you
deliberately want traced payloads in your log pipeline.

---

## Metrics Page

The Metrics page allows you to monitor your Camel microservice's performance and health in real time. It has two views: **Topology** and **Charts**, which you can toggle using the buttons in the top-right corner.

### Metrics Topology

The topology view shows the same route graph as the Debugger page, but overlaid with exchange count metrics. This lets you visualize traffic flow across your routes.

### Metrics Detail Modal

Click **show all metrics** to open a modal displaying all available metrics from your application's metrics endpoint.

![All Metrics](../images/metrics_all_metrics.png)

- Use the **filter text field** at the top of the modal to search metrics by keyword and quickly find the data you need.

![Filtered Metrics](../images/metrics_filtered_metrics.png)

### Metrics Charts

Switch to the **Charts** view to see real-time charts tracking key performance indicators:

![Metrics Charts](../images/metrics_charts.png)

- **CPU Usage** — System CPU and process CPU usage over time.
- **GC Average Pauses** — Garbage collection pause total over time.
- **JVM Memory Usage** — Heap used vs heap max over time.
- **Threads** — Live, daemon, and peak thread counts over time.

Charts auto-refresh based on the metrics refresh rate configured in Settings.

---

## Settings Page

The Settings page allows you to configure the CamelBee UI to match your application's setup.

![Settings](../images/settings_page.png)

Available settings:

| Setting | Description | Default |
|---------|-------------|---------|
| **Theme** | Switch between Light and Dark mode | Light |
| **Health URL** | Path to the health endpoint | `/health` |
| **Health Refresh Rate** | How often to poll the health endpoint (2–10 seconds) | 5 secs |
| **Metrics URL** | Path to the metrics endpoint | `/metrics` |
| **Metrics History** | Duration of metrics history to retain (300–600 seconds) | 300 secs |
| **Metrics Refresh Rate** | How often to poll the metrics endpoint (2–10 seconds) | 5 secs |
| **Max Characters in a Text Field** | Maximum characters displayed in message text fields (1000–30000) | 10000 |

Settings are persisted in the browser's local storage and applied immediately.

### Health and Metrics URLs per runtime

The defaults match the Spring Boot sample, which maps the endpoints to the server root. **On any
other runtime you have to change them**, or the Health panel and Metrics page stay empty with 404s
in the browser console:

| Runtime | Health URL | Metrics URL |
|---------|------------|-------------|
| Spring Boot | `/health` | `/metrics` |
| Quarkus (incl. Camel K) | `/q/health` | `/q/metrics` |
| Standalone | `/observe/health` | `/observe/metrics` |

The Metrics page reads the **Prometheus exposition format**, so on Spring Boot point it at the
`prometheus` endpoint (the sample maps it to `/metrics`), not `/actuator/metrics`, which returns
JSON. The endpoint also has to exist: Quarkus needs the
`quarkus-micrometer-registry-prometheus` extension, and standalone needs micrometer on the
classpath.
