# OURMEAL | INFORMACION TECNICA

> especificaciones del entorno de desarrollo, las dependencias y la arquitectura de la aplicación android ourmeal, verificadas en el código del proyecto `android-app/`.

## PILA TECNOLOGICA

- **lenguaje**: kotlin `1.9.0` en todo el código fuente, con corrutinas para leer y escribir datos fuera del hilo principal.
- **plataforma**: android nativo.
- **construcción**: gradle `8.7` con kotlin dsl (ficheros `.gradle.kts`), android gradle plugin `8.6.0` y catálogo de versiones en `gradle/libs.versions.toml`.

## CONFIGURACION DE COMPILACION

configuración del módulo en `android-app/app/build.gradle.kts`:

| parámetro | valor | implicación |
|---|---|---|
| `namespace` y `applicationId` | `app.ourmeat` | identificador de la aplicación instalada; el código kotlin vive en el paquete `ourmeal.app` |
| `minSdk` | 34 | requiere android 14 o superior |
| `compileSdk` y `targetSdk` | 35 | compila y se prueba contra android 15 |
| `sourceCompatibility`, `targetCompatibility` y `jvmTarget` | java 17 | bytecode de java 17 |
| `viewBinding` | activado | acceso tipado a las vistas xml |
| `buildConfig` | activado | `BuildConfig.PRICE_API_URL`: en depuración `http://10.0.2.2:8080/` y en publicación vacío; se cambia con `price.api.url` en `local.properties` o `-PpriceApiUrl` |
| `isMinifyEnabled` en `release` | `false` | la versión de publicación no se ofusca ni se reduce |

## DEPENDENCIAS

dependencias declaradas en `android-app/app/build.gradle.kts`:

| dependencia | uso |
|---|---|
| `androidx.core:core-ktx` | extensiones de kotlin para android |
| `androidx.appcompat:appcompat` | compatibilidad de actividades y modo oscuro forzado |
| `com.google.android.material:material` | componentes material 3: botones, tarjetas, campos, diálogos, snackbars y calendario |
| `androidx.activity:activity` | modo de pantalla completa (`enableEdgeToEdge`) |
| `androidx.lifecycle:lifecycle-runtime-ktx` | `lifecycleScope` para las corrutinas de cada pantalla |
| `com.google.code.gson:gson` | serialización json de los datos guardados y de las respuestas del servicio de precios |
| `com.squareup.retrofit2:retrofit` y `converter-gson` (2.11.0, apache-2.0) | cliente del servicio de precios; se usa la versión 2 porque la 3 exige kotlin 2 |
| `com.squareup.okhttp3:okhttp` (4.12.0, apache-2.0) | conexiones http con tiempos límite; la versión 5 exige kotlin 2 |
| `com.squareup.okhttp3:mockwebserver` (4.12.0, apache-2.0) | pruebas unitarias del cliente sin red real |
| `junit:junit` | pruebas unitarias |
| `androidx.test.ext:junit` y `androidx.test.espresso:espresso-core` | pruebas instrumentadas |

`gradle/libs.versions.toml` también define `androidx-constraintlayout`, `androidx-material3-android` y `androidx-material3-desktop`, pero el módulo no los usa.

## ARQUITECTURA Y PAQUETES

el código de `app/src/main/java/ourmeal/app/` sigue una separación sencilla de tipo modelo-vista-controlador:

- **`model/`**: entidades y lógica pura, sin dependencias de android. `UserList`, `ListItem` y `AppData` para las listas; `WeeklyMeals` y `Meal` para el menú; `WeekUtils` para las semanas iso; `normalizeName()` para limpiar nombres y `matchKey()` para compararlos sin mayúsculas ni acentos; `DishHistory`, `MealSlot` y `SuggestionSettings` para las sugerencias de platos y el aviso de repeticiones; `MenuDish`, `DishMemory`, `ShoppingDraft` y `ShoppingMerge` para preparar la compra desde el menú; `ShoppingRoute` para aprender el recorrido de compra y ordenar los productos según él; `PriceComparison`, `PriceFormat`, `PriceSettings`, `ProductAliases`, `PriceCache`, `ItemPriceState` y `PriceApiModels` para el comparador de precios.
- **`controller/`**: persistencia y adaptadores. `DataManager` guarda las listas, `MealDataManager` guarda el menú, `DishSuggestionManager` guarda los platos que no se deben sugerir, `DishMemoryManager` guarda los ingredientes recordados de cada plato, `ShoppingRouteManager` guarda el recorrido de compra aprendido, `PriceSettingsManager`, `ProductAliasManager` y `PriceCacheManager` guardan el comparador de precios sobre el almacén común `JsonFileStore`, `PriceService` habla con el servicio de precios, `CityLocator` obtiene el nombre de la ciudad, e `ItemAdapter` y `ListAdapter` pintan las listas con `DiffUtil` e identificadores estables.
- **pantallas**:
  - `MainActivity`: menú semanal, punto de entrada de la aplicación. `MealSuggestions` pinta la fila de sugerencias y el aviso de repetición de cada franja.
  - `MenuShoppingActivity`: preparación de la compra a partir de los platos de una semana.
  - `ListActivity`: listas de la compra.
  - `ListDetailActivity`: productos de una lista, ordenados según el recorrido de compra cuando el usuario lo sigue. `ListPrices` calcula la línea de precio de cada producto pendiente.
  - `SettingsActivity`: ajustes; hoy, el comparador de precios con la ciudad y los supermercados.
- **utilidades de interfaz compartidas**: `OurmealDialogs.kt` (diálogos y formularios de creación), `ScreenUi.kt` (estados de carga, vacío y error), `UiFeedback.kt` (snackbars), `UiMotion.kt` (transiciones que respetan la configuración del sistema) y `ScreenInsets.kt` (márgenes de barras de sistema y teclado). el comparador añade `PriceText.kt` (textos de la línea de precio), `PriceDialogs.kt` (detalle y elección de producto), `CitySearch.kt` (búsqueda de ciudad) y `PriceFeature.kt` (dirección del servicio).

## INTERFAZ

- vistas xml clásicas, sin jetpack compose.
- view binding en todas las pantallas, sin `findViewById()` salvo en los `ViewHolder` de los adaptadores.
- tema único oscuro (`Theme.Ourmeal`, basado en `Theme.Material3.Dark.NoActionBar`), forzado en `OurmealApplication` con independencia del tema del teléfono.
- colores en `res/values/colors.xml`, estilos en `res/values/themes.xml` y textos, plurales y descripciones accesibles en `res/values/strings.xml`.
- tipografías variables en `res/font/`: `bricolage_grotesque_display` para titulares, `bricolage_grotesque` para títulos y `source_sans_3` para la interfaz. cada familia xml fija los ejes de peso y tamaño óptico de su fichero `*_variable.ttf`, y los tamaños de la escala viven en `res/values/dimens.xml`, con variantes en `res/values-land/`.
- variantes horizontales de todas las pantallas en `res/layout-land/`.

## PERSISTENCIA

- los datos se guardan como json en el almacenamiento privado de la aplicación (`filesDir`): `user_lists.json` para las listas, `meal_plans.json` para el menú, `dish_suggestions.json` para los platos ocultos de las sugerencias, `dish_ingredients.json` para los ingredientes recordados de cada plato `shopping_route.json` para el recorrido de compra aprendido, `price_settings.json` para los ajustes del comparador, `product_aliases.json` para lo que significa cada nombre de producto y `price_cache.json` para los últimos precios recibidos. un fichero de ingredientes, de recorrido, de ajustes o de alias dañado nunca se sobrescribe; la caché de precios no guarda datos del usuario y se rehace si se daña.
- cada fichero se escribe con `AtomicFile`, de modo que una interrupción deja la versión anterior o la nueva, nunca un fichero a medias.
- cada gestor serializa sus operaciones con un bloqueo de proceso para que los ciclos de lectura, cambio y escritura sean atómicos entre pantallas.
- las lecturas y escrituras se hacen en `Dispatchers.IO`, también el guardado automático del menú.
- la copia de seguridad automática de android está activa (`android:allowBackup="true"`) con las reglas por defecto, que incluyen los ficheros del comparador.

## COMPARADOR DE PRECIOS

funcionalidad opcional, desactivada por defecto, especificada en `.docs/new-features/guia-desarrollo-comparador-precios.md`. esta versión conecta el cliente android con el servicio ktor de `backend/`, el catálogo postgresql y el lector de ingesta jvm `price-reader/`. la [guía del servicio](../../backend/readme.md) describe la configuración de fuentes y la ejecución local.

el [lector de precios](../../price-reader/readme.md) descubre supermercados dentro del límite administrativo de la ciudad seleccionada con nominatim y overpass, agrupa sus cadenas y utiliza las webs indicadas en openstreetmap o en reglas de navegación. no depende de tres cadenas prefijadas. abre chromium o chrome real con javascript y perfiles separados por ciudad, cadena y dominio; admite elegir ciudad o tienda en una sesión visible antes de reutilizar sus cookies. extrae precios verificables de productos json-ld, calcula el precio por unidad y conserva fecha y dirección de origen. no garantiza cobertura de todas las webs ni sustituye la resolución de equivalencias del backend.

- **permisos**: `INTERNET` para el servicio de precios y `ACCESS_COARSE_LOCATION`, pedido solo al tocar `Usar mi ubicación` y tras explicar su uso. la posición se usa en el teléfono con `LocationManager` y `Geocoder`, sin servicios de google, y nunca se guarda ni se envía.
- **red**: `network_security_config.xml` prohíbe http en general; solo la variante `debug` lo permite hacia `10.0.2.2`, `localhost` y `127.0.0.1`.
- **comparación**: el precio unitario se recalcula a partir del precio y el tamaño del envase (€/kg, €/L o €/ud). nunca se comparan kilos con litros, se prefieren los precios de menos de siete días y uno antiguo solo se recomienda si no hay ninguno reciente. cada precio lleva fecha y origen.
- **resolución**: cada nombre de producto se resuelve una vez (`/v1/resolve`). si el servicio duda, el usuario elige entre sus opciones y la elección se recuerda y se envía (`/v1/aliases`). los precios de todos los productos de la lista se piden en una sola llamada (`/v1/compare`, como máximo 40 productos) y se guardan seis horas.
- **fallos**: sin conexión se muestran los precios guardados; tras un fallo no se vuelve a llamar al servicio en 30 segundos salvo que el usuario toque `Reintentar`. una respuesta ilegible cuenta como servicio no disponible, no como falta de conexión.

los modelos de precios y `TextNormalizer.kt` viven en `shared/src/main/kotlin/ourmeal/app/model/`, conservando sus paquetes y el formato de los ficheros del teléfono. `ProductText` comparte las reglas de cantidades, sinónimos y restricciones entre resolución e ingesta. la búsqueda del catálogo combina texto completo y trigramas; las coincidencias dudosas requieren elección. los alias globales necesitan dos instalaciones distintas y las observaciones anómalas no se recomiendan.

android consulta el progreso de la ingesta cada 15 segundos, hasta tres minutos, sin bloquear la edición de la lista. salir de la pantalla cancela las consultas. la búsqueda manual de ciudades se envía al pulsar `Buscar`, sin autocompletado sobre nominatim público.

### CONTRATO DEL SERVICIO

la aplicación consume el contrato del apartado 6.12 de la guía, con cabecera `X-Api-Key` (aleatoria por instalación) y estos detalles que el servicio debe respetar:

| método y ruta | la aplicación envía | la aplicación espera |
|---|---|---|
| `GET v1/cities?query=` | texto escrito, desde dos letras | `[{id, name, province}]` |
| `GET v1/stores?city=&province=` | nombre de la ciudad y provincia | `{cityId, city, province, stores: [{id, chainId, chainName, name}], attribution}` |
| `POST v1/resolve` | `{query, mode, cityId, chainIds}` | `{status: RESOLVED, CHOOSE, REFINE o PROCESSING, candidates: [{groupId, canonicalId, name, brand, score}]}` |
| `POST v1/aliases` | `{query, groupId, canonicalId}` | cualquier respuesta `2xx` |
| `POST v1/compare` | `{cityId, chainIds, storeIds, items: [{id, mode, groupId, canonicalId, quantity}]}` | `{items: [{itemId, offers: [oferta]}], pending}` |

cada oferta lleva `chainId`, `name`, `price`, `unitPrice`, `unitKind` (`mass`, `volume` o `count`), `packTotal` (en g, ml o ud), `onOffer`, `observedAt` (iso 8601) y `source` (`online`, `open_prices` o `user`). `unitKind` y `packTotal` amplían el `Offer` de la guía para poder decir €/kg o €/L; una oferta sin precio válido, sin `unitKind`, sin fecha o con un origen desconocido se descarta.

## PRUEBAS

- **unitarias** (`app/src/test/java/ourmeal/app/`): comparador de precios (`PriceComparisonTest`, `PriceModelsTest` y `PriceServiceTest`, este último con `MockWebServer`); progreso de listas, días de la semana, normalización de nombres, cálculo de semanas iso y, para preparar la compra, agrupación de platos, memoria de ingredientes, borrador de la pantalla y fusión de productos sin duplicados; para el recorrido de compra, aprendizaje por viajes, descarte de marcados rápidos en casa, orden de los productos y saneado del fichero (`ShoppingRouteTest`).
- **instrumentadas** (`app/src/androidTest/java/ourmeal/app/`): contexto de la aplicación, formularios de creación, tema oscuro el recorrido completo de preparar la compra (`MenuShoppingTest`) y el orden de una lista según el recorrido aprendido (`ShoppingRouteOrderTest`). las pruebas que abren pantallas con datos (`CreationFormTest`, `MenuShoppingTest`, `ShoppingRouteOrderTest`) usan la regla `AppDataSandbox` para no leer ni sobrescribir los datos reales del dispositivo. `PriceComparisonListTest` comprueba la línea de precio de la lista a partir de alias y precios guardados, sin llamar al servicio.

las pruebas de `backend/` verifican persistencia, aislamiento por ciudad y cadena, restricciones de marca y formato, cuarentena de anomalías y el contrato http. `PriceComparisonEndToEndTest` recorre ajustes, selección de ciudad, ingesta de datos ficticios, api, postgresql y pantalla, incluido el giro, letra ampliada y desactivación. utiliza un emulador desechable y la api de `:backend:serveTestFixtures`.
