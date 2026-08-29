# HealthConnect — Hospital Management System (API)

A Spring Boot + MySQL backend for a small hospital management system: patients,
doctors, specialties, doctor availability, and appointments.

## 📋 Learning backlog

Actively enhancing this project as a way to learn backend concepts (events, async,
caching, scheduling, reporting, payments, third-party integration, design patterns).

👉 **[See the full backlog → BACKLOG.md](BACKLOG.md)** — 26 step-by-step tickets,
each with description, acceptance criteria, validation, the concept to learn, and a
code snippet.

## Tech stack

- Java · Spring Boot · Spring Data JPA
- MySQL
- Bean Validation, global exception handling, `ApiResponse` envelope

## Modules

| Area | Endpoints (base `/api/v1`) |
|---|---|
| Patients | `POST /patients`, `GET /patients`, `PUT /patients/{id}`, `DELETE /patients/{id}` |
| Doctors | `POST /doctors`, `POST /doctors/search`, `PUT /doctors/{id}`, `DELETE /doctors/{id}` |
| Doctor details | `GET /doctors/details/{id}` (profile + specialties + availability) |
| Availability | `POST /doctors/{id}/availability`, `DELETE /doctors/{id}/availability/{availabilityId}` |
| Specialties | `POST /specialties`, `GET /specialties`, `PUT /specialties/{id}`, `DELETE /specialties/{id}` |
| Doctor ↔ Specialty | `POST/GET /doctors/{id}/specialties`, `DELETE /doctors/{id}/specialties/{specialtyId}` |
| Appointments | `POST /appointments`, `GET /appointments/{doctorId}?appointmentDate=YYYY-MM-DD` |

## Getting started

```bash
# 1. Create the MySQL database
#    CREATE DATABASE healthconnect;
# 2. Set your DB credentials in src/main/resources/application.properties
# 3. Run
./mvnw spring-boot:run
```

The API runs on `http://localhost:8080`.

> **Security note:** move DB credentials and any API keys out of
> `application.properties` into environment variables before deploying.

## Frontend

A React (Vite + Ant Design) admin UI consumes this API — patients, doctors,
specialties, a Microsoft-Calendar-style doctor availability view, and an
appointment booking board.
