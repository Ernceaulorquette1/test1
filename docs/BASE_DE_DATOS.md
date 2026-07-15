# Base de Datos — NutriScan AI
PostgreSQL 16 · migraciones con Flyway (`backend/src/main/resources/db/migration`)

## Modelo entidad-relación

```mermaid
erDiagram
    USERS ||--o{ MEALS : registra
    USERS ||--o{ WATER_LOGS : bebe
    USERS ||--o{ WEIGHT_LOGS : pesa
    USERS ||--o{ CHAT_MESSAGES : conversa
    USERS ||--o{ SUBSCRIPTIONS : contrata
    MEALS ||--o{ MEAL_ITEMS : contiene

    USERS {
        uuid id PK
        varchar firebase_uid UK
        varchar email UK
        varchar name
        int age
        varchar sex
        numeric weight_kg
        numeric height_cm
        varchar activity_level
        varchar goal
        numeric target_weight_kg
        int target_calories
        timestamptz premium_until
    }
    MEALS {
        uuid id PK
        uuid user_id FK
        varchar meal_type
        varchar name
        text photo_url
        timestamptz eaten_at
        numeric calories
        numeric protein_g
        numeric carbs_g
        numeric fat_g
        numeric fiber_g
        numeric sodium_mg
        numeric sugar_g
    }
    MEAL_ITEMS {
        uuid id PK
        uuid meal_id FK
        varchar name
        varchar quantity
        numeric calories
    }
    WATER_LOGS {
        uuid id PK
        uuid user_id FK
        date log_date
        int ml
    }
    WEIGHT_LOGS {
        uuid id PK
        uuid user_id FK
        date log_date
        numeric weight_kg
    }
    CHAT_MESSAGES {
        uuid id PK
        uuid user_id FK
        varchar role
        text content
        timestamptz created_at
    }
    SUBSCRIPTIONS {
        uuid id PK
        uuid user_id FK
        varchar plan
        text purchase_token
        varchar status
        timestamptz expires_at
    }
```

## Decisiones de diseño
- **UUID** como PK (generación distribuida, no enumerable desde fuera — seguridad).
- Totales nutricionales **desnormalizados en `meals`**: el dashboard y las estadísticas se resuelven sin agregar `meal_items` (rendimiento con millones de filas).
- Índices por patrones de acceso reales: `meals(user_id, eaten_at DESC)`, `water_logs(user_id, log_date)`, `chat_messages(user_id, created_at)`.
- `ON DELETE CASCADE` desde `users`: la eliminación de cuenta borra todos los datos personales (requisito de privacidad).
- Restricciones `CHECK` en enums y rangos fisiológicos: defensa en profundidad además de la validación de la API.
