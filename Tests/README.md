This project contains the end-to-end tests, and code snippets used for trying things out.

## Running tests

| What | Command | Notes |
|---|---|---|
| Unit tests (all modules) | `mvn verify` | No server needed. Coverage reports in `*/target/site/jacoco/`. |
| End-to-end suite | `mvn install -DskipTests` then `mvn verify -Ptest -pl Tests` | Starts an embedded server and installs plugins from Maven Central. |
| Client tests against your own server | `mvn test -pl BimServerClientLib -DexcludedTestGroups=none` | Needs a server on `localhost:8080` with `admin@bimserver.org` / `admin`. |

End-to-end options:

- `-Dbimserver.test.port=7010`: port for the embedded server.
- `-Dplugins=/path/one;/path/two`: load plugins from local directories instead of Maven Central.
- `-Difcplugins.version=0.0.107-SNAPSHOT`: compile against a locally built IfcPlugins.

## Writing tests

- Unit tests go in a module's `test/` directory, run with Surefire and must not need a server. Use JUnit Jupiter, AssertJ and Mockito (all inherited from the parent pom), and `@TempDir` for files.
- End-to-end tests go in `Tests/test/org/bimserver/tests/` and extend `TestWithEmbeddedServer`. `AllTests` picks up every class in that package tree; Failsafe runs it during `verify`.
- Tests that need a manually started server are tagged `@Tag("live-server")` and are skipped by default.
