
# Secure Audio Microservice

Spring Boot mikroservice zapewniający bezpieczny dostęp do plików audio za pomocą uwierzytelniania JWT z zaawansowanymi funkcjami bezpieczeństwa, rate limiting i monitoringu.

## Architektura Aplikacji

### Wzorzec Architektury
- **Spring MVC** - tradycyjna architektura web aplikacji Spring Boot
- **JWT Authentication** - bezstanowe uwierzytelnianie tokenami JWT
- **RESTful API** - endpoints zgodne z zasadami REST
- **Circuit Breaker Pattern** - ochrona przed awariami zewnętrznych serwisów
- **Rate Limiting** - ochrona przed atakami DoS
- **Async Processing** - nieblokujące operacje I/O
- **Health Checks** - monitoring stanu aplikacji
- **Security Headers** - dodatkowe zabezpieczenia HTTP
- **Audit Logging** - szczegółowe logowanie bezpieczeństwa

### Struktura Pakietów

```
com.replit/
├── Application.java                    # Główna klasa aplikacji Spring Boot
├── HealthController.java              # Endpointy health check z monitoringiem
├── config/
│   └── AppConfig.java                 # Konfiguracja: RestTemplate, RateLimiter, Async
├── controller/                        # Warstwa kontrolerów REST
│   ├── AdminController.java           # Administracja i zarządzanie systemem
│   ├── AuthController.java            # Uwierzytelnianie z rate limiting
│   ├── AudioController.java           # Streaming plików audio z kontrolą dostępu
│   └── TestController.java            # Endpoint testowy JWT z rate limiting
├── dto/                              # Data Transfer Objects
│   ├── AuthRequest.java              # Payload do logowania z walidacją
│   └── AuthResponse.java             # Odpowiedź z tokenem JWT
├── exception/
│   └── GlobalExceptionHandler.java   # Globalna obsługa błędów
├── security/                         # Kompletna konfiguracja bezpieczeństwa
│   ├── JwtAuthenticationFilter.java  # Filtr uwierzytelniania JWT
│   ├── JwtService.java              # Serwis generowania/walidacji JWT
│   ├── SecurityConfig.java          # Konfiguracja Spring Security + CORS + Headers
│   └── SecurityHeadersFilter.java   # Security headers (CSP, HSTS, X-Frame-Options)
└── service/
    └── AccessService.java            # Logika kontroli dostępu z Circuit Breaker
```

## Szczegółowy Opis Komponentów

### Controllers Layer

#### **AuthController** (`/api/auth`)
- `POST /login` - uwierzytelnianie z rate limiting (10 req/min)
- **Rate Limiting**: Resilience4j protection
- **IP Tracking**: Logowanie prób uwierzytelniania z IP
- **Audit Logging**: Wszystkie próby logowania są logowane
- **Fallback Method**: Obsługa przekroczenia limitu żądań

#### **AudioController** (`/api/audio`)
- `GET /audio/stream/{resourceId}` - streaming plików z kontrolą dostępu
- **Rate Limiting**: 5 żądań/sekundę per user
- **Resource Validation**: Walidacja nazw plików (bezpieczeństwo ścieżek)
- **Access Control**: Weryfikacja uprawnień w głównej aplikacji
- **IP Tracking**: Logowanie dostępu do zasobów
- **File Security**: Ochrona przed path traversal

#### **TestController** (`/api`)
- `GET /test` - chroniony endpoint testowy
- **JWT Validation**: Testowanie poprawności tokenów
- **Rate Limiting**: Ochrona przed nadużyciem

#### **AdminController** (`/api/admin`)
- `GET /admin/health-check` - szczegółowy status systemu
- `POST /admin/reset-circuit-breaker` - reset Circuit Breaker
- **Admin Only**: Dostęp tylko dla uwierzytelnionych użytkowników

#### **HealthController** (`/`)
- `GET /health` - status aplikacji + connectivity check
- `GET /` - endpoint główny aplikacji
- **Metrics**: Circuit breaker failures, connectivity status
- **Real-time Monitoring**: Status głównej aplikacji

### Security Layer

#### **SecurityConfig**
- **CORS Configuration**: `https://*.replit.com`, `https://*.repl.co`
- **JWT Filter Chain**: Automatyczna walidacja tokenów
- **Security Headers**: HSTS, CSP, X-Frame-Options
- **Public Endpoints**: `/api/auth/**`, `/health`, `/`
- **Protected Endpoints**: `/api/audio/**`, `/api/admin/**`, `/api/test`

#### **SecurityHeadersFilter**
```java
// Implementowane headers:
- Content-Security-Policy: default-src 'self'
- X-Frame-Options: DENY  
- X-Content-Type-Options: nosniff
- X-XSS-Protection: 1; mode=block
- Referrer-Policy: strict-origin-when-cross-origin
- Strict-Transport-Security: max-age=31536000; includeSubDomains
```

#### **JwtService**
- **Algorithm**: HMAC-SHA256
- **Expiration**: 24 godziny
- **Secret Key**: Konfigurowalny w application.properties
- **Token Validation**: Sprawdzanie podpisu i czasu ważności
- **Claims Extraction**: Pobieranie danych użytkownika

#### **JwtAuthenticationFilter**
- **Authorization Header**: Sprawdzanie `Bearer` tokenów
- **Security Context**: Ustawienie kontekstu Spring Security
- **Error Handling**: Obsługa nieprawidłowych tokenów
- **Chain Processing**: Przekazanie do kolejnych filtrów

### Service Layer

#### **AccessService** (Główny komponent biznesowy)

**Funkcje bezpieczeństwa:**
- **External Authorization**: Sprawdzanie uprawnień w głównej aplikacji
- **Circuit Breaker**: Resilience4j protection (5 failures → open)
- **Retry Logic**: 3 próby z exponential backoff
- **Timeout Protection**: Maksymalnie 5 sekund per request
- **Async Processing**: Nieblokujące operacje z `@Async`

**Monitoring i Audit:**
- **Metrics**: Micrometer counters dla unauthorized access
- **Detailed Logging**: MDC context z IP, resourceId, timestamp
- **Audit Trail**: Osobny logger dla audit events
- **Health Checks**: Monitoring connectivity z główną aplikacją

**Konfiguracja:**
```java
@CircuitBreaker(name = "main-app", fallbackMethod = "accessDeniedFallback")
@Retry(name = "main-app")
@TimeLimiter(name = "main-app")
@Async
```

### Configuration Layer

#### **AppConfig**
- **RestTemplate**: HTTP client z timeoutami (5s connect/read)
- **Async Executor**: Thread pool (5-20 threads, queue 500)
- **Rate Limiters**: 
  - Default: 10 req/s
  - Audio Access: 5 req/s
- **Circuit Breaker**: Automatyczna konfiguracja Resilience4j

### Exception Handling

#### **GlobalExceptionHandler**
- **Authentication Errors**: 401 Unauthorized
- **Authorization Errors**: 403 Forbidden  
- **Validation Errors**: 400 Bad Request
- **Rate Limit Errors**: 429 Too Many Requests
- **General Errors**: 500 Internal Server Error
- **Structured Responses**: JSON error messages

## Konfiguracja Aplikacji

### application.properties
```properties
# Server Configuration
management.server.port=8080
server.port=8080

# JWT Configuration  
jwt.secret=myVerySecureSecretKeyThatIsLongEnoughForHMACHS256Algorithm
jwt.expiration=86400000

# External Service
main.app.url=http://localhost:3000
main.app.timeout=5000

# Rate Limiting
resilience4j.ratelimiter.instances.default.limit-for-period=10
resilience4j.ratelimiter.instances.default.limit-refresh-period=1s
resilience4j.ratelimiter.instances.audio-access.limit-for-period=5

# Circuit Breaker
resilience4j.circuitbreaker.instances.main-app.failure-rate-threshold=50
resilience4j.circuitbreaker.instances.main-app.wait-duration-in-open-state=30s
resilience4j.circuitbreaker.instances.main-app.sliding-window-size=10

# Logging
logging.level.com.replit.service.AccessService=INFO
logging.level.audit=WARN
```

### Dane Testowe
- **Username**: `user`
- **Password**: `password`
- **Rola**: `USER`

## API Endpoints

### Publiczne (nie wymagają uwierzytelniania)
- `POST /api/auth/login` - logowanie z rate limiting
- `GET /health` - status aplikacji
- `GET /` - główna strona

### Chronione (wymagają JWT token)
- `GET /api/test` - endpoint testowy
- `GET /api/audio/stream/{resourceId}` - streaming audio z kontrolą dostępu
- `GET /api/admin/health-check` - szczegółowy status systemu
- `POST /api/admin/reset-circuit-breaker` - reset Circuit Breaker

## Uruchamianie Aplikacji

### Development
```bash
mvn clean compile
mvn spring-boot:run
```

### Production Build
```bash
mvn clean package -Dmaven.test.skip=true
java -jar target/*.jar
```

### Replit (Kliknij przycisk "Run")
Automatycznie uruchomi workflow "Start Spring Boot":
1. `mvn clean compile`
2. `mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=8080"`

## Szczegółowy Przepływ Działania

### 1. Startup aplikacji
```
Application.java → SpringApplication.run()
│
├── SecurityConfig → Konfiguracja filtrów bezpieczeństwa
├── AppConfig → Inicjalizacja RestTemplate, RateLimiter, Async
├── AccessService → @PostConstruct inicjalizacja RestTemplate
└── Controllers → Rejestracja endpointów REST
```

### 2. Proces Uwierzytelniania

#### Żądanie tokena:
```bash
curl -X POST http://0.0.0.0:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "user", "password": "password"}'
```

#### Przepływ wewnętrzny:
```
1. AuthController.login()
   ├── @RateLimiter validation (10 req/min per IP)
   ├── @Valid request body validation
   └── IP address extraction

2. AuthenticationManager.authenticate()
   ├── UserDetailsService lookup
   ├── PasswordEncoder verification
   └── Authentication object creation

3. JwtService.generateToken()
   ├── Claims building (subject, expiration)
   ├── HMAC-SHA256 signing
   └── Token string generation

4. Response
   ├── Success: {"token": "eyJ...", "type": "Bearer"}
   └── Failure: Rate limit/Auth error handling
```

### 3. Dostęp do Chronionych Zasobów

#### Żądanie pliku audio:
```bash
curl -X GET http://0.0.0.0:8080/api/audio/stream/sample123 \
  -H "Authorization: Bearer YOUR_TOKEN"
```

#### Szczegółowy przepływ przetwarzania:

##### 3.1 Security Filter Chain
```
SecurityHeadersFilter
├── CSP: default-src 'self'
├── X-Frame-Options: DENY
├── HSTS: max-age=31536000
└── Anti-XSS headers

JwtAuthenticationFilter  
├── Authorization header extraction
├── "Bearer " prefix removal
├── JwtService.validateToken()
│   ├── Signature verification (HMAC-SHA256)
│   ├── Expiration check
│   └── Claims extraction
├── SecurityContextHolder.setAuthentication()
└── Chain continuation
```

##### 3.2 Controller Processing
```
AudioController.streamAudio()
├── @RateLimiter(name = "audio-access") - 5 req/s
├── Resource ID validation (regex: ^[a-zA-Z0-9_-]{1,50}$)
├── IP address extraction (X-Forwarded-For handling)
└── Access control delegation
```

##### 3.3 Access Control (AccessService)
```
AccessService.checkAccess()
├── @CircuitBreaker(name = "main-app")
├── @Retry(name = "main-app") - 3 attempts
├── @TimeLimiter(name = "main-app") - 5s timeout
├── HTTP Request: GET /api/internal/check-access
│   ├── Parameters: userId, resourceId, clientIp
│   ├── RestTemplate with timeout (5000ms)
│   └── Response: 200 OK / 403 Forbidden
├── Metrics: accessAttemptCounter.increment()
├── Audit Logging: MDC context + audit logger
└── Return: boolean access decision
```

##### 3.4 File Streaming
```
File Processing
├── Path construction: "audio-files/" + resourceId + ".mp3"
├── Security validation: Path traversal protection
├── File existence check
├── FileSystemResource creation
└── ResponseEntity with headers:
    ├── Content-Type: application/octet-stream
    ├── Content-Disposition: attachment; filename="..."
    └── Body: FileSystemResource
```

### 4. Monitoring i Health Checks

#### Health Endpoint (`/health`):
```json
{
  "status": "UP",
  "application": "audio-resource-provider",
  "main_app_connectivity": true,
  "circuit_breaker_failures": 0,
  "timestamp": 1634567890123
}
```

#### Metrics (Micrometer + Prometheus):
- `access_attempts_total` - liczba prób dostępu
- `unauthorized_access_attempts_total` - nieautoryzowane próby
- `circuit_breaker_failures_total` - błędy Circuit Breaker
- `rate_limit_exceeded_total` - przekroczenia limitów

### 5. Error Handling i Resilience

#### Circuit Breaker States:
```
CLOSED (normal) → OPEN (failures) → HALF_OPEN (testing) → CLOSED
│                    │                     │
│                    │                     └── Success → Reset
│                    │
│                    └── 30s wait → Retry attempt
```

#### Rate Limiting Responses:
```json
HTTP 429 Too Many Requests
{
  "error": "Rate limit exceeded",
  "message": "Too many requests. Please try again later.",
  "retry_after": "60"
}
```

#### Security Violations:
```json
HTTP 403 Forbidden  
{
  "error": "Access denied",
  "message": "You don't have permission to access this resource",
  "resource_id": "sample123"
}
```

## Bezpieczeństwo

### Warstwy Zabezpieczeń
1. **HTTP Security Headers** - CSP, HSTS, X-Frame-Options
2. **CORS Policy** - Tylko domeny replit.com/repl.co
3. **JWT Validation** - Podpis cyfrowy + expiration
4. **Rate Limiting** - Per-IP i per-endpoint limits
5. **Input Validation** - Regex patterns + path traversal protection
6. **External Authorization** - Delegacja decyzji do głównej aplikacji
7. **Audit Logging** - Wszystkie operacje bezpieczeństwa
8. **Circuit Breaker** - Ochrona przed awariami dependencji

### Secure Headers
```http
Content-Security-Policy: default-src 'self'
Strict-Transport-Security: max-age=31536000; includeSubDomains  
X-Frame-Options: DENY
X-Content-Type-Options: nosniff
X-XSS-Protection: 1; mode=block
Referrer-Policy: strict-origin-when-cross-origin
```

## Testowanie Aplikacji

### 1. Podstawowe testowanie JWT

```bash
# Pobieranie tokena
export TOKEN=$(curl -s -X POST http://0.0.0.0:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "user", "password": "password"}' | \
  jq -r '.token')

# Test chronionego endpointu  
curl -X GET http://0.0.0.0:8080/api/test \
  -H "Authorization: Bearer $TOKEN"
```

### 2. Test endpointu audio

```bash
# Streaming pliku audio
curl -X GET http://0.0.0.0:8080/api/audio/stream/sample123 \
  -H "Authorization: Bearer $TOKEN" \
  --output sample123.mp3
```

### 3. Test rate limiting

```bash
# Szybkie wysłanie wielu żądań (powinno zwrócić 429)
for i in {1..15}; do
  curl -X GET http://0.0.0.0:8080/api/test \
    -H "Authorization: Bearer $TOKEN"
done
```

### 4. Test health checks

```bash
# Status aplikacji
curl -X GET http://0.0.0.0:8080/health

# Admin health check (wymagane JWT)
curl -X GET http://0.0.0.0:8080/api/admin/health-check \
  -H "Authorization: Bearer $TOKEN"
```

## Deployment na Replit

### Konfiguracja Deployment (.replit)
```toml
[deployment]
build = "mvn clean package -Dmaven.test.skip=true"
run = "java -jar target/*.jar"

[[ports]]
localPort = 8080
externalPort = 80
```

### Environment Variables (Replit Secrets)
- `JWT_SECRET` - Klucz do podpisywania tokenów JWT
- `MAIN_APP_URL` - URL głównej aplikacji (do sprawdzania uprawnień)

### Production Readiness
- ✅ Health checks dla load balancer
- ✅ Graceful shutdown
- ✅ Structured logging (JSON)
- ✅ Metrics dla monitoring
- ✅ Security headers
- ✅ Rate limiting
- ✅ Circuit breaker pattern
- ✅ Async processing

## Troubleshooting

### Błędy kompilacji
```bash
mvn clean compile
```

### Circuit breaker w stanie OPEN
```bash
# Reset przez API
curl -X POST http://0.0.0.0:8080/api/admin/reset-circuit-breaker \
  -H "Authorization: Bearer $TOKEN"
```

### Rate limiting
- Domyślnie: 10 żądań/sekundę per endpoint
- Audio access: 5 żądań/sekundę
- Oczekaj 60 sekund lub użyj innego IP

### JWT errors
- Sprawdź ważność tokena (24h)
- Upewnij się o poprawnym formacie "Bearer TOKEN"
- Sprawdź konfigurację `jwt.secret`

### Connectivity issues
- Sprawdź `main.app.url` w application.properties
- Zweryfikuj dostępność głównej aplikacji
- Sprawdź status przez `/health` endpoint

**Aplikacja gotowa do production deployment na Replit! 🚀**
