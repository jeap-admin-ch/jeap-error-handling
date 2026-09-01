# Development

Working on this repository (the EHS library itself, not an instance).

## Build

```shell
# Full build (also runs npm ci + ng build for the UI module on first build)
./mvnw clean install
```

The Angular UI in `jeap-error-handling-ui` is built by Maven via npm and bundled into the service library
as static resources. Note that the UI build is skipped when `target/classes/static/` already exists —
delete that folder to force a rebuild.

## Run locally

1. Publish a local snapshot of this repository.
2. Use the [jme-messaging-example](https://git.example.com/jme/jme-messaging-example)
   project with a dependency on the snapshot:
   start its Docker infrastructure, the OAuth mock server (profile `local`), and the Error-SCS backend
   (profile `local-ui`).
3. Start the UI with `ng serve` (localhost:4200) from `jeap-error-handling-ui` in this repository.

## Tests

```shell
# Backend unit tests
./mvnw -pl jeap-error-handling-service test

# Integration tests (*IT, embedded Kafka + H2) run with the failsafe plugin
./mvnw -pl jeap-error-handling-service verify

# UI unit tests (Jest)
cd jeap-error-handling-ui && npm test
```

## Browser end-to-end tests

The `Ui*BrowserIT` classes in `jeap-error-handling-service` drive the bundled Angular UI with
[Playwright for Java](https://playwright.dev/java/) against the fully booted service (embedded Kafka, H2).
Authentication runs the real OIDC authorization code flow against the `OidcAuthorizationMockServer` from
`jeap-spring-boot-security-starter-test` (one instance with role profiles per tested role set, see
`UiBrowserTestBase`). The tests run with the other integration tests during `./mvnw verify` and require a
local Google Chrome installation (Playwright launches it via the `chrome` channel, no browser download
needed). Run them individually with:

```shell
./mvnw -pl jeap-error-handling-service verify -Dit.test='Ui*BrowserIT'
```

Test data is seeded either over Kafka, so that the failure travels the production path, or directly through the
repositories where the ingestion path is covered elsewhere — `UiModulithPublicationBrowserIT` seeds its failed
Modulith publications that way, because their intake is covered by `ModulithPublicationErrorHandlingIT`.

## Related

- [Getting Started](getting-started.md) — setting up an EHS instance for a business system
- [Architecture](architecture.md) — module and component overview
