# OURMEAL | HISTORIAL DE PLATOS: SUGERENCIAS Y AVISO DE REPETICIONES

> especificación funcional, diseño técnico y guía de implementación de la funcionalidad que aprovecha los menús ya guardados para sugerir platos mientras se escribe y avisar cuando un plato se repite. el documento es autónomo: no requiere leer otros documentos de `.docs/new-features/` para implementarla.

## ESTADO DEL DOCUMENTO

| campo | valor |
|---|---|
| estado | implementada el 2026-10-22; dirección de diseño y decisiones pendientes confirmadas por el mantenedor el 2026-10-22 |
| fecha del análisis | 2026-10-22 |
| ámbito | aplicación android en `android-app/` |
| commit de referencia | `15ed1a8`, último commit de `main` en el momento del análisis |
| funcionalidad relacionada | `.docs/new-features/compra-desde-el-menu.md`, independiente; solo comparte las utilidades de modelo descritas en [piezas compartidas](#piezas-compartidas) |

en este documento se distinguen cuatro tipos de afirmación:

- **existente**: comprobado en el código del commit de referencia.
- **propuesto**: cambio que debe implementarse.
- **decisión tomada**: alternativa elegida con su justificación; no se debe reabrir durante la implementación sin motivo nuevo.
- **pendiente** o **supuesto**: punto que requiere validación del mantenedor o comprobación en dispositivo.

## RESUMEN

cada semana, quien usa ourmeal escribe de nuevo los mismos platos en `etComida` y `etCena`, y la aplicación no recuerda nada de las semanas anteriores aunque las tiene guardadas en `meal_plans.json`. tampoco ayuda a variar el menú, que es una de las promesas del producto.

esta funcionalidad añade dos ayudas al editor del menú, sin pantallas nuevas:

1. **sugerencias de platos**: al enfocar un campo aparece, justo debajo, una tira de chips con los platos habituales; al escribir, la tira se filtra con los platos del historial que coinciden. un toque rellena el campo.
2. **aviso de repetición**: bajo cada campo, una línea discreta indica si ese plato ya está en otro hueco de la semana o si estuvo en el menú en los últimos 13 días.

ambas se basan solo en lo que el propio usuario ha planificado; no se inventan platos.

## PROBLEMA Y OBJETIVOS

### PROBLEMA

- **existente**: `MainActivity` carga solo la semana visible con `MealDataManager.getWeeklyMeals(weekId)`, aunque internamente `loadWeeklyMeals()` lee todas las semanas. el resto del historial se descarta.
- **existente**: `etComida` y `etCena` son `AppCompatEditText` sin autocompletado (`android:importantForAutofill="no"`), con hasta 120 caracteres y edición multilínea.
- **existente**: hasta el 2026-10-22, `claude.md` incluía entre las capacidades de la aplicación «organizar menús para evitar repeticiones», y `.docs/info-project/informacion-general.md` prometía «no repetir los mismos platos por accidente». no hay ningún código que detecte repeticiones; ambos documentos ya se corrigieron y hoy lo indican como mejora pendiente.

### OBJETIVOS

1. reducir la escritura en la tarea más frecuente de la aplicación: rellenar los huecos de la semana.
2. ayudar a variar el menú con información, no con bloqueos.
3. mantener los nombres de los platos coherentes entre semanas, lo que también mejora cualquier agrupación futura por plato.
4. no cambiar el formato de `meal_plans.json` ni el comportamiento del guardado automático.

### NO OBJETIVOS

- no se impide repetir platos ni se muestran errores por repetir: repetir es legítimo.
- no se sugieren platos que el usuario nunca escribió.
- no se generan menús automáticos ni se rellenan semanas enteras.
- no se añade una pantalla de gestión del historial.

## SELECCION Y JUSTIFICACION

se evaluaron estas alternativas a partir del análisis del código y de las promesas documentadas del producto:

| alternativa | valor cotidiano | encaje con el producto | viabilidad con la infraestructura actual | decisión |
|---|---|---|---|---|
| historial de platos con sugerencias y aviso de repeticiones | alto: se usa cada vez que se escribe un plato | cumple «evitar repeticiones», una promesa documentada no implementada | alta: los datos ya existen en `meal_plans.json` | **seleccionada** |
| preparar la compra desde el menú con memoria de ingredientes | alto: tarea semanal | une menú y listas, otra promesa documentada | alta | seleccionada como primera funcionalidad en `.docs/new-features/compra-desde-el-menu.md` |
| copiar la semana anterior o plantillas de semana | medio | útil para rutinas fijas, pero rígido; las sugerencias cubren el caso de forma granular | alta | descartada: menor valor y solapamiento parcial |
| modo compra: comprados al final, desmarcar todo, editar productos | medio-alto | mejoras sueltas de una pantalla | alta | descartada por ahora: no cubre ningún hueco de producto |
| autocompletado con el desplegable de `MaterialAutoCompleteTextView` | alto | mismo objetivo | media: el desplegable flotante se recorta o tapa el campo con el teclado abierto dentro de un `ScrollView`, y no encaja con campos multilínea | descartada como técnica; se usa una tira de chips en línea |
| recomendaciones de platos con datos externos | medio | inventaría contenido ajeno al usuario | baja: no hay red ni datos | descartada |

la funcionalidad seleccionada actúa sobre el gesto más repetido de la aplicación y convierte en útil un historial que hoy solo ocupa espacio. su coste es bajo porque no añade pantallas ni cambia el formato de datos.

## CONTEXTO VERIFICADO DEL PROYECTO

### ARQUITECTURA EXISTENTE

| pieza | ruta | responsabilidad verificada |
|---|---|---|
| menú semanal | `android-app/app/src/main/java/ourmeal/app/MainActivity.kt` | `loadWeek()` lee la semana en `Dispatchers.IO`; `bindWeek()` infla la tira de días; `renderSelectedDay()` rellena los campos con `isBindingMeals = true`; `watchMeal()` actualiza el modelo y llama a `scheduleSave()`; `saveCurrentWeek(onResult)` guarda una copia de la semana en `Dispatchers.IO`, en orden de llamada (`Mutex`) y sin cancelarse aunque se destruya la pantalla (`NonCancellable`); `scrollFieldIntoView()` mantiene el campo sobre el teclado |
| tablero del día | `android-app/app/src/main/res/layout/view_week_menu.xml` | `daySelectorScroll`, `layoutDaySelector`, `tvSelectedDay`, `tvSelectedTodayBadge`, `tvSelectedDate`, `etComida`, `etCena`; fondo `bg_menu_editor` (`surface`, radio `16dp`) |
| pantalla del menú | `android-app/app/src/main/res/layout/activity_main.xml` y `layout-land/activity_main.xml` | ambas incluyen `view_week_menu` como `menuBoard` y tienen un `ScrollView` `mainScroll`; en horizontal el tablero está en la columna derecha |
| persistencia del menú | `android-app/app/src/main/java/ourmeal/app/controller/MealDataManager.kt` | `AtomicFile` sobre `meal_plans.json` con `gson`; `loadWeeklyMeals()` devuelve todas las semanas o lanza `IOException`; `saveWeeklyMeal()` reemplaza la semana por `weekId` bajo `FILE_LOCK` |
| modelo | `android-app/app/src/main/java/ourmeal/app/model/` | `WeeklyMeals` (con `DAYS` en castellano como claves), `Meal(comida, cena)`, `WeekUtils` (ids iso `aaaa-Www`), `normalizeName()` |
| retroalimentación | `android-app/app/src/main/java/ourmeal/app/UiFeedback.kt` | `showSnackbar()` con acción opcional y duración de 6 s cuando hay acción |
| movimiento | `android-app/app/src/main/java/ourmeal/app/UiMotion.kt` | `reveal()` respeta `ValueAnimator.areAnimatorsEnabled()` |

formato real de `meal_plans.json`, derivado de `WeeklyMeals` y `Meal` sin anotaciones `@SerializedName`:

```json
[
  {
    "weekId": "2026-W43",
    "meals": {
      "Lunes": { "comida": "Lentejas", "cena": "Tortilla" },
      "Martes": { "comida": "", "cena": "" }
    }
  }
]
```

### TECNOLOGIA VERIFICADA

- kotlin `1.9.0`, agp `8.6.0`, gradle `8.7`, `compileSdk 35`, `minSdk 34`, `targetSdk 35`, `jvmTarget "17"`.
- vistas xml con view binding, material components `1.12.0` con tema `Theme.Material3.Dark.NoActionBar`, `appcompat`, `activity`, `lifecycle-runtime-ktx` y `gson 2.10.1`.
- no hay viewmodel, inyección de dependencias, room ni red. el estado vive en la actividad.
- pruebas unitarias jvm en `app/src/test/java/ourmeal/app/` y pruebas instrumentadas en `app/src/androidTest/java/ourmeal/app/`.

### SISTEMA VISUAL VERIFICADO

- tema único oscuro forzado en `OurmealApplication` con `MODE_NIGHT_YES`.
- colores en `res/values/colors.xml`: `surface #20221D`, `surface_raised #292C25`, `brand #FA9154`, `sage #C7DAB4`, `ink #FAFAFA`, `muted #B8B8B8`, `outline #4C5048`, `input_border #858A7F`.
- en el tablero, los rótulos `Comida` y `Cena` usan `TextAppearance.Ourmeal.Meta` con `source_sans_3_semibold`; los campos usan `bg_menu_field`, `18sp` e icono `ic_edit_meal`.
- la tira de días (`day_selector_chip.xml`, `bg_day_chip_*`) ya establece el lenguaje de piezas horizontales desplazables con objetivos táctiles amplios.

### LINEA BASE DE CALIDAD

ejecutado el 2026-10-22 desde `android-app/` con `./gradlew --offline testDebugUnitTest lintDebug`:

- compilación correcta y pruebas unitarias superadas.
- lint: 0 errores y 28 avisos, todos `GradleDependency` u `OldTargetApi`, preexistentes.
- kotlin: 1 aviso de deprecación preexistente por `SOFT_INPUT_ADJUST_RESIZE` en `OurmealDialogs.kt:31`.

la implementación no debe añadir errores ni avisos nuevos de lint o de compilación.

## CASOS DE USO

| id | actor | escenario | resultado esperado |
|---|---|---|---|
| cu-1 | persona que planifica cada domingo | enfoca la comida del lunes con el campo vacío | ve sus platos habituales que aún no están en la semana y elige uno con un toque |
| cu-2 | misma persona | escribe «alb» | la tira muestra `Albóndigas en salsa` aunque la escribió con tilde |
| cu-3 | pareja que quiere variar | escribe «pasta» el jueves cuando ya la puso el lunes | bajo el campo lee `También está el lunes en la comida.` y decide si la cambia |
| cu-4 | persona que planifica el lunes | escribe un plato que cenó el viernes anterior | lee `Ya estuvo en el menú hace 3 días.` |
| cu-5 | cualquier usuario | un error de escritura antiguo («lentjeas») aparece como sugerencia | mantiene pulsado el chip y deja de sugerirse; puede deshacer |
| cu-6 | usuario nuevo sin historial | enfoca un campo | no aparece nada nuevo: el editor se comporta como hoy |

## EXPERIENCIA DE USUARIO

### ESTRUCTURA DEL TABLERO

orden propuesto dentro de `view_week_menu.xml` para cada franja, comida y cena:

```text
Comida                                   rótulo existente
┌──────────────────────────────────────┐
│ Lentejas                           ✎ │  etComida (existente)
└──────────────────────────────────────┘
(Lentejas) (Pasta con atún) (Crema de…) ▸  tira de sugerencias, solo con foco
También está el jueves en la cena.         aviso de repetición, sage
────────────────────────────────────────   separador existente
Cena
…
```

- **tira de sugerencias**: `HorizontalScrollView` (`comidaSuggestionsScroll`, `cenaSuggestionsScroll`) con `scrollbars="none"` y un `ChipGroup` (`chipsComidaSuggestions`, `chipsCenaSuggestions`) con `app:singleLine="true"`, a `8dp` del campo. oculta por defecto (`gone`).
- **aviso de repetición**: `TextView` (`tvComidaRepeat`, `tvCenaRepeat`) con `TextAppearance.Ourmeal.Meta` en `@color/sage`, a `6dp` de lo anterior. oculto si no hay aviso.
- **decisión tomada**: la tira va pegada al campo porque es la continuación de lo que se escribe; el aviso va después porque describe el valor ya escrito y permanece visible sin foco.
- `view_week_menu.xml` es el mismo para vertical y horizontal, así que no se necesitan cambios en `layout-land/`.

### SUGERENCIAS

- **cuándo se ven**: solo mientras el campo tiene foco, la semana está cargada y hay al menos una sugerencia. sin foco, la tira está oculta.
- **campo vacío**: muestra «habituales»: platos con al menos 2 apariciones en el último año, que no estén ya en la semana visible ni ocultos, ordenados según [clasificación](#clasificacion).
- **campo con texto**: muestra los platos del historial cuya clave empieza por lo escrito, o alguna de cuyas palabras empieza por lo escrito, comparando con `matchKey()` (sin mayúsculas, acentos ni espacios repetidos). aquí sí se incluyen platos ya presentes en la semana: el aviso de repetición informará de ello.
- nunca se sugiere el plato que ya está escrito exactamente en el campo.
- se muestran como máximo 6 chips.
- **al tocar un chip**: el campo recibe el nombre del plato, el cursor va al final, la tira se oculta hasta la siguiente edición y se activa el guardado automático existente (`watchMeal()` → `scheduleSave()`). el foco permanece en el campo.
- **al pulsar la acción del teclado** o perder el foco, la tira se oculta.
- **al cambiar de día o de semana**, `finishMealEditing()` quita el foco y la tira se oculta.
- con el teclado abierto, al mostrarse la tira se pide que sea visible con `View.requestRectangleOnScreen()` tras su medida (`doOnLayout`), para que `mainScroll` la suba sobre el teclado si hace falta. se mantiene `scrollFieldIntoView()` como está.

### OCULTAR UNA SUGERENCIA

- mantener pulsado un chip, o usar la acción de accesibilidad `Dejar de sugerir`, oculta ese plato de las sugerencias.
- se muestra el snackbar `Ya no se sugerirá «Lentjeas».` con `Deshacer`, siguiendo el patrón de eliminación con deshacer de las listas.
- ocultar no borra nada del menú ni del historial y no afecta al aviso de repetición, que describe hechos.
- **decisión tomada**: un plato oculto vuelve a sugerirse automáticamente si el usuario lo escribe de nuevo y se guarda. así no hace falta una pantalla para gestionar ocultos y un error no deja un plato real oculto para siempre.
- si el fichero de ajustes no se pudo leer, la acción de ocultar no está disponible en esa sesión (ver [gestión de errores](#gestion-de-errores)).

### AVISO DE REPETICION

reglas, en orden de prioridad, para el plato escrito en un hueco (día `d`, franja `f`, fecha `D`):

1. **misma semana**: si otro hueco de la semana visible tiene la misma clave, el aviso es `También está el %1$s en la %2$s.` con el primer hueco coincidente en orden semanal, por ejemplo `También está el jueves en la cena.`.
2. **días recientes**: si no, se busca la aparición más reciente en otras semanas con fecha anterior a `D`. si está a entre 1 y 13 días, el aviso es `Ya estuvo en el menú hace %d días.` (`hace 1 día` en singular).
3. en otro caso, no hay aviso.

- el aviso se calcula para los dos campos del día seleccionado al mostrar el día y tras cada cambio de texto.
- solo se reescribe el `TextView` si el texto cambia, para que la región viva no repita el anuncio en cada pulsación.
- **decisión tomada**: el aviso es informativo, en `sage` y sin icono de alerta. usar `error` o `brand` lo haría parecer un fallo, y el producto busca un tono «cercano, claro y tranquilo» (`PRODUCT.md`).
- **decisión tomada**: se mira hacia atrás 13 días y no 7, para cubrir «la semana pasada» desde cualquier día de esta semana. las semanas futuras ya planificadas no generan aviso, porque el usuario las está construyendo después.

## DISEÑO VISUAL

- **estrategia de color**: contenida. los chips usan superficies neutras; `brand` solo aparece en el foco del campo, como hoy.
- **escena**: alguien en la cocina con el móvil en una mano, rellenando la semana con el teclado abierto y poca atención disponible. las sugerencias deben poder leerse de un vistazo y tocarse con el pulgar.
- **referencias**: la tira de días de `view_week_menu.xml`, las sugerencias en línea de teclados móviles y los chips de sugerencia de material 3.
- **nuevo estilo** en `res/values/themes.xml`: `Widget.Ourmeal.Chip.Suggestion` con padre `Widget.Material3.Chip.Suggestion`: `chipBackgroundColor @color/surface_raised`, texto `@color/ink` con `source_sans_3` a `16sp`, `chipStrokeColor @color/input_border`, `chipStrokeWidth 1dp`, `rippleColor @color/ripple_dark`, `ensureMinTouchTargetSize true` (objetivo táctil de `48dp`), `android:maxWidth 240dp` con `ellipsize end`.
- **contraste calculado** con los colores de `colors.xml`: `ink` sobre `surface_raised` 13,58:1; `sage` sobre `surface` 10,79:1; `input_border` sobre `surface` 4,54:1, que cumple el 3:1 de contraste no textual del contorno del chip. el borde `outline` (1,95:1) no se usa en los chips por no alcanzarlo.
- **foco**: los chips son enfocables con teclado; el estado de foco usa el `ripple` y el contorno de material, visibles sobre `surface`.
- **movimiento**: la tira aparece con `reveal()` (220 ms, sin animación si el sistema las desactiva) y desaparece sin animación para no retrasar la escritura. el aviso no se anima.
- **prohibido**: desplegables flotantes, iconos de alerta en el aviso, colores de error, bordes laterales de color y chips con fondo `brand`.

## ARQUITECTURA TECNICA

### VISTA GENERAL

```text
MainActivity.loadWeek()
   │  Dispatchers.IO
   ├─ MealDataManager.loadWeeklyMeals() ──▶ semana visible + resto de semanas
   │                                         └─ toDishRecords(excludeWeekId) ─▶ DishHistory
   └─ DishSuggestionManager.load() ───────▶ SuggestionSettings (platos ocultos)
                 │
                 ▼
   MealSuggestions (comida)      MealSuggestions (cena)
   etComida + tira + aviso       etCena + tira + aviso
                 │
                 └─ DishHistory.suggestions(…) y repeatHint(…)  ← funciones puras de model/
```

la clasificación y los avisos son funciones puras del paquete `model/`, probadas en jvm. un componente de interfaz, `MealSuggestions`, encapsula una franja (campo, tira y aviso) y se instancia dos veces para no duplicar lógica entre comida y cena. `MainActivity` solo coordina la carga y le pasa el contexto.

### PIEZAS COMPARTIDAS

estas utilidades también las necesita `.docs/new-features/compra-desde-el-menu.md`. **regla de coordinación**: si ya existen cuando se implemente esta funcionalidad, se reutilizan sin cambiar su contrato; si no existen, se crean exactamente así, con sus pruebas.

`android-app/app/src/main/java/ourmeal/app/model/TextNormalizer.kt` (añadir a la función existente `normalizeName()`, con los imports `java.text.Normalizer` y `java.util.Locale`):

```kotlin
/** Comparison key: same text for case, accents and spacing variants, keeping ñ. */
fun matchKey(raw: String): String = buildString {
  Normalizer.normalize(normalizeName(raw), Normalizer.Form.NFC)
    .lowercase(Locale.ROOT)
    .forEach { char ->
      if (char == 'ñ') append(char)
      else Normalizer.normalize(char.toString(), Normalizer.Form.NFD).forEach { part ->
        if (Character.getType(part) != Character.NON_SPACING_MARK.toInt()) append(part)
      }
    }
}
```

`android-app/app/src/main/java/ourmeal/app/model/MealSlot.kt` (nuevo):

```kotlin
enum class MealSlot { COMIDA, CENA }

fun Meal.textFor(slot: MealSlot): String = when (slot) {
  MealSlot.COMIDA -> comida
  MealSlot.CENA -> cena
}
```

**existente** desde el 2026-10-22: `WeekUtils.isValidWeekId(weekId)` en `android-app/app/src/main/java/ourmeal/app/model/WeekUtils.kt`, con pruebas en `WeekUtilsTest.kt`. no hay que crearla.

comportamiento exigido, verificado en la jvm durante el análisis:

- `matchKey()`: `"  Albóndigas  en salsa "` → `"albondigas en salsa"`; `"PIÑA"` → `"piña"`; `"Crème"` → `"creme"`; `"Pingüino"` → `"pinguino"`; `""` → `""`.
- `isValidWeekId()`, ya implementada y probada: `"2026-W43"` y `"2020-W53"` verdaderos; `"2021-W53"`, `"2026-W00"`, `"2026-W54"`, `"semana"` y `""` falsos.

**decisión tomada**: la comparación ignora mayúsculas, acentos y espacios repetidos, pero conserva la `ñ`, porque en castellano cambia la palabra. `isValidWeekId()` usa `rangeRefinedBy()` porque `LocalDate.with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, 53)` no falla en un año de 52 semanas, y `startOfWeek()` la usa para devolver el lunes actual ante cualquier id inválido.

### MODELO

**propuesto**: `android-app/app/src/main/java/ourmeal/app/model/DishHistory.kt`:

```kotlin
data class DishRecord(
  val key: String,
  val name: String,
  val date: LocalDate,
  val slot: MealSlot
)

data class DishSuggestion(val key: String, val name: String)

sealed interface RepeatHint {
  data class SameWeek(val day: String, val slot: MealSlot) : RepeatHint
  data class Recent(val daysAgo: Int) : RepeatHint
}

/** One record per non-blank slot; skips invalid week ids and unknown day keys. */
fun List<WeeklyMeals>.toDishRecords(excludeWeekId: String? = null): List<DishRecord>

class DishHistory(records: List<DishRecord>) {
  fun suggestions(
    query: String,
    slot: MealSlot,
    slotDate: LocalDate,
    plannedKeys: Set<String>,
    hiddenKeys: Set<String>,
    limit: Int = SUGGESTION_LIMIT
  ): List<DishSuggestion>

  /** Most recent record of [key] strictly before [date], or null. */
  fun lastBefore(key: String, date: LocalDate): DishRecord?
}

fun repeatHint(
  week: WeeklyMeals,
  day: String,
  slot: MealSlot,
  slotDate: LocalDate,
  history: DishHistory
): RepeatHint?

const val SUGGESTION_LIMIT = 6
const val SUGGESTION_WINDOW_DAYS = 364L
const val HABITUAL_MIN_COUNT = 2
const val RECENT_PENALTY_DAYS = 7L
const val HINT_WINDOW_DAYS = 13L
```

reglas de `toDishRecords()`:

- fecha de cada hueco: `WeekUtils.startOfWeek(weekId).plusDays(WeeklyMeals.DAYS.indexOf(day))`.
- se descartan las semanas con `WeekUtils.isValidWeekId(weekId) == false`, porque `startOfWeek()` les asignaría el lunes de la semana actual, una fecha que no les corresponde.
- se descartan claves de día que no estén en `WeeklyMeals.DAYS` y textos en blanco.
- `excludeWeekId` excluye la semana visible: su contenido se toma en vivo de `currentWeeklyMeals`, que puede estar más actualizado que el fichero.
- `name` es `normalizeName(texto)`; `key` es `matchKey(texto)`.

`DishHistory` agrupa los registros por `key` una sola vez en el constructor y guarda, por plato: nombre más reciente, número total de apariciones, apariciones por franja, fechas ordenadas y fecha más reciente.

### CLASIFICACION

`suggestions()` aplica, en este orden:

1. **ventana**: solo cuentan registros con fecha igual o posterior a `slotDate - SUGGESTION_WINDOW_DAYS`. los platos que no aparecen en el último año no se sugieren.
2. **exclusiones**: `hiddenKeys`; la clave igual a `matchKey(query)`; con consulta vacía, también `plannedKeys` y los platos con menos de `HABITUAL_MIN_COUNT` apariciones.
3. **coincidencia** (solo con consulta no vacía, `q = matchKey(query)`): nivel 0 si la clave empieza por `q`; nivel 1 si alguna palabra de la clave empieza por `q`; sin coincidencia, se descarta.
4. **orden** estable y determinista:
   1. nivel de coincidencia, ascendente.
   2. reciente: los platos con alguna aparición a `RECENT_PENALTY_DAYS` días o menos de `slotDate`, antes o después, van detrás. favorece la variedad sin ocultar nada.
   3. apariciones en la misma `slot`, descendente: lo que se suele cenar aparece primero en la cena.
   4. apariciones totales, descendente.
   5. fecha más reciente, descendente.
   6. `key`, ascendente, para desempatar.
5. **límite**: los primeros `limit`.

el nombre mostrado es el de la aparición más reciente, para respetar cómo escribe el usuario hoy.

### AJUSTES DE SUGERENCIAS

**propuesto**: `android-app/app/src/main/java/ourmeal/app/model/SuggestionSettings.kt`:

```kotlin
data class SuggestionSettings(
  @SerializedName("version") val version: Int = SUGGESTION_SETTINGS_VERSION,
  @SerializedName("hidden") val hidden: List<String> = emptyList()
) {
  val hiddenKeys: Set<String> get() = hidden.toSet()
  fun hide(key: String): SuggestionSettings
  fun unhide(keys: Set<String>): SuggestionSettings
}

const val SUGGESTION_SETTINGS_VERSION = 1
const val HIDDEN_DISH_CAP = 200
```

- `hide()` es idempotente, añade al final y recorta a `HIDDEN_DISH_CAP` descartando los más antiguos.
- `unhide()` es idempotente.

**propuesto**: `android-app/app/src/main/java/ourmeal/app/controller/DishSuggestionManager.kt`, con el patrón exacto de `MealDataManager` (`AtomicFile`, `Gson`, `FILE_LOCK` propio):

```kotlin
class DishSuggestionManager(context: Context) {
  private val file = AtomicFile(File(context.filesDir, "dish_suggestions.json"))

  /** Default settings when the file does not exist; IOException when it cannot be read. */
  fun load(): SuggestionSettings

  /** Read-modify-write under FILE_LOCK. False when reading or writing fails. */
  fun update(change: (SuggestionSettings) -> SuggestionSettings): Boolean
}
```

formato del fichero:

```json
{ "version": 1, "hidden": ["lentjeas"] }
```

- **riesgo conocido de gson**: `gson` crea los objetos sin el constructor de kotlin, así que un campo ausente llega como `null` aunque el tipo no sea anulable. `load()` debe sanear: `hidden` nulo pasa a lista vacía y se descartan elementos nulos o en blanco.
- **decisión tomada**: se usa un fichero json con `AtomicFile`, igual que el resto de la aplicación, y no `SharedPreferences`, para no introducir un segundo mecanismo de persistencia.
- **decisión tomada**: no se modifica `meal_plans.json` ni las clases `WeeklyMeals` y `Meal`. no hay migración de datos existentes.

### COMPONENTE DE INTERFAZ

**propuesto**: `android-app/app/src/main/java/ourmeal/app/MealSuggestions.kt`:

```kotlin
/** Suggestion strip and repeat hint for one meal field of the weekly board. */
internal class MealSuggestions(
  private val slot: MealSlot,
  private val input: AppCompatEditText,
  private val strip: HorizontalScrollView,
  private val chips: ChipGroup,
  private val hint: TextView,
  private val onPick: (DishSuggestion) -> Unit,
  private val onHide: ((DishSuggestion) -> Unit)?
) {
  /** Called after loading and after every change of day, text or hidden dishes. */
  fun render(state: SuggestionState)
  fun hideStrip()
}

internal data class SuggestionState(
  val history: DishHistory,
  val week: WeeklyMeals,
  val day: String,
  val slotDate: LocalDate,
  val hiddenKeys: Set<String>,
  val showStrip: Boolean
)
```

responsabilidades:

- calcular `suggestions()` con el texto actual y `plannedKeys` derivado de `week`, y reconstruir los chips solo si cambia la lista de claves.
- calcular `repeatHint()` y pintar el texto con `history_repeat_same_week` o `history_repeat_recent`; ocultar el aviso si es `null`.
- configurar cada chip: texto, `contentDescription` con `history_use_suggestion`, `setOnClickListener` → `onPick`, y, si `onHide != null`, `setOnLongClickListener` y `ViewCompat.addAccessibilityAction()` con `history_hide_action`.
- mostrar la tira con `reveal()` y pedir su visibilidad con `requestRectangleOnScreen()`.

**decisión tomada**: no se usa `RecyclerView` ni adaptador. son 6 chips como máximo y `MainActivity` ya infla piezas pequeñas directamente.

### CAMBIOS EN `MainActivity`

1. nuevos campos: `history: DishHistory?`, `settings: SuggestionSettings?` (nulo si no se pudo leer), `suggestionManager: DishSuggestionManager`, y dos `MealSuggestions` (`lunchSuggestions`, `dinnerSuggestions`) creados en `onCreate()` tras `setupMealFields()`.
2. `loadWeek()`: en el mismo bloque de `Dispatchers.IO`, sustituir `dataManager.getWeeklyMeals(weekId)` por una sola lectura con `loadWeeklyMeals()`, obtener la semana visible (`find { it.weekId == weekId } ?: WeeklyMeals(weekId)`) y construir `DishHistory(all.toDishRecords(excludeWeekId = weekId))`. leer los ajustes con `runCatching { suggestionManager.load() }.getOrNull()`. el error de lectura del menú sigue mostrando el estado de error actual.
3. `renderSelectedDay()`: tras rellenar los campos, llamar a `render()` de ambas franjas con `showStrip = false`.
4. `watchMeal()`: en `afterTextChanged`, después de actualizar el modelo, llamar a `render()` de la franja editada con `showStrip = input.hasFocus()`. cuando `isBindingMeals` es verdadero, no se hace nada nuevo.
5. el `OnFocusChangeListener` existente de `watchMeal()`: con foco, además de `scrollFieldIntoView()`, mostrar la tira; sin foco, `hideStrip()`.
6. `onPick`: `input.setText(name)`, `input.setSelection(input.length())`, y ocultar la tira hasta la siguiente edición del usuario. el `TextWatcher` existente guarda.
7. `saveCurrentWeek()`: en su callback, cuando el guardado es correcto, si algún plato de la semana está oculto, quitarlo de ocultos con `suggestionManager.update { it.unhide(keys) }` en `lifecycleScope.launch(Dispatchers.IO)` y actualizar `settings` en memoria.
8. `onHide`: actualizar `settings` en memoria, volver a pintar, persistir en `Dispatchers.IO` y mostrar el snackbar con `Deshacer`. si persistir falla, revertir y mostrar `history_hide_error`.

**existente**: `saveCurrentWeek()` ya escribe fuera del hilo principal y acepta un callback `onResult: (Boolean) -> Unit`. el desocultado se encadena a ese resultado sin cambiar el orden de las escrituras del menú.

### FICHEROS AFECTADOS

| ruta | cambio |
|---|---|
| `android-app/app/src/main/java/ourmeal/app/model/TextNormalizer.kt` | añadir `matchKey()` si no existe |
| `android-app/app/src/main/java/ourmeal/app/model/MealSlot.kt` | nuevo si no existe |
| `android-app/app/src/main/java/ourmeal/app/model/DishHistory.kt` | nuevo |
| `android-app/app/src/main/java/ourmeal/app/model/SuggestionSettings.kt` | nuevo |
| `android-app/app/src/main/java/ourmeal/app/controller/DishSuggestionManager.kt` | nuevo |
| `android-app/app/src/main/java/ourmeal/app/MealSuggestions.kt` | nuevo |
| `android-app/app/src/main/java/ourmeal/app/MainActivity.kt` | carga del historial, integración de ambas franjas, desocultar al guardar |
| `android-app/app/src/main/res/layout/view_week_menu.xml` | tiras y avisos de comida y cena |
| `android-app/app/src/main/res/values/themes.xml` | `Widget.Ourmeal.Chip.Suggestion` |
| `android-app/app/src/main/res/values/strings.xml` | textos de la sección [textos](#textos) |
| `android-app/app/src/test/java/ourmeal/app/` | pruebas nuevas |
| `android-app/app/src/androidTest/java/ourmeal/app/MealSuggestionsTest.kt` | nuevo |
| `changelog.md` | entradas bilingües en `SIN PUBLICAR / UNRELEASED` |
| `.docs/info-project/informacion-general.md` y `.docs/info-project/informacion-ayuda.md` | describir las sugerencias y el aviso como disponibles y retirar la nota de mejora pendiente |
| `claude.md` y `agents.md` | añadir sugerencias y aviso de repeticiones a la lista de capacidades de `CONTEXTO DEL PROYECTO` |

## CONTRATOS E INTERFACES

### DATOS DE ENTRADA Y SALIDA

| función | entrada | salida | garantías |
|---|---|---|---|
| `toDishRecords()` | todas las semanas leídas, id de semana excluida | lista de `DishRecord` | sin registros de semanas inválidas, días desconocidos ni textos en blanco |
| `DishHistory.suggestions()` | texto actual, franja, fecha del hueco, claves de la semana, claves ocultas | hasta 6 `DishSuggestion` | determinista para la misma entrada; nunca incluye ocultos ni el texto actual |
| `DishHistory.lastBefore()` | clave y fecha | `DishRecord` o `null` | solo fechas estrictamente anteriores |
| `repeatHint()` | semana visible, día, franja, fecha, historial | `RepeatHint` o `null` | `null` con texto en blanco; misma semana tiene prioridad |
| `DishSuggestionManager.load()` | sin parámetros | `SuggestionSettings` | ajustes por defecto si no hay fichero; `IOException` si está dañado |
| `DishSuggestionManager.update()` | función de cambio | `Boolean` | lectura, cambio y escritura atómicos bajo `FILE_LOCK` |

no hay apis remotas, eventos del sistema ni extras de navegación nuevos.

### TEXTOS

nuevas cadenas en `res/values/strings.xml`, en un bloque `<!-- Historial de platos -->`. el texto es literal y se escribe tal cual:

```xml
<string name="history_use_suggestion">Usar %1$s</string>
<string name="history_hide_action">Dejar de sugerir</string>
<string name="history_hidden">Ya no se sugerirá «%1$s».</string>
<string name="history_hide_error">No se pudo guardar el cambio</string>
<string name="history_repeat_same_week">También está el %1$s en la %2$s.</string>
<plurals name="history_repeat_recent">
  <item quantity="one">Ya estuvo en el menú hace %d día.</item>
  <item quantity="other">Ya estuvo en el menú hace %d días.</item>
</plurals>
```

- en `history_repeat_same_week`, `%1$s` es el día de `WeeklyMeals.DAYS` en minúscula con `Locale.forLanguageTag("es-ES")` y `%2$s` es `day_lunch` o `day_dinner` en minúscula.
- se reutiliza la cadena existente `undo` para la acción del snackbar.
- el `HorizontalScrollView` de cada franja lleva `android:importantForAccessibility="no"`, que afecta solo al contenedor y no a sus hijos; cada chip se anuncia con `history_use_suggestion` y el lector de pantalla lo trata como botón.

## GESTION DE ESTADOS

| estado | condición | tira | aviso |
|---|---|---|---|
| cargando | `loadWeek()` en curso | oculta | oculto |
| error de carga del menú | `IOException` en `loadWeeklyMeals()` | oculta (estado de error existente) | oculto |
| sin historial | ninguna otra semana con platos | oculta con campo vacío; oculta al escribir si no hay coincidencias | solo avisos de misma semana |
| campo enfocado y vacío | hay habituales | visible con habituales | oculto |
| campo enfocado con texto | hay coincidencias | visible con coincidencias | según reglas |
| campo enfocado con texto | sin coincidencias | oculta | según reglas |
| campo sin foco | cualquier texto | oculta | según reglas |
| tras elegir una sugerencia | hasta la siguiente edición | oculta | según reglas |
| ajustes no disponibles | `DishSuggestionManager.load()` lanza `IOException` | visible, sin ocultar por pulsación larga ni acción de accesibilidad | según reglas |
| ocultando | persistencia en curso | ya actualizada en memoria | sin cambios |
| guardado del menú con error | el callback de `saveCurrentWeek()` recibe `false` | sin cambios | sin cambios; no se desocultan platos |

## GESTION DE ERRORES

| situación | detección | comportamiento |
|---|---|---|
| `meal_plans.json` dañado | `IOException` en `loadWeeklyMeals()` | estado de error actual del menú con reintento; sin historial |
| `dish_suggestions.json` dañado | `IOException` en `load()` | sugerencias sin ocultos y acción de ocultar desactivada; **no se sobrescribe** el fichero dañado |
| fallo al persistir un ocultamiento | `update()` devuelve `false` | se revierte en memoria, se vuelve a pintar y snackbar `history_hide_error` |
| fallo al desocultar tras guardar | `update()` devuelve `false` | se ignora sin aviso; se reintentará en el siguiente guardado. el menú ya está guardado |
| semana con id inválido en el fichero | `isValidWeekId()` falso | se ignora en el historial; no se modifica el fichero |
| clave de día desconocida | no está en `WeeklyMeals.DAYS` | se ignora en el historial |
| valores nulos en `Meal` por un json editado a mano | `gson` deja `null` en un campo no anulable | **supuesto aceptado**: la aplicación nunca escribe `null`; el código existente tiene la misma suposición. si se quiere blindar, `toDishRecords()` puede leer el texto con `(meal.textFor(slot) as String?).orEmpty()` |
| cambio de semana antes de que termine la carga | `weekId != currentWeekId` tras `withContext` | se descarta el resultado, como hoy; el historial se reconstruye con la carga vigente |

### CASOS LIMITE

- platos multilínea: el nombre del historial se normaliza en una línea; elegir la sugerencia escribe esa versión.
- el mismo plato con mayúsculas o acentos distintos cuenta como uno; se muestra la escritura más reciente.
- un plato largo (120 caracteres) aparece truncado en el chip con `ellipsize end`; su nombre completo está en el `contentDescription`.
- años de uso: unas 728 apariciones al año como máximo (14 huecos × 52 semanas). construir el historial es lineal y ocurre una vez por carga de semana.
- semanas futuras ya planificadas cuentan para las sugerencias y para la penalización de recientes, pero no para el aviso.
- semana iso 53: `toDishRecords()` usa las fechas reales de `startOfWeek()` para ids válidos, así que el cambio de año no rompe el cálculo de días.
- texto de una sola letra: se aplican las reglas normales; con 6 chips como máximo, la tira sigue siendo manejable.
- tamaño de fuente al 200 %: los chips crecen en altura y la tira sigue siendo horizontal y desplazable.
- dirección de derecha a izquierda: `android:supportsRtl="true"` ya está activo y `HorizontalScrollView` lo respeta.

## SEGURIDAD, PRIVACIDAD Y RENDIMIENTO

- no se añaden permisos, red, servicios externos ni dependencias. `Chip` y `ChipGroup` pertenecen a `com.google.android.material:material`, ya incluida.
- el historial se calcula en memoria a partir de datos que la aplicación ya guarda. el único fichero nuevo, `dish_suggestions.json`, contiene claves de platos ocultos.
- la copia de seguridad automática (`android:allowBackup="true"`, reglas vacías en `res/xml/`) incluirá el nuevo fichero igual que los existentes. no hace falta cambiar las reglas.
- no se registran nombres de platos en `Log`.
- la construcción del historial se hace en `Dispatchers.IO` dentro de la carga ya existente, sin una segunda lectura del fichero: hoy `getWeeklyMeals()` ya lee todas las semanas y descarta el resto.
- el filtrado por pulsación recorre las claves distintas del último año (del orden de cientos) y es despreciable frente al coste de pintar.
- los chips solo se reconstruyen si cambia la lista de claves, lo que evita parpadeos y anuncios repetidos.
- el aviso solo se reescribe si cambia su texto.

## ESCALABILIDAD Y EVOLUCION

- `DishHistory` es la base natural de otras funciones por plato: estadísticas de variedad, «hace tiempo que no…», o la agrupación de platos de la compra.
- `RepeatHint` es un tipo cerrado; añadir otra regla (por ejemplo, «tres semanas seguidas») es añadir un caso y su texto.
- `SuggestionSettings.version` permite añadir ajustes futuros (por ejemplo, desactivar las sugerencias) sin romper ficheros existentes.
- si `meal_plans.json` creciera durante muchos años, se puede limitar la lectura del historial a la ventana de 364 días sin cambiar la interfaz de `DishHistory`.

## PLAN DE IMPLEMENTACION

### FASE 1: MODELO Y LOGICA PURA

tareas:

1. crear o reutilizar `matchKey()`, `MealSlot` y `Meal.textFor()`, y reutilizar `WeekUtils.isValidWeekId()`, según [piezas compartidas](#piezas-compartidas).
2. crear `DishHistory.kt` y `SuggestionSettings.kt`.
3. escribir sus pruebas unitarias.

criterio de finalización: `./gradlew testDebugUnitTest` en verde con las pruebas nuevas.

### FASE 2: PERSISTENCIA DE AJUSTES

dependencias: fase 1.

tareas:

1. crear `DishSuggestionManager` con saneado de nulos.

criterio de finalización: lectura sin fichero, escritura, relectura y fichero dañado cubiertos por la prueba instrumentada correspondiente.

### FASE 3: INTERFAZ

dependencias: fase 1.

tareas:

1. añadir tiras y avisos a `view_week_menu.xml`.
2. añadir `Widget.Ourmeal.Chip.Suggestion` y las cadenas.
3. crear `MealSuggestions`.

criterio de finalización: el proyecto compila y el tablero sin datos de historial se ve exactamente como antes.

### FASE 4: INTEGRACION EN EL MENU

dependencias: fases 2 y 3.

tareas:

1. integrar la carga del historial y de los ajustes en `loadWeek()`.
2. conectar foco, edición, cambio de día y elección de sugerencia.
3. desocultar tras guardar.

criterio de finalización: sugerencias y avisos funcionan en vertical y horizontal con datos de varias semanas; el guardado automático y el cambio de semana se comportan como antes.

### FASE 5: OCULTAR Y DESHACER

dependencias: fase 4.

tareas:

1. pulsación larga, acción de accesibilidad, snackbar con `Deshacer` y reversión ante fallo.

criterio de finalización: ocultar, deshacer, persistencia tras reinicio y desocultado al reescribir el plato verificados.

### FASE 6: CALIDAD Y DOCUMENTACION

dependencias: fase 5.

tareas:

1. revisión con talkback, teclado físico, fuente al 200 % y animaciones desactivadas, en teléfonos pequeños con el teclado abierto.
2. `./gradlew testDebugUnitTest lintDebug assembleDebug` y `./gradlew connectedDebugAndroidTest`.
3. añadir las entradas bilingües a `changelog.md` en `## SIN PUBLICAR / UNRELEASED`, categoría `NUEVO / ADDED`.
4. actualizar `.docs/info-project/informacion-general.md` (función disponible, sin la nota de mejora pendiente), `.docs/info-project/informacion-ayuda.md` (nuevo apartado de uso) y la lista de capacidades de `claude.md` y `agents.md`. la descripción debe ser exacta: la aplicación avisa y sugiere, no impide repetir. los ficheros ya siguen las reglas de markdown y deben seguir cumpliéndolas.
5. revisar `git diff --check` y el diff completo.

criterio de finalización: todos los [criterios de aceptación](#criterios-de-aceptacion) cumplidos y sin avisos nuevos de lint o compilación.

## PRUEBAS Y VALIDACION

### PRUEBAS UNITARIAS

en `android-app/app/src/test/java/ourmeal/app/`, con junit 4 como las existentes:

| fichero | casos mínimos |
|---|---|
| `ModelTest.kt` (ampliar `TextNormalizerTest`) | los cinco ejemplos de `matchKey()` |
| `DishHistoryTest.kt`: registros | `"2026-W43"` con `Lunes` → `2026-10-19` y `Domingo` → `2026-10-25`; semana `"2021-W53"` ignorada; día `"Festivo"` ignorado; huecos en blanco ignorados; `excludeWeekId` respetado |
| `DishHistoryTest.kt`: habituales | mínimo de 2 apariciones; exclusión de planificados y ocultos; límite de 6; prioridad por franja; penalización de recientes frente a un plato igual de frecuente pero antiguo; ventana de 364 días |
| `DishHistoryTest.kt`: búsqueda | `"albon"` encuentra `Albóndigas en salsa`; `"salsa"` también, pero detrás de los que empiezan por `salsa`; `"ond"` no encuentra nada; el texto exacto actual no se sugiere; se muestra la escritura más reciente; empates resueltos por clave |
| `DishHistoryTest.kt`: avisos | misma semana antes que recientes; mismo día en la otra franja; 13 días con aviso y 14 sin aviso; fechas posteriores ignoradas; texto en blanco sin aviso |
| `SuggestionSettingsTest.kt` | `hide()` y `unhide()` idempotentes; recorte a `HIDDEN_DISH_CAP`; ida y vuelta con `Gson`; json sin `hidden` o con nulos saneado sin excepción |

### PRUEBAS INSTRUMENTADAS

`android-app/app/src/androidTest/java/ourmeal/app/MealSuggestionsTest.kt`:

- declarar `@get:Rule val appData = AppDataSandbox()`, como `CreationFormTest`. la regla aparta los ficheros reales de `filesDir` antes de cada prueba y los restaura al terminar. cada prueba siembra después sus semanas, relativas a `WeekUtils.getCurrentWeekId()` y `WeekUtils.getPreviousWeek()`, con `MealDataManager`.
- seguir el patrón de `CreationFormTest`: `requestFocus()`, `setText()`, `performClick()` y `performLongClick()` dentro de `scenario.onActivity`, sin inyección de entrada de espresso, que el propio test documenta como incompatible con api 37.
- casos:
  1. foco en `etComida` vacío → `chipsComidaSuggestions` visible con los habituales esperados en orden.
  2. escribir `"alb"` → solo coincidencias.
  3. tocar un chip → `etComida` contiene el nombre, la tira se oculta y, tras más de `700` ms, `meal_plans.json` contiene el plato.
  4. mismo plato en `etCena` → `tvCenaRepeat` muestra el aviso de misma semana.
  5. plato de la semana anterior a menos de 14 días → aviso de días recientes.
  6. pulsación larga en un chip → desaparece de la tira, `dish_suggestions.json` lo contiene y `Deshacer` lo restaura.
  7. escribir un plato oculto y esperar al guardado → deja de estar oculto.
  8. `dish_suggestions.json` con contenido no json → sugerencias visibles, pulsación larga sin efecto y fichero sin modificar.
  9. `scenario.recreate()` → los avisos se recalculan igual.

### VALIDACION MANUAL

| comprobación | cómo |
|---|---|
| vertical y horizontal | emulador api 34 y api 35; en horizontal, el tablero está en la columna derecha con su propio `mainScroll` |
| teclado en pantalla | en un teléfono pequeño, la tira queda visible sobre el teclado al enfocar cada campo |
| tamaño de fuente | 130 % y 200 % |
| talkback | cada chip se anuncia como `Usar …`; la acción `Dejar de sugerir` aparece en el menú de acciones; el aviso se anuncia una vez al cambiar |
| teclado físico | tabulación del campo a los chips y vuelta; `Intro` activa el chip; foco visible |
| animaciones desactivadas | la tira aparece sin transición |
| regresión | guardado automático, estado `Guardando cambios…` / `Cambios guardados.`, cambio de semana, selector de fecha, `Volver a hoy`, rotación con edición en curso |

### COMANDOS

desde `android-app/`:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew connectedDebugAndroidTest
git diff --check
```

## CRITERIOS DE ACEPTACION

1. sin historial, el tablero se ve y se comporta exactamente como antes.
2. con historial, al enfocar un campo vacío aparecen hasta 6 habituales que no están en la semana, ordenados según [clasificación](#clasificacion).
3. al escribir, la tira muestra coincidencias por inicio de clave o de palabra, sin distinguir mayúsculas ni acentos, y se oculta si no hay ninguna.
4. tocar una sugerencia rellena el campo, mantiene el foco, oculta la tira y el plato se guarda con el guardado automático existente.
5. la tira desaparece al perder el foco, al pulsar la acción del teclado y al cambiar de día o de semana.
6. el aviso de misma semana y el de días recientes aparecen con los textos y prioridades descritos, en `sage`, y nunca como error.
7. ocultar una sugerencia la retira de inmediato, persiste tras reiniciar la aplicación, se puede deshacer y se revierte si no se puede guardar.
8. un plato oculto vuelve a sugerirse cuando el usuario lo escribe y se guarda.
9. un `dish_suggestions.json` dañado nunca se sobrescribe y no impide usar el menú.
10. `meal_plans.json` mantiene su formato; ninguna semana existente se modifica por esta funcionalidad.
11. todos los chips tienen nombre accesible, objetivo táctil de al menos `48dp`, foco visible y contraste aa; la tira funciona con teclado físico, talkback, fuente al 200 % y animaciones desactivadas.
12. `testDebugUnitTest`, `lintDebug`, `assembleDebug` y `connectedDebugAndroidTest` terminan sin errores, sin avisos nuevos y con las pruebas de [pruebas y validación](#pruebas-y-validacion) incluidas.
13. `changelog.md`, `.docs/info-project/informacion-general.md`, `.docs/info-project/informacion-ayuda.md`, `claude.md` y `agents.md` están actualizados.

## DECISIONES Y SUPUESTOS

| tipo | punto | propuesta por defecto |
|---|---|---|
| decisión tomada | dirección de diseño (tira de chips en línea solo con foco, sin rótulo `Sugerencias`, aviso informativo en `sage` para la misma semana o los últimos 13 días, ocultar con pulsación larga) | confirmada por el mantenedor el 2026-10-22 en la ronda de validación de `impeccable shape` |
| decisión tomada | formato de `.docs/info-project/` | resuelto el 2026-10-22: los tres ficheros se reformatearon por completo según las reglas y describen el comportamiento real |
| supuesto | `Widget.Material3.Chip.Suggestion` con `ensureMinTouchTargetSize` mantiene la tira en una altura de unos `48dp` | verificar en dispositivo durante la fase 3 |

## INCIDENCIAS PREEXISTENTES DETECTADAS

fuera del alcance de esta funcionalidad; se documentan sin corregirlas. el 2026-10-22 se corrigieron las incidencias de `WeekUtils.startOfWeek()`, el guardado del menú en el hilo principal, el aislamiento de `CreationFormTest` y la documentación desactualizada de `claude.md`, `agents.md` y `.docs/info-project/`.

- `meal_plans.json` crece sin límite: cada semana visitada y editada se conserva para siempre.
