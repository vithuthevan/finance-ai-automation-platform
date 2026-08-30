# Frontend

Independent Angular 19 workspace. It does not depend on backend source.

Local development (API proxied to `http://localhost:8080`):

```
cd frontend
npm install
npm start
```

Runtime API base URL is `src/assets/config.json`.

- Same origin / local proxy: `{ "apiBaseUrl": "/api/v1" }`
- Separate API host: `{ "apiBaseUrl": "https://api.example.com/api/v1" }`

Do not put JWT secrets, database credentials, or cloud keys in Angular files.
