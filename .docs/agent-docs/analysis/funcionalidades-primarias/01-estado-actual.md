# OURMEAL | ESTADO ACTUAL

> inventario de las funciones primarias que ya existen, de los datos y piezas que se pueden reutilizar, de las restricciones que heredan las nuevas funcionalidades y de los huecos que dejan. se verificó en el código de `android-app/` y en `.docs/new-features/` el 2026-11-23.

## FUNCIONES PRIMARIAS EXISTENTES

| función | pantalla o clase | dato que escribe | dato que lee |
|---|---|---|---|
| menú semanal de comida y cena | `MainActivity`, `WeeklyMeals` | `meal_plans.json` | `meal_plans.json` |
| sugerencias de platos | `MealSuggestions.kt`, `DishHistory` | `dish_suggestions.json` (platos ocultos) | `meal_plans.json`, `dish_suggestions.json` |
| aviso de repeticiones | `repeatHint()` en `DishHistory.kt` | ninguno | `meal_plans.json` |
| listas de la compra | `ListActivity`, `DataManager` | `user_lists.json` | `user_lists.json` |
| productos de una lista | `ListDetailActivity`, `ItemAdapter` | `user_lists.json` | `user_lists.json` |
| preparar la compra desde el menú | `MenuShoppingActivity`, `ShoppingDraft`, `ShoppingMerge` | `dish_ingredients.json`, `user_lists.json` | `meal_plans.json`, `dish_ingredients.json` |
| recorrido de compra aprendido | `ShoppingRoute`, `ShoppingRouteManager` | `shopping_route.json` | `shopping_route.json` |
| compartir una lista como texto | `shareList()` en `ListDetailActivity.kt` | ninguno | `user_lists.json` |

el flujo real del usuario es un ciclo: planificar el menú, preparar la compra, comprar siguiendo el recorrido y volver a planificar con las sugerencias del historial.

## PIEZAS REUTILIZABLES

- **comparación de texto:** `normalizeName()` y `matchKey()` en `model/TextNormalizer.kt` comparan sin mayúsculas ni acentos y conservan la `ñ`. toda funcionalidad que reconozca nombres debe usarlas.
- **fusión sin duplicados:** `UserList.withMergedItems()` y `DataManager.mergeIntoList()` / `createListWithItems()`. el propio código indica que sirven para otras fuentes de productos, por ejemplo importar un texto compartido.
- **historial de platos:** `toDishRecords()` y `DishHistory` ya recorren todas las semanas guardadas en una sola carga.
- **memoria de ingredientes:** `DishMemory` incluye un campo `version` pensado para migrar a objetos con cantidad sin romper ficheros existentes.
- **recorrido:** `ShoppingRoute` ya distingue viajes cerrados de viajes abiertos y descarta los marcados hechos en casa.
- **patrón de sugerencias:** una tira de chips en línea, con pulsación larga para ocultar y deshacer, como en `MealSuggestions.kt`. se eligió en lugar de un desplegable porque este se recorta con el teclado abierto.
- **interfaz compartida:** `OurmealDialogs.kt` (formularios con validación y envío único), `ScreenUi.kt` (carga, vacío y error), `UiFeedback.kt` (snackbars), `UiMotion.kt` y `ScreenInsets.kt`.
- **persistencia segura:** `AtomicFile` bajo un bloqueo de proceso compartido, sin sobrescribir nunca un fichero dañado y con `sanitized()` en cada modelo que lee de disco.
- **prueba de datos:** la regla `AppDataSandbox` aparta los datos reales del dispositivo durante cada prueba instrumentada.

## RESTRICCIONES HEREDADAS

| restricción | origen | efecto sobre las propuestas |
|---|---|---|
| la aplicación no declara permisos y nada sale del dispositivo | `agents.md`, `AndroidManifest.xml` | una propuesta que pida un permiso lo declara y actualiza la documentación de privacidad |
| no se generan menús automáticos ni se rellenan semanas | no objetivos de `historial-de-platos.md` | ninguna propuesta escribe platos por el usuario |
| no se añade una pantalla de gestión del historial | no objetivos de `historial-de-platos.md` | `m5` revisa esta decisión y lo indica |
| no se calculan cantidades ni se suman unidades; no hay recetario con pasos o raciones | no objetivos de `compra-desde-el-menu.md` | `a2` y `a3` revisan estas decisiones y lo indican |
| no hay categorías ni pasillos que mantener a mano | `recorrido-de-compra.md` | las propuestas de orden se apoyan en el recorrido aprendido |
| el formato de `meal_plans.json` es estable | `agents.md` | ninguna propuesta cambia su estructura |
| un fichero dañado nunca se sobrescribe | `agents.md` | todo fichero nuevo sigue el mismo comportamiento y se prueba con uno corrupto |
| el texto de la interfaz está solo en español y en `res/values/strings.xml` | `agents.md` | las cadenas nuevas van a `strings.xml` con plurales y descripciones accesibles |
| la versión mínima es android 14 (`minSdk 34`) | `app/build.gradle.kts` | no hace falta compatibilidad con versiones anteriores de las interfaces del sistema |

## DEUDA QUE CONDICIONA

- tres pantallas concentran casi toda la lógica de interfaz: `MainActivity.kt` (667 líneas), `ListDetailActivity.kt` (657) y `MenuShoppingActivity.kt` (583). varias propuestas de dificultad baja las tocan a la vez; conviene extraer a clases propias, como ya se hizo con `MealSuggestions.kt`, en lugar de seguir ampliándolas.
- no hay capa de `ViewModel` ni inyección de dependencias. las propuestas siguen el patrón actual de modelo puro, gestor y `lifecycleScope`.
- hay pocas pruebas instrumentadas de pantallas con interacción real: `agents.md` indica que evitan la inyección de entrada de espresso por su incompatibilidad con la api 37.
- la copia de seguridad automática de android está activa con las reglas de plantilla (`backup_rules.xml`, `data_extraction_rules.xml`) y no hay política de privacidad. `m6` afecta a este punto.

## HUECOS DETECTADOS

| hueco | efecto para el usuario | propuestas que lo cubren |
|---|---|---|
| nada se puede editar tras crearlo | corregir un nombre obliga a borrar y volver a escribir | `b3`, `b6` |
| una lista usada no se reutiliza | repetir una compra habitual exige reescribirla | `b4`, `m1`, `m7` |
| el menú no se repite ni se copia | una semana de rutina son catorce huecos a mano | `b5` |
| el menú y la compra no se conocen | no se sabe si la compra de la semana ya está preparada | `b7`, `a1` |
| lo compartido solo se puede leer | la otra persona tiene que reescribir la lista | `m2`, `b2` |
| los datos solo viven en el teléfono | perder o cambiar el móvil borra el historial, las listas y el recorrido | `m6` |
| un solo recorrido para todas las tiendas | cambiar de tienda obliga a olvidar lo aprendido | `m3` |
| lo que ya hay en casa no se tiene en cuenta | cada semana se compra de nuevo la sal o el aceite si se escriben como ingredientes | `m4` |
| los platos solo existen como texto del menú | los ingredientes no se pueden revisar fuera de la semana | `m5`, `a3` |
| se pierde lo comprado al borrar una lista | no hay memoria de qué y cuánto se compra | `m7`, `m9` |
| el menú solo se ve dentro de la aplicación | hay que abrirla para saber qué toca hoy | `m8`, `a1` |
| no hay cantidades ni precios | la lista dice qué comprar, pero no cuánto ni a qué coste | `b6`, `m9`, `a2` |
