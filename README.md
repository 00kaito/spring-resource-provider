
# Secure Audio Microservice

Spring Boot mikroservice zapewniający bezpieczny dostęp do plików audio za pomocą uwierzytelniania JWT.

## Architektura Aplikacji

### Wzorzec Architektury
- **Spring MVC** - tradycyjna architektura web aplikacji Spring Boot
- **JWT Authentication** - bezstanowe uwierzytelnianie tokenami JWT
- **RESTful API** - endpoints zgodne z zasadami REST
- **In-Memory Authentication** - proste uwierzytelnianie dla testów

### Struktura Pakietów

```
com.replit/
├── Application.java                 # Główna klasa aplikacji Spring Boot
├── HealthController.java           # Endpointy health check
├── config/
│   └── AppConfig.java              # Konfiguracja aplikacji
├── controller/                     # Warstwa kontrolerów REST
│   ├── AuthController.java         # Uwierzytelnianie (login)
│   ├── TestController.java         # Endpoint testowy JWT
│   └── AudioController.java        # Streaming plików audio
├── dto/                            # Data Transfer Objects
│   ├── AuthRequest.java            # Payload do logowania
│   └── AuthResponse.java           # Odpowiedź z tokenem JWT
├── security/                       # Konfiguracja bezpieczeństwa
│   ├── SecurityConfig.java         # Konfiguracja Spring Security
│   ├── JwtService.java            # Serwis generowania/walidacji JWT
│   └── JwtAuthenticationFilter.java # Filtr uwierzytelniania
├── service/
│   └── AccessService.java          # Logika kontroli dostępu do zasobów
└── exception/
    └── GlobalExceptionHandler.java # Globalna obsługa błędów
```

## Opis Klas

### Controllers

**AuthController** (`/api/auth`)
- `POST /login` - uwierzytelnianie użytkownika, zwraca JWT token

**TestController** (`/api`)  
- `GET /test` - chroniony endpoint testowy wymagający JWT

**AudioController** (`/api`)
- `GET /audio/stream/{resourceId}` - streaming plików audio z kontrolą dostępu

**HealthController** (`/`)
- `GET /health` - status aplikacji  
- `GET /` - podstawowy endpoint aplikacji

### Security Layer

**SecurityConfig**
- Konfiguracja Spring Security
- Definicje chronionych endpointów
- Konfiguracja uwierzytelniania in-memory (user/password)

**JwtService**
- Generowanie tokenów JWT
- Walidacja tokenów
- Extraktowanie danych z tokenów
- Klucz JWT: `myVerySecureSecretKeyThatIsLongEnoughForHMACHS256Algorithm`
- Czas wygaśnięcia: 24 godziny

**JwtAuthenticationFilter**
- Przechwytuje requesty HTTP
- Waliduje tokeny JWT w headerze Authorization
- Ustawia kontekst bezpieczeństwa

### Services

**AccessService**
- Kontrola dostępu do zasobów audio
- Walidacja uprawnień użytkowników do konkretnych plików

### Exception Handling

**GlobalExceptionHandler**
- Centralizowana obsługa błędów
- Obsługa błędów uwierzytelniania (401)
- Obsługa błędów walidacji (400)  
- Obsługa błędów bezpieczeństwa (403)

## Konfiguracja

### application.properties
```properties
management.server.port=8080
jwt.secret=myVerySecureSecretKeyThatIsLongEnoughForHMACHS256Algorithm
jwt.expiration=86400000
```

### Dane Testowe
- **Username**: `user`
- **Password**: `password`
- **Rola**: `USER`

## API Endpoints

### Publiczne (nie wymagają uwierzytelniania)
- `POST /api/auth/login` - logowanie
- `GET /health` - status aplikacji
- `GET /` - główna strona

### Chronione (wymagają JWT token)
- `GET /api/test` - endpoint testowy
- `GET /api/audio/stream/{resourceId}` - streaming audio

## Uruchamianie Aplikacji

**Maven:**
```bash
mvn clean compile
mvn spring-boot:run
```

**Kliknij przycisk "Run"** - uruchomi workflow "Start Spring Boot"

## Testowanie JWT

### 1. Pobieranie tokena

```bash
curl -X POST http://0.0.0.0:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "user", "password": "password"}'
```

**Odpowiedź:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "type": "Bearer"
}
```

### 2. Testowanie chronionego endpointu

```bash
# Zapisz token
export TOKEN="twój-jwt-token"

# Test endpoint
curl -X GET http://0.0.0.0:8080/api/test \
  -H "Authorization: Bearer $TOKEN"
```

**Oczekiwana odpowiedź:**
```
Hello user! JWT authentication works correctly.
```

### 3. Test endpointu audio

```bash
curl -X GET http://0.0.0.0:8080/api/audio/stream/sample123 \
  -H "Authorization: Bearer $TOKEN"
```

## Troubleshooting

### Błąd kompilacji
```bash
mvn clean compile
```

### Aplikacja nie startuje
- Sprawdź czy port 8080 jest wolny
- Sprawdź logi w konsoli

### Token nie działa
- Sprawdź czy token nie wygasł (24h)
- Upewnij się że używasz "Bearer " przed tokenem
- Sprawdź format JSON w request body

### Deployment na Replit
Aplikacja jest skonfigurowana do deployment na Replit:
- Build command: `mvn clean package -Dmaven.test.skip=true`
- Run command: `java -jar target/*.jar`
