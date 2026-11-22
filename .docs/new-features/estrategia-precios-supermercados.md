# Estrategia para obtener y comparar precios de supermercados

**Proyecto:** app Android (Kotlin) que compara dónde es más barato cada producto de una lista de la compra.
**Objetivo de este documento:** definir la mejor forma de obtener precios fiables y de emparejar lo que escribe el usuario con el producto real de cada supermercado, aunque el texto no coincida.
**Fecha:** 22/11/2026

---

## 1. Resumen ejecutivo

1. **No existe una opción que "siempre funcione".** Ninguna cadena grande ofrece una API pública de precios, y cualquier fuente puede cambiar o caerse. Lo que sí se puede hacer es una arquitectura **tolerante a fallos**: varias fuentes, un catálogo propio y un sistema de búsqueda que no dependa de que el texto coincida.
2. **Recomendación:** un **backend propio** con cuatro capas:
   - **Ingesta multi-fuente** (catálogos de las tiendas online, Open Prices/Open Food Facts y precios aportados por usuarios).
   - **Catálogo canónico**, con un producto "genérico" (p. ej. *leche entera 1 L*) enlazado con las referencias concretas de cada cadena.
   - **Búsqueda híbrida** (texto + trigramas + embeddings multilingües) con reordenación por atributos.
   - **Confirmación del usuario que enseña al sistema** (si dudamos, preguntamos una vez y lo recordamos).
3. **El emparejamiento se resuelve a nivel de "intención de compra", no de texto.** El usuario escribe "papel water" y la app lo convierte en un producto canónico, nunca en una comparación de cadenas de texto.
4. **El mayor riesgo no es técnico, sino legal y de datos.** Hay que decidir con asesoramiento jurídico cómo se obtienen los catálogos (ver sección 8) antes de construir sobre ellos.
5. **Lo que no hay que prometer** al usuario: "precio exacto en tu tienda". Los precios varían por tienda, código postal y online frente a físico. La app debe mostrar *fecha de actualización* y *nivel de confianza*.

---

## 2. Fuentes de precios evaluadas

| Fuente | Cobertura | Fiabilidad | Riesgo legal / operativo | Veredicto |
|---|---|---|---|---|
| **Catálogos de las tiendas online de cada cadena** (extracción automática) | Alta: es lo que usan comparadores existentes como Findit (>150.000 productos de unas 14 cadenas, actualizados a diario) o POY (11 cadenas, semanal) | Media-alta; precio online, que puede diferir del físico | Medio-alto: condiciones de uso, derecho *sui generis* sobre bases de datos, cambios de web que rompen el extractor | **Base de la solución**, con cautelas legales y técnicas |
| **Acuerdo directo / afiliación con cadenas** | Una cadena cada vez | Muy alta | Bajo | Ideal, pero poco realista al principio. Intentarlo en paralelo |
| **Open Prices (Open Food Facts)** | Irregular, depende de la comunidad. Datos bajo licencia ODbL | Media; con prueba (foto del ticket/etiqueta) | Bajo: hay que citar la fuente y respetar la licencia | **Complemento** y vía para validar precios |
| **Precios aportados por los usuarios** (escáner de código de barras + foto del ticket) | Crece con el uso; precio físico real | Variable: requiere validación | Bajo si se tratan bien los datos personales | **Fuente de respaldo** y vía para precios en tienda física |
| **Datos abiertos del Gobierno** | Para alimentación no hay un dataset equivalente al de combustibles; existen solicitudes ciudadanas en datos.gob.es | — | — | No disponible hoy; vigilar |
| **Descargas puntuales de terceros** (p. ej. CSV antiguos en Zenodo) | Una cadena y una fecha | Obsoleta | Variable | Solo para pruebas |

**Conclusión:** la única fuente que da cobertura amplia hoy son los catálogos online. Por eso la solución debe ser híbrida y no depender de una sola.

---

## 3. Arquitectura recomendada

```
┌──────────────┐     HTTPS      ┌───────────────────────────────────────────┐
│ App Android  │ ─────────────▶ │ API propia (backend)                      │
│ (Kotlin)     │ ◀───────────── │  /search  /resolve  /compare  /report     │
└──────────────┘                └───────────────┬───────────────────────────┘
                                                │
                       ┌────────────────────────┼─────────────────────────┐
                       ▼                        ▼                         ▼
              ┌────────────────┐      ┌───────────────────┐     ┌───────────────────┐
              │ PostgreSQL     │      │ Cola de trabajos  │     │ Servicio de       │
              │ + pg_trgm      │◀─────│ (ingesta, match,  │────▶│ embeddings / LLM  │
              │ + pgvector     │      │ reintentos)       │     │ (offline, cacheado)│
              └────────────────┘      └─────────┬─────────┘     └───────────────────┘
                                                │
          ┌──────────────┬──────────────────────┼───────────────┬──────────────┐
          ▼              ▼                      ▼               ▼              ▼
   Conector        Conector               Conector        Open Prices     Aportes de
   Cadena A        Cadena B               Cadena N        / OFF           usuarios
```

Principios:

- **La app nunca extrae datos de las cadenas.** Todo pasa por tu backend, así controlas fallos, límites y cumplimiento.
- **Un conector por cadena, aislado.** Si uno se rompe, el resto sigue funcionando y la app muestra "sin datos recientes" solo para esa cadena.
- **Los precios se guardan con historial** (no se sobrescriben). Permite detectar errores y ofrecer evolución.
- **El emparejamiento se hace offline** (al ingerir), no en cada búsqueda. La búsqueda del usuario solo resuelve la intención y consulta tablas ya preparadas. Resultado: respuesta rápida y barata.

---

## 4. El problema central: el texto del usuario casi nunca coincide

Ejemplos reales de desajuste:

| Lo que escribe el usuario | Cómo se llama en la tienda |
|---|---|
| "papel water" | "Papel higiénico 2 capas 12 rollos" |
| "cocacola zero" | "Refresco de cola Coca-Cola Zero lata 33 cl" |
| "leche" | "Leche entera Hacendado brik 1 L" (¿cuál de las 50?) |
| "yogur griego" | "Yogur tipo griego natural 4x125 g" |
| "bolsas basura" | "Bolsas de basura industriales 30 L" |

Hay **dos problemas distintos** y conviene separarlos:

1. **Resolución de la intención:** ¿qué quiere el usuario? (*genérico* "leche" o *concreto* "Puleva entera 1 L").
2. **Emparejamiento entre cadenas:** ¿cuál es el equivalente en cada supermercado?

### 4.1 Pipeline de emparejamiento (al ingerir cada producto)

1. **Normalización del texto:** minúsculas, quitar tildes, signos y stopwords; unificar unidades (`1l`, `1 litro`, `1000 ml` → `1000 ml`); separar `4x125 g` → *4 uds × 125 g = 500 g*.
2. **Extracción de atributos:** marca, tipo, variante (entera/desnatada, zero, sin gluten), formato y cantidad total, si es marca blanca. Se hace con reglas y, para lo ambiguo, con un LLM en proceso por lotes.
3. **Asignación a categoría y producto canónico:** cada producto de cada cadena se enlaza a un `canonical_product` (p. ej. *Leche entera 1 L, genérica*).
4. **Enlace por código de barras (EAN) cuando existe:** es el único identificador universal. Open Food Facts permite completar el EAN, la marca y los datos nutricionales. Si dos productos comparten EAN, son el mismo sin discusión.
5. **Revisión humana o por usuarios de los casos de baja confianza**, no los publicamos como comparables.

### 4.2 Resolución de la búsqueda del usuario (en tiempo real)

Se usa **recuperación híbrida** y después reordenación:

1. **Normalizar la consulta** igual que los productos, más corrección de erratas y **diccionario de sinónimos/alias** ("papel water" → "papel higiénico", "cocacola" → "coca-cola", "kleenex" → "pañuelos de papel").
2. **Recuperar candidatos con tres señales a la vez:**
   - *Búsqueda de texto completo* en español (stemming, `tsvector`).
   - *Similitud por trigramas* (`pg_trgm`), que tolera erratas y palabras pegadas.
   - *Embeddings multilingües* (`pgvector`), que captura el significado ("refresco de cola" ≈ "coca-cola").
3. **Fusionar rankings** (por ejemplo, *Reciprocal Rank Fusion*) para no depender de una sola señal. Es el mismo enfoque híbrido (léxico + semántico en Postgres con `pgvector`) que describe Instacart para su búsqueda de supermercado.
4. **Reordenar por atributos:** coincidencia de marca, formato, variante y cantidad con lo que pidió el usuario. Penalizar atributos contradictorios (si pide "desnatada", penalizar "entera").
5. **Puntuación de confianza** y tres posibles resultados:
   - **Alta:** se resuelve solo.
   - **Media:** se muestra una elección rápida de 2-3 opciones.
   - **Baja:** se pide al usuario que concrete ("¿te refieres a…?").
6. **Aprender del usuario:** cada elección se guarda como alias (`"papel water" → canonical 1234`). La próxima vez, y para todos los usuarios si es válido, se resuelve directamente.

### 4.3 Genérico frente a específico (clave para que no falle)

El usuario debe poder marcar cada línea de la lista como:

- **Genérico** → "leche entera 1 L": se compara el **mejor precio unitario** entre las variantes equivalentes (marcas blancas incluidas).
- **Marca concreta** → "Puleva entera 1 L": solo se comparan productos equivalentes de esa marca, y si una cadena no lo vende se muestra *"no disponible"* en lugar de sustituirlo en silencio.

Y siempre se comparan **precios por unidad normalizada** (€/L, €/kg, €/ud), no precio de envase.

### 4.4 Uso de LLM: dónde sí y dónde no

| Sí | No |
|---|---|
| Extraer atributos de nombres sucios, en lote al ingerir | Decidir el precio |
| Proponer el producto canónico para casos ambiguos (con revisión) | Llamarlo en cada pulsación del usuario (coste y latencia) |
| Generar sinónimos y alias iniciales | Sustituir un producto sin que el usuario lo vea |

Los resultados del LLM se **cachean** y se guardan con su puntuación de confianza.

---

## 5. Modelo de datos (PostgreSQL)

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS vector;

-- Cadenas y tiendas
CREATE TABLE chain (
  id          SERIAL PRIMARY KEY,
  name        TEXT NOT NULL UNIQUE
);

CREATE TABLE store (
  id          SERIAL PRIMARY KEY,
  chain_id    INT REFERENCES chain(id),
  osm_id      TEXT,                 -- procedencia: OpenStreetMap
  name        TEXT,
  city        TEXT,
  postal_code TEXT,
  lat         DOUBLE PRECISION,
  lon         DOUBLE PRECISION
);

-- Producto canónico (lo que el usuario "quiere comprar")
CREATE TABLE canonical_product (
  id            SERIAL PRIMARY KEY,
  name          TEXT NOT NULL,           -- "Leche entera 1 L"
  category      TEXT,
  brand         TEXT,                    -- NULL si es genérico
  variant       TEXT,
  unit_kind     TEXT NOT NULL,           -- 'l' | 'kg' | 'ud'
  unit_quantity NUMERIC NOT NULL,        -- cantidad base normalizada
  is_generic    BOOLEAN DEFAULT TRUE,
  ean           TEXT,
  embedding     vector(384)              -- dimensión según el modelo elegido
);

-- Producto tal y como aparece en cada cadena
CREATE TABLE chain_product (
  id              SERIAL PRIMARY KEY,
  chain_id        INT REFERENCES chain(id),
  external_id     TEXT NOT NULL,         -- id en la web de la cadena
  raw_name        TEXT NOT NULL,
  normalized_name TEXT NOT NULL,
  brand           TEXT,
  ean             TEXT,
  pack_quantity   NUMERIC,
  unit_kind       TEXT,
  canonical_id    INT REFERENCES canonical_product(id),
  match_score     REAL,                  -- confianza del emparejamiento
  match_status    TEXT DEFAULT 'auto',   -- auto | reviewed | rejected
  embedding       vector(384),
  UNIQUE (chain_id, external_id)
);

-- Precios con historial y procedencia
CREATE TABLE price (
  id               BIGSERIAL PRIMARY KEY,
  chain_product_id INT REFERENCES chain_product(id),
  store_id         INT REFERENCES store(id),   -- NULL = precio general de la cadena
  price            NUMERIC(8,2) NOT NULL,
  unit_price       NUMERIC(8,3),               -- €/l, €/kg o €/ud
  on_offer         BOOLEAN DEFAULT FALSE,
  source           TEXT NOT NULL,              -- 'online' | 'open_prices' | 'user'
  observed_at      TIMESTAMPTZ NOT NULL,
  confidence       REAL DEFAULT 1.0
);

-- Alias aprendidos ("papel water" -> canonical)
CREATE TABLE alias (
  id           SERIAL PRIMARY KEY,
  text_norm    TEXT NOT NULL,
  canonical_id INT REFERENCES canonical_product(id),
  votes        INT DEFAULT 1,
  UNIQUE (text_norm, canonical_id)
);

CREATE INDEX ON chain_product USING gin (normalized_name gin_trgm_ops);
CREATE INDEX ON chain_product USING hnsw (embedding vector_cosine_ops);
CREATE INDEX ON canonical_product USING hnsw (embedding vector_cosine_ops);
CREATE INDEX ON price (chain_product_id, observed_at DESC);
```

### Consulta de ejemplo: mejor precio por cadena de un producto canónico

```sql
SELECT DISTINCT ON (cp.chain_id)
       c.name       AS cadena,
       cp.raw_name  AS producto,
       p.price,
       p.unit_price,
       p.observed_at,
       p.source
FROM chain_product cp
JOIN chain c ON c.id = cp.chain_id
JOIN price p ON p.chain_product_id = cp.id
WHERE cp.canonical_id = :canonical_id
  AND cp.match_status <> 'rejected'
  AND p.observed_at > now() - interval '7 days'
ORDER BY cp.chain_id, p.unit_price ASC;
```

---

## 6. Resolución de búsqueda (pseudocódigo)

```text
resolve(query, user_ctx):
    q = normalize(query)                      # minúsculas, sin tildes, unidades unificadas

    # 1. Atajos exactos
    if alias_exact(q): return alias_result(q)

    # 2. Recuperación híbrida
    lex   = fts_search(q, k=30)
    tri   = trigram_search(q, k=30)
    sem   = vector_search(embed(q), k=30)
    cands = rrf_fuse(lex, tri, sem)           # Reciprocal Rank Fusion

    # 3. Reordenación por atributos
    attrs = parse_attributes(q)               # marca, formato, variante, cantidad
    for c in cands:
        c.score = c.fused_score
                  + w_brand   * match(attrs.brand, c.brand)
                  + w_format  * match(attrs.size, c.size)
                  - w_conflict * contradicts(attrs, c)

    # 4. Decisión según confianza
    top = best(cands)
    if top.score >= HIGH and gap(top, second) >= MARGIN:
        return resolved(top)
    if top.score >= MEDIUM:
        return ask_user(cands[:3])            # elección rápida, se guarda como alias
    return ask_user_refine(q)
```

Los pesos y umbrales (`HIGH`, `MEDIUM`, `MARGIN`) se calibran con un conjunto de pruebas propio (sección 9).

---

## 7. Cobertura, frescura y fallos

- **Frescura visible:** cada precio muestra su fecha. Pasado un umbral (p. ej. 7 días) se marca como *"precio antiguo"* y se excluye del "más barato" salvo que no haya nada más reciente.
- **Detección de anomalías:** descartar o revisar saltos de precio imposibles (p. ej. ×10 o ÷10 respecto al histórico) para evitar errores de extracción.
- **Aislamiento por conector:** *circuit breaker* por cadena; si falla N veces seguidas, se pausa y se avisa, y la app sigue con las demás.
- **Precio por tienda frente a precio de cadena:** si la cadena fija precios por código postal o tienda, se guarda en `store_id`; si no hay dato local, se muestra como *"precio online de la cadena"*.
- **Producto sin equivalente:** se muestra *"no encontrado en X"* en vez de inventar una equivalencia débil.
- **Plan B por cadena:** si el conector oficial se rompe, los precios de usuarios y de Open Prices mantienen una cobertura parcial.

---

## 8. Aspectos legales y de cumplimiento (consultar con un abogado)

Este documento no es asesoramiento jurídico. Puntos a revisar **antes** de construir sobre catálogos de terceros:

- **Condiciones de uso** de cada web: muchas prohíben la extracción automatizada.
- **Derecho *sui generis* sobre bases de datos** (Ley 5/1998, arts. 133 y ss. de la LPI): protege la inversión sustancial en obtener, verificar o presentar el contenido de una base de datos. La jurisprudencia es matizada: en el caso Ryanair/eDreams, el Tribunal Supremo (2014) no apreció infracción porque la empresa no invertía en *obtener* esos datos, sino que los generaba por su propia actividad. Y el caso *CV-Online Latvia/Melons* del TJUE ha redefinido el derecho frente a agregadores, ponderando también el acceso a la información y la competencia. No es una garantía de que la extracción sea legal en cualquier circunstancia.
- **Competencia desleal:** evitar sobrecargar sus servidores, respetar límites razonables de peticiones y no suplantar usuarios.
- **Buenas prácticas técnicas mínimas:** respetar `robots.txt` y límites de frecuencia, identificarse, usar solo datos públicos sin inicio de sesión y no replicar imágenes o descripciones protegidas más allá de lo necesario.
- **Open Food Facts / Open Prices:** datos bajo ODbL (atribución y compartir igual); las imágenes de prueba, bajo CC-BY-SA.
- **Datos personales (RGPD):** ubicación, cuentas y listas son datos personales. Hace falta política de privacidad, base legal, minimización (con la ciudad basta; no guardar coordenadas exactas) y la declaración de *Seguridad de los datos* en Google Play.
- **Marcas y logotipos:** usar el nombre de las cadenas de forma descriptiva y revisar el uso de logotipos.
- **Vía más segura a medio plazo:** acuerdos con cadenas o con proveedores de datos con licencia, y el modelo colaborativo.

---

## 9. Cómo medir que funciona

Crear desde el primer día un **conjunto de pruebas** de 300-500 consultas reales (con erratas, jerga y marcas) y su producto correcto, y medir:

| Métrica | Objetivo inicial |
|---|---|
| **Acierto en el primer resultado** (consultas genéricas) | ≥ 85 % |
| **Acierto en los 3 primeros resultados** | ≥ 95 % |
| **Falsas equivalencias** (productos distintos tratados como iguales) | < 2 % |
| **Cobertura**: % de la lista con precio en ≥ 2 cadenas | ≥ 80 % |
| **Frescura**: % de precios con menos de 7 días | ≥ 90 % |
| **Latencia** de `/resolve` | < 300 ms |

Una **falsa equivalencia es peor que un "no encontrado"**, porque lleva al usuario a decidir con datos erróneos. Hay que optimizar para minimizarlas aunque baje algo la cobertura.

---

## 10. Plan por fases

| Fase | Contenido | Resultado |
|---|---|---|
| **0. Validación (1-2 semanas)** | Decisión legal sobre fuentes. Elegir 1 ciudad y 2-3 cadenas. Conjunto de pruebas de 300 consultas | Viabilidad confirmada |
| **1. Datos (3-4 semanas)** | Conectores de 2-3 cadenas, esquema Postgres, ingesta diaria, historial | Catálogo con precios frescos |
| **2. Matching (3-4 semanas)** | Normalización, extracción de atributos, EAN + Open Food Facts, canónicos, búsqueda híbrida | Métricas de la sección 9 medidas |
| **3. App MVP (3-4 semanas)** | Ubicación, tiendas cercanas (OSM), lista de la compra, comparación por línea, elección genérico/marca | Beta cerrada |
| **4. Comunidad (continuo)** | Escáner de código de barras, aportes de precio, aliases compartidos, revisión de baja confianza | Cobertura y frescura crecientes |
| **5. Escala** | Más ciudades y cadenas, acuerdos con proveedores o cadenas | Reducir dependencia de la extracción |

---

## 11. Riesgos principales

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Reclamación o bloqueo por parte de una cadena | Alto | Asesoramiento legal, límites de frecuencia, acuerdos, fuentes alternativas |
| Cambios en las webs rompen los conectores | Alto | Conector por cadena aislado, alertas, tests de humo diarios |
| Falsas equivalencias | Alto | Umbrales conservadores, revisión de baja confianza, "no encontrado" antes que adivinar |
| Precio online distinto del físico | Medio | Etiquetar origen del precio, aportes de usuarios en tienda |
| Coste de embeddings/LLM | Bajo-Medio | Procesar offline, cachear, modelo pequeño para consultas |
| Escasa comunidad al principio | Medio | No depender del crowdsourcing en el MVP; usarlo como complemento |

---

## 12. Decisiones recomendadas ahora

1. Validar con un abogado el uso de los catálogos online y plantear acuerdos con alguna cadena.
2. Empezar con **una ciudad y 2-3 cadenas**, y medir con un conjunto de pruebas real.
3. Usar **PostgreSQL con `pg_trgm` y `pgvector`** (una sola base de datos para texto y semántica), en lugar de añadir otro motor de búsqueda.
4. Construir el **catálogo canónico y los aliases desde el principio**: es el activo que hace que la búsqueda no dependa del texto exacto.
5. Mostrar siempre **fecha y origen del precio** y preferir "no encontrado" a una equivalencia dudosa.

---

## Fuentes

- Open Prices (Open Food Facts): [documentación y API](https://openfoodfacts.github.io/open-prices/guides/API/), [proyecto y licencia ODbL](https://wiki.openfoodfacts.org/Prices)
- Comparadores existentes en España, como prueba de viabilidad: [Findit](https://finditapp.es/como-funciona), [POY.es (Genbeta)](https://www.genbeta.com/actualidad/esta-web-compara-cuanto-vale-comprar-muchos-supermercados-mercadona-sale-muy-bien-parada), [Precio al Plato](https://github.com/precio-al-plato)
- Solicitud de datos abiertos de precios en supermercados: [datos.gob.es](https://datos.gob.es/ca/solicitud-de-datos/precios-diarios-actualizados-de-los-supermercados)
- Derecho *sui generis* y *scraping*: [Ley 5/1998 (WIPO)](https://www.wipo.int/wipolex/es/text/126689), [Ryanair vs. agencias online (LegalToday)](https://www.legaltoday.com/practica-juridica/derecho-civil/nuevas-tecnologias-civil/ryanair-pierde-su-batalla-legal-frente-a-las-agencias-de-viajes-online-2013-01-14/), [Caso Melons y agregadores (ULL)](https://www.ull.es/revistas/index.php/derecho/article/view/6384)
- Búsqueda híbrida en Postgres: [Instacart](https://company.instacart.com/tech-innovation/how-instacart-built-a-modern-search-infrastructure-on-postgres), [embeddings + pgvector para emparejar productos](https://sixfeetup.com/blog/implement-text-similarity-with-embeddings-in-django)
