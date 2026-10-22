# CHANGELOG

## SIN PUBLICAR / UNRELEASED

### CASTELLANO

#### NUEVO
- incorpora `project-web/` como sitio publico de OurMeal con una pagina principal, una demo interactiva de menus y listas de la compra, y las paginas legales de aviso legal, privacidad y cookies.
- añade documentacion de proyecto, ayuda, arquitectura y contribucion en `.docs/`, `agents.md` y `claude.md`.
- reemplaza la interfaz de la aplicación Android por un sistema visual basado en la landing de `project-web/`, con tipografías Bricolage Grotesque y Source Sans 3, paleta de marca, iconos vectoriales y componentes Material 3.
- rediseña «El menú» como un tablero semanal con selector de día y edición en línea de comida y cena, sustituyendo las tarjetas repetidas de lunes a domingo.
- añade estados de carga, vacío, error y deshabilitado, reintentos, deshacer en eliminaciones, detección de productos duplicados y retroalimentación con snackbars.
- incorpora un selector de semana con `MaterialDatePicker`, resaltado del día actual y progreso de planificación semanal.
- fuerza el inicio de semana en lunes también en el selector de fechas, en línea con el calendario ISO que usa la aplicación.
- añade variantes de disposición para landscape en las tres pantallas, con columnas adaptadas al espacio disponible.
- mejora la accesibilidad con encabezados de pantalla, descripciones de acciones y estados anunciados por lectores de pantalla.
- añade pruebas unitarias para semanas ISO, progreso de listas, días semanales y normalización de nombres.
- añade `CreationFormTest.kt` para comprobar que los nombres vacíos no se envían, que el borrador del formulario sobrevive a la recreación de la pantalla y que la aplicación mantiene el tema oscuro con independencia del teléfono.
- añade en `.docs/new-features/` las especificaciones de dos funcionalidades pendientes: preparar la compra desde el menú y el historial de platos con sugerencias y aviso de repeticiones.
- añade `WeekUtils.isValidWeekId()` para distinguir los ids de semana iso reales.
- añade la regla `AppDataSandbox` para que las pruebas instrumentadas trabajen sin datos y restauren los ficheros reales del dispositivo al terminar.
- sugiere en el menú semanal los platos ya planificados en otras semanas, con habituales al enfocar un campo vacío y coincidencias sin mayúsculas ni acentos al escribir.
- permite dejar de sugerir un plato con una pulsación larga o la acción de accesibilidad `Dejar de sugerir`, con deshacer, y lo vuelve a sugerir cuando el usuario lo escribe de nuevo.
- avisa bajo cada plato si se repite en la semana o estuvo en el menú en los últimos 13 días, sin impedir repetirlo.
- añade pruebas unitarias del historial de platos y de los ajustes de sugerencias, y `MealSuggestionsTest.kt` para el flujo completo en el menú.
- añade `PRODUCT.md` con el contexto de producto y de diseño, y las skills locales de `.docs/skills/` para los agentes que trabajan en la interfaz.
- añade `.docs/guidelines/` con las reglas de markdown, de commits y de pull requests, merges y releases.
- añade `Preparar la compra` al menú para convertir los platos de la semana en productos de una lista nueva o existente, sin duplicar los que ya estaban.
- recuerda en `dish_ingredients.json` los ingredientes escritos para cada plato y los propone la siguiente vez, sin sobrescribir nunca un fichero dañado.
- muestra al abrir la lista cuántos productos del menú se añadieron y cuántos ya estaban.
- añade pruebas unitarias de la agrupación de platos, la memoria de ingredientes, el borrador y la fusión de productos, y `MenuShoppingTest.kt` para el recorrido completo.
- aprende el recorrido de compra a partir del orden en que se marcan los productos y ordena los pendientes de cada lista según él, con lo comprado al final y sin configurar pasillos ni categorías.
- guarda el recorrido en `shopping_route.json`, descarta los viajes de menos de tres productos o dos minutos para no aprender los marcados hechos en casa y nunca sobrescribe un fichero dañado.
- añade el interruptor `Seguir mi recorrido`, un resumen bajo el nombre de la lista con los pendientes ya ubicados y la acción `Olvidar`, con deshacer, para empezar de cero al cambiar de tienda.
- comparte las listas en el orden en que se muestran y avisa al abrir una lista de cuántos productos aprendió del último viaje.
- añade `ShoppingRouteTest.kt` y `ShoppingRouteOrderTest.kt` para el aprendizaje del recorrido y el orden de la lista.

#### CAMBIOS

- unifica los formularios de creación de listas y productos en un contenedor desplazable con mensaje propio, texto de ayuda, contador de caracteres y espacio reservado para errores.
- distingue la acción principal de los diálogos con un botón relleno y aplica color de error a las confirmaciones destructivas mediante `ThemeOverlay.Ourmeal.DestructiveDialog`.
- aplica `ThemeOverlay.Ourmeal.Calendar` al selector de fecha con las etiquetas `Elegir fecha` y `Cancelar`, y cierra la edición del menú antes de abrirlo.
- amplía el relleno de los campos del menú semanal y unifica su tamaño de texto en `18sp` para dar aire al panel diario.
- conserva los borradores de creación al recrear la pantalla, bloquea envíos repetidos durante el guardado y fija el modo oscuro de la aplicación con independencia del tema del teléfono.
- rediseña el menú con una tira de fechas compacta, un panel diario unificado, edición directa con foco visible y estado de guardado breve; asegura la visibilidad del día seleccionado y cierra la edición al cambiar de día.
- sustituye los campos sobredimensionados por un panel diario compacto con separadores, iconos de edición, etiquetas persistentes y estados de foco; ajusta los textos de ayuda a `Añadir comida` y `Añadir cena`.
- reduce el selector de días a una tira horizontal con el día activo resaltado, conserva objetivos táctiles accesibles y elimina fondos y recursos gráficos que ya no participan en la composición.
- mejora el icono del lanzador reemplazando el dibujo anterior por un icono `utensils` limpio de lucide icons, sin fondo cuadrado innecesario.
- reduce el tamaño de la cabecera de listas en `activity_list.xml` y ajusta el espacio previo a la sección para conservar una composición más compacta en pantallas verticales.
- ajusta la variante horizontal de `activity_list.xml` para mantener una jerarquía visual compacta al mostrar el título y la descripción.
- reorganiza la aplicacion Android desde la raiz del repositorio hacia `android-app/` para separar el cliente movil del resto de productos.
- añade `android-app/.gitignore` para excluir artefactos de Gradle, compilacion, IDE y configuracion local del proyecto Android.
- configura la aplicacion web con Astro, TypeScript, Tailwind CSS, tipografias locales, iconos Lucide y comandos de comprobacion, compilacion y previsualizacion.
- mantiene `project-web/` como implementacion web temporal importada mientras se completa la pagina antes de retirarla.
- actualiza la estructura de configuracion y desarrollo del repositorio con `.vscode/`, los bloqueos de dependencias web y la configuracion Gradle del proyecto Android.
- traslada los textos de la interfaz a `res/values/strings.xml`, con plurales y descripciones accesibles.
- ejecuta la lectura de datos en segundo plano con corrutinas y actualiza los adaptadores con `DiffUtil` e identificadores estables.
- sustituye los botones basados en `TextView` por `MaterialButton` con estados, ripple y tamaños táctiles accesibles.
- serializa las operaciones de persistencia con un bloqueo compartido a nivel de proceso para mantener atómicas las lecturas y escrituras entre actividades.
- simplifica el tema a una única variante oscura y configura el arranque, los diálogos y los snackbars desde `themes.xml`.
- pule la tipografía y los cambios de estado de la aplicación, y hace desplazable el selector semanal para conservar áreas táctiles cómodas en pantallas estrechas.
- mejora la demo web con transiciones breves, progreso visible de la compra y una composición móvil más equilibrada.
- refina las tarjetas de listas y productos con estados visibles, controles táctiles accesibles, progreso animado y acciones deshabilitadas durante el guardado.
- adapta el selector semanal y la demo web a contenidos largos y tamaños de texto ampliados, manteniendo el desplazamiento y las áreas táctiles utilizables.
- guarda el menú semanal en segundo plano, en orden de llamada y sin perder el último guardado al salir de la pantalla, en lugar de escribir en el hilo principal.
- aplica `AppDataSandbox` a `CreationFormTest` para no leer ni sobrescribir los datos reales del dispositivo de pruebas.
- actualiza `claude.md` y `agents.md` para describir la aplicación android real en lugar de una página web inexistente, con `jvmTarget 17`, la estructura, los comandos de verificación y las reglas de accesibilidad de android.
- reescribe `.docs/info-project/` según las reglas de markdown del repositorio y corrige las funciones descritas que no existen.
- lee todas las semanas guardadas en una sola carga del menú para construir el historial de platos, sin cambiar el formato de `meal_plans.json`.
- detecta los productos duplicados al añadirlos a mano sin distinguir mayúsculas ni acentos, igual que al añadirlos desde el menú.
- documenta la preparación de la compra en `.docs/info-project/`, `claude.md` y `agents.md`.
- usa las versiones variables de Bricolage Grotesque y Source Sans 3 con una escala tipográfica semántica en `res/values/dimens.xml`, pesos reales por estilo, tamaño óptico ajustado a títulos y titulares y cifras tabulares en fechas y progresos.

#### CORREGIDO
- corrige la prueba instrumentada de contexto para comparar el `applicationId` real `app.ourmeat` y homogeneizar sus finales de línea.
- corrige la actualización concurrente de listas y productos al añadir, marcar, eliminar o restaurar elementos mediante snapshots de persistencia y bloqueo temporal de interacciones.
- corrige el contraste de los checkboxes, el desbordamiento de resúmenes y el recorte de acciones en pantallas estrechas o con fuentes ampliadas.
- corrige el color del texto de los snackbars, que se veía oscuro sobre fondo oscuro, y ajusta su forma y tipografía al resto de la interfaz.
- corrige `WeekUtils.startOfWeek()`, que convertía en silencio una semana 53 inexistente, como `2021-W53`, en la primera semana del año siguiente.
- corrige los estilos de texto y los componentes material, que se mostraban en roboto porque el atributo `fontFamily` heredado de material 3 prevalecía sobre las fuentes de la marca.

#### ELIMINADO
- retira la estructura Android anterior ubicada directamente en la raiz del repositorio para sustituirla por la nueva organizacion en `android-app/`.
- retira recursos y estilos obsoletos de la interfaz anterior, incluidos el tema nocturno duplicado y los iconos sin uso.
- retira la tarjeta diaria `day_meal_item.xml` al sustituirla por el tablero semanal del menú.
- retira `project-web/` y sus dependencias, paginas, estilos, recursos y artefactos generados al finalizar la fase temporal del sitio web.

### ENGLISH

#### ADDED
- add `project-web/` as the public OurMeal website with a home page, an interactive meal and shopping-list demo, and legal notice, privacy, and cookies pages.
- add project, help, architecture, and contribution documentation in `.docs/`, `agents.md`, and `claude.md`.
- replace the Android interface with a visual system based on the `project-web/` landing page, using Bricolage Grotesque and Source Sans 3, brand palette, vector icons, and Material 3 components.
- redesign the menu as a weekly board with a day picker and inline lunch and dinner editing, replacing the repeated Monday-to-Sunday cards.
- add loading, empty, error, and disabled states, retries, undo on deletion, duplicate product detection, and snackbar feedback.
- add a week picker based on `MaterialDatePicker`, today highlighting, and weekly planning progress.
- force weeks to start on Monday in the date picker as well, consistent with the ISO calendar used by the app.
- add landscape layout variants for all three screens, with columns adapted to the available space.
- improve accessibility with screen headings, action descriptions, and states announced by screen readers.
- add unit tests for ISO weeks, list progress, week days, and name normalization.
- add `CreationFormTest.kt` to check that blank names cannot be submitted, that the creation draft survives screen recreation, and that the app keeps its dark theme regardless of the phone setting.
- add specifications for two pending features in `.docs/new-features/`: preparing the shopping list from the menu, and a dish history with suggestions and repeat hints.
- add `WeekUtils.isValidWeekId()` to tell real iso week ids apart.
- add the `AppDataSandbox` rule so instrumented tests run without data and restore the device's real files when they finish.
- suggest dishes already planned in other weeks in the weekly menu, with usual dishes when an empty field gets focus and accent- and case-insensitive matches while typing.
- let users stop suggesting a dish with a long press or the `Dejar de sugerir` accessibility action, with undo, and suggest it again when the user writes it once more.
- show a hint under each dish when it repeats in the week or was on the menu in the last 13 days, without preventing the repeat.
- add unit tests for the dish history and the suggestion settings, and `MealSuggestionsTest.kt` for the full menu flow.
- add `PRODUCT.md` with product and design context, and the local skills in `.docs/skills/` for agents working on the interface.
- add `.docs/guidelines/` with the markdown, commit, and pull request, merge, and release rules.
- add `Preparar la compra` to the menu to turn the dishes of the week into products of a new or existing list, without duplicating the ones already there.
- remember in `dish_ingredients.json` the ingredients written for each dish and offer them next time, never overwriting a damaged file.
- show on opening the list how many menu products were added and how many were already there.
- add unit tests for dish grouping, ingredient memory, the draft, and product merging, and `MenuShoppingTest.kt` for the full flow.
- learn the shopping route from the order in which products are checked off and sort the pending products of every list by it, with bought ones at the end and no aisles or categories to set up.
- store the route in `shopping_route.json`, discard trips with fewer than three products or shorter than two minutes so items checked off at home are not learned, and never overwrite a damaged file.
- add the `Seguir mi recorrido` switch, a summary under the list name with the pending products already placed, and the `Olvidar` action, with undo, to start over after changing shops.
- share lists in the order they are shown and announce on opening a list how many products the last trip taught.
- add `ShoppingRouteTest.kt` and `ShoppingRouteOrderTest.kt` for route learning and list ordering.

#### CHANGED

- unify list and product creation forms in a scrollable container with their own message, helper text, character counter, and reserved space for errors.
- distinguish the primary dialog action with a filled button and apply the error color to destructive confirmations through `ThemeOverlay.Ourmeal.DestructiveDialog`.
- apply `ThemeOverlay.Ourmeal.Calendar` to the date picker with the `Elegir fecha` and `Cancelar` labels, and close meal editing before opening it.
- increase the padding of the weekly menu fields and unify their text size at `18sp` to give the daily panel more breathing room.
- preserve creation drafts when recreating the screen, prevent repeated submissions while saving and keep the app dark independently of the device theme.
- redesign the menu with a compact date strip, a unified daily panel, inline editing with visible focus and brief save status; keep the selected day visible and close editing when switching days.
- replace oversized fields with a compact daily panel using separators, edit icons, persistent labels, and focus states; update helper copy to `Añadir comida` and `Añadir cena`.
- reduce the day selector to a horizontal strip with the active day highlighted, preserve accessible touch targets, and remove backgrounds and graphic resources no longer used by the composition.
- improve the app icon by replacing the previous launcher icon with a clean `utensils` icon from lucide icons, removing the unnecessary square background.
- reduce the list header size in `activity_list.xml` and adjust the spacing before the section to keep a more compact composition on portrait screens.
- adjust the landscape variant of `activity_list.xml` to keep a compact visual hierarchy when showing the title and description.
- move the Android application from the repository root to `android-app/` to separate the mobile client from the other products.
- add `android-app/.gitignore` to exclude Gradle, build, IDE, and local configuration artifacts from the Android project.
- configure the web application with Astro, TypeScript, Tailwind CSS, local fonts, Lucide icons, and check, build, and preview commands.
- keep `project-web/` as a temporary imported web implementation while the page is completed before its removal.
- update the repository development and configuration structure with `.vscode/`, web dependency lockfiles, and the Android Gradle configuration.
- move interface copy to `res/values/strings.xml` with plurals and accessible descriptions.
- read data in the background with coroutines and update adapters with `DiffUtil` and stable IDs.
- replace `TextView`-based buttons with `MaterialButton` components that expose states, ripple, and accessible touch targets.
- serialize persistence operations with a process-wide lock so read-modify-write cycles stay atomic across activities.
- simplify the theme to a single dark variant and configure launch, dialogs, and snackbars from `themes.xml`.
- refine typography and state changes in the application, and make the weekly selector scrollable to preserve comfortable touch targets on narrow screens.
- improve the web demo with brief transitions, visible shopping progress, and a more balanced mobile composition.
- refine list and product cards with visible states, accessible touch controls, animated progress, and disabled actions while saving.
- adapt the weekly selector and web demo to long content and larger text sizes while keeping scrolling and touch targets usable.
- save the weekly menu in the background, in call order and without losing the last save when leaving the screen, instead of writing on the main thread.
- apply `AppDataSandbox` to `CreationFormTest` so it never reads or overwrites the real data on the test device.
- update `claude.md` and `agents.md` to describe the real android app instead of a missing website, with `jvmTarget 17`, the structure, the verification commands, and android accessibility rules.
- rewrite `.docs/info-project/` following the repository markdown rules and correct the described features that do not exist.
- read every saved week in a single menu load to build the dish history, without changing the `meal_plans.json` format.
- detect duplicate products added by hand regardless of case and accents, the same way as products added from the menu.
- document shopping preparation in `.docs/info-project/`, `claude.md`, and `agents.md`.
- use the variable versions of Bricolage Grotesque and Source Sans 3 with a semantic type scale in `res/values/dimens.xml`, real weights per style, optical size tuned for titles and headlines, and tabular figures in dates and progress.

#### FIXED
- fix the instrumented context test to compare the real `app.ourmeat` `applicationId` and normalize its line endings.
- fix concurrent list and product updates when adding, checking, deleting, or restoring items by using persistence snapshots and temporarily locking interactions.
- fix checkbox contrast, summary overflow, and clipped actions on narrow screens or with larger font sizes.
- fix snackbar text, which rendered dark on a dark background, and align the snackbar shape and typography with the rest of the interface.
- fix `WeekUtils.startOfWeek()`, which silently turned a missing week 53, such as `2021-W53`, into the first week of the next year.
- fix text styles and Material components rendering in Roboto because the `fontFamily` attribute inherited from Material 3 took precedence over the brand fonts.

#### REMOVED
- remove the previous Android structure located directly in the repository root and replace it with the new `android-app/` organization.
- remove obsolete resources and styles from the previous interface, including the duplicated night theme and unused icons.
- remove the daily card `day_meal_item.xml` in favor of the weekly menu board.
- remove `project-web/` and its dependencies, pages, styles, assets, and generated artifacts after completing the temporary website phase.
