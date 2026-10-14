# Validation report — October 14, 2026

## Verified in this workspace

| Check | Result |
|---|---|
| Java 21 / Spring Boot 4.0.8 backend package | Passed; four executable service JARs built |
| Backend unit tests | 43 passed, no failures or errors |
| React production build | Passed |
| Frontend API unit tests | 3 passed |
| Playwright browser test | Passed: login, desktop/mobile navigation, order dialog, and ruleset comparison |
| Event JSON Schema and consumer/provider Pact | Passed against the Java provider serialization fixture |
| Terraform formatting and validation | Passed; AWS provider pinned and lockfile generated |
| Local and AWS Docker Compose configuration | Passed; configuration validation only |
| Python scripts | Compiled successfully |
| Frontend production dependency audit | Zero reported vulnerabilities |

The browser test uses deterministic API fixtures. Its screenshots are UI previews, not evidence that MySQL, MongoDB, Kafka, or AWS is running. Pact validates a specific event contract against a Java-generated fixture; it is not full end-to-end messaging verification.

## Not yet verified

Docker image builds, full-stack startup, Testcontainers execution, full-stack acceptance/recovery scenarios, backup restoration, the 30-minute benchmark, and live AWS hosting have not run. No throughput or memory result is claimed. Testcontainers tests compile and are enabled by `mvn -Pintegration verify`; they are not counted among the 43 unit tests.

The Docker CLI is installed, but its Linux engine pipe is missing. Its desktop-start command also cannot find the installation registry entry. Automatic approval review rejected the direct Docker Desktop launch because sandbox approvals are disabled. Start or repair Docker Desktop outside this session before attempting integration verification.

AWS remains unprovisioned. The billing/eligibility gate cannot pass without actual credit balance, expiry dates, region, account access, a cost estimate, and a measured memory footprint. A scheduled host shutdown does not remove persistent storage charges; complete teardown is still required.

## Implementation boundaries

- SQL service-owned tables store JSON aggregate payloads with relational IDs, optimistic versions, and selected indexes; they are not a normalized financial ledger.
- Projection rebuild coordination supports one Live View instance.
- Default traffic limits are 500 orders and 1000 executions. The performance override is local-only.
- CSV supports unquoted fields with an exact header; no full RFC 4180 parser is claimed.
- All instruments, rules, portfolios, and executions are synthetic.

Machine-readable details: `validation-results.json`. Startup, real-stack acceptance, benchmarking, and deployment instructions are in the repository README and operations documentation.


## Windows startup correction

The user subsequently reported successful builds for all five application images, followed by MySQL and Kafka initialization failures. The generated shell scripts contained Windows CRLF line endings. Seven scripts were converted to LF; `.gitattributes`, a pre-bootstrap/CI line-ending check, a Docker runtime normalization guard, and `scripts/repair-startup.ps1` were added.

The line-ending check passes. The repair script preserves volumes and reruns idempotent MySQL initialization. Runtime recovery is still unverified because this session cannot access the Docker engine pipe. Run the repair in the user's normal PowerShell window and inspect any printed logs.


## Login 401 correction

The frontend and real-stack test client previously sent an empty bearer header during login. Spring's bearer-token resolver rejects that malformed header before credential authentication. Public login requests now omit Authorization, including when an expired token remains in memory; authenticated requests retain valid bearer headers.

Verified: six frontend API tests, two new tests using Spring's actual bearer-token resolver, three Python client tests, and a successful production frontend build. These targeted checks supplement the earlier 43-test backend run. Live login still requires rebuilding the web container in the user's PowerShell session.


## User-confirmed local startup

After startup-script repairs, the user confirmed all four backend services, MySQL, MongoDB, and Kafka were healthy; the web container was running, and both initialization containers exited successfully. This confirms reported container startup, not full workflow acceptance or benchmark execution. The later login-header fix is covered by targeted tests; live login confirmation remains separate.
