# EJECUTAR EL COMPARADOR DE PRECIOS

este servicio conecta la aplicación android con el [lector de supermercados](../price-reader/readme.md) y un catálogo persistente en postgresql. resuelve los nombres de la lista y devuelve precios de las cadenas presentes en la ciudad seleccionada. conserva la interfaz xml y los ficheros locales del teléfono.

## PREPARAR EL ENTORNO LOCAL

necesitas java 17 o posterior, postgresql 17 con `pg_trgm` y chromium instalado por playwright. desde la raíz del repositorio, define una contraseña local fuera de git y prepara la base de datos:

```bash
export OURMEAL_DB_PASSWORD='tu_clave_local_de_pruebas'
docker compose -f backend/compose.yaml up -d db
export OURMEAL_DB_URL='jdbc:postgresql://127.0.0.1:5433/ourmeal_prices'
export OURMEAL_DB_USER='ourmeal_prices'
```

el valor de ejemplo no es una credencial publicada. utiliza secretos gestionados en un despliegue real. si ya tienes postgresql, configura estas variables para una base de datos dedicada; no ejecutes las migraciones contra una base ajena al comparador.

desde `android-app/`, instala el navegador y prepara el esquema mediante un comando explícito:

```bash
./gradlew :price-reader:installBrowser :backend:installDist
./gradlew :backend:run --args=migrate
./gradlew :backend:run --args=serve
```

`serve` no aplica migraciones. escucha en `127.0.0.1:8080` por defecto y necesita el esquema creado por `migrate`. `GET /health` comprueba las tablas y muestra el estado de la cola y de las fuentes, sin nombres de productos ni claves de instalación.

## CONECTAR ANDROID

la compilación de depuración ya utiliza `http://10.0.2.2:8080/`, que comunica el emulador con el equipo anfitrión. en ajustes, activa el comparador, busca una ciudad y selecciona las cadenas. bajo cada producto pendiente aparece el supermercado con mejor precio por kilo, litro o unidad.

para otra dirección, compila desde `android-app/` con la propiedad existente:

```bash
./gradlew :app:assembleDebug -PpriceApiUrl=https://tu_servicio.example/
```

sustituye la dirección de ejemplo por la del servicio. una compilación de distribución exige https y mantiene el comparador no disponible si no se configura una dirección. publicar, configurar certificados y poner el servicio en explotación son operaciones separadas de esta implementación.

## ACTIVAR LAS FUENTES DE PRECIOS

las fuentes web están desactivadas por defecto. tras preparar las webs y sus perfiles de ciudad o tienda según la [guía del lector](../price-reader/readme.md), configura su archivo de reglas y activa la ingesta:

```bash
export OURMEAL_PRICE_RULES=/ruta/privada/reglas.json
export OURMEAL_BROWSER_ENABLED=true
./gradlew :backend:run --args=serve
```

el servicio comparte con el lector la carpeta de estado `~/.local/state/ourmeal-price-reader/`. puedes cambiarla con `OURMEAL_PRICE_STATE`. las reglas ayudan a localizar la web y la búsqueda; no añaden cadenas a una ciudad donde openstreetmap no las ha detectado.

el catálogo no incluye precios de ejemplo. sin fuentes operativas ni precios ingeridos no se puede recomendar un supermercado. las páginas que no publican productos y ofertas json-ld necesitan un adaptador verificado; un navegador real no garantiza acceso ni cobertura universal. si una web pide intervención, prepara su perfil con `setup` y vuelve a intentar la consulta cuando termine la pausa de la fuente.

## CONTRATO Y COMPORTAMIENTO

la app utiliza estos endpoints:

| método y ruta | resultado |
| --- | --- |
| `GET /v1/cities?query=` | ciudades candidatas; búsqueda enviada expresamente, sin autocompletado |
| `GET /v1/stores?city=&province=` | supermercados del límite administrativo elegido, agrupados por cadena |
| `POST /v1/resolve` | `RESOLVED`, `CHOOSE`, `REFINE` o `PROCESSING` |
| `POST /v1/compare` | ofertas por producto y `pending` mientras se completa la ingesta |
| `POST /v1/aliases` | registra una elección sin duplicar el voto de una instalación |
| `GET /health` | disponibilidad del esquema y estado de la ingesta |

`resolve` recibe `query`, `mode`, `cityId` y `chainIds`. `compare` recibe también `storeIds` y un máximo de 40 productos. el servicio rechaza cadenas o tiendas ajenas a la ciudad seleccionada. la clave aleatoria `X-Api-Key` identifica la instalación para el límite de 60 peticiones por minuto; no representa una cuenta ni acredita la identidad de una persona.

el módulo [compartido](../shared/) conserva los modelos existentes en el paquete `ourmeal.app.model` y añade `ProductText`. la ingesta y la resolución reutilizan normalización, sinónimos, cantidades y restricciones. postgresql recupera candidatos con texto completo y trigramas. una coincidencia exacta y única se resuelve automáticamente; las coincidencias dudosas requieren elección. una marca explícita no se sustituye por otra y un formato concreto conserva su cantidad en modo marca.

los grupos genéricos permiten comparar envases de distinto tamaño mediante precio unitario. los productos canónicos conservan marca y formato; una marca desconocida no se inventa ni se comparte entre cadenas en modo marca. los cambios anómalos de precio unitario quedan en el historial con confianza insuficiente para recomendarlos. cada precio conserva el producto observado, la fecha y la dirección de origen.

las consultas de precios sirven el catálogo guardado. una cola de 64 trabajos, con dos lectores, realiza las consultas web pendientes fuera de las peticiones http. cada perfil de ciudad y cadena se utiliza de forma exclusiva y las navegaciones se separan por dominio. cinco fallos consecutivos pausan la fuente durante 30 minutos; una verificación de acceso también la pausa. las demás cadenas siguen funcionando.

android conserva los precios locales durante seis horas, permite usar la lista sin conexión y muestra los datos guardados cuando falla la actualización. durante la ingesta consulta el progreso cada 15 segundos, hasta tres minutos; después ofrece reintentar. tras un fallo de conexión o del servicio, reintenta cada 30 segundos mientras la lista siga visible. salir de la lista cancela sus consultas y desactivar el comparador cancela también la búsqueda de ciudad o ubicación que estuviera pendiente. la búsqueda inicial de tiendas admite una espera mayor que las consultas de precios.

## VERIFICAR EL RECORRIDO COMPLETO

desde `android-app/`, ejecuta las pruebas de modelos, navegador, api y base de datos:

```bash
./gradlew :shared:test :price-reader:test :price-reader:browserTest
./gradlew :backend:integrationTest
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

`integrationTest` utiliza testcontainers y una base desechable. si docker no está disponible, admite `OURMEAL_TEST_DB_URL`, `OURMEAL_TEST_DB_USER` y `OURMEAL_TEST_DB_PASSWORD` para una instancia local de pruebas. el nombre de base debe empezar por `ourmeal_test` y la dirección debe ser de bucle local. las pruebas rechazan otros destinos antes de truncar sus tablas.

para comprobar la pantalla con un emulador desechable, inicia esta api de pruebas en una terminal:

```bash
./gradlew :backend:serveTestFixtures
```

en otra terminal, ejecuta las pruebas instrumentadas:

```bash
./gradlew :app:connectedDebugAndroidTest
```

`serveTestFixtures` usa datos ficticios que solo existen en `src/test/`; no se incluyen en la distribución del servicio. la prueba `PriceComparisonEndToEndTest` activa el comparador, selecciona sevilla, consulta la api y postgresql y comprueba el resultado en la lista. también verifica el reintento tras fallar la carga de tiendas, la recuperación automática tras un fallo del servicio, el detalle, el giro de pantalla, letra ampliada y desactivación. `AppDataSandbox` aparta y restaura los ficheros del teléfono, aunque se debe utilizar un emulador desechable.

## CONFIGURACION Y LIMITES DE EXPLOTACION

las opciones adicionales son:

| variable | uso |
| --- | --- |
| `OURMEAL_PRICE_HOST` | interfaz de escucha; por defecto, bucle local |
| `OURMEAL_PRICE_PORT` | puerto http; por defecto, `8080` |
| `OURMEAL_PRICE_STATE` | caché de descubrimiento y perfiles privados del navegador |
| `OURMEAL_PRICE_RULES` | reglas de navegación por cadena |
| `OURMEAL_BROWSER_CHANNEL` | canal de navegador instalado; por ejemplo, `chrome` |
| `OURMEAL_NOMINATIM_URL` | instancia de geocodificación |
| `OURMEAL_OVERPASS_URL` | instancia de descubrimiento de tiendas |

el proceso sirve http local para desarrollo o detrás de un proxy con https. limita también el tráfico y el acceso a redes internas desde la infraestructura del navegador. no registres cuerpos de peticiones, claves, cookies ni perfiles. conserva copias de seguridad del catálogo y aplica los avisos de licencia y las obligaciones de cada fuente antes de distribuir datos.

el descubrimiento utiliza por defecto la [instancia pública de overpass-api.de](https://wiki.openstreetmap.org/wiki/Overpass_API#Public_Overpass_API_instances). se mantiene la caché y la separación entre peticiones. configura una instancia propia o contratada para cargas de explotación que excedan el uso de los servidores públicos.

esta integración cubre el flujo de comparación bajo cada producto. no implementa el escáner de códigos, los aportes de usuarios, una cesta total ni el motor de embeddings y su evaluación de 300 consultas de la especificación ampliada. los informes, el modelo semántico y la política de privacidad para publicación necesitan su propio trabajo y validación; no se declaran terminados por pasar las pruebas de este flujo.
