# CamelBee Embedded UI

The React-based CamelBee UI that is bundled into the core libraries and served directly from your application at the `/camelbee` path. It provides route visualization, message tracing, debugging with replay, filtering, endpoint triggering, and metrics.

## Tech Stack

- **React 19** + **TypeScript**, built with **Vite**
- **@xyflow/react** + **dagre** for the interactive route topology graph
- **TanStack Query** for polling the CamelBee REST API, **Zustand** for state
- **Recharts** for the metrics charts, **Tailwind CSS** for styling
- **Vitest** + Testing Library for unit tests

## Development

```bash
npm install
npm run dev        # dev server with hot reload (proxies API calls to a running backend)
npm run test       # run unit tests
npm run build      # type-check + production build into ./dist
```

## How It Gets Embedded

The production build output (`ui/dist/`) is copied into each core library's jar at build time by a `maven-resources-plugin` execution (`copy-ui-dist`) in the core poms, so the UI is served by the application itself with no separate deployment:

| Runtime | Copied into | Served at |
|---------|-------------|-----------|
| Quarkus | `META-INF/resources/camelbee` | `http://localhost:8080/camelbee/index.html` |
| Spring Boot | `static/camelbee` | `http://localhost:8080/camelbee/index.html` |
| Standalone (`camel-main`) | `camelbee` (classpath root) | `http://localhost:8081/camelbee` (management server) |

Because the copy happens at core build time, **build the UI before building the core libraries** (running `mvn clean install` from the repository root handles this ordering).

The Vite `base` is `/camelbee/`, matching the serve path in all runtimes. The version badge in the navigation bar is hardcoded in `src/components/NavBar.tsx` — bump it together with the project version.

For a guide to the UI's pages and features, see the [CamelBee User Guide](../docs/camelbee_userguide.md).
