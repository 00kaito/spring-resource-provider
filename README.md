# Secure Audio Microservice

Spring Boot microservice that provides secure access to audio files using JWT authentication.

## Usage

1. Install and run the application
2. Use the authentication endpoint to get a JWT token
3. Access protected resources with the token

## Authentication

### Test Credentials
- Username: `user`
- Password: `password`

### Login Endpoint
```
POST /api/auth/login
Content-Type: application/json

{
  "username": "user",
  "password": "password"
}
```

Response:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "type": "Bearer"
}
```

## Protected Endpoints

### Test Endpoint
```
GET /api/test
Authorization: Bearer <your-jwt-token>
```

### Audio Streaming
```
GET /api/audio/stream/{resourceId}
Authorization: Bearer <your-jwt-token>
```

## Configuration

Set the JWT secret in environment variables:
```
JWT_SECRET=your-secret-key-here
```

## Troubleshooter

1. env

   os environment not effect, so javac/java command in compile/run execute error.

   you can use "mvn clean package" for compile, and "java -jar target/*.jar" for run.

2. unfree package

   mark allowUnfree as true not work in file '.config/nixpkgs/config.nix', though linked to '~/.config/nixpkgs/config.nix'