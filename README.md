# Secure Audio Microservice

Spring Boot microservice that provides secure access to audio files using JWT authentication.

## Uruchamianie aplikacji

1. Kompilacja i uruchomienie:
```bash
mvn clean compile
mvn spring-boot:run
```

2. Alternatywnie (jeśli są problemy z Maven):
```bash
mvn clean package
java -jar target/spring-boot-1.0.0-SNAPSHOT.jar
```

## Testowanie JWT - Instrukcje krok po kroku

### 1. Pobieranie tokena JWT

**Metoda 1: Używając curl**
```bash
curl -X POST http://0.0.0.0:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "user", "password": "password"}'
```

**Metoda 2: Używając Postman/Insomnia**
- URL: `POST http://0.0.0.0:8080/api/auth/login`
- Headers: `Content-Type: application/json`
- Body (JSON):
```json
{
  "username": "user",
  "password": "password"
}
```

**Odpowiedź:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyIiwiaWF0IjoxNzI0MzM5MTAwLCJleHAiOjE3MjQzNDI3MDB9.signature",
  "type": "Bearer"
}
```

### 2. Weryfikacja tokena - Testowanie chronionych endpointów

**Test 1: Endpoint testowy**
```bash
# Zapisz token do zmiennej
export TOKEN="twój-jwt-token-tutaj"

# Testuj chroniony endpoint
curl -X GET http://0.0.0.0:8080/api/test \
  -H "Authorization: Bearer $TOKEN"
```

**Test 2: Endpoint audio**
```bash
curl -X GET http://0.0.0.0:8080/api/audio/stream/sample123 \
  -H "Authorization: Bearer $TOKEN"
```

### 3. Testowanie błędnych scenariuszy

**Brak tokena:**
```bash
curl -X GET http://0.0.0.0:8080/api/test
# Oczekiwana odpowiedź: 401 Unauthorized
```

**Nieprawidłowy token:**
```bash
curl -X GET http://0.0.0.0:8080/api/test \
  -H "Authorization: Bearer invalid-token"
# Oczekiwana odpowiedź: 401 Unauthorized
```

**Wygasły token:**
```bash
curl -X GET http://0.0.0.0:8080/api/test \
  -H "Authorization: Bearer expired-token"
# Oczekiwana odpowiedź: 401 Unauthorized
```

### 4. Sprawdzanie zawartości tokena (deweloperskie)

Możesz zdekodować token JWT online na https://jwt.io lub używając narzędzi deweloperskich:

**Przykład struktury tokena:**
```json
{
  "header": {
    "alg": "HS256",
    "typ": "JWT"
  },
  "payload": {
    "sub": "user",
    "iat": 1724339100,
    "exp": 1724342700
  }
}
```

### 5. Testowanie z różnymi narzędziami

**HTTPie:**
```bash
# Logowanie
http POST 0.0.0.0:8080/api/auth/login username=user password=password

# Test chronionego endpointu
http GET 0.0.0.0:8080/api/test Authorization:"Bearer YOUR_TOKEN"
```

**JavaScript (Fetch API):**
```javascript
// Logowanie
fetch('http://0.0.0.0:8080/api/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'user', password: 'password' })
})
.then(response => response.json())
.then(data => {
  const token = data.token;
  
  // Test chronionego endpointu
  return fetch('http://0.0.0.0:8080/api/test', {
    headers: { 'Authorization': `Bearer ${token}` }
  });
})
.then(response => response.text())
.then(data => console.log(data));
```

## Konfiguracja

### Dane testowe
- Username: `user`
- Password: `password`

### Zmienne środowiskowe
```bash
# Ustaw własny sekret JWT (opcjonalne)
export JWT_SECRET=your-very-secure-secret-key-here

# Czas wygaśnięcia tokena w milisekundach (domyślnie 1 godzina)
export JWT_EXPIRATION=3600000
```

## Troubleshooting

### 1. Problemy z kompilacją
```bash
# Wyczyść i przebuduj projekt
mvn clean compile
```

### 2. Problemy z uruchomieniem
```bash
# Sprawdź czy port 8080 jest wolny
netstat -tlnp | grep 8080

# Uruchom z innym portem jeśli potrzeba
java -jar target/spring-boot-1.0.0-SNAPSHOT.jar --server.port=8081
```

### 3. Problemy z tokenem
- Sprawdź czy token nie wygasł (domyślnie 1 godzina)
- Upewnij się, że używasz prefiksu "Bearer " przed tokenem
- Sprawdź logi aplikacji w konsoli

### 4. Testowanie lokalne vs. Replit
- Na Replit używaj: `https://twoja-nazwa-repla.replit.app`
- Lokalnie: `http://0.0.0.0:8080` lub `http://localhost:8080`

## Troubleshooter

1. env

   os environment not effect, so javac/java command in compile/run execute error.

   you can use "mvn clean package" for compile, and "java -jar target/*.jar" for run.

2. unfree package

   mark allowUnfree as true not work in file '.config/nixpkgs/config.nix', though linked to '~/.config/nixpkgs/config.nix'