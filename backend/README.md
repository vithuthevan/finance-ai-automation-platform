# Backend

Spring Boot modular monolith. One deployable: `platform-app`.

```
cd backend
./gradlew :platform-app:bootRun
```

Default profile is `local`. Production:

```
SPRING_PROFILES_ACTIVE=prod
```

See the root `README.md` and `.env.example` for environment variables.
