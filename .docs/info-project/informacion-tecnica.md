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
| `com.google.code.gson:gson` | serialización json de los datos guardados |
| `junit:junit` | pruebas unitarias |
| `androidx.test.ext:junit` y `androidx.test.espresso:espresso-core` | pruebas instrumentadas |

`gradle/libs.versions.toml` también define `androidx-constraintlayout`, `androidx-material3-android` y `androidx-material3-desktop`, pero el módulo no los usa.

## ARQUITECTURA Y PAQUETES

el código de `app/src/main/java/ourmeal/app/` sigue una separación sencilla de tipo modelo-vista-controlador:

- **`model/`**: entidades y lógica pura, sin dependencias de android. `UserList`, `ListItem` y `AppData` para las listas; `WeeklyMeals` y `Meal` para el menú; `WeekUtils` para las semanas iso; `normalizeName()` para limpiar nombres y `matchKey()` para compararlos sin mayúsculas ni acentos; `DishHistory`, `MealSlot` y `SuggestionSettings` para las sugerencias de platos y el aviso de repeticiones; `MenuDish`, `DishMemory`, `ShoppingDraft` y `ShoppingMerge` para preparar la compra desde el menú; `ShoppingRoute` para aprender el recorrido de compra y ordenar los productos según él.
- **`controller/`**: persistencia y adaptadores. `DataManager` guarda las listas, `MealDataManager` guarda el menú, `DishSuggestionManager` guarda los platos que no se deben sugerir, `DishMemoryManager` guarda los ingredientes recordados de cada plato, `ShoppingRouteManager` guarda el recorrido de compra aprendido, e `ItemAdapter` y `ListAdapter` pintan las listas con `DiffUtil` e identificadores estables.
- **pantallas**:
  - `MainActivity`: menú semanal, punto de entrada de la aplicación. `MealSuggestions` pinta la fila de sugerencias y el aviso de repetición de cada franja.
  - `MenuShoppingActivity`: preparación de la compra a partir de los platos de una semana.
  - `ListActivity`: listas de la compra.
  - `ListDetailActivity`: productos de una lista, ordenados según el recorrido de compra cuando el usuario lo sigue.
- **utilidades de interfaz compartidas**: `OurmealDialogs.kt` (diálogos y formularios de creación), `ScreenUi.kt` (estados de carga, vacío y error), `UiFeedback.kt` (snackbars), `UiMotion.kt` (transiciones que respetan la configuración del sistema) y `ScreenInsets.kt` (márgenes de barras de sistema y teclado).

## INTERFAZ

- vistas xml clásicas, sin jetpack compose.
- view binding en todas las pantallas, sin `findViewById()` salvo en los `ViewHolder` de los adaptadores.
- tema único oscuro (`Theme.Ourmeal`, basado en `Theme.Material3.Dark.NoActionBar`), forzado en `OurmealApplication` con independencia del tema del teléfono.
- colores en `res/values/colors.xml`, estilos en `res/values/themes.xml` y textos, plurales y descripciones accesibles en `res/values/strings.xml`.
- tipografías `bricolage_grotesque` para títulos y `source_sans_3` para la interfaz, en `res/font/`.
- variantes horizontales de todas las pantallas en `res/layout-land/`.

## PERSISTENCIA

- los datos se guardan como json en el almacenamiento privado de la aplicación (`filesDir`): `user_lists.json` para las listas, `meal_plans.json` para el menú, `dish_suggestions.json` para los platos ocultos de las sugerencias, `dish_ingredients.json` para los ingredientes recordados de cada plato y `shopping_route.json` para el recorrido de compra aprendido. un fichero de ingredientes o de recorrido dañado nunca se sobrescribe.
- cada fichero se escribe con `AtomicFile`, de modo que una interrupción deja la versión anterior o la nueva, nunca un fichero a medias.
- cada gestor serializa sus operaciones con un bloqueo de proceso para que los ciclos de lectura, cambio y escritura sean atómicos entre pantallas.
- las lecturas y escrituras se hacen en `Dispatchers.IO`, también el guardado automático del menú.
- la copia de seguridad automática de android está activa (`android:allowBackup="true"`) con las reglas por defecto.

## PRUEBAS

- **unitarias** (`app/src/test/java/ourmeal/app/`): progreso de listas, días de la semana, normalización de nombres, cálculo de semanas iso y, para preparar la compra, agrupación de platos, memoria de ingredientes, borrador de la pantalla y fusión de productos sin duplicados; para el recorrido de compra, aprendizaje por viajes, descarte de marcados rápidos en casa, orden de los productos y saneado del fichero (`ShoppingRouteTest`).
- **instrumentadas** (`app/src/androidTest/java/ourmeal/app/`): contexto de la aplicación, formularios de creación, tema oscuro el recorrido completo de preparar la compra (`MenuShoppingTest`) y el orden de una lista según el recorrido aprendido (`ShoppingRouteOrderTest`). las pruebas que abren pantallas con datos (`CreationFormTest`, `MenuShoppingTest`, `ShoppingRouteOrderTest`) usan la regla `AppDataSandbox` para no leer ni sobrescribir los datos reales del dispositivo.
