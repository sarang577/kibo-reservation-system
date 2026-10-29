# AI Usage Notes

This file documents how AI assistance was used during development of the KIBO Reservation System. It distinguishes AI-assisted implementation and documentation from verification performed locally.

## Accepted Assistance

AI assistance was used to:

- Break down the assignment into API design, persistence, inventory safety, caching, messaging, testing, and documentation tasks.
- Develop and explain the Spring Boot controller, service, and repository structure.
- Explain the reservation lifecycle, including hold creation, confirmation, cancellation, and expiration.
- Suggest Redis caching with short TTLs and cache eviction after inventory mutations.
- Explain and implement the transactional outbox pattern for recording reservation events in the same database transaction as business state changes.
- Draft Docker Compose configuration and project documentation.
- Troubleshoot Redis JSON serialization and cache-related issues.
- Identify an approach for running Maven tests in Docker when a Maven wrapper was not present in the project.
- Explain approaches for validating inventory consistency and concurrent reservation requests against MySQL.
- Explain how to externalize configuration values into a `.env` file, exclude local environment files from version control, and provide a `.env.example` template.

AI-generated suggestions were reviewed and adjusted during local development. The developer remains responsible for understanding the code and verifying that it meets the assignment requirements.

## Configuration and Secret Handling

- Docker Compose reads environment variables from a `.env` file located alongside `docker-compose.yml` in the project root.
- Database credentials and RabbitMQ credentials are supplied through environment variables instead of being hardcoded directly in the Compose service configuration.
- `.gitignore` excludes `.env` and other local environment files from version control while allowing `.env.example` to be committed.
- `.env.example` documents the configuration variables required to run the application. It should contain placeholders or non-sensitive local-development defaults, not production secrets.
- Existing database credentials must remain consistent with the MySQL database already initialized in the persistent Docker volume. Changing `.env` does not automatically change the password of an existing MySQL user.
- Docker Compose uses the `.env` file for variable substitution. The application running directly from Spring Tool Suite (STS) does not automatically load that file as Spring Boot configuration.
- Local development credentials must not be reused in production. Production deployments should use an appropriate secret-management solution.

## Local Verification Completed

The following checks were performed during development:

- The Maven test suite completed successfully using a Maven Docker image.
- `ReservationServiceTest` reported 5 tests run, 0 failures, 0 errors, and 0 skipped.
- Maven reported `BUILD SUCCESS`.
- The application started successfully using Docker Compose.
- The drop-list API returned the seeded drops.
- A reservation hold was created and successfully confirmed.
- A reservation hold was cancelled, and its status was verified as `CANCELLED`.
- Hold expiration was tested end to end. Expired holds were marked `EXPIRED`, and the corresponding inventory was restored.
- Redis cache behavior was checked by requesting drop data and inspecting cache keys.
- Reservation events were observed in the configured RabbitMQ queue.
- MySQL tables, including `drops`, `holds`, and `outbox_events`, were inspected.
- MySQL inventory and hold records were checked after reservation lifecycle operations.
- A concurrent reservation test issued 30 requests against a drop containing 25 available units. The collected HTTP results showed 25 successful requests and 5 rejected requests.
- After the concurrency test, MySQL showed 25 active one-unit holds for that test, with available inventory at zero and no negative inventory observed.
- After the environment configuration was updated, `docker compose up -d --build` completed successfully, and the application and its dependency containers started. The API should be rechecked after configuration changes to confirm post-change behavior.

These checks validate the observed scenarios, not every possible API flow, failure scenario, or concurrency condition.

## Design Considerations and Limitations

- **Inventory source of truth:** MySQL is authoritative. Redis must not be used as the source of truth for inventory.
- **Transactional outbox:** Reservation state changes and outbox records are intended to be persisted in the same database transaction. This avoids depending on a direct message publish to achieve atomicity with MySQL.
- **At-least-once delivery:** A publisher crash after sending an event but before marking it as published can result in duplicate delivery. Consumers should be idempotent.
- **Publisher scaling:** The included scheduled publisher is a simple implementation. Multi-instance deployments require robust row claiming or locking, retries, dead-letter handling, and monitoring.
- **Cache consistency:** Short TTLs and eviction after mutations reduce stale reads, but cache invalidation and concurrent requests should be tested further.
- **Concurrency:** The concurrent HTTP test demonstrated 25 successful reservations and 5 rejected requests for 25 available units. MySQL contained 25 active holds, and inventory did not become negative. This validates the tested scenario but does not prove correctness under every possible concurrent workload.
- **Database schema:** Hibernate schema updates are suitable for development. Production deployments should use versioned migrations such as Flyway.
- **Security and operations:** Development credentials, error handling, logging, monitoring, and operational recovery need further review before production use.

## Not Assumed

The following claims should not be made without additional evidence:

- That all reservation flows and edge cases have been tested.
- That concurrency handling is correct under every possible database and application workload.
- That every RabbitMQ event has been consumed and processed.
- That outbox retries and duplicate delivery have been tested.
- That the application is production-ready.
- That unit tests alone prove distributed-system reliability or concurrency safety.
- That externalizing local configuration into `.env` by itself provides production-grade secret management.
