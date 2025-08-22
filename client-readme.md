
# Audio Resource Provider API - Specyfikacja dla Klientów

## Przegląd Architektury

**Audio Resource Provider** to zabezpieczony mikroservice Spring Boot zapewniający kontrolowany dostęp do plików audio poprzez uwierzytelnianie JWT. Mikroservice działa jako **Resource Provider** w architekturze OAuth-podobnej, gdzie główna aplikacja pełni rolę **Authorization Server**.

### Wzorzec Komunikacji
```
[Główna Aplikacja] ←→ [Audio Resource Provider]
       │                        │
   Authorization              Resource
     Server                   Provider
       │                        │
   Zarządza                 Udostępnia
   użytkownikami            pliki audio
   i uprawnieniami          po weryfikacji
```

## Architektura Bezpieczeństwa

### 1. Dwufazowy Model Autoryzacji

#### Faza 1: Uwierzytelnianie w Resource Provider
- Użytkownik loguje się do Audio Resource Provider
- Otrzymuje JWT token do komunikacji

#### Faza 2: Autoryzacja przez Główną Aplikację
- Resource Provider sprawdza uprawnienia w głównej aplikacji
- Decyzja o dostępie podejmowana przez główną aplikację

### 2. Przepływ Bezpieczeństwa
```
1. Client → Audio Provider: POST /api/auth/login
2. Audio Provider → Client: JWT Token
3. Client → Audio Provider: GET /api/audio/stream/{id} + JWT
4. Audio Provider → Main App: GET /api/internal/check-access
5. Main App → Audio Provider: 200 OK / 403 Forbidden
6. Audio Provider → Client: Audio File / Access Denied
```

## Kompletna Specyfikacja API

### Base URL
```
Production:  https://YOUR-REPL-NAME.YOUR-USERNAME.repl.co
Development: http://0.0.0.0:8080
```

### Publiczne Endpointy (bez uwierzytelniania)

#### 1. Health Check
```http
GET /health
```
**Odpowiedź:**
```json
{
  "status": "UP",
  "application": "audio-resource-provider", 
  "main_app_connectivity": true,
  "circuit_breaker_failures": 0,
  "timestamp": 1634567890123
}
```

#### 2. Główna Strona
```http
GET /
```
**Odpowiedź:** `text/plain`
```
Secure Audio Microservice is running! Check /health for detailed status.
```

#### 3. Uwierzytelnianie
```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "user",
  "password": "password"
}
```

**Odpowiedź Success (200):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "type": "Bearer",
  "expires_in": 86400
}
```

**Odpowiedź Error (401):**
```json
{
  "error": "Unauthorized",
  "message": "Invalid credentials"
}
```

**Rate Limiting (429):**
```json
{
  "error": "Rate limit exceeded",
  "message": "Too many login attempts. Please try again later.",
  "retry_after": 60
}
```

### Chronione Endpointy (wymagają JWT)

#### Nagłówek Autoryzacji
```http
Authorization: Bearer YOUR_JWT_TOKEN
```

#### 4. Endpoint Testowy
```http
GET /api/test
Authorization: Bearer YOUR_JWT_TOKEN
```

**Odpowiedź (200):**
```json
{
  "message": "Hello user! JWT authentication works correctly.",
  "user": "user",
  "timestamp": "1634567890123"
}
```

#### 5. Streaming Plików Audio (GŁÓWNY ENDPOINT)
```http
GET /api/audio/stream/{resourceId}
Authorization: Bearer YOUR_JWT_TOKEN
```

**Parametry:**
- `resourceId` - identyfikator zasobu (regex: `^[a-zA-Z0-9_-]{1,50}$`)

**Odpowiedź Success (200):**
```http
Content-Type: application/octet-stream
Content-Disposition: attachment; filename="sample123.mp3"
X-Content-Type-Options: nosniff
X-Frame-Options: DENY

[BINARY AUDIO DATA]
```

**Odpowiedź Access Denied (403):**
```json
{
  "error": "Access denied",
  "message": "You don't have permission to access this resource",
  "resource_id": "sample123"
}
```

**Odpowiedź Not Found (404):**
```json
{
  "error": "Not found",
  "message": "Audio file not found"
}
```

#### 6. Admin - Status Systemu
```http
GET /api/admin/health-check
Authorization: Bearer YOUR_JWT_TOKEN
```

**Odpowiedź (200):**
```json
{
  "status": "UP",
  "service": "audio-resource-provider",
  "authenticated_user": "user",
  "access_service_failures": 0
}
```

#### 7. Admin - Reset Circuit Breaker
```http
POST /api/admin/reset-circuit-breaker
Authorization: Bearer YOUR_JWT_TOKEN
```

**Odpowiedź (200):**
```json
{
  "message": "Circuit breaker reset by user",
  "status": "success"
}
```

## Wymagania dla Głównej Aplikacji

### KRYTYCZNY ENDPOINT - Check Access

Audio Resource Provider **MUSI** mieć możliwość komunikacji z główną aplikacją w celu weryfikacji uprawnień. 

#### Endpoint Weryfikacji Uprawnień
```http
GET /api/internal/check-access
```

**Parametry Query:**
- `userId` (string) - identyfikator użytkownika
- `resourceId` (string) - identyfikator żądanego zasobu
- `clientIp` (string) - adres IP klienta

**Przykład żądania:**
```http
GET /api/internal/check-access?userId=user123&resourceId=sample123&clientIp=192.168.1.100
```

### Wymagane Odpowiedzi Głównej Aplikacji

#### Dostęp Autoryzowany
```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "access": "granted",
  "user_id": "user123",
  "resource_id": "sample123",
  "permissions": ["read"],
  "expires_at": "2024-01-01T12:00:00Z"
}
```

#### Dostęp Zabroniony
```http
HTTP/1.1 403 Forbidden
Content-Type: application/json

{
  "access": "denied",
  "reason": "insufficient_permissions",
  "user_id": "user123", 
  "resource_id": "sample123"
}
```

#### Błąd Wewnętrzny
```http
HTTP/1.1 500 Internal Server Error
Content-Type: application/json

{
  "error": "internal_error",
  "message": "Unable to check permissions"
}
```

## Konfiguracja Głównej Aplikacji

### 1. URL Głównej Aplikacji
Skonfiguruj w Audio Resource Provider:

**application.properties:**
```properties
main.app.url=https://YOUR-MAIN-APP.repl.co
main.app.timeout=5000
```

### 2. Dane Testowe Użytkownika
Audio Resource Provider ma wbudowanego użytkownika testowego:
```
Username: user
Password: password
Role: USER
```

### 3. JWT Konfiguracja
```properties
jwt.secret=myVerySecureSecretKeyThatIsLongEnoughForHMACHS256Algorithm
jwt.expiration=86400000  # 24 godziny w ms
```

## Rate Limiting i Ograniczenia

### Limity per Endpoint
- `/api/auth/login`: **10 żądań/minutę** per IP
- `/api/audio/stream/*`: **5 żądań/sekundę** per user  
- `/api/test`: **10 żądań/sekundę** per IP
- Pozostałe: **10 żądań/sekundę** per IP

### Odpowiedzi Rate Limiting
```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/json

{
  "error": "Rate limit exceeded",
  "message": "Too many requests. Please try again later.",
  "retry_after": 60
}
```

## Security Headers

### Automatyczne Headers
Audio Resource Provider automatycznie dodaje security headers:

```http
Content-Security-Policy: default-src 'self'
Strict-Transport-Security: max-age=31536000; includeSubDomains
X-Frame-Options: DENY
X-Content-Type-Options: nosniff
X-XSS-Protection: 1; mode=block
Referrer-Policy: strict-origin-when-cross-origin
```

### CORS Policy
Dozwolone origins:
- `https://*.replit.com`
- `https://*.repl.co`

## Resilience Patterns

### Circuit Breaker
Audio Resource Provider implementuje Circuit Breaker dla komunikacji z główną aplikacją:

**Konfiguracja:**
- **Failure Threshold**: 50% błędów
- **Open State Duration**: 30 sekund
- **Sliding Window**: 10 żądań

**Stany:**
- `CLOSED` - normalna komunikacja
- `OPEN` - wszystkie żądania odrzucane (fallback)
- `HALF_OPEN` - testowanie połączenia

### Retry Logic
- **Maksimum prób**: 3
- **Backoff Strategy**: Exponential
- **Timeout**: 5 sekund per próba

### Fallback Behavior
Gdy główna aplikacja nie jest dostępna:
- Wszystkie żądania audio są **odrzucane** (403 Forbidden)
- Health check pokazuje `main_app_connectivity: false`
- Circuit breaker można zresetować przez admin endpoint

## Monitoring i Metryki

### Health Endpoints
```bash
# Podstawowy status
curl http://0.0.0.0:8080/health

# Szczegółowy status (wymaga JWT)
curl -H "Authorization: Bearer TOKEN" \
  http://0.0.0.0:8080/api/admin/health-check
```

### Metryki Prometheus
Dostępne pod `/actuator/metrics`:
- `access_attempts_total` - liczba prób dostępu
- `unauthorized_access_attempts_total` - nieautoryzowane próby  
- `circuit_breaker_failures_total` - błędy Circuit Breaker
- `rate_limit_exceeded_total` - przekroczenia limitów

### Audit Logging
Wszystkie operacje bezpieczeństwa są logowane:
```
ACCESS_GRANTED: user=user123, resource=sample123, ip=192.168.1.100, timestamp=2024-01-01T12:00:00
ACCESS_DENIED: user=user456, resource=secret789, ip=192.168.1.101, timestamp=2024-01-01T12:01:00
```

## Przykłady Implementacji Klienta

### JavaScript/TypeScript Example
```typescript
class AudioResourceClient {
  private baseUrl: string;
  private token: string | null = null;

  constructor(baseUrl: string) {
    this.baseUrl = baseUrl;
  }

  async login(username: string, password: string): Promise<string> {
    const response = await fetch(`${this.baseUrl}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password })
    });

    if (!response.ok) {
      throw new Error(`Login failed: ${response.status}`);
    }

    const data = await response.json();
    this.token = data.token;
    return this.token;
  }

  async streamAudio(resourceId: string): Promise<Blob> {
    if (!this.token) {
      throw new Error('Not authenticated');
    }

    const response = await fetch(`${this.baseUrl}/api/audio/stream/${resourceId}`, {
      headers: { 'Authorization': `Bearer ${this.token}` }
    });

    if (!response.ok) {
      throw new Error(`Audio streaming failed: ${response.status}`);
    }

    return response.blob();
  }

  async checkHealth(): Promise<any> {
    const response = await fetch(`${this.baseUrl}/health`);
    return response.json();
  }
}

// Usage
const client = new AudioResourceClient('https://your-app.repl.co');
await client.login('user', 'password');
const audioBlob = await client.streamAudio('sample123');
```

### Python Example
```python
import requests
import json

class AudioResourceClient:
    def __init__(self, base_url: str):
        self.base_url = base_url
        self.token = None
        self.session = requests.Session()
    
    def login(self, username: str, password: str) -> str:
        response = self.session.post(
            f"{self.base_url}/api/auth/login",
            json={"username": username, "password": password}
        )
        response.raise_for_status()
        
        data = response.json()
        self.token = data['token']
        self.session.headers.update({
            'Authorization': f'Bearer {self.token}'
        })
        return self.token
    
    def stream_audio(self, resource_id: str) -> bytes:
        if not self.token:
            raise Exception("Not authenticated")
        
        response = self.session.get(
            f"{self.base_url}/api/audio/stream/{resource_id}"
        )
        response.raise_for_status()
        return response.content
    
    def check_health(self) -> dict:
        response = self.session.get(f"{self.base_url}/health")
        return response.json()

# Usage
client = AudioResourceClient('https://your-app.repl.co')
client.login('user', 'password')
audio_data = client.stream_audio('sample123')
with open('audio.mp3', 'wb') as f:
    f.write(audio_data)
```

### cURL Examples
```bash
#!/bin/bash

BASE_URL="https://your-app.repl.co"

# 1. Login and get token
TOKEN=$(curl -s -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username": "user", "password": "password"}' | \
  jq -r '.token')

echo "Token: $TOKEN"

# 2. Test authentication
curl -X GET "$BASE_URL/api/test" \
  -H "Authorization: Bearer $TOKEN"

# 3. Download audio file
curl -X GET "$BASE_URL/api/audio/stream/sample123" \
  -H "Authorization: Bearer $TOKEN" \
  --output sample123.mp3

# 4. Check health
curl -X GET "$BASE_URL/health" | jq

# 5. Admin health check  
curl -X GET "$BASE_URL/api/admin/health-check" \
  -H "Authorization: Bearer $TOKEN" | jq
```

## Implementacja Check Access w Głównej Aplikacji

### Spring Boot Implementation
```java
@RestController
@RequestMapping("/api/internal")
public class AccessController {
    
    @GetMapping("/check-access")
    public ResponseEntity<Map<String, Object>> checkAccess(
            @RequestParam String userId,
            @RequestParam String resourceId, 
            @RequestParam String clientIp) {
        
        // Twoja logika sprawdzania uprawnień
        boolean hasAccess = permissionService.hasAccess(userId, resourceId);
        
        Map<String, Object> response = new HashMap<>();
        
        if (hasAccess) {
            response.put("access", "granted");
            response.put("user_id", userId);
            response.put("resource_id", resourceId);
            response.put("permissions", Arrays.asList("read"));
            response.put("expires_at", LocalDateTime.now().plusHours(1));
            return ResponseEntity.ok(response);
        } else {
            response.put("access", "denied");
            response.put("reason", "insufficient_permissions");
            response.put("user_id", userId);
            response.put("resource_id", resourceId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }
    }
}
```

### Express.js Implementation  
```javascript
app.get('/api/internal/check-access', (req, res) => {
  const { userId, resourceId, clientIp } = req.query;
  
  // Twoja logika sprawdzania uprawnień
  const hasAccess = permissionService.hasAccess(userId, resourceId);
  
  if (hasAccess) {
    res.json({
      access: 'granted',
      user_id: userId,
      resource_id: resourceId,
      permissions: ['read'],
      expires_at: new Date(Date.now() + 3600000).toISOString()
    });
  } else {
    res.status(403).json({
      access: 'denied',
      reason: 'insufficient_permissions',
      user_id: userId,
      resource_id: resourceId
    });
  }
});
```

## Deployment na Replit

### Environment Variables (Replit Secrets)
```bash
JWT_SECRET=your-super-secure-jwt-secret-key-here
MAIN_APP_URL=https://your-main-app.repl.co
```

### .replit Configuration
```toml
[deployment]
build = "mvn clean package -Dmaven.test.skip=true"  
run = "java -jar target/*.jar"

[[ports]]
localPort = 8080
externalPort = 80
```

## Troubleshooting

### 1. Główna aplikacja niedostępna
**Objaw:** Circuit breaker w stanie OPEN
```bash
# Reset circuit breaker
curl -X POST "https://your-app.repl.co/api/admin/reset-circuit-breaker" \
  -H "Authorization: Bearer $TOKEN"
```

### 2. Rate limiting errors  
**Objaw:** HTTP 429 Too Many Requests
- Poczekaj 60 sekund
- Użyj innego IP
- Zmniejsz częstotliwość żądań

### 3. JWT token expired
**Objaw:** HTTP 401 Unauthorized
- Token ważny przez 24 godziny
- Wykonaj ponowne logowanie

### 4. File not found
**Objaw:** HTTP 404 Not Found
- Sprawdź czy plik istnieje w `audio-files/{resourceId}.mp3`
- Waliduj format resourceId: `^[a-zA-Z0-9_-]{1,50}$`

## Security Best Practices

### 1. JWT Token Handling
- ✅ Przechowuj tokeny bezpiecznie (nie w localStorage)
- ✅ Implementuj token refresh logic
- ✅ Logout = usunięcie tokena z pamięci

### 2. API Communication
- ✅ Używaj HTTPS w production
- ✅ Waliduj wszystkie parametry wejściowe
- ✅ Implementuj proper error handling

### 3. Rate Limiting
- ✅ Monitoruj limity w aplikacji klienckiej
- ✅ Implementuj exponential backoff
- ✅ Cachuj odpowiedzi gdzie to możliwe

### 4. Error Handling
- ✅ Nie eksponuj szczegółów błędów do klienta
- ✅ Loguj wszystkie błędy bezpieczeństwa
- ✅ Implementuj proper fallback mechanisms

---

**Audio Resource Provider v1.0**  
*Secure, scalable, production-ready microservice dla plików audio* 🎵🔐
