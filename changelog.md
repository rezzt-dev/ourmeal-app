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

#### CAMBIOS
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

#### CORREGIDO
- corrige la actualización concurrente de listas y productos al añadir, marcar, eliminar o restaurar elementos mediante snapshots de persistencia y bloqueo temporal de interacciones.
- corrige el contraste de los checkboxes, el desbordamiento de resúmenes y el recorte de acciones en pantallas estrechas o con fuentes ampliadas.

#### ELIMINADO
- retira la estructura Android anterior ubicada directamente en la raiz del repositorio para sustituirla por la nueva organizacion en `android-app/`.
- retira recursos y estilos obsoletos de la interfaz anterior, incluidos el tema nocturno duplicado y los iconos sin uso.
- retira la tarjeta diaria `day_meal_item.xml` al sustituirla por el tablero semanal del menú.

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

#### CHANGED
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

#### FIXED
- fix concurrent list and product updates when adding, checking, deleting, or restoring items by using persistence snapshots and temporarily locking interactions.
- fix checkbox contrast, summary overflow, and clipped actions on narrow screens or with larger font sizes.

#### REMOVED
- remove the previous Android structure located directly in the repository root and replace it with the new `android-app/` organization.
- remove obsolete resources and styles from the previous interface, including the duplicated night theme and unused icons.
- remove the daily card `day_meal_item.xml` in favor of the weekly menu board.
