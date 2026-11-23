# OURMEAL | PREPARAR LA COMPRA DESDE EL MENU

> especificación funcional, diseño técnico y guía de implementación de la funcionalidad que convierte los platos del menú semanal en productos de una lista de la compra. el documento es autónomo: no requiere leer otros documentos de `.docs/new-features/` para implementarla.

## ESTADO DEL DOCUMENTO

| campo | valor |
|---|---|
| estado | propuesta lista para implementar; dirección de diseño y decisiones pendientes confirmadas por el mantenedor el 2026-10-22 |
| fecha del análisis | 2026-10-22 |
| ámbito | aplicación android en `android-app/` |
| commit de referencia | `15ed1a8`, último commit de `main` en el momento del análisis |
| funcionalidad relacionada | `.docs/new-features/historial-de-platos.md`, independiente; solo comparte las utilidades de modelo descritas en [piezas compartidas](#piezas-compartidas) |

en este documento se distinguen cuatro tipos de afirmación:

- **existente**: comprobado en el código del commit de referencia.
- **propuesto**: cambio que debe implementarse.
- **decisión tomada**: alternativa elegida con su justificación; no se debe reabrir durante la implementación sin motivo nuevo.
- **pendiente** o **supuesto**: punto que requiere validación del mantenedor o comprobación en dispositivo.

## RESUMEN

ourmeal tiene dos mitades que hoy no se comunican: el menú semanal (`MainActivity`) y las listas de la compra (`ListActivity` y `ListDetailActivity`). quien planifica «lentejas» el lunes tiene que acordarse de abrir una lista y escribir a mano lentejas, chorizo y zanahoria, y repetirlo cada semana.

esta funcionalidad añade un recorrido guiado, **preparar la compra**, que parte del menú de la semana visible, agrupa los platos planificados, propone los ingredientes que el usuario ya indicó otras veces para cada plato, le deja revisarlos y los añade a una lista nueva o existente sin duplicar productos. la aplicación recuerda los ingredientes de cada plato, de modo que la segunda vez que aparece un plato el trabajo se reduce a revisar y confirmar.

## PROBLEMA Y OBJETIVOS

### PROBLEMA

- **existente**: `MealDataManager` (`meal_plans.json`) y `DataManager` (`user_lists.json`) no tienen ninguna relación en el código. ninguna pantalla lee ambos ficheros.
- **existente**: hasta el 2026-10-22, `.docs/info-project/informacion-general.md` prometía un «generador inteligente de listas» que deducía los productos del menú, aunque ese comportamiento no existe. el documento ya se corrigió y hoy indica que el menú y las listas son independientes y que esta mejora está pendiente.
- **existente**: los platos son texto libre de hasta 120 caracteres (`android:maxLength="120"` en `etComida` y `etCena`). no hay ingredientes ni recetas en el modelo, así que la aplicación no puede deducir ingredientes por sí sola sin inventar datos.

### OBJETIVOS

1. reducir a un único recorrido la tarea semanal de pasar del menú a la compra.
2. evitar escribir de nuevo los ingredientes de los platos habituales gracias a una memoria local de ingredientes por plato.
3. no duplicar productos que ya estén en la lista de destino.
4. no inventar ingredientes: la aplicación solo propone lo que el propio usuario escribió antes.
5. mantener intactos los flujos actuales de menú y listas.

### NO OBJETIVOS

- no se crea un recetario completo con pasos, raciones o fotos.
- no se calculan cantidades ni se suman unidades. un ingrediente es un nombre, igual que un `ListItem` actual.
- no se descarga información de internet ni se añaden permisos.
- no se divide automáticamente un plato compuesto («lentejas y ensalada») en varios platos. el usuario edita el menú si quiere separarlos.

## SELECCION Y JUSTIFICACION

se evaluaron estas alternativas a partir del análisis del código y de las promesas documentadas del producto:

| alternativa | valor cotidiano | encaje con el producto | viabilidad con la infraestructura actual | decisión |
|---|---|---|---|---|
| preparar la compra desde el menú con memoria de ingredientes | alto: tarea semanal y repetitiva | une las dos mitades de la aplicación y cumple una promesa documentada | alta: dos ficheros json existentes y un fichero nuevo con el mismo patrón | **seleccionada** |
| historial de platos con sugerencias y aviso de repeticiones | alto: se usa cada vez que se escribe un plato | cumple «evitar repeticiones», también documentado | alta | seleccionada como segunda funcionalidad en `.docs/new-features/historial-de-platos.md` |
| modo compra: comprados al final, desmarcar todo, editar productos | medio-alto | mejora un flujo existente, pero son mejoras sueltas de una pantalla | alta | descartada por ahora: se compone de mejoras menores y no cubre ningún hueco de producto |
| copiar la semana anterior o plantillas de semana | medio | parcialmente cubierto por las sugerencias del historial | alta | descartada: menor valor que las dos seleccionadas |
| compartir el menú semanal como texto | bajo-medio | reutiliza el patrón de `shareList()` | alta | descartada: mejora menor |
| deducción automática de ingredientes con una base de recetas | alto en teoría | obligaría a inventar o descargar datos | baja: no hay red, backend ni datos de recetas | descartada |
| sincronización familiar en la nube | alto | requiere cuentas, backend y política de privacidad | nula sin un backend | descartada |

la funcionalidad seleccionada aporta el mayor valor porque convierte el menú en algo accionable: es la diferencia entre una agenda de platos y un asistente de compra. además, corrige una discrepancia real entre la documentación y el producto.

## CONTEXTO VERIFICADO DEL PROYECTO

### ARQUITECTURA EXISTENTE

| pieza | ruta | responsabilidad verificada |
|---|---|---|
| menú semanal | `android-app/app/src/main/java/ourmeal/app/MainActivity.kt` | carga la semana en `Dispatchers.IO`, edita `etComida` y `etCena` en línea, guarda con retardo de `700` ms (`SAVE_DELAY_MS`) y guarda de forma síncrona en `onPause()` y `changeWeek()` |
| listas | `android-app/app/src/main/java/ourmeal/app/ListActivity.kt` | crea, elimina con deshacer y abre listas; ordena por `createdAt` descendente |
| detalle de lista | `android-app/app/src/main/java/ourmeal/app/ListDetailActivity.kt` | añade productos con detección de duplicados `equals(ignoreCase = true)`, marca, elimina con deshacer y comparte como texto; guarda snapshots con `UserList.snapshot()` |
| persistencia de listas | `android-app/app/src/main/java/ourmeal/app/controller/DataManager.kt` | `AtomicFile` sobre `user_lists.json` con `gson`, bloqueo de proceso `FILE_LOCK`; `loadLists()` lanza `IOException` si el fichero está dañado |
| persistencia del menú | `android-app/app/src/main/java/ourmeal/app/controller/MealDataManager.kt` | `AtomicFile` sobre `meal_plans.json`; `getWeeklyMeals(weekId)` devuelve una semana vacía si no existe |
| modelo | `android-app/app/src/main/java/ourmeal/app/model/` | `UserList`, `ListItem`, `AppData`, `WeeklyMeals`, `Meal`, `WeekUtils`, `normalizeName()` |
| diálogos y formularios | `android-app/app/src/main/java/ourmeal/app/OurmealDialogs.kt` | `ourmealDialog(destructive)`, `prepareForm()`, `setFormSaving()`, `CreationFormState` |
| estados reutilizables | `android-app/app/src/main/java/ourmeal/app/ScreenUi.kt` y `layout/view_loading.xml`, `layout/view_state_panel.xml` | `bindLoading()` y `bindState()` con acción opcional |
| retroalimentación | `android-app/app/src/main/java/ourmeal/app/UiFeedback.kt` | `showSnackbar()` con acción y ancla opcionales |
| movimiento | `android-app/app/src/main/java/ourmeal/app/UiMotion.kt` | `reveal()` y `setProgressWithMotion()`, respetan `ValueAnimator.areAnimatorsEnabled()` |
| márgenes de sistema | `android-app/app/src/main/java/ourmeal/app/ScreenInsets.kt` | `applyScreenInsets()` para barras, recortes y teclado |

### TECNOLOGIA VERIFICADA

- kotlin `1.9.0`, agp `8.6.0`, gradle `8.7`, `compileSdk 35`, `minSdk 34`, `targetSdk 35`, `jvmTarget "17"`.
- vistas xml con view binding, material components `1.12.0` con tema `Theme.Material3.Dark.NoActionBar`, `appcompat`, `activity`, `lifecycle-runtime-ktx` y `gson 2.10.1`.
- no hay viewmodel, inyección de dependencias, room, red ni navegación por fragmentos. el estado vive en la actividad y se conserva con `onSaveInstanceState()` o `savedStateRegistry`.
- pruebas unitarias jvm en `app/src/test/java/ourmeal/app/` (`ModelTest.kt`, `WeekUtilsTest.kt`) y pruebas instrumentadas en `app/src/androidTest/java/ourmeal/app/` (`CreationFormTest.kt`, `ExampleInstrumentedTest.kt`).

### SISTEMA VISUAL VERIFICADO

- tema único oscuro forzado en `OurmealApplication` con `MODE_NIGHT_YES`.
- colores en `res/values/colors.xml`: `canvas #121212`, `surface #20221D`, `surface_raised #292C25`, `brand #FA9154`, `sage #C7DAB4`, `ink #FAFAFA`, `muted #B8B8B8`, `outline #4C5048`, `hairline #33FFFFFF`, `error #FFB4AB`.
- tipografías `bricolage_grotesque` para títulos y `source_sans_3` y `source_sans_3_semibold` para interfaz.
- estilos en `res/values/themes.xml`: `TextAppearance.Ourmeal.Display`, `.SectionTitle`, `.CardTitle`, `.Muted`, `.Label`, `.Meta`; `Widget.Ourmeal.Button.Primary`, `.Secondary`, `.Text.Muted`, `.Icon`; `Widget.Ourmeal.TextInputLayout`, `Widget.Ourmeal.TextInputEditText`; `ThemeOverlay.Ourmeal.AlertDialog` y `ThemeOverlay.Ourmeal.DestructiveDialog`.
- tarjetas de lista como `MaterialCardView` con `surface`, radio `10dp`, borde `hairline` de `1dp` y sin elevación (`item_list_item.xml`).
- todas las pantallas tienen variante horizontal en `res/layout-land/`.

### LINEA BASE DE CALIDAD

ejecutado el 2026-10-22 desde `android-app/` con `./gradlew --offline testDebugUnitTest lintDebug`:

- compilación correcta y pruebas unitarias superadas.
- lint: 0 errores y 28 avisos, todos `GradleDependency` u `OldTargetApi`, preexistentes.
- kotlin: 1 aviso de deprecación preexistente por `SOFT_INPUT_ADJUST_RESIZE` en `OurmealDialogs.kt:31`.

la implementación no debe añadir errores ni avisos nuevos de lint o de compilación.

## CASOS DE USO

| id | actor | escenario | resultado esperado |
|---|---|---|---|
| cu-1 | persona que planifica la semana en familia | el domingo rellena el menú y quiere la lista del sábado | en menos de un minuto tiene una lista nueva «compra 26 oct – 1 nov 2026» con los ingredientes de todos los platos |
| cu-2 | misma persona, semanas después | repite «lentejas» y «tortilla» | las tarjetas de esos platos aparecen con los ingredientes recordados; solo confirma |
| cu-3 | pareja con una lista fija «compra semanal» | quiere añadir lo del menú a la lista que ya comparten | elige la lista existente; la aplicación avisa de que «aceite» ya está y no lo repite |
| cu-4 | persona que come fuera algunos días | no quiere comprar para la cena del viernes | desmarca ese plato y no se añaden sus ingredientes |
| cu-5 | cualquier usuario | planifica un plato nuevo sin ingredientes | escribe sus ingredientes en la tarjeta; quedan recordados al confirmar |
| cu-6 | cualquier usuario | sale de la pantalla tras escribir ingredientes | la aplicación pide confirmación antes de descartar lo escrito |

## EXPERIENCIA DE USUARIO

### PUNTO DE ENTRADA

- **propuesto**: botón `btnPrepareShopping` en `MainActivity`, estilo `Widget.Ourmeal.Button.Secondary`, ancho completo, alto `56dp`, icono `@drawable/ic_basket`, texto `Preparar la compra`.
- en vertical (`layout/activity_main.xml`) se coloca justo debajo de `menuBoard` y antes de la fila de `tvSaveStatus`.
- en horizontal (`layout-land/activity_main.xml`) se coloca en la columna izquierda, debajo de la fila de `tvSaveStatus`, junto a la información de la semana.
- el botón solo es visible cuando `menuBoard` es visible. durante la carga y el error permanece oculto.
- está habilitado cuando la semana tiene al menos un plato no vacío. el recuento reutiliza el cálculo de `updateWeekProgress()`.
- cuando está deshabilitado se muestra debajo `tvPrepareShoppingHint` (estilo `TextAppearance.Ourmeal.Meta`) con `Planifica algún plato para preparar la compra.`. el texto explica el estado sin depender del color.
- **decisión tomada**: no se añade entrada desde `ListActivity`. el recorrido parte siempre de una semana concreta y el menú es el único sitio donde esa semana es explícita.

### FLUJO PRINCIPAL

1. el usuario pulsa `Preparar la compra`.
2. `MainActivity` cierra la edición con `finishMealEditing()`, cancela el guardado diferido con `cancelPendingSave()` y guarda la semana con `saveCurrentWeek { saved -> … }`, que escribe en segundo plano y llama al callback con el resultado. solo si `saved` es verdadero se abre la pantalla nueva; si falla, se queda en el menú: el snackbar de error y reintento ya existe. mientras tanto se ignoran pulsaciones repetidas del botón con una bandera, como hace `changeWeek()` con `isChangingWeek`.
3. se abre `MenuShoppingActivity` con el extra `EXTRA_WEEK_ID`.
4. la pantalla muestra la carga con `view_loading` y el texto `Reuniendo los platos de la semana…`.
5. aparecen las tarjetas de platos agrupados, en el orden de la semana (lunes comida, lunes cena, martes comida…), y la sección `Añadir a`.
6. el usuario revisa cada tarjeta: incluye o excluye el plato, quita ingredientes o escribe nuevos.
7. elige el destino: una lista nueva (nombre propuesto y editable) o una de sus listas.
8. el resumen inferior indica cuántos productos se añadirán y cuántos se omiten por estar ya en la lista.
9. pulsa `Añadir N productos`.
10. al terminar, la aplicación abre `ListDetailActivity` de la lista de destino y muestra un snackbar con el resultado. la pantalla de preparación se cierra, de modo que «atrás» desde el detalle vuelve al menú.

### ESTRUCTURA DE LA PANTALLA

disposición vertical, con la misma rejilla de `24dp` laterales que `activity_list_detail.xml`:

```text
┌──────────────────────────────────────┐
│ ← Volver al menú                     │  btnBack
│ DEL MENÚ A LA COMPRA                 │  Label
│ Prepara la compra                    │  Display, encabezado
│ Semana del 19 oct – 25 oct 2026      │  Muted
│                                      │
│ Platos                     5 platos  │  SectionTitle + Label
│ ┌──────────────────────────────────┐ │
│ │ ☑ Lentejas                       │ │  tarjeta de plato
│ │   Lunes, comida · Jueves, cena   │ │
│ │   Ingredientes recordados        │ │
│ │   (Lentejas ✕) (Chorizo ✕)       │ │
│ │   [ Añadir ingrediente      + ]  │ │
│ └──────────────────────────────────┘ │
│ ┌──────────────────────────────────┐ │
│ │ ☐ Pizza                          │ │  plato excluido
│ │   Viernes, cena · No se incluirá │ │
│ └──────────────────────────────────┘ │
│                                      │
│ Añadir a                             │  SectionTitle
│ (•) Una lista nueva                  │
│     [ Compra 19 oct – 25 oct 2026 ]  │
│ ( ) Una de mis listas                │
│     [ Compra semanal            ▾ ]  │
├──────────────────────────────────────┤
│ 9 productos nuevos · 2 ya estaban    │  resumen, región viva
│ [        Añadir 9 productos        ] │  Primary
└──────────────────────────────────────┘
```

- la zona central es un `NestedScrollView` con peso `1`. la barra inferior (`actionBar`) queda fija, igual que en `activity_list_detail.xml`, para que la acción principal esté siempre al alcance del pulgar.
- la variante `layout-land/activity_menu_shopping.xml` usa dos columnas, siguiendo `layout-land/activity_list_detail.xml`: a la izquierda cabecera, sección `Añadir a` y barra de acción; a la derecha la lista desplazable de platos.

### TARJETA DE PLATO

- cabecera: `MaterialCheckBox` `cbIncludeDish` de `48dp` con `@color/checkbox_tint`, nombre del plato `tvDishName` (`source_sans_3_semibold`, `16sp`, `ink`, sin límite de líneas) y ocurrencias `tvDishOccurrences` (`TextAppearance.Ourmeal.Meta`).
- las ocurrencias se muestran como `Lunes, comida · Jueves, cena`. a partir de tres ocurrencias se muestran las tres primeras y `y N más`.
- etiqueta `tvRememberedBadge` (`TextAppearance.Ourmeal.Label` en `sage`) con `Ingredientes recordados` cuando los ingredientes vienen de la memoria. da confianza sobre el origen de los datos.
- cuerpo: `ChipGroup` `chipGroupIngredients` con un chip de entrada por ingrediente y botón de cierre para quitarlo.
- `tvNoIngredients` (`Meta`, `muted`) con `Todavía sin ingredientes.` cuando el plato incluido no tiene ninguno.
- campo `ingredientField` (`Widget.Ourmeal.TextInputLayout`) con `etIngredient` (`Widget.Ourmeal.TextInputEditText`), pista `Añadir ingrediente`, `maxLength 80`, `singleLine`, `imeOptions="actionDone|flagNoExtractUi"`, `inputType="textCapSentences"` y icono final personalizado `@drawable/ic_plus` como acción de añadir.
- al pulsar la acción del teclado o el icono, se valida el texto, se añade el chip, se vacía el campo y **se mantiene el foco** para escribir el siguiente ingrediente sin tocar la pantalla.
- tocar la tarjeta fuera del campo alterna la casilla, igual que `ItemAdapter`.
- cuando el plato se excluye, el cuerpo se oculta y `tvDishOccurrences` añade `· No se incluirá`. **decisión tomada**: no se baja la opacidad del texto para indicar exclusión, porque reduciría el contraste por debajo de aa.

### SECCION DE DESTINO

- `RadioGroup` con dos `MaterialRadioButton`: `rbNewList` (`Una lista nueva`) y `rbExistingList` (`Una de mis listas`).
- `newListNameField` con el mismo contrato que `dialog_create_list.xml`: `counterMaxLength 80`, `maxLength 80`, error reservado. valor inicial: `Compra %1$s` con `WeekUtils.getWeekDisplay(weekId)`, por ejemplo `Compra 19 oct – 25 oct 2026`.
- `existingListField` con estilo de menú desplegable de material (`Widget.Material3.TextInputLayout.OutlinedBox.ExposedDropdownMenu` como padre de un nuevo `Widget.Ourmeal.TextInputLayout.Dropdown`) y un `MaterialAutoCompleteTextView` `actvExistingList` con `inputType="none"`. cada opción muestra el nombre de la lista y su número de productos. el orden es el de `ListActivity`: `createdAt` descendente.
- solo el campo de la opción seleccionada está visible.
- si no hay listas, `rbExistingList` está deshabilitado y su texto secundario dice `Aún no tienes listas.`.
- **decisión tomada**: el destino por defecto es siempre `Una lista nueva`. es la opción que nunca mezcla datos sin que el usuario lo decida.

### BARRA DE ACCION

- `tvShoppingSummary` (`TextAppearance.Ourmeal.Muted`, `accessibilityLiveRegion="polite"`) con el resumen calculado en vivo.
- `btnConfirmShopping` (`Widget.Ourmeal.Button.Primary`, `56dp`, `maxLines 2`) con el texto en plural `Añadir %d producto` o `Añadir %d productos`.
- el botón se deshabilita si no hay productos nuevos, si el nombre de la lista nueva está vacío, si el destino existente no está elegido o mientras se guarda.

### SALIR CON CAMBIOS

- la pantalla está «modificada» si algún ingrediente difiere de lo recordado o si el usuario cambió la inclusión de algún plato.
- con cambios, `btnBack` y el gesto de atrás (`onBackPressedDispatcher` con un `OnBackPressedCallback` habilitado solo en ese caso) abren un diálogo `ourmealDialog(destructive = true)` con título `¿Salir sin añadir?`, mensaje `Los ingredientes que has escrito no se guardarán.`, acción negativa `Seguir editando` y acción positiva `Salir`.
- sin cambios, se sale directamente.

### RESULTADO EN EL DETALLE DE LISTA

- **propuesto**: `ListDetailActivity` acepta los extras opcionales `EXTRA_ADDED_COUNT` y `EXTRA_SKIPPED_COUNT`.
- tras el primer `bindList()` correcto, si `savedInstanceState == null` y existe `EXTRA_ADDED_COUNT`, muestra un snackbar anclado a `actionBar`:
  - `Se añadieron 9 productos del menú.` si no se omitió nada.
  - `Se añadieron 9 productos del menú. 2 ya estaban en la lista.` si se omitieron.
  - `Todo lo del menú ya estaba en la lista.` si `added == 0`.
- después se eliminan los extras con `intent.removeExtra()` para que una recreación no repita el mensaje.

## DISEÑO VISUAL

- **estrategia de color**: contenida, como el resto del producto. `brand` solo en la acción principal y en el foco; `sage` para la etiqueta de ingredientes recordados; superficies `surface` y `surface_raised`.
- **escena**: una persona en el sofá o en la cocina, de noche, con el móvil en una mano, revisando el menú antes de la compra del fin de semana. confirma el tema oscuro existente; no se introduce ningún tema nuevo.
- **referencias**: la propia `ListDetailActivity` (cabecera, barra fija inferior), la tira de días de `view_week_menu.xml` (inflado de piezas acotadas) y los chips de entrada de material 3 tal como se usan en aplicaciones de notas.
- **nuevos estilos** en `res/values/themes.xml`:
  - `Widget.Ourmeal.Chip.Ingredient` con padre `Widget.Material3.Chip.Input`: `chipBackgroundColor @color/surface_raised`, texto `@color/ink` con `source_sans_3` a `16sp`, `chipStrokeColor @color/input_border`, `chipStrokeWidth 1dp`, `closeIconTint @color/muted`, `ensureMinTouchTargetSize true`.
  - `Widget.Ourmeal.TextInputLayout.Dropdown` con padre `Widget.Material3.TextInputLayout.OutlinedBox.ExposedDropdownMenu` y los mismos colores de ayuda, contador y error que `Widget.Ourmeal.TextInputLayout`.
- **contraste calculado** con los colores de `colors.xml`: `ink` sobre `surface_raised` 13,58:1; `muted` sobre `surface` 8,09:1; `muted` sobre `surface_raised` 7,15:1; `sage` sobre `surface` 10,79:1; `canvas` sobre `brand` 8,25:1; `input_border` sobre `surface` 4,54:1. todos superan los mínimos de wcag 2.2 aa. el borde del chip usa `input_border` y no `outline` (1,95:1), para que el contorno del componente cumpla el 3:1 de contraste no textual.
- **movimiento**: aparición de la pantalla con `reveal()`; sin animaciones nuevas. las transiciones de `ChipGroup` usan las de material, que respetan la configuración de animaciones del sistema.
- **iconos**: se reutilizan `ic_basket`, `ic_plus`, `ic_arrow_back` y `ic_alert`. el icono de cierre del chip es el de material. no se añaden dibujos nuevos.
- **prohibido**: tarjetas anidadas, bordes laterales de color, modales para añadir ingredientes y reducir opacidad de textos.

## ARQUITECTURA TECNICA

### VISTA GENERAL

```text
MainActivity ──(EXTRA_WEEK_ID)──▶ MenuShoppingActivity ──(EXTRA_LIST_ID, EXTRA_ADDED_COUNT, EXTRA_SKIPPED_COUNT)──▶ ListDetailActivity
                                       │
             ┌─────────────────────────┼──────────────────────────┐
             ▼                         ▼                          ▼
     MealDataManager            DishMemoryManager             DataManager
     meal_plans.json            dish_ingredients.json         user_lists.json
     (solo lectura)             (nuevo, lectura y escritura)  (lectura y fusión atómica)
             │                         │                          │
             └──────────── model/: plannedDishes(), DishMemory, mergeItems(), matchKey() ─┘
```

la lógica de negocio vive en funciones puras del paquete `model/` y se prueba en jvm. los gestores de `controller/` solo leen y escriben ficheros bajo su bloqueo. la actividad coordina carga, estado de borrador y navegación, igual que las actividades existentes.

### PIEZAS COMPARTIDAS

estas utilidades también las necesita `.docs/new-features/historial-de-platos.md`. **regla de coordinación**: si ya existen cuando se implemente esta funcionalidad, se reutilizan sin cambiar su contrato; si no existen, se crean exactamente así, con sus pruebas.

`android-app/app/src/main/java/ourmeal/app/model/TextNormalizer.kt` (añadir a la función existente `normalizeName()`):

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

**existente** desde el 2026-10-22: `WeekUtils.isValidWeekId(weekId)` en `android-app/app/src/main/java/ourmeal/app/model/WeekUtils.kt`, con pruebas en `WeekUtilsTest.kt`. valida con `rangeRefinedBy()`, porque `LocalDate.with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, 53)` no falla en un año de 52 semanas, y `startOfWeek()` la usa para devolver el lunes actual ante cualquier id inválido. no hay que crearla.

`matchKey()` necesita los imports `java.text.Normalizer` y `java.util.Locale`.

comportamiento exigido de `matchKey()`: `"  Albóndigas  en salsa "` → `"albondigas en salsa"`; `"PIÑA"` → `"piña"`; `"Crème"` → `"creme"`; `"Pingüino"` → `"pinguino"`; `""` → `""`.

**decisión tomada**: la comparación ignora mayúsculas, acentos y espacios repetidos, pero conserva la `ñ`, porque en castellano cambia la palabra («año» y «ano»). es más tolerante que la comparación actual de `ListDetailActivity.addItem()`, que solo ignora mayúsculas; el mantenedor confirmó el 2026-10-22 que `addItem()` debe pasar a usar `matchKey()`.

### MODELO

ficheros nuevos en `android-app/app/src/main/java/ourmeal/app/model/`:

`MenuDish.kt`:

```kotlin
data class DishOccurrence(val day: String, val slot: MealSlot)

data class MenuDish(
  val key: String,
  val name: String,
  val occurrences: List<DishOccurrence>
)

/** Planned dishes grouped by matchKey, in week order (day, then lunch before dinner). */
fun WeeklyMeals.plannedDishes(): List<MenuDish>
```

reglas de `plannedDishes()`:

- recorre `WeeklyMeals.DAYS` en orden y, para cada día, `MealSlot.COMIDA` y después `MealSlot.CENA`.
- descarta textos vacíos o solo con espacios.
- la clave es `matchKey(texto)`; el nombre visible es `normalizeName(texto)` de la **primera** aparición.
- devuelve como máximo 14 platos, uno por hueco de la semana.

`DishMemory.kt`:

```kotlin
data class DishIngredients(
  @SerializedName("key") val key: String,
  @SerializedName("name") val name: String,
  @SerializedName("ingredients") val ingredients: List<String>,
  @SerializedName("updated_at") val updatedAt: Long
)

data class DishMemory(
  @SerializedName("version") val version: Int = DISH_MEMORY_VERSION,
  @SerializedName("dishes") val dishes: List<DishIngredients> = emptyList()
) {
  /** Replaces or removes the given dishes and keeps only the most recent [cap] entries. */
  fun upsert(changes: List<DishIngredients>, cap: Int = DISH_MEMORY_CAP): DishMemory
  fun ingredientsFor(key: String): List<String>?
}

const val DISH_MEMORY_VERSION = 1
const val DISH_MEMORY_CAP = 300
const val DISH_INGREDIENT_CAP = 30
```

- un cambio con `ingredients` vacío **elimina** la entrada de ese plato.
- `upsert()` ordena por `updatedAt` descendente y recorta a `cap`. es una caché acotada para que el fichero no crezca sin límite.
- **riesgo conocido de gson**: `gson` crea los objetos sin pasar por el constructor de kotlin, así que un campo ausente en el json llega como `null` aunque el tipo no sea anulable y aunque tenga valor por defecto. `DishMemoryManager` debe sanear el resultado leído: `dishes` nulo pasa a lista vacía y se descartan las entradas con `key`, `name` o `ingredients` nulos.

`ShoppingDraft.kt` (estado de la pantalla, puro y serializable):

```kotlin
data class DishDraft(
  val key: String,
  val name: String,
  val occurrences: List<DishOccurrence>,
  val included: Boolean,
  val ingredients: List<String>,
  val remembered: List<String>?   // null when the dish had no memory entry
) {
  val isChanged: Boolean get() = ingredients != (remembered ?: emptyList())
}

/** Ingredients to add, in dish order and de-duplicated by matchKey. */
fun List<DishDraft>.ingredientsToAdd(): List<String>

/** Dish entries to write to memory: only dishes whose ingredients changed. */
fun List<DishDraft>.memoryChanges(now: Long): List<DishIngredients>
```

- `memoryChanges()` incluye también los platos excluidos que se editaron: lo escrito es conocimiento del usuario sobre ese plato, aunque esta semana no se compre.
- **decisión tomada**: los ingredientes se guardan como lista ordenada de nombres. el campo `version` permite migrar en el futuro a objetos con cantidad sin romper ficheros existentes.

`ShoppingMerge.kt`:

```kotlin
data class MergeResult(val list: UserList, val added: Int, val skipped: Int)

/** Returns a copy of the list with the new names on top, skipping matchKey duplicates. */
fun UserList.withMergedItems(names: List<String>, now: Long): MergeResult
```

- no muta la lista recibida; devuelve una copia profunda, igual que `UserList.snapshot()`.
- normaliza cada nombre con `normalizeName()`, descarta vacíos y recorta a 80 caracteres, el límite de `dialog_add_item.xml`.
- omite nombres cuya clave ya exista en la lista o que se repitan dentro de la entrada.
- inserta los nuevos `ListItem` al principio, conservando el orden recibido, con `isCompleted = false`, `createdAt = now` y `updatedAt = now`.
- ids: secuencia desde `now` que salta los ids existentes, con la misma estrategia que `ListDetailActivity.nextItemId()`.
- actualiza `updatedAt` de la lista a `now` solo si `added > 0`.

### PERSISTENCIA

**propuesto**: `android-app/app/src/main/java/ourmeal/app/controller/DishMemoryManager.kt`, con el patrón exacto de `MealDataManager`:

```kotlin
class DishMemoryManager(context: Context) {
  private val file = AtomicFile(File(context.filesDir, "dish_ingredients.json"))

  /** Empty memory when the file does not exist; IOException when it cannot be read. */
  fun load(): DishMemory

  /** Read-modify-write under FILE_LOCK. False when reading or writing fails. */
  fun save(changes: List<DishIngredients>): Boolean

  private companion object {
    val FILE_LOCK = Any()
  }
}
```

formato del fichero:

```json
{
  "version": 1,
  "dishes": [
    {
      "key": "lentejas",
      "name": "Lentejas",
      "ingredients": ["Lentejas", "Chorizo", "Zanahoria"],
      "updated_at": 1792000000000
    }
  ]
}
```

**propuesto**: ampliar `DataManager` con dos operaciones atómicas bajo `FILE_LOCK`, que releen el fichero para fusionar contra los datos actuales y no contra una copia antigua de la pantalla:

```kotlin
sealed interface MergeOutcome {
  data class Saved(val listId: Long, val added: Int, val skipped: Int) : MergeOutcome
  data object ListMissing : MergeOutcome
  data object Failed : MergeOutcome
}

fun mergeIntoList(listId: Long, names: List<String>): MergeOutcome
fun createListWithItems(name: String, names: List<String>): MergeOutcome
```

- `mergeIntoList()`: `loadLists()` (si lanza `IOException`, `Failed`), busca la lista (si no existe, `ListMissing`), aplica `withMergedItems()` y guarda con `saveLists()` (si falla, `Failed`).
- `createListWithItems()`: crea `UserList` con id único bajo el bloqueo (misma estrategia que `ListActivity.nextListId()`, pero contra los datos leídos), aplica `withMergedItems()` sobre la lista vacía y guarda la nueva lista al principio.
- **decisión tomada**: no se modifica el esquema de `UserList`, `ListItem`, `AppData` ni `WeeklyMeals`. no hay migración de datos existentes. la única novedad en disco es `dish_ingredients.json`.
- **decisión tomada**: no se rellena `ListItem.notes` (existe en el modelo, pero ninguna pantalla lo muestra). anotar «para lentejas» en cada producto queda como posible evolución.

### ORDEN DE ESCRITURA Y CONSISTENCIA

al confirmar, en `Dispatchers.IO`:

1. escribir la lista con `createListWithItems()` o `mergeIntoList()`.
2. solo si el paso 1 devuelve `Saved` y la memoria se leyó bien al cargar, escribir la memoria con `DishMemoryManager.save(drafts.memoryChanges(now))`.

**decisión tomada**: no hay transacción entre ficheros. la lista es el resultado que el usuario espera, así que se escribe primero. si falla la memoria, la compra ya está hecha y solo se pierde el recuerdo de los ingredientes: el resultado lo comunica sin bloquear (ver [gestión de errores](#gestion-de-errores)).

### ACTIVIDAD

**propuesto**: `android-app/app/src/main/java/ourmeal/app/MenuShoppingActivity.kt`, registrada en `AndroidManifest.xml` con `android:exported="false"` y `android:windowSoftInputMode="adjustResize"`, igual que `ListDetailActivity`.

responsabilidades:

- `onCreate()`: `enableEdgeToEdge()` con `SystemBarStyle.dark(Color.TRANSPARENT)`, `applyScreenInsets()`, instanciar los tres gestores, validar `EXTRA_WEEK_ID` con `WeekUtils.isValidWeekId()` y lanzar la carga.
- carga en paralelo en `lifecycleScope` con `async(Dispatchers.IO)` para menú, listas y memoria.
- construir `List<DishDraft>` a partir de `plannedDishes()` y `DishMemory.ingredientsFor()`.
- inflar una tarjeta `ItemMenuDishBinding` por plato dentro de `layoutDishes`, como hace `MainActivity.bindWeek()` con `DaySelectorChipBinding`.
- mantener el borrador como `List<DishDraft>` inmutable y reemplazarlo en cada cambio; refrescar solo la tarjeta afectada y el resumen.
- guardar y restaurar el borrador (ver [gestión de estados](#gestion-de-estados)).
- confirmar, navegar y gestionar el diálogo de salida.

**decisión tomada**: no se usa `RecyclerView` para los platos. el máximo es 14 tarjetas y cada una contiene un campo de texto editable; un `RecyclerView` reciclaría vistas con foco y texto a medio escribir, lo que complica el estado sin beneficio de rendimiento. `MainActivity` ya infla piezas acotadas de la misma forma.

**decisión tomada**: no se introduce `ViewModel`. ninguna pantalla lo usa y el estado de esta cabe en un `Bundle`. introducirlo crearía un segundo patrón de estado en una aplicación de tres pantallas.

### FICHEROS AFECTADOS

| ruta | cambio |
|---|---|
| `android-app/app/src/main/java/ourmeal/app/model/TextNormalizer.kt` | añadir `matchKey()` si no existe |
| `android-app/app/src/main/java/ourmeal/app/model/MealSlot.kt` | nuevo si no existe |
| `android-app/app/src/main/java/ourmeal/app/model/MenuDish.kt` | nuevo |
| `android-app/app/src/main/java/ourmeal/app/model/DishMemory.kt` | nuevo |
| `android-app/app/src/main/java/ourmeal/app/model/ShoppingDraft.kt` | nuevo |
| `android-app/app/src/main/java/ourmeal/app/model/ShoppingMerge.kt` | nuevo |
| `android-app/app/src/main/java/ourmeal/app/controller/DishMemoryManager.kt` | nuevo |
| `android-app/app/src/main/java/ourmeal/app/controller/DataManager.kt` | añadir `MergeOutcome`, `mergeIntoList()` y `createListWithItems()` |
| `android-app/app/src/main/java/ourmeal/app/MenuShoppingActivity.kt` | nuevo |
| `android-app/app/src/main/java/ourmeal/app/MainActivity.kt` | botón de entrada, habilitado y navegación |
| `android-app/app/src/main/java/ourmeal/app/ListDetailActivity.kt` | extras de resultado y snackbar; usar `matchKey()` en la detección de duplicados de `addItem()` |
| `android-app/app/src/main/AndroidManifest.xml` | registrar `ourmeal.app.MenuShoppingActivity` |
| `android-app/app/src/main/res/layout/activity_menu_shopping.xml` | nuevo |
| `android-app/app/src/main/res/layout-land/activity_menu_shopping.xml` | nuevo |
| `android-app/app/src/main/res/layout/item_menu_dish.xml` | nuevo |
| `android-app/app/src/main/res/layout/activity_main.xml` y `layout-land/activity_main.xml` | `btnPrepareShopping` y `tvPrepareShoppingHint` |
| `android-app/app/src/main/res/values/themes.xml` | `Widget.Ourmeal.Chip.Ingredient` y `Widget.Ourmeal.TextInputLayout.Dropdown` |
| `android-app/app/src/main/res/values/strings.xml` | textos de la sección [textos](#textos) |
| `android-app/app/src/test/java/ourmeal/app/` | pruebas nuevas |
| `android-app/app/src/androidTest/java/ourmeal/app/MenuShoppingTest.kt` | nuevo |
| `changelog.md` | entradas bilingües en `SIN PUBLICAR / UNRELEASED` |
| `.docs/info-project/informacion-general.md` y `.docs/info-project/informacion-ayuda.md` | describir la nueva función como disponible y retirar la nota de mejora pendiente |
| `claude.md` y `agents.md` | añadir la preparación de la compra a la lista de capacidades de `CONTEXTO DEL PROYECTO` |

**decisión tomada** sobre `ListDetailActivity.addItem()`: usar `matchKey()` en lugar de `equals(ignoreCase = true)` para que añadir a mano y añadir desde el menú detecten los mismos duplicados. es un cambio de comportamiento pequeño y deliberado: `Plátano` y `platano` pasan a considerarse el mismo producto.

## CONTRATOS E INTERFACES

### EXTRAS DE NAVEGACION

| emisor | receptor | extra | tipo | obligatorio | validación |
|---|---|---|---|---|---|
| `MainActivity` | `MenuShoppingActivity` | `MenuShoppingActivity.EXTRA_WEEK_ID` = `"WEEK_ID"` | `String` | sí | `WeekUtils.isValidWeekId()`; si falla, estado de error sin reintento |
| `MenuShoppingActivity` | `ListDetailActivity` | `ListDetailActivity.EXTRA_LIST_ID` = `"LIST_ID"` (existente) | `Long` | sí | comportamiento actual |
| `MenuShoppingActivity` | `ListDetailActivity` | `ListDetailActivity.EXTRA_ADDED_COUNT` = `"ADDED_COUNT"` | `Int` | no | `>= 0`; si falta, no hay snackbar |
| `MenuShoppingActivity` | `ListDetailActivity` | `ListDetailActivity.EXTRA_SKIPPED_COUNT` = `"SKIPPED_COUNT"` | `Int` | no | `>= 0`; por defecto `0` |
| `MenuShoppingActivity` | `ListDetailActivity` | `ListDetailActivity.EXTRA_MEMORY_FAILED` = `"MEMORY_FAILED"` | `Boolean` | no | por defecto `false` |

### ESTADO GUARDADO

| clave | contenido |
|---|---|
| `"shopping_draft"` | `List<DishDraft>` serializada con `gson` |
| `"shopping_destination"` | `"new"` o `"existing"` |
| `"shopping_new_name"` | texto actual del nombre de la lista nueva |
| `"shopping_existing_id"` | id de la lista elegida o ausente |

el tamaño máximo esperado es 14 platos × 30 ingredientes × 80 caracteres, unos 34 kb en el peor caso, dentro del margen razonable de un `Bundle`.

### TEXTOS

nuevas cadenas en `res/values/strings.xml`, en un bloque `<!-- Preparar la compra -->`. el texto es literal y se escribe tal cual:

```xml
<string name="prepare_shopping">Preparar la compra</string>
<string name="prepare_shopping_hint">Planifica algún plato para preparar la compra.</string>
<string name="menu_shopping_kicker">Del menú a la compra</string>
<string name="menu_shopping_title">Prepara la compra</string>
<string name="menu_shopping_week">Semana del %1$s</string>
<string name="menu_shopping_loading">Reuniendo los platos de la semana…</string>
<string name="menu_shopping_dishes_title">Platos</string>
<string name="menu_shopping_occurrence">%1$s, %2$s</string>
<string name="menu_shopping_occurrence_more">y %1$d más</string>
<string name="menu_shopping_excluded">No se incluirá</string>
<string name="menu_shopping_remembered">Ingredientes recordados</string>
<string name="menu_shopping_no_ingredients">Todavía sin ingredientes.</string>
<string name="menu_shopping_ingredient_hint">Añadir ingrediente</string>
<string name="menu_shopping_ingredient_add">Añadir ingrediente a %1$s</string>
<string name="menu_shopping_ingredient_remove">Quitar %1$s</string>
<string name="menu_shopping_ingredient_required">Escribe el nombre del ingrediente</string>
<string name="menu_shopping_ingredient_exists">Ya está en este plato</string>
<string name="menu_shopping_ingredient_limit">Como máximo 30 ingredientes por plato</string>
<string name="menu_shopping_include">Incluir %1$s en la compra</string>
<string name="menu_shopping_destination_title">Añadir a</string>
<string name="menu_shopping_new_list">Una lista nueva</string>
<string name="menu_shopping_existing_list">Una de mis listas</string>
<string name="menu_shopping_no_lists">Aún no tienes listas.</string>
<string name="menu_shopping_pick_list">Elige una lista</string>
<string name="menu_shopping_default_list_name">Compra %1$s</string>
<string name="menu_shopping_nothing_new">No hay productos nuevos que añadir.</string>
<string name="menu_shopping_add_some">Añade ingredientes a algún plato para preparar la compra.</string>
<string name="menu_shopping_memory_unavailable">No pudimos recuperar los ingredientes recordados. Puedes escribirlos igualmente.</string>
<string name="menu_shopping_empty_title">Esta semana aún no tiene platos.</string>
<string name="menu_shopping_empty_body">Planifica alguna comida o cena y vuelve para preparar la compra.</string>
<string name="menu_shopping_error_title">No pudimos reunir tu compra.</string>
<string name="menu_shopping_error_body">Tus datos siguen guardados. Vuelve a intentarlo.</string>
<string name="menu_shopping_invalid_week">No pudimos abrir esa semana.</string>
<string name="menu_shopping_save_error">No se pudo guardar la compra. Tus cambios siguen aquí.</string>
<string name="menu_shopping_list_missing">Esa lista ya no existe. Elige otra o crea una nueva.</string>
<string name="menu_shopping_discard_title">¿Salir sin añadir?</string>
<string name="menu_shopping_discard_message">Los ingredientes que has escrito no se guardarán.</string>
<string name="menu_shopping_keep_editing">Seguir editando</string>
<string name="menu_shopping_discard">Salir</string>
<string name="menu_shopping_all_present">Todo lo del menú ya estaba en la lista.</string>
<string name="menu_shopping_memory_failed">No se pudieron recordar los ingredientes para la próxima vez.</string>
<plurals name="menu_shopping_dish_count">
  <item quantity="one">%d plato</item>
  <item quantity="other">%d platos</item>
</plurals>
<plurals name="menu_shopping_confirm">
  <item quantity="one">Añadir %d producto</item>
  <item quantity="other">Añadir %d productos</item>
</plurals>
<plurals name="menu_shopping_summary_new">
  <item quantity="one">%d producto nuevo</item>
  <item quantity="other">%d productos nuevos</item>
</plurals>
<plurals name="menu_shopping_summary_skipped">
  <item quantity="one">%d ya estaba en la lista</item>
  <item quantity="other">%d ya estaban en la lista</item>
</plurals>
<plurals name="menu_shopping_added">
  <item quantity="one">Se añadió %d producto del menú.</item>
  <item quantity="other">Se añadieron %d productos del menú.</item>
</plurals>
```

se reutilizan las cadenas existentes `back_to_menu`, `retry`, `cancel`, `adding` y `list_name_label`. los nombres de los días salen de `WeeklyMeals.DAYS` y las franjas de `day_lunch` y `day_dinner` en minúscula para la ocurrencia.

## GESTION DE ESTADOS

### ESTADOS DE PANTALLA

| estado | condición | qué se ve | acciones |
|---|---|---|---|
| cargando | lecturas en curso | `view_loading` con `menu_shopping_loading`; cabecera visible; barra de acción oculta | atrás |
| semana inválida | `EXTRA_WEEK_ID` ausente o inválido | `view_state_panel` con `ic_alert`, `menu_shopping_invalid_week` y acción `back_to_menu` | volver |
| error de lectura | `getWeeklyMeals()` o `loadLists()` lanzan `IOException` | `view_state_panel` con `menu_shopping_error_title`, `menu_shopping_error_body` y `retry` | reintentar, volver |
| vacío | `plannedDishes()` vacío | `view_state_panel` con `ic_basket`, `menu_shopping_empty_title`, `menu_shopping_empty_body` y acción `back_to_menu` | volver |
| listo | datos cargados | tarjetas, destino y barra de acción con `reveal()` | editar, confirmar |
| listo sin memoria | `DishMemoryManager.load()` lanza `IOException` | igual que listo, con un aviso `Meta` encima de las tarjetas (`menu_shopping_memory_unavailable`) | igual que listo; la memoria no se escribe en esta sesión |
| guardando | confirmación en curso | botón con `adding` (`Añadiendo…`), todo deshabilitado, sin diálogo de salida | ninguna |
| éxito | `MergeOutcome.Saved` | navegación a `ListDetailActivity` y cierre de la pantalla | ninguna |

### ESTADOS DEL BOTON PRINCIPAL

| condición | habilitado | texto | resumen |
|---|---|---|---|
| ningún plato incluido tiene ingredientes | no | `Añadir 0 productos` | `menu_shopping_add_some` |
| todos los ingredientes ya están en la lista elegida | no | `Añadir 0 productos` | `menu_shopping_nothing_new` |
| hay nuevos y algunos se omiten | sí | `Añadir 9 productos` | `9 productos nuevos · 2 ya estaban en la lista` |
| lista nueva con nombre vacío | no | `Añadir N productos` | error `list_name_required` en el campo |
| lista existente sin elegir | no | `Añadir N productos` | `menu_shopping_pick_list` |
| guardando | no | `Añadiendo…` | sin cambios |

### CONSERVACION DEL BORRADOR

- `onSaveInstanceState()` guarda las claves de [estado guardado](#estado-guardado).
- tras una recreación, la pantalla vuelve a leer los ficheros y **fusiona** el borrador guardado con los platos leídos por `key`: conserva inclusión e ingredientes de los platos que siguen existiendo y descarta los que ya no están en el menú.
- si la lista existente guardada ya no existe, se selecciona `Una lista nueva`.
- el texto a medio escribir en `etIngredient` no se conserva. **decisión tomada**: solo se conservan ingredientes confirmados como chip, para no complicar el estado por un caso marginal.

## GESTION DE ERRORES

| situación | detección | comportamiento |
|---|---|---|
| fallo al guardar la semana antes de abrir | el callback de `saveCurrentWeek()` recibe `false` | no se navega; snackbar existente `save_error` con `retry` |
| fichero de menú o listas dañado | `IOException` en la carga | estado de error con reintento; no se escribe nada |
| fichero de memoria dañado | `IOException` en `DishMemoryManager.load()` | modo sin memoria; **no se sobrescribe** el fichero dañado, para no destruir datos recuperables |
| ingrediente vacío | `normalizeName()` vacío | error `menu_shopping_ingredient_required` en `ingredientField` |
| ingrediente repetido en el plato | misma `matchKey()` | error `menu_shopping_ingredient_exists`; no se añade |
| más de 30 ingredientes | tamaño de la lista | error `menu_shopping_ingredient_limit`; campo deshabilitado hasta quitar alguno |
| nombre de lista vacío | `normalizeName()` vacío | error `list_name_required` (existente) y botón deshabilitado |
| lista elegida eliminada entre la carga y la confirmación | `MergeOutcome.ListMissing` | se recargan las listas, se selecciona `Una lista nueva` y snackbar `menu_shopping_list_missing`; el borrador se conserva |
| fallo de escritura de la lista (disco lleno, e/s) | `MergeOutcome.Failed` | snackbar `menu_shopping_save_error` con `retry`; borrador intacto |
| fallo de escritura de la memoria tras guardar la lista | `save()` devuelve `false` | se navega igualmente con `EXTRA_MEMORY_FAILED = true`; el detalle añade `menu_shopping_memory_failed` al snackbar de resultado |
| la actividad se destruye durante el guardado | `isFinishing || isDestroyed` tras `withContext` | se ignora el resultado, como en `ListDetailActivity`; `AtomicFile` garantiza que el fichero queda en su versión anterior o en la nueva |
| muerte del proceso durante el guardado | `AtomicFile` | el fichero queda completo en una de las dos versiones; al volver, el usuario ve la lista con o sin los productos, nunca un json a medias |

### CASOS LIMITE

- el mismo plato escrito de formas distintas (`Lentejas`, `lentejas `, `LENTEJAS`) produce una sola tarjeta con todas sus ocurrencias.
- un plato multilínea se muestra en una línea: `normalizeName()` convierte saltos de línea en espacios.
- un plato que ocupa los 14 huecos muestra `Lunes, comida · Lunes, cena · Martes, comida · y 11 más`.
- un mismo ingrediente en dos platos («aceite» en lentejas y tortilla) se añade una sola vez.
- una lista de destino con cientos de productos: la fusión usa un conjunto de claves y es lineal.
- un nombre de lista igual al de otra lista existente: está permitido, como en `ListActivity`.
- una semana pasada o futura: el recorrido funciona igual; el nombre propuesto usa el rango de esa semana.
- tamaño de fuente al 200 %: todas las cadenas pueden ocupar varias líneas; la barra inferior admite dos líneas en el botón.

## SEGURIDAD, PRIVACIDAD Y RENDIMIENTO

- no se añaden permisos, red, servicios externos ni dependencias. `ChipGroup`, `Chip`, `MaterialRadioButton` y `MaterialAutoCompleteTextView` pertenecen a `com.google.android.material:material`, ya incluida.
- todos los datos siguen en `context.filesDir`, privados de la aplicación.
- la copia de seguridad automática está activa (`android:allowBackup="true"`) con reglas vacías en `res/xml/backup_rules.xml` y `res/xml/data_extraction_rules.xml`; `dish_ingredients.json` se incluirá igual que los ficheros actuales. no hace falta cambiar las reglas.
- toda entrada se normaliza con `normalizeName()` y se limita en longitud (80 caracteres) y número (30 ingredientes por plato, 300 platos en memoria). los textos se muestran en `TextView` y `Chip`, sin html.
- no se registran ingredientes ni nombres en `Log`. los mensajes de error de `Log.e` siguen el formato existente, sin contenido del usuario.
- lecturas y escrituras en `Dispatchers.IO`. el tamaño de los datos es de kilobytes y la carga paralela evita esperas encadenadas.
- se infla un máximo de 14 tarjetas. al cambiar un ingrediente solo se actualizan la tarjeta afectada y el resumen.
- **existente**: `MainActivity.saveCurrentWeek()` escribe una copia de la semana en `Dispatchers.IO`, en orden de llamada (`Mutex`) y sin cancelarse aunque se destruya la pantalla (`NonCancellable`). el punto de entrada usa su callback y no bloquea el hilo principal.

## ESCALABILIDAD Y EVOLUCION

- `DishMemory.version` permite migrar a ingredientes con cantidad (`{"name": "Tomates", "quantity": "500 g"}`) leyendo la versión 1 como nombres sin cantidad.
- `MergeOutcome` y `withMergedItems()` sirven para futuras fuentes de productos (por ejemplo, importar un texto compartido) sin tocar la persistencia.
- la memoria por plato puede alimentar en el futuro una vista de «mis platos» para editar ingredientes fuera del recorrido semanal, sin cambiar el formato.
- `ListItem.notes` queda disponible para anotar el plato de origen cuando exista una interfaz que lo muestre.
- si `user_lists.json` creciera mucho, el cuello de botella sería la lectura completa del fichero, un límite actual de toda la aplicación y no de esta funcionalidad.

## PLAN DE IMPLEMENTACION

### FASE 1: MODELO Y LOGICA PURA

tareas:

1. crear o reutilizar `matchKey()`, `MealSlot` y `Meal.textFor()`, y reutilizar `WeekUtils.isValidWeekId()`, según [piezas compartidas](#piezas-compartidas).
2. crear `MenuDish.kt`, `DishMemory.kt`, `ShoppingDraft.kt` y `ShoppingMerge.kt`.
3. escribir sus pruebas unitarias.

criterio de finalización: `./gradlew testDebugUnitTest` en verde con las pruebas nuevas; sin cambios de interfaz todavía.

### FASE 2: PERSISTENCIA

dependencias: fase 1.

tareas:

1. crear `DishMemoryManager` con saneado de nulos de `gson`.
2. añadir `MergeOutcome`, `mergeIntoList()` y `createListWithItems()` a `DataManager`.
3. cambiar la detección de duplicados de `ListDetailActivity.addItem()` a `matchKey()`.

criterio de finalización: prueba instrumentada de persistencia en verde (ver [pruebas y validación](#pruebas-y-validacion)); los flujos actuales de listas siguen funcionando.

### FASE 3: PANTALLA

dependencias: fase 2.

tareas:

1. crear `item_menu_dish.xml`, `activity_menu_shopping.xml` y su variante `layout-land`.
2. añadir estilos y cadenas.
3. crear `MenuShoppingActivity` con carga, estados, edición de ingredientes, destino, resumen, confirmación, diálogo de salida y conservación del borrador.
4. registrar la actividad en `AndroidManifest.xml`.

criterio de finalización: la pantalla funciona abierta directamente con un `EXTRA_WEEK_ID` válido, en vertical y horizontal, con todos los estados de [gestión de estados](#gestion-de-estados).

### FASE 4: INTEGRACION

dependencias: fase 3.

tareas:

1. añadir `btnPrepareShopping` y `tvPrepareShoppingHint` a ambos `activity_main.xml` y su lógica en `MainActivity`.
2. añadir los extras de resultado y el snackbar a `ListDetailActivity`.

criterio de finalización: recorrido completo menú → preparación → detalle en un emulador api 34 o superior, con datos nuevos y con memoria previa.

### FASE 5: CALIDAD Y DOCUMENTACION

dependencias: fase 4.

tareas:

1. revisión de accesibilidad con talkback, teclado físico, fuente al 200 % y animaciones desactivadas.
2. `./gradlew testDebugUnitTest lintDebug assembleDebug` y `./gradlew connectedDebugAndroidTest`.
3. añadir las entradas bilingües a `changelog.md` en `## SIN PUBLICAR / UNRELEASED`, categoría `NUEVO / ADDED` (y `CAMBIOS / CHANGED` para la detección de duplicados de `addItem()`).
4. actualizar `.docs/info-project/informacion-general.md` (función disponible, sin la nota de mejora pendiente) e `.docs/info-project/informacion-ayuda.md` (nuevo apartado de uso), y la lista de capacidades de `claude.md` y `agents.md`. la descripción debe ser exacta: la aplicación propone los ingredientes que el usuario indicó antes, no los deduce. ambos ficheros ya siguen las reglas de markdown y deben seguir cumpliéndolas.
5. revisar `git diff --check` y el diff completo.

criterio de finalización: todos los [criterios de aceptación](#criterios-de-aceptacion) cumplidos y sin avisos nuevos de lint o compilación.

## PRUEBAS Y VALIDACION

### PRUEBAS UNITARIAS

en `android-app/app/src/test/java/ourmeal/app/`, con junit 4 como las existentes:

| fichero | casos mínimos |
|---|---|
| `ModelTest.kt` (ampliar `TextNormalizerTest`) | los cinco ejemplos de `matchKey()` de [piezas compartidas](#piezas-compartidas) |
| `MenuDishTest.kt` | semana vacía sin platos; orden lunes comida → domingo cena; agrupación por mayúsculas, acentos y espacios; nombre de la primera aparición; huecos en blanco ignorados; texto multilínea normalizado |
| `ShoppingMergeTest.kt` | lista vacía; nombres repetidos en la entrada; omisión de existentes por `matchKey()`; orden al principio; ids únicos frente a ids existentes; la lista original no se modifica; `updatedAt` sin cambios si `added == 0`; recorte a 80 caracteres |
| `ShoppingDraftTest.kt` | platos excluidos no aportan ingredientes; ingrediente compartido entre platos una sola vez; `memoryChanges()` solo con platos modificados, incluidos los excluidos; vaciar ingredientes genera eliminación |
| `DishMemoryTest.kt` | `upsert()` reemplaza por clave, elimina con lista vacía y recorta a `cap` conservando los más recientes; ida y vuelta con `Gson`; json sin `dishes` o con entradas nulas se sanea sin excepción |

### PRUEBAS INSTRUMENTADAS

`android-app/app/src/androidTest/java/ourmeal/app/MenuShoppingTest.kt`:

- declarar `@get:Rule val appData = AppDataSandbox()`, como `CreationFormTest`. la regla, en `app/src/androidTest/java/ourmeal/app/AppDataSandbox.kt`, aparta los ficheros reales de `filesDir` antes de cada prueba y los restaura al terminar, así que cada prueba empieza sin datos y siembra los suyos con `MealDataManager` y `DataManager`.
- seguir el patrón de `CreationFormTest`: interacción con `performClick()`, `setText()` y `onEditorAction(EditorInfo.IME_ACTION_DONE)` dentro de `scenario.onActivity`, sin inyección de entrada de espresso, que el propio test documenta como incompatible con api 37.
- casos:
  1. semana con dos platos sin memoria → añadir ingredientes → lista nueva → `user_lists.json` contiene la lista con los productos en orden y `dish_ingredients.json` los recuerda.
  2. segunda apertura de la misma semana → tarjetas con `Ingredientes recordados` y chips precargados.
  3. lista existente con un producto coincidente por acento → resumen con un omitido → tras confirmar, ese producto no se duplica.
  4. plato desmarcado → sus ingredientes no se añaden.
  5. `scenario.recreate()` con ingredientes nuevos → el borrador se conserva.
  6. semana sin platos → estado vacío.
  7. `EXTRA_WEEK_ID` inválido → estado de semana inválida.
  8. `dish_ingredients.json` con contenido no json → modo sin memoria y el fichero no se modifica tras confirmar.

### VALIDACION MANUAL

| comprobación | cómo |
|---|---|
| vertical y horizontal | emulador api 34 y api 35, rotando en cada estado |
| tamaño de fuente | ajustes de accesibilidad al 130 % y al 200 % |
| talkback | orden de lectura cabecera → platos → destino → resumen → botón; casillas con `menu_shopping_include`; chips con `menu_shopping_ingredient_remove`; anuncio del resumen al cambiar |
| teclado físico | tabulación por casillas, campos, chips, opciones de destino y botón; foco visible en todos |
| animaciones desactivadas | ajustes de desarrollador con escalas a `0`; la pantalla aparece sin transiciones y sin estados intermedios |
| teclado en pantalla | el campo de ingrediente enfocado queda visible sobre el teclado; la barra inferior no tapa el campo |
| regresión | crear, abrir, marcar, eliminar, deshacer y compartir listas; editar el menú y cambiar de semana |

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

1. con una semana que tiene al menos un plato, `Preparar la compra` está visible y habilitado en el menú, en vertical y horizontal; con la semana vacía está deshabilitado y se muestra `Planifica algún plato para preparar la compra.`.
2. al pulsarlo, la semana se guarda antes de abrir la nueva pantalla; si el guardado falla, no se navega.
3. la pantalla agrupa los platos por `matchKey()` en orden semanal y muestra sus ocurrencias.
4. los platos con memoria aparecen con `Ingredientes recordados` y sus ingredientes precargados; los nuevos aparecen sin ingredientes.
5. se pueden añadir ingredientes de forma consecutiva sin perder el foco y quitarlos desde el chip; se rechazan vacíos, repetidos y más de 30.
6. excluir un plato oculta su cuerpo y elimina sus ingredientes del resumen.
7. el resumen y el botón reflejan en vivo los productos nuevos y los omitidos según el destino elegido.
8. confirmar con una lista nueva crea la lista con el nombre indicado y los productos nuevos al principio, sin duplicados.
9. confirmar con una lista existente añade solo los productos cuya `matchKey()` no está en la lista.
10. tras confirmar se abre el detalle de la lista de destino con el snackbar de resultado correcto, que no se repite al rotar; atrás vuelve al menú.
11. los ingredientes modificados quedan recordados para la siguiente vez, incluidos los de platos excluidos.
12. salir con cambios pide confirmación; salir sin cambios no.
13. el borrador sobrevive a la rotación y a la recreación de la actividad.
14. todos los estados y errores de [gestión de estados](#gestion-de-estados) y [gestión de errores](#gestion-de-errores) se comportan como se describe; un fichero de memoria dañado nunca se sobrescribe.
15. añadir un producto a mano en `ListDetailActivity` detecta duplicados con `matchKey()`.
16. todos los controles tienen nombre accesible, objetivo táctil de al menos `48dp`, foco visible y contraste aa; la pantalla funciona con fuente al 200 % y animaciones desactivadas.
17. `testDebugUnitTest`, `lintDebug`, `assembleDebug` y `connectedDebugAndroidTest` terminan sin errores, sin avisos nuevos y con las pruebas de [pruebas y validación](#pruebas-y-validacion) incluidas.
18. `changelog.md`, los documentos de `.docs/info-project/` afectados, `claude.md` y `agents.md` están actualizados.

## DECISIONES Y SUPUESTOS

| tipo | punto | propuesta por defecto |
|---|---|---|
| decisión tomada | detección de duplicados de `addItem()` | confirmado el 2026-10-22: usa `matchKey()`, igual que la fusión desde el menú |
| decisión tomada | formato de `.docs/info-project/` | resuelto el 2026-10-22: los tres ficheros se reformatearon por completo según las reglas y describen el comportamiento real |
| decisión tomada | dirección de diseño (pantalla nueva, chips de ingredientes, destino por defecto `Una lista nueva`, apertura del detalle al confirmar) | confirmada por el mantenedor el 2026-10-22 en la ronda de validación de `impeccable shape` |
| supuesto | `Widget.Material3.TextInputLayout.OutlinedBox.ExposedDropdownMenu` y `Widget.Material3.Chip.Input` se integran con el tema oscuro sin ajustes extra | verificar en dispositivo durante la fase 3 |

## INCIDENCIAS PREEXISTENTES DETECTADAS

fuera del alcance de esta funcionalidad; se documentan sin corregirlas. el 2026-10-22 se corrigieron las incidencias de `WeekUtils.startOfWeek()`, el guardado del menú en el hilo principal, el aislamiento de `CreationFormTest` y la documentación desactualizada de `claude.md`, `agents.md` y `.docs/info-project/`.

- `AppData.version` y `ListItem.notes` existen en el modelo, pero no se usan; no hay mecanismo de migración de esquema.
- `readme.md` enlaza `./LICENSE`, pero el fichero es `license.md`.
- `res/xml/backup_rules.xml` y `res/xml/data_extraction_rules.xml` conservan las plantillas por defecto.
- el árbol de trabajo tenía, al hacer el análisis, cambios de permisos (`100644` → `100755`) en `.docs/`, `license.md` y `readme.md` sin cambios de contenido.
