# CamelBee Security Modules

Optional modules providing reusable JWT validation Camel routes for securing your Camel microservices:

- `quarkus-security` — for Camel Quarkus applications
- `springboot-security` — for Camel Spring Boot applications

Both modules provide the same functionality:

- A `direct:validateJWT` route that validates JWT bearer tokens (signature, issuer, audience, expiry with configurable clock skew) — call it from your own routes to protect endpoints.
- A `direct:fetchJWKS` route that fetches and caches the JSON Web Key Set from your identity provider.
- Authorization utilities for role/scope checks, and typed exceptions (`InvalidTokenException`, `TokenExpiredException`, `InsufficientPrivilegesException`, ...).

## Configuration

Configure via `camelbee.security.*` properties:

```properties
camelbee.security.enabled = true
camelbee.security.issuer = https://your-idp/realms/your-realm
camelbee.security.audience = your-audience
camelbee.security.jwks-url = https://your-idp/realms/your-realm/protocol/openid-connect/certs
camelbee.security.jwks-cache-duration = 3600
camelbee.security.algorithm = RS256
camelbee.security.clock-skew = 30
```

## Installation

```xml
<dependency>
  <groupId>io.camelbee</groupId>
  <artifactId>camelbee-quarkus-security</artifactId> <!-- or camelbee-springboot-security -->
  <version>3.3.1</version>
</dependency>
```
