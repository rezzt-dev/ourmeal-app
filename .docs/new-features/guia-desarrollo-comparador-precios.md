# Guía de desarrollo: comparador de precios de la lista de la compra (Kotlin)

**Qué es este documento:** la especificación técnica completa para implementar, en un proyecto Kotlin (app Android y backend), la funcionalidad de **resolver lo que escribe el usuario, emparejarlo con los productos de cada supermercado y decirle dónde es más barato cada producto de su lista**.

**Documento relacionado:** `estrategia-precios-supermercados.md` (el porqué de cada decisión). Esta guía es el cómo.

**Fecha:** 22/11/2026

---

## 0. Instrucciones para quien desarrolle (persona o agente de IA)

### 0.1 Autorización expresa para instalar dependencias

> **Estás autorizado expresamente a instalar, añadir, actualizar o configurar cualquier paquete, biblioteca, plugin de Gradle, SDK, imagen de Docker, modelo de machine learning o herramienta de línea de comandos que sea necesario o que mejore el funcionamiento de esta funcionalidad.** No hace falta pedir permiso para cada dependencia.

Condiciones al hacerlo:

1. **Versiones estables.** Usa la última versión estable compatible (nada de alpha, beta o RC salvo que no exista alternativa). Las versiones de esta guía son **de referencia**. Si hay una más reciente y compatible, úsala.
2. **Licencias compatibles con una app comercial:** Apache-2.0, MIT, BSD o EPL. Evita GPL/AGPL en el código que se distribuye. Los datos de OpenStreetMap y Open Food Facts son ODbL: requieren atribución (ver §11).
3. **Centraliza las versiones** en `gradle/libs.versions.toml`. No escribas versiones sueltas en los `build.gradle.kts`.
4. **Deja constancia** de cada dependencia nueva y su motivo en `docs/DECISIONS.md` (una línea por dependencia).
5. **Si una dependencia de esta guía está obsoleta o abandonada,** sustitúyela por su alternativa mantenida y anótalo en `DECISIONS.md`.
6. **No metas secretos en el repositorio** (claves de API, contraseñas). Usa variables de entorno o `local.properties`, que no se versiona.

### 0.2 Reglas de comportamiento de la funcionalidad (no negociables)

- **Antes "no encontrado" que una equivalencia dudosa.** Nunca sustituyas un producto en silencio.
- **Compara siempre por precio unitario** (€/L, €/kg, €/ud), además de mostrar el precio del envase.
- **Cada precio lleva fecha y origen** (`online`, `open_prices`, `user`).
- **La app nunca accede a las webs de los supermercados.** Todo pasa por el backend.
- **Los conectores de cadenas vienen desactivados por defecto** (`connectors.<cadena>.enabled=false`) hasta que haya validación legal (ver §11). Para desarrollar se usan datos de prueba (§6.7).
- **Privacidad:** la app no envía coordenadas exactas al backend, solo el nombre de la ciudad.

---

## 1. Alcance funcional

### 1.1 Historias de usuario

1. Como usuario, concedo la ubicación aproximada y la app detecta mi **ciudad**. Si no la concedo, la elijo de una lista.
2. Veo los **supermercados de mi ciudad** agrupados por cadena y elijo en cuáles compro.
3. Añado productos a mi **lista** escribiendo libremente ("papel water", "cocacola zero", "leche semi") o **escaneando el código de barras**.
4. La app **entiende** cada línea:
   - si está segura, la resuelve sola;
   - si duda, me ofrece 2 o 3 opciones;
   - si no entiende, me pide que concrete.
5. Por cada línea elijo **genérico** ("me vale cualquier leche semidesnatada de 1 L") o **marca concreta**.
6. La app me dice, **por producto, en qué cadena es más barato**, el **total por cadena** y el **total óptimo repartiendo la compra**.
7. Puedo **reportar un precio** (de tienda física) o un emparejamiento erróneo.

### 1.2 Fuera de alcance en esta fase

- Compra online o carrito en las tiendas.
- Precios por tienda física concreta, salvo los que aporten los usuarios.
- Rutas o mapas de navegación.

---

## 2. Arquitectura

```
┌────────────────────────── app Android (:app) ──────────────────────────┐
│ Compose UI ─ ViewModels ─ Repositorios ─ Room (lista, caché de alias)   │
│         │                      │                                        │
│         │                Retrofit (HTTPS, JSON)                         │
└─────────┼──────────────────────┼────────────────────────────────────────┘
          │                      ▼
          │        ┌──────────── backend Ktor (:backend) ─────────────┐
          │        │ API REST /v1/*                                    │
          │        │ ├─ ResolveService  (búsqueda híbrida + rerank)    │
          │        │ ├─ CompareService  (mejor precio y cesta)         │
          │        │ ├─ StoreService    (Overpass/OSM + caché)         │
          │        │ ├─ ProductService  (EAN → Open Food Facts)        │
          │        │ └─ Jobs: ingesta, matching, embeddings, limpieza  │
          │        │        │                    │                     │
          │        │   PostgreSQL 17       Embedder ONNX (e5-small)    │
          │        │   pg_trgm + pgvector                              │
          │        └───────────────────────────────────────────────────┘
          │
     módulo :shared (Kotlin/JVM puro): normalización de texto, cantidades,
     atributos y DTOs. Lo usan la app y el backend, así ambos normalizan igual.
```

**Por qué un módulo `:shared`:** el texto del usuario y el de los productos se normalizan con **exactamente el mismo código**. Si cada lado normalizara distinto, la búsqueda fallaría de forma silenciosa.

---

## 3. Estructura del repositorio

```
comparador/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml
├── docker-compose.yml
├── docs/
│   ├── DECISIONS.md
│   └── golden-set.jsonl            # conjunto de pruebas de búsqueda (§9)
├── shared/                          # Kotlin/JVM puro, sin Android
│   └── src/main/kotlin/com/ejemplo/compra/shared/
│       ├── text/TextNormalizer.kt
│       ├── text/QuantityParser.kt
│       ├── text/AttributeExtractor.kt
│       └── dto/ApiDtos.kt
├── backend/
│   ├── src/main/kotlin/com/ejemplo/compra/backend/
│   │   ├── Application.kt
│   │   ├── config/AppConfig.kt
│   │   ├── db/Database.kt
│   │   ├── search/HybridSearchRepository.kt
│   │   ├── search/Reranker.kt
│   │   ├── search/ResolveService.kt
│   │   ├── compare/CompareService.kt
│   │   ├── embeddings/E5Embedder.kt
│   │   ├── ingest/ChainConnector.kt
│   │   ├── ingest/IngestionPipeline.kt
│   │   ├── ingest/connectors/FixtureConnector.kt
│   │   ├── stores/OverpassClient.kt
│   │   ├── products/OpenFoodFactsClient.kt
│   │   └── routes/*.kt
│   ├── src/main/resources/
│   │   ├── application.conf
│   │   ├── db/migration/V1__schema.sql …
│   │   ├── dictionaries/brands.txt, variants.yaml, synonyms.yaml
│   │   └── fixtures/products-sample.csv
│   └── src/test/kotlin/…
└── app/
    └── src/main/java/com/ejemplo/compra/
        ├── App.kt
        ├── di/…
        ├── data/local/ (Room)
        ├── data/remote/ (Retrofit)
        ├── data/location/LocationRepository.kt
        ├── domain/…
        └── ui/ (Compose: city, stores, list, compare, scan)
```

`settings.gradle.kts`:

```kotlin
rootProject.name = "comparador"
include(":shared", ":backend", ":app")
```

---

## 4. Dependencias

> Versiones **de referencia**. Según §0.1, actualiza a la última estable compatible y añade lo que haga falta.

### 4.1 `gradle/libs.versions.toml`

```toml
[versions]
kotlin = "2.2.0"
agp = "8.10.1"
ksp = "2.2.0-2.0.2"
coroutines = "1.10.2"
serialization = "1.8.1"

# Android
composeBom = "2025.06.00"
activityCompose = "1.10.1"
lifecycle = "2.9.1"
navigation = "2.9.0"
hilt = "2.56.2"
hiltNavigationCompose = "1.2.0"
room = "2.7.2"
datastore = "1.1.7"
workmanager = "2.10.1"
retrofit = "2.11.0"
okhttp = "4.12.0"
playServicesLocation = "21.3.0"
codeScanner = "16.1.0"
coil = "3.2.0"

# Backend
ktor = "3.1.3"
exposed = "0.61.0"
hikari = "6.3.0"
postgres = "42.7.7"
flyway = "11.8.2"
pgvector = "0.1.6"
onnxruntime = "1.22.0"
djl = "0.33.0"
jsoup = "1.21.1"
crawlerCommons = "1.4"
commonsText = "1.13.1"
logback = "1.5.18"
caffeine = "3.2.0"

# Tests
junit = "5.12.2"
mockk = "1.14.2"
turbine = "1.2.0"
testcontainers = "1.21.0"

[libraries]
# Comunes
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-play-services = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-play-services", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
commons-text = { module = "org.apache.commons:commons-text", version.ref = "commonsText" }

# Android
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigation" }
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { module = "androidx.hilt:hilt-navigation-compose", version.ref = "hiltNavigationCompose" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
room-testing = { module = "androidx.room:room-testing", version.ref = "room" }
datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
work-runtime = { module = "androidx.work:work-runtime-ktx", version.ref = "workmanager" }
retrofit = { module = "com.squareup.retrofit2:retrofit", version.ref = "retrofit" }
retrofit-kotlinx-serialization = { module = "com.squareup.retrofit2:converter-kotlinx-serialization", version.ref = "retrofit" }
okhttp-logging = { module = "com.squareup.okhttp3:logging-interceptor", version.ref = "okhttp" }
play-services-location = { module = "com.google.android.gms:play-services-location", version.ref = "playServicesLocation" }
play-services-code-scanner = { module = "com.google.android.gms:play-services-code-scanner", version.ref = "codeScanner" }
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
coil-network-okhttp = { module = "io.coil-kt.coil3:coil-network-okhttp", version.ref = "coil" }

# Backend
ktor-server-core = { module = "io.ktor:ktor-server-core", version.ref = "ktor" }
ktor-server-netty = { module = "io.ktor:ktor-server-netty", version.ref = "ktor" }
ktor-server-content-negotiation = { module = "io.ktor:ktor-server-content-negotiation", version.ref = "ktor" }
ktor-server-status-pages = { module = "io.ktor:ktor-server-status-pages", version.ref = "ktor" }
ktor-server-call-logging = { module = "io.ktor:ktor-server-call-logging", version.ref = "ktor" }
ktor-server-rate-limit = { module = "io.ktor:ktor-server-rate-limit", version.ref = "ktor" }
ktor-server-auth = { module = "io.ktor:ktor-server-auth", version.ref = "ktor" }
ktor-server-test-host = { module = "io.ktor:ktor-server-test-host", version.ref = "ktor" }
ktor-serialization-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-cio = { module = "io.ktor:ktor-client-cio", version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
exposed-core = { module = "org.jetbrains.exposed:exposed-core", version.ref = "exposed" }
exposed-jdbc = { module = "org.jetbrains.exposed:exposed-jdbc", version.ref = "exposed" }
exposed-java-time = { module = "org.jetbrains.exposed:exposed-java-time", version.ref = "exposed" }
hikari = { module = "com.zaxxer:HikariCP", version.ref = "hikari" }
postgresql = { module = "org.postgresql:postgresql", version.ref = "postgres" }
flyway-core = { module = "org.flywaydb:flyway-core", version.ref = "flyway" }
flyway-postgres = { module = "org.flywaydb:flyway-database-postgresql", version.ref = "flyway" }
pgvector = { module = "com.pgvector:pgvector", version.ref = "pgvector" }
onnxruntime = { module = "com.microsoft.onnxruntime:onnxruntime", version.ref = "onnxruntime" }
djl-tokenizers = { module = "ai.djl.huggingface:tokenizers", version.ref = "djl" }
jsoup = { module = "org.jsoup:jsoup", version.ref = "jsoup" }
crawler-commons = { module = "com.github.crawler-commons:crawler-commons", version.ref = "crawlerCommons" }
caffeine = { module = "com.github.ben-manes.caffeine:caffeine", version.ref = "caffeine" }
logback = { module = "ch.qos.logback:logback-classic", version.ref = "logback" }

# Tests
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter", version.ref = "junit" }
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
testcontainers-postgresql = { module = "org.testcontainers:postgresql", version.ref = "testcontainers" }
testcontainers-junit = { module = "org.testcontainers:junit-jupiter", version.ref = "testcontainers" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ktor = { id = "io.ktor.plugin", version.ref = "ktor" }
```

### 4.2 Para qué sirve cada dependencia clave

| Dependencia | Dónde | Para qué |
|---|---|---|
| `play-services-location` + `kotlinx-coroutines-play-services` | app | Ubicación aproximada con `FusedLocationProviderClient` y `await()` |
| `play-services-code-scanner` | app | Escáner de código de barras de Google **sin pedir permiso de cámara** (la UI la pone Play Services) |
| Room (+ KSP) | app | Lista de la compra y caché local de alias, con funcionamiento sin conexión |
| Retrofit + `converter-kotlinx-serialization` | app | Cliente de la API propia |
| Hilt | app | Inyección de dependencias |
| WorkManager | app | Sincronizar aliases y reportes cuando haya red |
| Ktor server | backend | API REST |
| Ktor client | backend | Llamadas a Overpass, Open Food Facts y conectores |
| PostgreSQL + `pg_trgm` + `pgvector` | backend | Texto completo, tolerancia a erratas y búsqueda semántica en una sola base de datos |
| `com.pgvector:pgvector` | backend | Tipo `vector` en JDBC |
| ONNX Runtime + DJL tokenizers | backend | Calcular embeddings multilingües en la JVM, sin Python |
| Flyway | backend | Migraciones del esquema |
| `commons-text` | shared | Jaro-Winkler y Levenshtein para comparar atributos (marca, variante) |
| `crawler-commons` | backend | Interpretar `robots.txt` antes de cualquier conector |
| Caffeine | backend | Caché en memoria (embeddings de consultas frecuentes, resultados de `/resolve`) |
| Testcontainers | backend | Tests de integración con un Postgres real con pgvector |

### 4.3 Infraestructura local (`docker-compose.yml`)

```yaml
services:
  db:
    image: pgvector/pgvector:pg17
    environment:
      POSTGRES_DB: compra
      POSTGRES_USER: compra
      POSTGRES_PASSWORD: compra
    ports: ["5432:5432"]
    volumes: ["pgdata:/var/lib/postgresql/data"]
volumes:
  pgdata:
```

### 4.4 Modelo de embeddings

- **Modelo recomendado:** `intfloat/multilingual-e5-small` (384 dimensiones, multilingüe, licencia MIT). Es ligero y funciona bien en español.
- **Exportarlo a ONNX** una vez (la herramienta se puede instalar libremente, §0.1):

```bash
pip install "optimum[exporters]" onnx
optimum-cli export onnx --model intfloat/multilingual-e5-small models/e5-small/
# Resultado: models/e5-small/model.onnx y models/e5-small/tokenizer.json
```

- Los ficheros del modelo **no se versionan** en git. Se descargan en el arranque o en el despliegue (ruta en `application.conf`).
- **Importante con e5:** los textos de productos se prefijan con `"passage: "` y las consultas con `"query: "`.
- **Alternativa** si la JVM da problemas: un servicio auxiliar en Python con `sentence-transformers` detrás de un endpoint HTTP interno. La interfaz `Embedder` (§6.4) permite cambiarlo sin tocar nada más.

---

## 5. Base de datos

### 5.1 Conceptos

- **`product_group`**: la *intención genérica* ("Leche semidesnatada 1 L"). Es lo que se compara en modo **genérico**.
- **`canonical_product`**: un producto concreto e independiente de la cadena ("Leche semidesnatada Puleva 1 L", o "Leche semidesnatada marca blanca 1 L"). Pertenece a un grupo. Es lo que se compara en modo **marca**.
- **`chain_product`**: cómo aparece ese producto en una cadena concreta, con su nombre y su identificador. Se enlaza a un `canonical_product`.
- **`price`**: observaciones de precio con historial.
- **`alias`**: textos de usuario aprendidos ("papel water" → grupo X).

> Esto refina el esquema del documento de estrategia, porque añade `product_group` para separar con claridad genérico y marca.

### 5.2 Migración `V1__schema.sql`

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE chain (
  id        SERIAL PRIMARY KEY,
  code      TEXT NOT NULL UNIQUE,           -- 'cadena_a'
  name      TEXT NOT NULL,
  osm_brand TEXT[] NOT NULL DEFAULT '{}'    -- valores de la etiqueta brand en OSM
);

CREATE TABLE city (
  id          SERIAL PRIMARY KEY,
  name        TEXT NOT NULL,
  name_norm   TEXT NOT NULL,
  province    TEXT,
  UNIQUE (name_norm, province)
);

CREATE TABLE store (
  id          SERIAL PRIMARY KEY,
  chain_id    INT REFERENCES chain(id),     -- NULL si no se reconoce la cadena
  city_id     INT NOT NULL REFERENCES city(id),
  osm_type    TEXT NOT NULL,
  osm_id      BIGINT NOT NULL,
  name        TEXT,
  address     TEXT,
  lat         DOUBLE PRECISION NOT NULL,
  lon         DOUBLE PRECISION NOT NULL,
  fetched_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (osm_type, osm_id)
);

CREATE TABLE product_group (
  id            SERIAL PRIMARY KEY,
  name          TEXT NOT NULL,              -- "Leche semidesnatada 1 L"
  name_norm     TEXT NOT NULL,
  category      TEXT,
  unit_kind     TEXT NOT NULL CHECK (unit_kind IN ('mass','volume','count')),
  embedding     vector(384),
  search_tsv    tsvector GENERATED ALWAYS AS (to_tsvector('spanish', name_norm)) STORED
);

CREATE TABLE canonical_product (
  id            SERIAL PRIMARY KEY,
  group_id      INT NOT NULL REFERENCES product_group(id),
  name          TEXT NOT NULL,
  name_norm     TEXT NOT NULL,
  brand         TEXT,                       -- NULL = marca blanca/desconocida
  is_private_label BOOLEAN NOT NULL DEFAULT FALSE,
  variants      TEXT[] NOT NULL DEFAULT '{}',
  unit_kind     TEXT NOT NULL CHECK (unit_kind IN ('mass','volume','count')),
  pack_total    NUMERIC NOT NULL,           -- en g, ml o ud
  ean           TEXT,
  embedding     vector(384),
  search_tsv    tsvector GENERATED ALWAYS AS (to_tsvector('spanish', name_norm)) STORED
);

CREATE TABLE chain_product (
  id              SERIAL PRIMARY KEY,
  chain_id        INT NOT NULL REFERENCES chain(id),
  external_id     TEXT NOT NULL,
  raw_name        TEXT NOT NULL,
  name_norm       TEXT NOT NULL,
  brand           TEXT,
  ean             TEXT,
  unit_kind       TEXT,
  pack_total      NUMERIC,
  image_url       TEXT,
  canonical_id    INT REFERENCES canonical_product(id),
  match_score     REAL,
  match_status    TEXT NOT NULL DEFAULT 'pending'
                  CHECK (match_status IN ('pending','auto','reviewed','rejected')),
  last_seen_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (chain_id, external_id)
);

CREATE TABLE price (
  id               BIGSERIAL PRIMARY KEY,
  chain_product_id INT NOT NULL REFERENCES chain_product(id),
  store_id         INT REFERENCES store(id),   -- NULL = precio general (online)
  price            NUMERIC(8,2) NOT NULL CHECK (price > 0),
  unit_price       NUMERIC(10,4) NOT NULL,     -- € por kg, por L o por ud
  on_offer         BOOLEAN NOT NULL DEFAULT FALSE,
  source           TEXT NOT NULL CHECK (source IN ('online','open_prices','user')),
  confidence       REAL NOT NULL DEFAULT 1.0,
  observed_at      TIMESTAMPTZ NOT NULL
);

CREATE TABLE alias (
  id          SERIAL PRIMARY KEY,
  text_norm   TEXT NOT NULL,
  group_id    INT REFERENCES product_group(id),
  canonical_id INT REFERENCES canonical_product(id),
  votes       INT NOT NULL DEFAULT 1,
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (group_id IS NOT NULL OR canonical_id IS NOT NULL)
);
CREATE UNIQUE INDEX alias_uq ON alias (text_norm, COALESCE(group_id, 0), COALESCE(canonical_id, 0));

CREATE TABLE match_review (
  id               SERIAL PRIMARY KEY,
  chain_product_id INT NOT NULL REFERENCES chain_product(id),
  proposed_canonical_id INT REFERENCES canonical_product(id),
  score            REAL,
  reason           TEXT,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  resolved_at      TIMESTAMPTZ
);

CREATE TABLE user_report (
  id          SERIAL PRIMARY KEY,
  kind        TEXT NOT NULL CHECK (kind IN ('price','wrong_match')),
  payload     JSONB NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  processed   BOOLEAN NOT NULL DEFAULT FALSE
);

-- Índices
CREATE INDEX pg_trgm_idx      ON product_group     USING gin (name_norm gin_trgm_ops);
CREATE INDEX cp_trgm_idx      ON canonical_product USING gin (name_norm gin_trgm_ops);
CREATE INDEX chp_trgm_idx     ON chain_product     USING gin (name_norm gin_trgm_ops);
CREATE INDEX pg_tsv_idx       ON product_group     USING gin (search_tsv);
CREATE INDEX cp_tsv_idx       ON canonical_product USING gin (search_tsv);
CREATE INDEX pg_emb_idx       ON product_group     USING hnsw (embedding vector_cosine_ops);
CREATE INDEX cp_emb_idx       ON canonical_product USING hnsw (embedding vector_cosine_ops);
CREATE INDEX cp_ean_idx       ON canonical_product (ean);
CREATE INDEX chp_ean_idx      ON chain_product (ean);
CREATE INDEX chp_canon_idx    ON chain_product (canonical_id);
CREATE INDEX price_latest_idx ON price (chain_product_id, observed_at DESC);
CREATE INDEX alias_text_idx   ON alias (text_norm);
```

Notas:

- `name_norm` **ya viene normalizado desde Kotlin** (sin tildes, en minúsculas, con unidades unificadas). Por eso basta con la configuración `spanish` de texto completo, que aporta stemming ("yogures" → "yogur").
- El umbral de similitud de trigramas se ajusta por sesión: `SET pg_trgm.similarity_threshold = 0.25;`.
- `pgvector` con `hnsw` requiere pgvector 0.5 o superior. La imagen `pgvector/pgvector:pg17` lo incluye.

---

## 6. Backend (Ktor)

### 6.1 Normalización de texto (`:shared`)

Es la pieza más importante. Lo que escribe el usuario y los nombres de los productos pasan por aquí.

```kotlin
package com.ejemplo.compra.shared.text

import java.text.Normalizer
import java.util.Locale

object TextNormalizer {

    private val DIACRITICS = "\\p{InCombiningDiacriticalMarks}+".toRegex()

    // OJO: "sin" NO es stopword ("sin gluten", "sin lactosa" cambian el producto).
    private val STOPWORDS = setOf(
        "de", "del", "la", "las", "el", "los", "y", "en", "para", "al", "un", "una", "con", "a"
    )

    // Unidades y abreviaturas → forma única
    private val TOKEN_SYNONYMS = mapOf(
        "litro" to "l", "litros" to "l", "lt" to "l", "lts" to "l", "ltr" to "l",
        "mililitros" to "ml", "mililitro" to "ml",
        "centilitros" to "cl", "centilitro" to "cl",
        "gramos" to "g", "gramo" to "g", "gr" to "g", "grs" to "g",
        "kilo" to "kg", "kilos" to "kg", "kgs" to "kg", "kilogramos" to "kg",
        "unidades" to "ud", "unidad" to "ud", "uds" to "ud", "u" to "ud",
        "semi" to "semidesnatada", "semidesnatado" to "semidesnatada",
        "desnatado" to "desnatada", "entero" to "entera",
        "×" to "x",
    )

    /** Normalización completa: minúsculas, sin tildes, sin signos, unidades unificadas. */
    fun normalize(input: String): String {
        var s = input.lowercase(Locale.ROOT)
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replace(DIACRITICS, "") // ñ -> n, á -> a
        s = s.replace(Regex("(\\d),(\\d)"), "$1.$2")       // 1,5 -> 1.5
        s = s.replace(Regex("[^a-z0-9.×x ]"), " ")         // fuera signos (coca-cola -> coca cola)
        s = s.replace(Regex("(\\d)([a-z])"), "$1 $2")      // 500g -> 500 g
        s = s.replace(Regex("([a-z])(\\d)"), "$1 $2")      // x125 -> x 125
        return s.split(' ')
            .asSequence()
            .map { it.trim('.') }
            .filter { it.isNotBlank() }
            .map { TOKEN_SYNONYMS[it] ?: it }
            .filterNot { it in STOPWORDS }
            .joinToString(" ")
    }

    /** Variante compacta para claves de alias: además une palabras ("coca cola" -> "cocacola"). */
    fun aliasKey(input: String): String = normalize(input).replace(" ", "")
}
```

Sinónimos de dominio (cargados desde `dictionaries/synonyms.yaml` y ampliables sin recompilar):

```yaml
# clave normalizada -> forma canónica
"papel water": "papel higienico"
"papel wc": "papel higienico"
"cocacola": "coca cola"
"kleenex": "panuelos papel"
"yogurt": "yogur"
"yogures": "yogur"
"detergente lavadora": "detergente ropa"
"lavavajillas maquina": "detergente lavavajillas"
```

El `SynonymExpander` sustituye frases completas (de la más larga a la más corta) **después** de normalizar.

### 6.2 Cantidades (`QuantityParser`)

```kotlin
package com.ejemplo.compra.shared.text

enum class UnitKind { MASS, VOLUME, COUNT }

/** total en unidad base: g (masa), ml (volumen) o ud (conteo). */
data class Quantity(val kind: UnitKind, val total: Double, val packs: Int = 1)

object QuantityParser {
    private const val NUM = "(\\d+(?:\\.\\d+)?)"
    private val MULTI = Regex("(\\d+) x $NUM (g|kg|ml|cl|l)\\b")          // "4 x 125 g"
    private val SINGLE = Regex("$NUM (g|kg|ml|cl|l)\\b")                   // "1.5 l"
    private val COUNT = Regex("(\\d+) (ud|rollos|rollo|huevos|bolsas|capsulas|lavados)\\b")
    private val DOZEN = Regex("\\b(media )?docena\\b")

    /** Recibe texto YA normalizado. */
    fun parse(norm: String): Quantity? {
        MULTI.find(norm)?.let { m ->
            val packs = m.groupValues[1].toInt()
            val (kind, each) = toBase(m.groupValues[2].toDouble(), m.groupValues[3])
            return Quantity(kind, packs * each, packs)
        }
        SINGLE.find(norm)?.let { m ->
            val (kind, v) = toBase(m.groupValues[1].toDouble(), m.groupValues[2])
            return Quantity(kind, v)
        }
        COUNT.find(norm)?.let { m -> return Quantity(UnitKind.COUNT, m.groupValues[1].toDouble()) }
        DOZEN.find(norm)?.let { m -> return Quantity(UnitKind.COUNT, if (m.groupValues[1].isNotEmpty()) 6.0 else 12.0) }
        return null
    }

    private fun toBase(v: Double, unit: String): Pair<UnitKind, Double> = when (unit) {
        "g" -> UnitKind.MASS to v
        "kg" -> UnitKind.MASS to v * 1000
        "ml" -> UnitKind.VOLUME to v
        "cl" -> UnitKind.VOLUME to v * 10
        "l" -> UnitKind.VOLUME to v * 1000
        else -> error("unidad desconocida: $unit")
    }

    /** Precio unitario en €/kg, €/L o €/ud. */
    fun unitPrice(price: Double, q: Quantity): Double = when (q.kind) {
        UnitKind.MASS, UnitKind.VOLUME -> price / (q.total / 1000.0)
        UnitKind.COUNT -> price / q.total
    }
}
```

### 6.3 Atributos (`AttributeExtractor`)

```kotlin
package com.ejemplo.compra.shared.text

data class ParsedText(
    val norm: String,
    val brand: String?,
    val variants: Set<String>,
    val quantity: Quantity?,
)

/**
 * brands: marcas conocidas, ya normalizadas (cargadas de BD/diccionario).
 * variantGroups: grupos de variantes mutuamente excluyentes.
 */
class AttributeExtractor(
    private val brands: Set<String>,
    private val variantGroups: List<Set<String>> = DEFAULT_VARIANT_GROUPS,
    private val freeVariants: Set<String> = DEFAULT_FREE_VARIANTS,
) {
    fun parse(raw: String): ParsedText {
        val norm = TextNormalizer.normalize(raw)
        val padded = " $norm "
        val brand = brands.filter { padded.contains(" $it ") }.maxByOrNull { it.length }
        val variants = (variantGroups.flatten() + freeVariants).filter { padded.contains(" $it ") }.toSet()
        return ParsedText(norm, brand, variants, QuantityParser.parse(norm))
    }

    /** true si a y b tienen variantes distintas del mismo grupo excluyente. */
    fun conflicts(a: Set<String>, b: Set<String>): Boolean =
        variantGroups.any { g -> val x = a intersect g; val y = b intersect g
            x.isNotEmpty() && y.isNotEmpty() && x != y }

    companion object {
        val DEFAULT_VARIANT_GROUPS = listOf(
            setOf("entera", "semidesnatada", "desnatada"),
            setOf("con gas", "sin gas"),
            setOf("zero", "light", "original"),
            setOf("integral", "blanco"),
            setOf("con sal", "sin sal"),
        )
        val DEFAULT_FREE_VARIANTS = setOf(
            "sin gluten", "sin lactosa", "ecologico", "bio", "natural", "griego", "azucarado", "sin azucar"
        )
    }
}
```

### 6.4 Embeddings (`E5Embedder`)

```kotlin
package com.ejemplo.compra.backend.embeddings

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.LongBuffer
import java.nio.file.Path
import kotlin.math.sqrt

interface Embedder : AutoCloseable {
    val dim: Int
    fun embedQuery(text: String): FloatArray
    fun embedPassage(text: String): FloatArray
}

class E5Embedder(modelDir: Path) : Embedder {
    override val dim = 384
    private val env = OrtEnvironment.getEnvironment()
    private val session = env.createSession(modelDir.resolve("model.onnx").toString(), OrtSession.SessionOptions())
    private val tokenizer = HuggingFaceTokenizer.newInstance(modelDir.resolve("tokenizer.json"))

    override fun embedQuery(text: String) = embed("query: $text")
    override fun embedPassage(text: String) = embed("passage: $text")

    private fun embed(text: String): FloatArray {
        val enc = tokenizer.encode(text)
        val ids = enc.ids
        val mask = enc.attentionMask
        val shape = longArrayOf(1, ids.size.toLong())

        OnnxTensor.createTensor(env, LongBuffer.wrap(ids), shape).use { idsT ->
            OnnxTensor.createTensor(env, LongBuffer.wrap(mask), shape).use { maskT ->
                OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(ids.size)), shape).use { typeT ->
                    val all = mapOf("input_ids" to idsT, "attention_mask" to maskT, "token_type_ids" to typeT)
                    val inputs = all.filterKeys { it in session.inputNames }   // según cómo se exportó
                    session.run(inputs).use { out ->
                        @Suppress("UNCHECKED_CAST")
                        val hidden = (out[0].value as Array<Array<FloatArray>>)[0]  // [tokens][dim]
                        return meanPoolNormalize(hidden, mask)
                    }
                }
            }
        }
    }

    private fun meanPoolNormalize(hidden: Array<FloatArray>, mask: LongArray): FloatArray {
        val v = FloatArray(dim)
        var count = 0
        for (t in hidden.indices) if (mask[t] == 1L) {
            count++
            for (d in 0 until dim) v[d] += hidden[t][d]
        }
        var norm = 0f
        for (d in 0 until dim) { v[d] /= count; norm += v[d] * v[d] }
        val n = sqrt(norm)
        for (d in 0 until dim) v[d] /= n
        return v
    }

    override fun close() { session.close() }
}
```

- Embebe el **texto normalizado más sinónimos expandidos**, no el texto crudo.
- Cachea los embeddings de las consultas en Caffeine (clave: texto normalizado, hasta 50.000 entradas, 24 h).
- `OrtSession.run` es seguro entre hilos, así que basta con una sola instancia del embedder.
- Las llamadas son bloqueantes: ejecútalas en `Dispatchers.Default`.

### 6.5 Búsqueda híbrida (`HybridSearchRepository`)

Busca sobre **grupos** (modo genérico) o sobre **productos canónicos** (modo marca, o cuando la consulta contiene una marca). La consulta con *Reciprocal Rank Fusion* (k = 60):

```sql
-- :q = texto normalizado (y expandido); :emb = embedding de la consulta; :k = candidatos por señal
WITH
fts AS (
  SELECT g.id, row_number() OVER (ORDER BY ts_rank(g.search_tsv, websearch_to_tsquery('spanish', :q)) DESC) AS r
  FROM product_group g
  WHERE g.search_tsv @@ websearch_to_tsquery('spanish', :q)
  ORDER BY r LIMIT :k
),
tri AS (
  SELECT g.id, row_number() OVER (ORDER BY similarity(g.name_norm, :q) DESC) AS r
  FROM product_group g
  WHERE g.name_norm % :q
  ORDER BY r LIMIT :k
),
sem AS (
  SELECT g.id, row_number() OVER (ORDER BY g.embedding <=> :emb) AS r
  FROM product_group g
  ORDER BY g.embedding <=> :emb LIMIT :k
),
fused AS (
  SELECT id, SUM(1.0 / (60 + r)) AS rrf
  FROM (SELECT * FROM fts UNION ALL SELECT * FROM tri UNION ALL SELECT * FROM sem) u
  GROUP BY id
)
SELECT g.id, g.name, g.name_norm, g.unit_kind, f.rrf,
       similarity(g.name_norm, :q) AS trigram_sim
FROM fused f JOIN product_group g ON g.id = f.id
ORDER BY f.rrf DESC
LIMIT 20;
```

Implementación en Kotlin (JDBC con parámetros con nombre convertidos a `?`):

```kotlin
class HybridSearchRepository(private val ds: javax.sql.DataSource) {

    data class Hit(val id: Int, val name: String, val nameNorm: String, val unitKind: String,
                   val rrf: Double, val trigramSim: Double)

    fun searchGroups(qNorm: String, emb: FloatArray, k: Int = 30): List<Hit> =
        ds.connection.use { conn ->
            com.pgvector.PGvector.addVectorType(conn)
            conn.createStatement().use { it.execute("SET pg_trgm.similarity_threshold = 0.25") }
            conn.prepareStatement(GROUP_SQL).use { st ->
                val v = com.pgvector.PGvector(emb)
                // Orden de los ? en GROUP_SQL: q, q, k, q, q, k, emb, emb, k, q
                var i = 1
                st.setString(i++, qNorm); st.setString(i++, qNorm); st.setInt(i++, k)
                st.setString(i++, qNorm); st.setString(i++, qNorm); st.setInt(i++, k)
                st.setObject(i++, v); st.setObject(i++, v); st.setInt(i++, k)
                st.setString(i, qNorm)
                st.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) add(Hit(
                            rs.getInt("id"), rs.getString("name"), rs.getString("name_norm"),
                            rs.getString("unit_kind"), rs.getDouble("rrf"), rs.getDouble("trigram_sim")))
                    }
                }
            }
        }

    companion object {
        // La SQL de arriba con ? en lugar de :q/:emb/:k, en ese mismo orden.
        // Recomendación: guardarla en resources/sql/search_groups.sql y cargarla al arrancar.
        val GROUP_SQL: String = loadSql("sql/search_groups.sql")
    }
}
```

> Para no equivocarse con el orden de los `?`, puede instalarse una librería de parámetros con nombre (p. ej. **Jdbi**, Apache-2.0) y anotarlo en `DECISIONS.md`.

Existe una consulta análoga, `searchCanonical`, sobre `canonical_product`, que además devuelve `brand`, `variants`, `pack_total` y `unit_kind` para el reranking.

### 6.6 Reranking y confianza (`Reranker`)

```kotlin
class Reranker(private val extractor: AttributeExtractor) {

    data class Candidate(
        val id: Int, val name: String, val nameNorm: String, val rrf: Double, val trigramSim: Double,
        val brand: String? = null, val variants: Set<String> = emptySet(), val quantity: Quantity? = null,
    )
    data class Scored(val c: Candidate, val score: Double)

    private val maxRrf = 3.0 / 61.0   // las tres señales en primera posición

    fun rerank(query: ParsedText, cands: List<Candidate>): List<Scored> = cands.map { c ->
        var s = 0.55 * (c.rrf / maxRrf).coerceAtMost(1.0) + 0.25 * c.trigramSim

        // Marca
        if (query.brand != null) s += if (c.brand == query.brand) 0.25 else -0.40

        // Variantes
        val shared = query.variants intersect c.variants
        s += 0.08 * shared.size
        if (extractor.conflicts(query.variants, c.variants)) s -= 0.60

        // Cantidad (solo si el usuario la dio)
        val qq = query.quantity; val cq = c.quantity
        if (qq != null && cq != null) {
            s += if (qq.kind != cq.kind) -0.30
                 else if (kotlin.math.abs(qq.total - cq.total) / qq.total <= 0.10) 0.12 else -0.05
        }
        Scored(c, s.coerceIn(0.0, 1.0))
    }.sortedByDescending { it.score }

    companion object {
        // Valores INICIALES: calibrar con el golden set (§9)
        const val HIGH = 0.72
        const val MEDIUM = 0.42
        const val MARGIN = 0.12
    }
}
```

### 6.7 Servicio de resolución (`ResolveService`)

Flujo de `/v1/resolve`:

```
1. norm = SynonymExpander.expand(TextNormalizer.normalize(query))
2. Alias exacto (aliasKey(norm)) con votos ≥ 2  → RESOLVED (score 1.0)
3. Si la consulta parece un EAN (8 o 13 dígitos) → buscar por EAN → RESOLVED
4. parsed = AttributeExtractor.parse(query)
5. emb = embedder.embedQuery(norm)   (con caché)
6. Si parsed.brand != null o mode == BRAND → searchCanonical; si no → searchGroups
7. ranked = reranker.rerank(parsed, candidatos)
8. Decisión:
   top ≥ HIGH y (top − segundo) ≥ MARGIN  → RESOLVED
   top ≥ MEDIUM                            → CHOOSE  (3 mejores)
   resto                                   → REFINE  (sugerencias de categoría)
```

```kotlin
class ResolveService(
    private val aliases: AliasRepository,
    private val search: HybridSearchRepository,
    private val embedder: Embedder,
    private val extractor: AttributeExtractor,
    private val reranker: Reranker,
    private val synonyms: SynonymExpander,
    private val embCache: com.github.benmanes.caffeine.cache.Cache<String, FloatArray>,
) {
    suspend fun resolve(req: ResolveRequest): ResolveResponse = withContext(Dispatchers.Default) {
        val norm = synonyms.expand(TextNormalizer.normalize(req.query))
        if (norm.isBlank()) return@withContext ResolveResponse(ResolveStatus.REFINE, emptyList())

        aliases.findConfident(TextNormalizer.aliasKey(norm))?.let {
            return@withContext ResolveResponse(ResolveStatus.RESOLVED, listOf(it.toDto(score = 1.0)))
        }

        val parsed = extractor.parse(req.query).copy(norm = norm)
        val emb = embCache.get(norm) { embedder.embedQuery(it) }
        val useCanonical = parsed.brand != null || req.mode == ItemMode.BRAND
        val cands = if (useCanonical) search.searchCanonical(norm, emb).map { it.toCandidate() }
                    else search.searchGroups(norm, emb).map { it.toCandidate() }
        val ranked = reranker.rerank(parsed, cands)

        val top = ranked.firstOrNull()
        val second = ranked.getOrNull(1)?.score ?: 0.0
        when {
            top == null -> ResolveResponse(ResolveStatus.REFINE, emptyList())
            top.score >= Reranker.HIGH && top.score - second >= Reranker.MARGIN ->
                ResolveResponse(ResolveStatus.RESOLVED, listOf(top.toDto(useCanonical)))
            top.score >= Reranker.MEDIUM ->
                ResolveResponse(ResolveStatus.CHOOSE, ranked.take(3).map { it.toDto(useCanonical) })
            else -> ResolveResponse(ResolveStatus.REFINE, ranked.take(5).map { it.toDto(useCanonical) })
        }
    }
}
```

**Aprendizaje (`POST /v1/aliases`):** cuando el usuario elige una opción, se hace un *upsert* de `alias(text_norm, group_id|canonical_id)` con `votes = votes + 1`. Un alias solo se usa como atajo global con **2 votos o más** de usuarios distintos (evita envenenamiento). Con 1 voto se usa solo en el dispositivo de ese usuario (caché local de la app, §7.6).

### 6.8 Ingesta y emparejamiento de productos de cadenas

#### Interfaz de conector

```kotlin
data class RawProduct(
    val externalId: String,
    val name: String,
    val brand: String?,
    val ean: String?,
    val price: Double,
    val unitPrice: Double?,      // si la tienda lo da
    val onOffer: Boolean,
    val imageUrl: String?,
    val category: String?,
    val postalCode: String? = null,
)

interface ChainConnector {
    val chainCode: String
    /** Emite el catálogo completo. Debe respetar robots.txt y los límites de frecuencia. */
    fun fetchCatalog(): kotlinx.coroutines.flow.Flow<RawProduct>
}
```

- **`FixtureConnector`** lee `fixtures/products-sample.csv`. Es el conector de desarrollo y tests. Crea un CSV con unos 300 productos inventados de 3 cadenas ficticias (`cadena_a`, `cadena_b`, `cadena_c`), con nombres deliberadamente distintos entre cadenas para el mismo producto.
- **Conectores reales:** una clase por cadena, desactivados por configuración hasta la validación legal. Requisitos obligatorios para cualquier conector real:
  - comprobar `robots.txt` con `crawler-commons` antes de pedir nada;
  - un máximo de 1 petición por segundo por dominio y `User-Agent` identificable con contacto;
  - solo datos públicos, sin iniciar sesión ni saltarse protecciones;
  - reintentos con *backoff* exponencial y *circuit breaker* (si hay 5 fallos seguidos, se pausa y se avisa);
  - parseo con `kotlinx.serialization` si la fuente es JSON, o con `jsoup` si es HTML.

#### Pipeline (`IngestionPipeline`)

Por cada `RawProduct`:

```
1. norm = normalize(name); q = QuantityParser.parse(norm); attrs = extractor.parse(name)
2. Upsert chain_product (chain_id, external_id) → raw_name, name_norm, brand, ean, pack_total, unit_kind, last_seen_at
3. Calcular unit_price (el de la tienda o QuantityParser.unitPrice). Si no hay cantidad → unit_price = price y unit_kind = 'count'
4. Precio:
   - Si el último precio es igual y tiene < 24 h → no insertar
   - Si |nuevo/último| > 3 o < 0.33 → insertar con confidence 0.3 y abrir match_review (posible error)
   - Si no → insertar
5. Si chain_product.canonical_id ya está y match_status ∈ (auto, reviewed) → fin
6. Emparejar:
   a) EAN: si hay canonical_product con ese EAN → enlazar, match_status = auto, score 1.0
   b) Si no: embedPassage(norm) → searchCanonical(norm, emb) → rerank con attrs
      - score ≥ 0.85, misma unit_kind, pack_total ±5 % y sin conflicto de variantes → auto
      - 0.60 ≤ score < 0.85 → match_review (propuesta)
      - score < 0.60 → crear canonical_product nuevo y asignarlo a un grupo
        (buscar grupo con searchGroups; si no hay ≥ 0.70 → crear grupo nuevo + match_review)
7. Guardar embedding en canonical_product / product_group si son nuevos
```

**Reglas duras de equivalencia** (nunca auto si fallan):

- misma `unit_kind`;
- `pack_total` dentro del ±5 % (el modo genérico compara por precio unitario, pero el canónico debe ser el mismo formato);
- sin conflicto de variantes (entera ≠ desnatada);
- si los dos tienen marca y es distinta → no es el mismo canónico (sí puede ser el mismo grupo).

#### Enriquecimiento con Open Food Facts

Para productos con EAN, `OpenFoodFactsClient` consulta:

```
GET https://world.openfoodfacts.org/api/v2/product/{ean}.json?fields=product_name,brands,quantity,categories_tags,image_front_small_url
User-Agent: Comparador/1.0 (contacto@tudominio.es)   ← obligatorio según su política de uso
```

Úsalo para completar la marca, la cantidad y la categoría. Guarda el resultado (caché de 30 días) y **no lo consultes en cada búsqueda**.

#### LLM opcional para casos ambiguos

Se puede añadir un cliente HTTP a una API de LLM (p. ej. Claude) **solo en proceso por lotes** sobre `match_review` pendientes, para:

- extraer atributos de nombres muy sucios;
- proponer el grupo adecuado entre los 5 mejores candidatos.

Instrucción tipo (salida JSON estricta):

```text
Eres un clasificador de productos de supermercado en España.
Dado un producto y una lista de candidatos, responde SOLO con JSON:
{"brand": string|null, "variants": [string], "quantity": {"kind":"mass|volume|count","total":number}|null,
 "best_candidate_id": number|null, "same_product": boolean, "confidence": number}
Si no estás seguro, best_candidate_id = null. No inventes marcas.
Producto: "<raw_name>"
Candidatos: <id y nombre de cada candidato>
```

El resultado **no se aplica automáticamente** si `confidence < 0.9`; queda para revisión.

#### Jobs programados

| Job | Frecuencia | Qué hace |
|---|---|---|
| `IngestJob(chain)` | diario (escalonado por cadena) | Ejecuta conector y pipeline |
| `ReviewLlmJob` | cada hora | Propuestas de LLM para `match_review` |
| `UserReportJob` | cada 15 min | Valida precios de usuarios (§6.10) |
| `StaleCleanupJob` | diario | Marca `chain_product` no vistos en 14 días como no disponibles |
| `StoreRefreshJob` | semanal | Refresca tiendas de ciudades activas desde Overpass |

Para programarlos sirve un `CoroutineScope` con `delay` en el arranque de Ktor, o se puede instalar **JobRunr** (LGPL/comercial: revisar licencia) o **db-scheduler** (Apache-2.0, recomendado) si hace falta persistencia y que no se repitan en varias instancias.

### 6.9 Tiendas de una ciudad (Overpass / OpenStreetMap)

```kotlin
class OverpassClient(private val http: io.ktor.client.HttpClient) {
    suspend fun supermarkets(city: String): OverpassResponse {
        val safe = city.replace("\"", "")
        val q = """
            [out:json][timeout:25];
            area["name"="$safe"]["boundary"="administrative"]["admin_level"~"^(7|8)$"]->.a;
            nwr["shop"="supermarket"](area.a);
            out center tags;
        """.trimIndent()
        return http.post("https://overpass-api.de/api/interpreter") {
            setBody(io.ktor.client.request.forms.FormDataContent(io.ktor.http.Parameters.build { append("data", q) }))
            header("User-Agent", "Comparador/1.0 (contacto@tudominio.es)")
        }.body()
    }
}
```

- Asigna cada tienda a una cadena con las etiquetas `brand` o `brand:wikidata` frente a `chain.osm_brand`. Si no casa, `chain_id = NULL` ("otros").
- **Caché de 7 días por ciudad** en la tabla `store`. Nunca consultes Overpass por cada usuario.
- Si una ciudad devuelve 0 áreas (nombres duplicados o distinto `admin_level`), reintenta sin `admin_level` y filtra por provincia.
- Atribución obligatoria en la app: "© colaboradores de OpenStreetMap".

### 6.10 Precios aportados por usuarios

- `POST /v1/reports/price` con `{ean | chainProductId, chainId, storeId?, price, observedAt, photoProof?}`.
- **Validación:**
  - un precio con foto vale `confidence = 0.8`; sin foto, `0.5`;
  - si se desvía más de un 50 % de la mediana de los últimos 30 días → `0.2` y revisión;
  - si dos o más usuarios distintos coinciden (±2 %) en 7 días → sube a `0.9`.
- El algoritmo de comparación usa `confidence ≥ 0.5`.
- Opcional: publicar los precios validados en Open Prices (mismo espíritu ODbL), lo que exige autenticación con su API.

### 6.11 Comparación (`CompareService`)

#### SQL: mejor oferta por cadena para un ítem

```sql
-- Modo GENERIC: :groupId; modo BRAND: sustituir la condición por cp.id = :canonicalId
SELECT DISTINCT ON (chp.chain_id)
       chp.chain_id, chp.id AS chain_product_id, chp.raw_name, chp.image_url, chp.pack_total,
       lp.price, lp.unit_price, lp.on_offer, lp.observed_at, lp.source, lp.confidence
FROM canonical_product cp
JOIN chain_product chp
  ON chp.canonical_id = cp.id AND chp.match_status IN ('auto','reviewed')
JOIN LATERAL (
  SELECT p.* FROM price p
  WHERE p.chain_product_id = chp.id
    AND p.confidence >= 0.5
    AND (p.store_id IS NULL OR p.store_id = ANY(:storeIds))
  ORDER BY p.observed_at DESC
  LIMIT 1
) lp ON TRUE
WHERE cp.group_id = :groupId
  AND chp.chain_id = ANY(:chainIds)
  AND chp.last_seen_at > now() - interval '14 days'
ORDER BY chp.chain_id, lp.unit_price ASC, lp.observed_at DESC;
```

#### Lógica en Kotlin

```kotlin
data class Offer(val chainId: Int, val chainProductId: Int, val name: String, val price: Double,
                 val unitPrice: Double, val observedAt: java.time.Instant, val source: String, val stale: Boolean)

data class ItemComparison(val itemId: String, val offers: List<Offer>, val best: Offer?)

data class BasketResult(
    val items: List<ItemComparison>,
    val totalByChain: Map<Int, Double>,          // solo ítems disponibles en esa cadena
    val missingByChain: Map<Int, List<String>>,  // ítems sin precio en esa cadena
    val splitTotal: Double,                      // comprando cada cosa en su cadena más barata
)

class CompareService(private val repo: CompareRepository, private val clock: java.time.Clock) {
    private val staleAfter = java.time.Duration.ofDays(7)

    suspend fun compare(req: CompareRequest): BasketResult {
        val items = req.items.map { item ->
            val offers = repo.bestOffersPerChain(item, req.chainIds, req.storeIds)
                .map { it.copy(stale = it.observedAt < clock.instant().minus(staleAfter)) }
            // Preferir precios frescos; los antiguos solo si no hay nada fresco
            val fresh = offers.filterNot { it.stale }
            val best = (fresh.ifEmpty { offers }).minByOrNull { it.unitPrice }
            ItemComparison(item.id, offers.sortedBy { it.unitPrice }, best)
        }
        val totals = mutableMapOf<Int, Double>()
        val missing = mutableMapOf<Int, MutableList<String>>()
        for (chain in req.chainIds) for ((i, ic) in items.withIndex()) {
            val o = ic.offers.firstOrNull { it.chainId == chain }
            if (o == null) missing.getOrPut(chain) { mutableListOf() } += ic.itemId
            else totals[chain] = (totals[chain] ?: 0.0) + o.price * req.items[i].quantity
        }
        val split = items.withIndex().sumOf { (i, ic) -> (ic.best?.price ?: 0.0) * req.items[i].quantity }
        return BasketResult(items, totals, missing, split)
    }
}
```

> En modo genérico, `quantity` es **número de envases** del formato del grupo. Si el usuario indica "3 L" y el grupo es de 1 L, la app lo convierte a 3 envases antes de llamar.

### 6.12 Contrato de la API (DTOs en `:shared`)

```kotlin
@Serializable enum class ItemMode { GENERIC, BRAND }
@Serializable enum class ResolveStatus { RESOLVED, CHOOSE, REFINE }

@Serializable data class ResolveRequest(val query: String, val mode: ItemMode = ItemMode.GENERIC)
@Serializable data class CandidateDto(
    val groupId: Int? = null, val canonicalId: Int? = null,
    val name: String, val brand: String? = null, val score: Double, val imageUrl: String? = null,
)
@Serializable data class ResolveResponse(val status: ResolveStatus, val candidates: List<CandidateDto>)

@Serializable data class AliasRequest(val query: String, val groupId: Int? = null, val canonicalId: Int? = null)

@Serializable data class CompareItemDto(
    val id: String, val mode: ItemMode, val groupId: Int? = null, val canonicalId: Int? = null, val quantity: Int = 1,
)
@Serializable data class CompareRequest(val chainIds: List<Int>, val storeIds: List<Int> = emptyList(),
                                        val items: List<CompareItemDto>)
// CompareResponse = BasketResult serializable (offers con observedAt en ISO-8601)

@Serializable data class StoreDto(val id: Int, val chainId: Int?, val chainName: String?, val name: String?,
                                  val address: String?, val lat: Double, val lon: Double)
@Serializable data class CityStoresResponse(val cityId: Int, val city: String, val stores: List<StoreDto>,
                                            val attribution: String = "© colaboradores de OpenStreetMap")
```

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/v1/cities?query=` | Buscar ciudad (selección manual) |
| GET | `/v1/stores?city=&province=` | Tiendas de una ciudad (caché OSM) |
| POST | `/v1/resolve` | Resolver texto del usuario |
| POST | `/v1/aliases` | Registrar elección del usuario |
| GET | `/v1/products/by-ean/{ean}` | Producto por código de barras |
| POST | `/v1/compare` | Comparar lista |
| POST | `/v1/reports/price` | Aportar precio |
| POST | `/v1/reports/wrong-match` | Reportar emparejamiento erróneo |
| GET | `/health` | Salud (BD, embedder, última ingesta por cadena) |

Requisitos transversales:

- Autenticación mediante cabecera `X-Api-Key` por instalación (generada en el primer arranque de la app).
- Rate limit de 60 req/min por clave (`ktor-server-rate-limit`).
- Errores con `StatusPages` en formato `{code, message}`.
- Objetivo de latencia: `/resolve` p95 < 300 ms y `/compare` (30 ítems) p95 < 800 ms.

---

## 7. App Android

### 7.1 `app/build.gradle.kts` (lo esencial)

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.ejemplo.compra"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.ejemplo.compra"
        minSdk = 26
        targetSdk = 36
        buildConfigField("String", "API_BASE_URL", "\"${project.findProperty("apiBaseUrl") ?: "http://10.0.2.2:8080/"}\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":shared"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui); implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview); debugImplementation(libs.compose.ui.tooling)
    implementation(libs.activity.compose); implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose); implementation(libs.lifecycle.runtime.compose)
    implementation(libs.hilt.android); ksp(libs.hilt.compiler); implementation(libs.hilt.navigation.compose)
    implementation(libs.room.runtime); implementation(libs.room.ktx); ksp(libs.room.compiler)
    implementation(libs.datastore.preferences); implementation(libs.work.runtime)
    implementation(libs.retrofit); implementation(libs.retrofit.kotlinx.serialization); implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android); implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.play.services.location); implementation(libs.play.services.code.scanner)
    implementation(libs.coil.compose); implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit.jupiter); testImplementation(libs.mockk)
    testImplementation(libs.turbine); testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom)); androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.room.testing)
}
```

> `10.0.2.2` es el host desde el emulador. En producción, **solo HTTPS**. Si en desarrollo se usa HTTP, permítelo únicamente en `debug` con `network_security_config`.

### 7.2 Manifiesto

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <!-- NO se pide ACCESS_FINE_LOCATION: para la ciudad basta la aproximada. -->
    <!-- NO se pide CAMERA: el escáner de Play Services gestiona la cámara él mismo. -->

    <application android:name=".App" ...>
        <meta-data
            android:name="com.google.mlkit.vision.DEPENDENCIES"
            android:value="barcode_ui" /> <!-- descarga anticipada del módulo del escáner -->
        ...
    </application>
</manifest>
```

### 7.3 Ubicación → ciudad

```kotlin
@Singleton
class LocationRepository @Inject constructor(@ApplicationContext private val ctx: Context) {

    private val fused = LocationServices.getFusedLocationProviderClient(ctx)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun currentCoarseLocation(): Location? {
        if (!hasPermission()) return null
        val req = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMaxUpdateAgeMillis(15 * 60 * 1000L)
            .setDurationMillis(10_000L)
            .build()
        return runCatching { fused.getCurrentLocation(req, null).await() }.getOrNull()
            ?: runCatching { fused.lastLocation.await() }.getOrNull()
    }

    suspend fun cityFrom(location: Location): CityGuess? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(ctx, Locale.forLanguageTag("es-ES"))
        val address: Address? = if (Build.VERSION.SDK_INT >= 33) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(location.latitude, location.longitude, 1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses.firstOrNull())
                        override fun onError(errorMessage: String?) = cont.resume(null)
                    })
            }
        } else withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            runCatching { geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull() }.getOrNull()
        }
        val city = address?.locality ?: address?.subAdminArea ?: return null
        return CityGuess(name = city, province = address.subAdminArea)
    }
}

data class CityGuess(val name: String, val province: String?)
```

Petición del permiso en Compose:

```kotlin
@Composable
fun CityScreen(vm: CityViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.onPermissionResult(granted)
    }
    // Explicar ANTES de pedir: "Usamos tu ubicación aproximada solo para saber tu ciudad".
    when (val s = state) {
        CityUiState.NeedsPermission -> PermissionRationale(
            onAccept = { launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
            onManual = vm::chooseManually)
        CityUiState.Detecting -> LoadingIndicator("Detectando tu ciudad…")
        is CityUiState.Detected -> ConfirmCity(s.city, onConfirm = vm::confirm, onChange = vm::chooseManually)
        is CityUiState.Manual -> CitySearch(onQuery = vm::searchCities, results = s.results, onPick = vm::confirm)
    }
}
```

Reglas:

- Si el permiso se deniega o el Geocoder falla, se pasa a **selección manual** (`/v1/cities?query=`). La app nunca se bloquea por la ubicación.
- La ciudad confirmada se guarda en DataStore. No se pregunta en cada arranque, pero se ofrece un "cambiar ciudad".
- **Al backend solo se envía el nombre de la ciudad**, nunca las coordenadas.

### 7.4 Base de datos local (Room)

```kotlin
@Entity(tableName = "shopping_list")
data class ShoppingListEntity(@PrimaryKey val id: String, val name: String, val createdAt: Long)

enum class ItemStatus { PENDING, RESOLVED, NEEDS_CHOICE, NEEDS_REFINE, OFFLINE }

@Entity(
    tableName = "shopping_item",
    foreignKeys = [ForeignKey(entity = ShoppingListEntity::class, parentColumns = ["id"],
                              childColumns = ["listId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("listId")]
)
data class ShoppingItemEntity(
    @PrimaryKey val id: String,
    val listId: String,
    val rawText: String,
    val mode: ItemMode,
    val groupId: Int?,
    val canonicalId: Int?,
    val resolvedName: String?,
    val quantity: Int,
    val status: ItemStatus,
    val checked: Boolean = false,
    val position: Int,
)

@Entity(tableName = "local_alias")
data class LocalAliasEntity(
    @PrimaryKey val key: String,       // TextNormalizer.aliasKey(texto)
    val groupId: Int?,
    val canonicalId: Int?,
    val name: String,
    val updatedAt: Long,
)

@Entity(tableName = "selected_chain")
data class SelectedChainEntity(@PrimaryKey val chainId: Int, val name: String)

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_item WHERE listId = :listId ORDER BY position")
    fun observeItems(listId: String): Flow<List<ShoppingItemEntity>>
    @Upsert suspend fun upsertItem(item: ShoppingItemEntity)
    @Query("DELETE FROM shopping_item WHERE id = :id") suspend fun deleteItem(id: String)
    @Query("SELECT * FROM local_alias WHERE `key` = :key") suspend fun alias(key: String): LocalAliasEntity?
    @Upsert suspend fun upsertAlias(alias: LocalAliasEntity)
}
```

(Usa un `TypeConverter` para `ItemMode` e `ItemStatus`, o guárdalos como `String`.)

### 7.5 Cliente de la API

```kotlin
interface CompraApi {
    @GET("v1/cities") suspend fun cities(@Query("query") q: String): List<CityDto>
    @GET("v1/stores") suspend fun stores(@Query("city") city: String, @Query("province") province: String?): CityStoresResponse
    @POST("v1/resolve") suspend fun resolve(@Body body: ResolveRequest): ResolveResponse
    @POST("v1/aliases") suspend fun alias(@Body body: AliasRequest)
    @GET("v1/products/by-ean/{ean}") suspend fun byEan(@Path("ean") ean: String): CandidateDto
    @POST("v1/compare") suspend fun compare(@Body body: CompareRequest): CompareResponse
    @POST("v1/reports/price") suspend fun reportPrice(@Body body: PriceReportDto)
    @POST("v1/reports/wrong-match") suspend fun reportWrongMatch(@Body body: WrongMatchDto)
}

@Module @InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun json() = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Provides @Singleton
    fun okHttp(keyStore: ApiKeyStore): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain -> chain.proceed(chain.request().newBuilder()
            .header("X-Api-Key", keyStore.key()).build()) }
        .apply { if (BuildConfig.DEBUG) addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC)) }
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
        .build()

    @Provides @Singleton
    fun api(client: OkHttpClient, json: Json): CompraApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build().create(CompraApi::class.java)
}
```

### 7.6 Añadir un producto a la lista (flujo central)

```kotlin
class AddItemUseCase @Inject constructor(
    private val dao: ShoppingDao,
    private val api: CompraApi,
) {
    sealed interface Result {
        data class Resolved(val item: ShoppingItemEntity) : Result
        data class Choose(val item: ShoppingItemEntity, val options: List<CandidateDto>) : Result
        data class Refine(val item: ShoppingItemEntity, val suggestions: List<CandidateDto>) : Result
        data class Offline(val item: ShoppingItemEntity) : Result
    }

    suspend operator fun invoke(listId: String, text: String, mode: ItemMode, position: Int): Result {
        val base = ShoppingItemEntity(UUID.randomUUID().toString(), listId, text.trim(), mode,
            null, null, null, quantity = 1, status = ItemStatus.PENDING, position = position)

        // 1. Caché local de alias (instantáneo y sin conexión)
        dao.alias(TextNormalizer.aliasKey(text))?.let { a ->
            val item = base.copy(groupId = a.groupId, canonicalId = a.canonicalId,
                                 resolvedName = a.name, status = ItemStatus.RESOLVED)
            dao.upsertItem(item); return Result.Resolved(item)
        }

        // 2. Backend
        val resp = try { api.resolve(ResolveRequest(text, mode)) } catch (e: IOException) {
            val item = base.copy(status = ItemStatus.OFFLINE); dao.upsertItem(item)
            return Result.Offline(item)      // WorkManager lo resolverá al volver la red
        }

        return when (resp.status) {
            ResolveStatus.RESOLVED -> {
                val c = resp.candidates.first()
                val item = base.copy(groupId = c.groupId, canonicalId = c.canonicalId,
                                     resolvedName = c.name, status = ItemStatus.RESOLVED)
                dao.upsertItem(item); Result.Resolved(item)
            }
            ResolveStatus.CHOOSE -> base.copy(status = ItemStatus.NEEDS_CHOICE)
                .also { dao.upsertItem(it) }.let { Result.Choose(it, resp.candidates) }
            ResolveStatus.REFINE -> base.copy(status = ItemStatus.NEEDS_REFINE)
                .also { dao.upsertItem(it) }.let { Result.Refine(it, resp.candidates) }
        }
    }

    /** El usuario elige una opción: se guarda en la lista, en la caché local y se envía como alias. */
    suspend fun choose(item: ShoppingItemEntity, c: CandidateDto) {
        dao.upsertItem(item.copy(groupId = c.groupId, canonicalId = c.canonicalId,
                                 resolvedName = c.name, status = ItemStatus.RESOLVED))
        dao.upsertAlias(LocalAliasEntity(TextNormalizer.aliasKey(item.rawText), c.groupId, c.canonicalId,
                                         c.name, System.currentTimeMillis()))
        runCatching { api.alias(AliasRequest(item.rawText, c.groupId, c.canonicalId)) }
            .onFailure { /* encolar en WorkManager */ }
    }
}
```

UX de cada estado:

| Estado | Qué ve el usuario |
|---|---|
| `RESOLVED` | La línea muestra su texto y debajo, en gris, *"→ Leche semidesnatada 1 L"*. Al tocarla puede cambiarlo. |
| `NEEDS_CHOICE` | *Bottom sheet* con 2-3 opciones (nombre, imagen y "genérico" o marca) y "Ninguna de estas". |
| `NEEDS_REFINE` | "No hemos encontrado *X*. ¿Puedes concretar?" con sugerencias y un campo para reescribir. |
| `OFFLINE` | Icono de nube. Se resolverá al recuperar la conexión. |

Interruptor por línea **"Cualquier marca / Esta marca"** (`ItemMode`). Al cambiarlo se vuelve a resolver.

### 7.7 Escáner de código de barras

```kotlin
class BarcodeScanner @Inject constructor(@ApplicationContext private val ctx: Context) {
    suspend fun scanEan(): String? {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A)
            .enableAutoZoom()
            .build()
        return runCatching { GmsBarcodeScanning.getClient(ctx, options).startScan().await().rawValue }
            .getOrNull()
    }
}
```

Tras escanear: `GET /v1/products/by-ean/{ean}`. Si existe, la línea se añade en modo `BRAND` (es un producto concreto) y se ofrece pasarla a "cualquier marca". Si no existe, se ofrece "Añadir precio" (§6.10).

### 7.8 Pantalla de comparación

- Llama a `/v1/compare` con las cadenas seleccionadas y los ítems en estado `RESOLVED`.
- **Por cada producto:** la cadena más barata destacada, el precio del envase y el precio unitario ("1,15 € · 1,15 €/L"), y las demás cadenas ordenadas. Los precios antiguos se marcan con un aviso: "precio del 12/11".
- **Resumen:**
  - total por cadena, con *"faltan 2 productos"* si no los tiene;
  - **total repartido** (cada cosa en su cadena más barata) y el ahorro frente a la mejor cadena única.
- Etiqueta de origen: "precio online" o "aportado por usuarios".
- Acción por oferta: **"Esto no es el mismo producto"** → `/v1/reports/wrong-match`.
- Pie: atribución de OpenStreetMap y Open Food Facts.

### 7.9 Sincronización en segundo plano (WorkManager)

- `ResolvePendingWorker`: resuelve ítems `OFFLINE` al volver la red (`NetworkType.CONNECTED`).
- `SyncAliasesWorker`: envía aliases y reportes encolados.
- Ambos con `BackoffPolicy.EXPONENTIAL` y `setRequiredNetworkType(CONNECTED)`.

---

## 8. Seguridad, privacidad y Google Play

- **Permisos mínimos:** `INTERNET` y `ACCESS_COARSE_LOCATION`. Ni ubicación precisa, ni en segundo plano, ni cámara.
- **Datos que salen del dispositivo:** nombre de ciudad, textos de búsqueda, elecciones (alias) y reportes. **Ni coordenadas ni identificadores personales**: la `X-Api-Key` es aleatoria por instalación.
- **Política de privacidad** publicada y enlazada en la app y en Play Console, y declaración de **Seguridad de los datos** coherente con lo anterior.
- **Backend:** HTTPS obligatorio, rate limit, registros sin el texto completo de búsqueda asociado a la clave más de 30 días y copias de seguridad de Postgres.
- **Secretos** (claves de LLM, BD) como variables de entorno, nunca en el repositorio.

---

## 9. Pruebas y calidad

### 9.1 Tests unitarios (`:shared`)

Casos mínimos de `TextNormalizer` y `QuantityParser`:

| Entrada | `normalize` | `QuantityParser` |
|---|---|---|
| `"Leche Semidesnatada 1L"` | `leche semidesnatada 1 l` | volumen 1000 ml |
| `"Yogur griego 4x125g"` | `yogur griego 4 x 125 g` | masa 500 g, 4 envases |
| `"Coca-Cola Zero lata 33cl"` | `coca cola zero lata 33 cl` | volumen 330 ml |
| `"Aceite de oliva 1,5 litros"` | `aceite oliva 1.5 l` | volumen 1500 ml |
| `"Papel higiénico 12 rollos"` | `papel higienico 12 rollos` | conteo 12 |
| `"Huevos L media docena"` | `huevos l media docena` | conteo 6 * |
| `"Pan SIN gluten"` | `pan sin gluten` (conserva "sin") | — |

\* Cuidado con `"huevos l"`: la talla L no debe interpretarse como litros. El parser solo acepta la unidad si va precedida de número, que es lo que hacen las expresiones regulares de §6.2. Añade este caso al test.

Tests de `AttributeExtractor.conflicts` (entera frente a desnatada → `true`; sin gluten frente a sin lactosa → `false`).

### 9.2 Golden set de búsqueda

Fichero `docs/golden-set.jsonl`, con una línea por caso (empieza con 300 o más, ampliables con los reportes reales):

```json
{"query": "papel water", "mode": "GENERIC", "expected_group": "papel higienico"}
{"query": "cocacola zero", "mode": "BRAND", "expected_canonical": "coca cola zero lata 33 cl"}
{"query": "leche semi", "mode": "GENERIC", "expected_group": "leche semidesnatada 1 l"}
{"query": "yogures griegos", "mode": "GENERIC", "expected_group": "yogur griego natural"}
{"query": "leche desnatada", "mode": "GENERIC", "must_not": ["leche entera 1 l"]}
```

Tarea Gradle `./gradlew :backend:evalMatching` que levanta Postgres con Testcontainers, carga los fixtures, ejecuta cada consulta y muestra:

| Métrica | Umbral para aprobar |
|---|---|
| Acierto top-1 | ≥ 85 % |
| Acierto top-3 | ≥ 95 % |
| Violaciones `must_not` en top-1 (falsas equivalencias) | < 2 % |
| % `RESOLVED` incorrectos | < 1 % |

**Este eval es el que manda:** cualquier cambio en sinónimos, pesos o umbrales debe mejorar o mantener estas cifras. Úsalo también para calibrar `HIGH`, `MEDIUM` y `MARGIN`.

### 9.3 Integración (backend)

- Testcontainers con `pgvector/pgvector:pg17`, migraciones Flyway y `FixtureConnector`.
- Comprobar la ingesta (nuevos, actualizados, precios duplicados no insertados, anomalías a revisión), el emparejamiento por EAN y la comparación con precios frescos frente a antiguos.
- `ktor-server-test-host` para los endpoints.

### 9.4 App

- ViewModels con `kotlinx-coroutines-test` y Turbine, con la API simulada mediante MockK.
- Room con base de datos en memoria.
- Compose UI tests: añadir producto → *bottom sheet* de elección → resolución; denegar ubicación → selección manual.

---

## 10. Orden de implementación (checklist)

1. [ ] Crear el proyecto multi-módulo (`:shared`, `:backend`, `:app`), el catálogo de versiones y el `docker-compose`.
2. [ ] `:shared`: `TextNormalizer`, `QuantityParser`, `AttributeExtractor` y DTOs **con sus tests en verde**.
3. [ ] Backend: Ktor básico, `/health`, conexión a la BD y migración `V1`.
4. [ ] Exportar `multilingual-e5-small` a ONNX e implementar `E5Embedder` con un test de similitud ("refresco de cola" más cerca de "coca cola" que de "lejía").
5. [ ] `FixtureConnector` con CSV de unos 300 productos de 3 cadenas ficticias, más `IngestionPipeline`.
6. [ ] `HybridSearchRepository`, `Reranker` y `ResolveService` con `/v1/resolve` y `/v1/aliases`.
7. [ ] Golden set y `evalMatching`, calibrando hasta cumplir los umbrales de §9.2.
8. [ ] `CompareService` y `/v1/compare`.
9. [ ] `OverpassClient` y `/v1/stores` con caché, más `/v1/cities`.
10. [ ] App: Hilt, Retrofit, Room, DataStore y navegación.
11. [ ] App: pantalla de ciudad (permiso, Geocoder y selección manual) y de tiendas (elegir cadenas).
12. [ ] App: lista de la compra con `AddItemUseCase`, estados y *bottom sheet* de elección.
13. [ ] App: escáner de código de barras y `/v1/products/by-ean`.
14. [ ] App: pantalla de comparación y reportes.
15. [ ] WorkManager para el modo sin conexión.
16. [ ] Reportes de precio de usuarios con validación (§6.10).
17. [ ] Privacidad, atribuciones y textos legales.
18. [ ] **(Tras la validación legal)** conectores reales, uno por cadena, con las salvaguardas de §6.8.

---

## 11. Consideraciones legales y de licencias

- **Conectores de cadenas:** las condiciones de uso de las webs y el derecho *sui generis* sobre bases de datos (Ley 5/1998) pueden limitar la extracción. Por eso quedan **desactivados por defecto** y se activan solo tras revisión jurídica o con acuerdo con la cadena. Ver `estrategia-precios-supermercados.md`, §8.
- **OpenStreetMap (Overpass):** ODbL. Atribución "© colaboradores de OpenStreetMap" visible y uso razonable del servidor público (caché). Si el volumen crece, usar una instancia propia de Overpass.
- **Open Food Facts / Open Prices:** datos ODbL e imágenes CC-BY-SA. Atribución en la app y `User-Agent` identificable.
- **Modelo e5:** licencia MIT.
- **Dependencias:** ver §0.1 (Apache-2.0, MIT, BSD o EPL; anotar cada una en `DECISIONS.md`).
- **Marcas y logotipos de las cadenas:** uso descriptivo del nombre. Revisar el uso de logotipos antes de publicar.

---

## 12. Criterios de aceptación de la funcionalidad

- [ ] Con la ubicación concedida, la ciudad se detecta en menos de 10 s. Si se deniega, se puede elegir manualmente y la app sigue funcionando.
- [ ] Aparecen las tiendas de la ciudad agrupadas por cadena, con atribución a OSM.
- [ ] "papel water", "cocacola zero", "leche semi" y "yogures griegos" se resuelven correctamente o, como mucho, con una elección entre 3 opciones.
- [ ] "leche desnatada" **nunca** se empareja con leche entera.
- [ ] Una elección del usuario hace que la misma consulta se resuelva sola la próxima vez (en su dispositivo enseguida, y para todos con 2 votos).
- [ ] La comparación muestra por producto la cadena más barata, el precio unitario, la fecha y el origen, además del total por cadena y el total repartido.
- [ ] Los productos sin equivalente aparecen como "no disponible en X", nunca sustituidos en silencio.
- [ ] Sin conexión se pueden añadir productos, que se resuelven al volver la red.
- [ ] `evalMatching` cumple los umbrales de §9.2.
- [ ] Permisos de la app: solo `INTERNET` y `ACCESS_COARSE_LOCATION`.
