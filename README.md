# Latensys

**Real-time API monitoring, P95 latency analysis, and Redis-backed rate limiting platform**

Latensys is a Spring Boot application built to help you monitor API performance in real time, analyze latency trends, and protect services with rate limiting powered by Redis.

## Features

* Real-time API monitoring
* P95 latency tracking and analysis
* Redis-backed rate limiting
* REST API built with Spring Boot
* Persistence with Spring Data JPA
* PostgreSQL support
* Health and metrics exposure with Spring Boot Actuator

## Tech Stack

* **Java 17**
* **Spring Boot 3.4.1**
* **Spring Web**
* **Spring Data JPA**
* **Spring Data Redis**
* **PostgreSQL**
* **Spring Boot Actuator**
* **Maven**

## Project Structure

```text
latensys/
├── src/
├── .mvn/
├── mvnw
├── mvnw.cmd
├── pom.xml
└── .gitignore
```

## Getting Started

### Prerequisites

* Java 17 or newer
* Maven
* Redis
* PostgreSQL

### Clone the repository

```bash
git clone https://github.com/HarshitS17/latensys.git
cd latensys
```

### Configure the application

Set your database and Redis settings in `application.properties` or `application.yml`.

Example:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/latensys
spring.datasource.username=postgres
spring.datasource.password=your_password

spring.redis.host=localhost
spring.redis.port=6379
```

### Run the application

Using Maven wrapper:

```bash
./mvnw spring-boot:run
```

On Windows:

```bash
mvnw.cmd spring-boot:run
```

## What this project is for

Latensys is useful when you want to:

* understand API response times under load
* spot latency regressions early
* enforce request limits for stability and fairness
* keep performance data in a structured backend

## Future Improvements

* dashboard for latency trends
* alerting for slow endpoints
* request tracing and analytics
* configurable rate-limit policies
* exportable reports

## Contributing

Contributions are welcome. Feel free to open an issue or submit a pull request with improvements.

## License

Add a license file if you want the project to be open source under a clear license.
