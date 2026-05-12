# How to Run

## Prerequisites

| Tool | Minimum Version |
|------|----------------|
| Java | 17 |
| Maven | 3.8+ |

Verify your setup:
```bash
java -version
mvn -version
```

---

## 1. Build

From the `homework-1/` directory:

```bash
mvn package -DskipTests
```

This produces `target/banking-api-1.0.0.jar`.

---

## 2. Run

**Option A — Maven plugin (development)**
```bash
mvn spring-boot:run
```

**Option B — JAR directly**
```bash
java -jar target/banking-api-1.0.0.jar
```

**Option C — demo script**
```bash
chmod +x demo/run.sh
./demo/run.sh
```

The server starts on **http://localhost:8080**.

---

## 3. Test the API

### Quick smoke test with curl

```bash
# Create a transfer
curl -s -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "fromAccount": "ACC-12345",
    "toAccount":   "ACC-67890",
    "amount":      100.50,
    "currency":    "USD",
    "type":        "transfer"
  }' | jq .

# List all transactions
curl -s http://localhost:8080/transactions | jq .

# Get account balance
curl -s http://localhost:8080/accounts/ACC-67890/balance | jq .
```

### VS Code REST Client / IntelliJ HTTP Client

Open `demo/sample-requests.http` and click **Send Request** next to any block.

---

## 4. API Reference

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/transactions` | Create a transaction |
| `GET` | `/transactions` | List all (supports filters) |
| `GET` | `/transactions/{id}` | Get by ID |
| `GET` | `/accounts/{accountId}/balance` | Get account balance |
| `GET` | `/transactions/export?format=csv` | Download CSV export |

### Filters on `GET /transactions`

```
?accountId=ACC-12345        filter by account (sender or receiver)
?type=transfer              deposit | withdrawal | transfer
?from=2024-01-01            start date (ISO 8601, inclusive)
?to=2024-01-31              end date (ISO 8601, inclusive)
```

---

## 5. Configuration

Edit `src/main/resources/application.properties` to change defaults:

```properties
server.port=8080
```
