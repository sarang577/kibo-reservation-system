# KIBO Reservation System

A Java 17 / Spring Boot 3.3.5 service for managing reservations during limited product drops. MySQL is the source of truth for inventory and reservation holds, Redis caches drop reads, and a transactional outbox records reservation events in the same database transaction before a scheduled publisher sends them to RabbitMQ.

## Features

- List all drops and retrieve an individual drop.
- Create temporary inventory holds (5 minutes by default).
- Confirm or cancel an active hold.
- Expire active holds and release reserved inventory.
- Use pessimistic database row locking for inventory-changing operations.
- Cache drop reads in Redis with short TTLs and cache eviction after inventory mutations.
- Persist reservation events through the transactional outbox pattern.
- Publish reservation events to RabbitMQ.
- Run the application and its dependencies using Docker Compose.
- Test service logic with JUnit 5 and Mockito.

## Prerequisites

**Recommended:** Docker Desktop with Docker Compose.

For local development without Docker, install Java 17+, Maven, MySQL 8, Redis 7, and RabbitMQ 3.

## Environment Configuration

Docker Compose reads environment variables from a `.env` file in the project root, alongside `docker-compose.yml`.

1. Create a `.env` file or copy `.env.example` to `.env`.
2. Configure the required variables:

```dotenv
MYSQL_DATABASE=kibo
MYSQL_USER=kibo
MYSQL_PASSWORD=your-local-database-password
MYSQL_ROOT_PASSWORD=your-local-root-password

REDIS_PORT=6379

RABBITMQ_DEFAULT_USER=guest
RABBITMQ_DEFAULT_PASS=guest
```

3. Replace the example database passwords with your local development credentials. If you use the supplied Compose configuration with an existing MySQL volume, keep the credentials consistent with the database already initialized in that volume.

The actual `.env` file is excluded from version control through `.gitignore`. Commit `.env.example`, not `.env`. Do not commit real passwords, tokens, or other secrets.

## Run with Docker Compose

Run the following commands from the project root:

```bash
docker compose up -d --build
```

Check service status:

```bash
docker compose ps
```

To view application logs:

```bash
docker compose logs --tail=100 app
```

### Verify the application

- List drops: `GET http://localhost:8080/api/drops`
- Get a specific drop: `GET http://localhost:8080/api/drops/1`
- RabbitMQ management UI: `http://localhost:15672`

RabbitMQ credentials are configured using `RABBITMQ_DEFAULT_USER` and `RABBITMQ_DEFAULT_PASS` in `.env`. The default local development values in the example are `guest` / `guest`.

To stop the services:

```bash
docker compose down
```

To remove the persisted MySQL volume as well, run:

```bash
docker compose down -v
```

**Warning:** The `-v` option deletes the local database volume and its data.

## Run Tests

### Option 1: Maven installed locally

Run this from the project root:

```bash
mvn test
```

### Option 2: Run Maven in Docker

If Maven is not installed and the project does not include `mvnw.cmd`, use the Maven Docker image from PowerShell:

```powershell
docker run --rm `
  -v "${PWD}:/workspace" `
  -w /workspace `
  maven:3.9-eclipse-temurin-17 `
  mvn test
```

The command mounts the project into a temporary Maven container and runs the test suite. The Maven image may need to be downloaded the first time.

### Verification Status

The following checks were completed during development:

- **Unit tests:** 5 tests passed; 0 failures, 0 errors, 0 skipped.
- **Build:** Maven reported `BUILD SUCCESS`.
- **API smoke test:** The drop-list endpoint returned the seeded drops.
- **Reservation flow:** A hold was created and confirmed successfully.
- **Cancellation and expiration:** A hold was cancelled and verified as `CANCELLED`. Expired holds were verified as `EXPIRED`, and reserved inventory was restored.
- **Redis:** Drop caching and the presence of cache keys were verified; cache entries use short TTLs.
- **RabbitMQ:** Reservation events were observed in the configured queue.
- **MySQL:** Inventory and hold records were inspected after reservation lifecycle operations.
- **Concurrency:** Thirty concurrent reservation requests were issued against a drop with 25 available units. The collected HTTP results showed 25 successful requests and 5 rejected requests. MySQL contained 25 active one-unit holds for this test, and available inventory reached zero without becoming negative.
- **Docker Compose and `.env`:** The application image was rebuilt and the Compose services started successfully after the environment configuration was updated. Recheck the API after configuration changes if you need to confirm post-change behavior.

These checks demonstrate the observed behavior in the tested scenarios. They are not a complete integration test suite or a production-readiness certification. Additional testing is needed for outbox retries, duplicate delivery, invalid requests, boundary cases, and broader concurrency workloads.

## Run from STS on Windows

For running the application directly from Spring Tool Suite (STS), ensure the dependencies are running and the datasource and messaging configuration match your environment.

Typical local dependency endpoints are:

- MySQL: `localhost:3306` for a local MySQL installation, or `localhost:3307` for the Compose-managed MySQL instance.
- Database: `kibo`
- Database user: `kibo`
- Redis: `localhost:6379`
- RabbitMQ: `localhost:5672`

Configure database passwords and other settings through your local environment or an untracked local configuration file. Do not commit local secrets.

**Important:** In the provided Docker Compose setup, MySQL is exposed on host port `3307` to avoid conflicting with an existing local MySQL installation on port `3306`. From inside the Docker Compose network, the application connects to MySQL using the hostname `mysql` and container port `3306`. If you run the application directly from STS against the Compose-managed database, use `localhost:3307`.

The Compose `.env` file is used for variable substitution by Docker Compose; Spring Boot running directly from STS does not automatically load that file as its application configuration.

## APIs

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/drops` | List all drops |
| GET | `/api/drops/{dropId}` | Get a specific drop |
| POST | `/api/drops/{dropId}/holds` | Create an inventory hold |
| POST | `/api/holds/{holdId}/confirm` | Confirm a hold |
| POST | `/api/holds/{holdId}/cancel` | Cancel a hold |
| GET | `/api/holds/{holdId}` | Get hold status |

### Create a Hold

Example request to `POST /api/drops/1/holds`:

```json
{
  "customerId": "customer-101",
  "quantity": 1
}
```

Use the returned hold ID to confirm or cancel the hold.

## Event Flow

Reservation state changes and their corresponding outbox records are intended to be written within the same MySQL transaction. The scheduled outbox publisher sends pending event JSON to RabbitMQ using:

- Exchange: `kibo.reservation.events`
- Routing key: `reservation.event`
- Queue: `kibo.reservation.events.queue`

Delivery is **at least once**. If the publisher sends an event but crashes before marking its outbox row as published, the event may be sent again. Consumers should therefore be idempotent.

The included publisher is a simple implementation intended for development. Multi-instance production deployments should introduce reliable row claiming or locking, appropriate retry and dead-letter handling, and operational monitoring. A queued event does not, by itself, prove that a consumer processed it.

## Important Design Notes

- MySQL remains authoritative for inventory; Redis is only a cache.
- Cached drop reads have short TTLs and are evicted after inventory mutations.
- The outbox pattern avoids relying on a direct RabbitMQ publish as part of the business transaction.
- The outbox table is managed by Hibernate in the development configuration (`ddl-auto=update`). Use versioned database migrations, such as Flyway, in production.
- Docker Compose credentials are for local development only and must not be reused in production.
- Mockito unit tests do not establish real database locking or concurrency guarantees. The concurrent reservation test validated one scenario against the running application and MySQL, but additional integration and stress testing are needed before making broader reliability claims.
