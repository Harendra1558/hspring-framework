# 5. REST API Reference

Base URL: `http://localhost:8080` (configurable, see [Running](06-running-testing-extending.md#configuration)).
All API responses are JSON (`application/json`, UTF-8) except `/`, which returns HTML.

## Endpoints

| Method | Path | Description | Success |
|---|---|---|---|
| GET | `/` | HTML landing page listing the API | 200 |
| GET | `/health` | Health check with JVM info | 200 |
| GET | `/api/users` | List users, ordered by id (`?limit=N` optional) | 200 |
| GET | `/api/users/{id}` | Get one user | 200 |
| POST | `/api/users` | Create a user | 201 + `Location` header |
| PUT | `/api/users/{id}` | Partial update (`name` and/or `email`) | 200 |
| DELETE | `/api/users/{id}` | Delete a user | 200 |

The server starts with three users: John Doe (1), Jane Smith (2), Bob Johnson (3).

## User object

```json
{
  "id": 1,
  "name": "John Doe",
  "email": "john@example.com",
  "createdAt": 1761552000000,
  "updatedAt": 1761552100000
}
```

`updatedAt` is omitted until the user is first updated. Timestamps are epoch milliseconds.

## Examples

### Health check

```bash
curl http://localhost:8080/health
```
```json
{"system":{"totalMemory":"254 MB","availableProcessors":8,"javaVersion":"17.0.17","freeMemory":"240 MB"},"version":"1.1.0","status":"UP","timestamp":1761552000000}
```

### List users

```bash
curl "http://localhost:8080/api/users?limit=2"
```
```json
{"success":true,"count":2,"data":[
  {"id":1,"name":"John Doe","email":"john@example.com","createdAt":1761552000000},
  {"id":2,"name":"Jane Smith","email":"jane@example.com","createdAt":1761552000001}
]}
```

`limit` must be a non-negative integer.

### Get a user

```bash
curl http://localhost:8080/api/users/1
```
```json
{"success":true,"data":{"id":1,"name":"John Doe","email":"john@example.com","createdAt":1761552000000}}
```

### Create a user

```bash
curl -i -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"name": "Alice Wonder", "email": "alice@example.com"}'
```
```
HTTP/1.1 201 Created
Location: /api/users/4

{"success":true,"message":"User created successfully","data":{"id":4,"name":"Alice Wonder","email":"alice@example.com","createdAt":1761552000000}}
```

Rules: `name` required and not blank; `email` required and must look like `x@y.z`. Values are trimmed.

### Update a user

```bash
curl -X PUT http://localhost:8080/api/users/1 \
  -H "Content-Type: application/json" \
  -d '{"email": "john.new@example.com"}'
```
```json
{"success":true,"message":"User updated successfully","data":{"id":1,"name":"John Doe","email":"john.new@example.com","createdAt":1761552000000,"updatedAt":1761552100000}}
```

Send at least one of `name`, `email`. Missing fields keep their value.

### Delete a user

```bash
curl -X DELETE http://localhost:8080/api/users/3
```
```json
{"success":true,"message":"User deleted successfully"}
```

## Errors

Every error has the same shape:

```json
{"error": "<reason phrase>", "message": "<details>", "status": <code>, "path": "<request path>"}
```

Validation errors add `"field"`. (One exception: when a filter rejects a request by returning `false` without
writing its own response, the server answers `403` with `{"error", "message", "status"}` and no `path`.)

| Situation | Status | Example `message` |
|---|---|---|
| Missing/invalid field | 400 | `Valid email is required` (`"field": "email"`) |
| Non-numeric id | 400 | `User ID must be a valid number` (`"field": "id"`) |
| Bad `limit` | 400 | `limit must be a non-negative number` |
| Empty or non-JSON body | 400 | `Request body is required` / `Request body must be a JSON object with string fields` |
| Malformed HTTP request / body > 1 MB | 400 | `Malformed request line: …` |
| Rejected by an auth filter | 401 | (filter's message) |
| User doesn't exist | 404 | `User not found with id: 999` |
| Unknown path | 404 | `No handler found for GET /nope` |
| Path exists, wrong method | 405 + `Allow` header | `Method DELETE is not supported for /api/users` |
| Unexpected server error | 500 | `An unexpected error occurred` |

Example:

```bash
curl -i http://localhost:8080/api/users/999
```
```
HTTP/1.1 404 Not Found
Content-Type: application/json

{"error": "Not Found", "message": "User not found with id: 999", "status": 404, "path": "/api/users/999"}
```
