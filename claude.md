# OURMEAL APP — INSTRUCCIONES PARA AGENTES

> este documento define el contexto, las reglas y el flujo de trabajo obligatorio para cualquier agente de ia que intervenga en este repositorio. su alcance comprende todo el árbol del proyecto.

## ORDEN DE PRECEDENCIA

1. seguir las instrucciones explícitas del usuario.
2. aplicar este documento y, cuando corresponda, la skill local seleccionada.
3. consultar los documentos de apoyo de `.docs/` cuando se necesite ampliar el contexto.
4. respetar las convenciones ya presentes en el código antes de introducir otras nuevas.

si una fuente contradice a otra, se debe informar de la contradicción y aplicar la de mayor prioridad. no se deben inventar requisitos, dependencias, comandos ni decisiones de arquitectura que el repositorio todavía no contenga.

## PROTOCOLO OBLIGATORIO DE SKILLS

antes de comenzar cualquier tarea se debe acceder siempre a `.docs/skills/` para comprobar qué skills locales están disponibles.

1. listar los ficheros `.docs/skills/*/SKILL.md`.
2. comparar la petición con el campo `description` de cada skill.
3. leer de principio a fin cada `SKILL.md` aplicable antes de modificar archivos o ejecutar acciones de la tarea.
4. resolver las rutas relativas desde el directorio de la skill y leer las referencias que esta marque como obligatorias.
5. aplicar todas las instrucciones de la skill mientras sean compatibles con la petición y con este documento.
6. si ninguna skill es aplicable, continuar con estas reglas y las convenciones del repositorio.

no basta con recordar una skill de otra sesión. se debe consultar siempre la copia versionada dentro de `.docs/skills/`, ya que es la fuente local vigente.

### CATALOGO DE SKILLS

| ruta | uso principal |
|---|---|
| `.docs/skills/frontend-design/SKILL.md` | creación de páginas, componentes e interfaces web con una dirección visual distintiva y calidad de producción. |
| `.docs/skills/impeccable/SKILL.md` | diseño, rediseño, auditoría, mejora, accesibilidad, rendimiento, adaptación y pulido de interfaces. |
| `.docs/skills/shadcn/SKILL.md` | instalación, búsqueda, composición, personalización y depuración de componentes de `shadcn/ui`. |
| `.docs/skills/vercel-composition-patterns/SKILL.md` | arquitectura y composición escalable de componentes de react, especialmente ante exceso de propiedades booleanas o api poco flexible. |
| `.docs/skills/vercel-react-best-practices/SKILL.md` | escritura, revisión y refactorización de react con atención a rendimiento, carga, renderizado y tamaño del bundle. |
| `.docs/skills/vercel-react-view-transitions/SKILL.md` | transiciones de vista, navegación animada, elementos compartidos y cambios de estado en react. |
| `.docs/skills/web-design-guidelines/SKILL.md` | revisión de interfaz, experiencia de usuario y accesibilidad conforme a las directrices web indicadas por la skill. |
| `.docs/skills/writing-guidelines/SKILL.md` | revisión de documentación, tono, claridad y calidad de la escritura. |

las skills de react, `shadcn/ui` y diseño web no se aplican a la interfaz android de xml. `impeccable` sí se aplica al diseño de la aplicación, adaptando sus reglas de css a los recursos de android, y `writing-guidelines` a la documentación, siempre subordinada a las reglas de markdown de este documento.

## CONTEXTO DEL PROYECTO

`ourmeal-app` es el repositorio de ourmeal, una aplicación android orientada a simplificar la planificación de comidas y la gestión de compras.

la aplicación permite, según el código actual:

- planificar la comida y la cena de cada día de la semana, navegar entre semanas y elegir una fecha en el calendario.
- ver el progreso de planificación de la semana y conservar automáticamente los cambios del menú.
- sugerir platos ya planificados mientras se escribe el menú, ocultar sugerencias con deshacer y avisar, sin impedirlo, cuando un plato se repite en la semana o estuvo en el menú en los últimos 13 días.
- preparar la compra desde el menú de la semana: agrupar los platos planificados, proponer los ingredientes que el usuario indicó antes para cada plato, revisarlos y añadirlos a una lista nueva o existente sin duplicar productos.
- crear y eliminar listas de la compra, con deshacer.
- añadir, marcar como comprados y eliminar productos de cada lista, con detección de duplicados y deshacer.
- aprender el recorrido de compra a partir del orden en que se marcan los productos y ordenar los pendientes de las listas según él, con interruptor para volver al orden de la lista y opción de olvidar lo aprendido, con deshacer.
- compartir una lista como texto, en el orden mostrado, para imprimirla, guardarla o enviarla.

las funcionalidades propuestas y todavía no implementadas se especifican en `.docs/new-features/`. no se deben presentar como existentes hasta que estén implementadas y verificadas.

el público principal incluye familias, parejas, personas que organizan su dieta y cualquier usuario que quiera sustituir las listas en papel por un flujo más ordenado.

## CONTEXTO TECNICO

datos verificados en `android-app/app/build.gradle.kts` y `android-app/gradle/libs.versions.toml`:

- lenguaje: kotlin `1.9.0`, con corrutinas para la lectura y escritura en segundo plano.
- plataforma: android nativo.
- construcción: gradle `8.7` con kotlin dsl, agp `8.6.0` y catálogo de versiones en `gradle/libs.versions.toml`.
- `namespace` y `applicationId`: `app.ourmeat`. el código kotlin vive en el paquete `ourmeal.app`.
- sdk mínimo: api 34, android 14.
- sdk de compilación y objetivo: api 35, android 15.
- objetivo jvm: java 17, `jvmTarget = "17"`.
- interfaz: vistas xml con view binding, material components con tema `Theme.Material3.Dark.NoActionBar` y variantes en `res/layout-land/`.
- organización: paquetes `model/` y `controller/`, junto con `MainActivity.kt`, `MenuShoppingActivity.kt`, `ListActivity.kt`, `ListDetailActivity.kt` y utilidades de interfaz compartidas en `ourmeal.app`.
- persistencia: ficheros json en `filesDir` escritos con `AtomicFile` y `gson` (`user_lists.json`, `meal_plans.json`, `dish_suggestions.json`, `dish_ingredients.json` y `shopping_route.json`).
- pruebas: junit en `app/src/test/` y pruebas instrumentadas con androidx junit en `app/src/androidTest/`.

si `.docs/info-project/informacion-tecnica.md` contradice estos datos, prevalece la configuración del proyecto.

## ESTADO Y ESTRUCTURA DEL REPOSITORIO

| ruta | finalidad |
|---|---|
| `android-app/` | proyecto gradle de la aplicación android; el módulo es `android-app/app/`. |
| `.docs/info-project/` | contexto funcional, técnico y de ayuda de ourmeal. |
| `.docs/new-features/` | especificaciones de funcionalidades propuestas, pendientes de implementar. |
| `.docs/guidelines/` | documentos de origen para las convenciones editoriales y de git consolidadas aquí. |
| `.docs/skills/` | skills locales obligatorias para los agentes. |
| `PRODUCT.md` | contexto de producto y principios de diseño usados por `impeccable`. |
| `changelog.md` | registro bilingüe de cambios. |
| `readme.md` | presentación pública y guía básica del repositorio. |
| `license.md` | licencia no comercial aplicable al proyecto. |

la página web temporal (`project-web/`) se retiró del repositorio, como recoge `changelog.md`. no se debe reintroducir ninguna implementación web salvo petición expresa.

## PRINCIPIOS DE DESARROLLO

- inspeccionar el estado del repositorio y los archivos relacionados antes de editar.
- mantener los cambios limitados a la petición y conservar el trabajo ajeno no relacionado.
- reutilizar la identidad, los recursos y las convenciones existentes cuando sigan siendo válidos.
- no añadir librerías, servicios externos ni un framework sin una necesidad concreta.
- mantener una estructura clara, componentes con responsabilidades acotadas y nombres descriptivos.
- evitar duplicación, código muerto, marcadores de posición engañosos y contenido que afirme funciones inexistentes.
- no modificar binarios, recursos históricos o información legal sin una petición explícita.
- conservar rutas, comandos, nombres de archivo, identificadores y api con su escritura literal.
- actualizar la documentación afectada cuando cambien la estructura, los comandos, los requisitos o el comportamiento público.

## DISEÑO, EXPERIENCIA Y ACCESIBILIDAD

para cualquier trabajo visual se deben cargar primero las skills aplicables de `.docs/skills/` y seguir sus comprobaciones obligatorias.

- diseñar desde el contexto de ourmeal y su sistema visual existente (`res/values/colors.xml`, `res/values/themes.xml`) y evitar interfaces genéricas o ajenas a la marca.
- usar controles nativos de android y material, encabezados de pantalla con `android:accessibilityHeading` y textos en `res/values/strings.xml`.
- garantizar navegación completa con talkback y teclado físico, foco visible, `contentDescription` útiles e iconos decorativos ocultos a la accesibilidad.
- cumplir como mínimo wcag 2.2 nivel aa, incluido un contraste de `4.5:1` para texto normal y `3:1` para texto grande y contornos de componentes.
- mantener objetivos táctiles de al menos `48dp` y admitir tamaños de fuente ampliados, orientación horizontal y pantallas estrechas sin pérdida de información.
- respetar la configuración de animaciones del sistema (`ValueAnimator.areAnimatorsEnabled()`, como en `UiMotion.kt`) y no depender de la animación para comunicar estado.
- leer y escribir datos fuera del hilo principal y evitar saltos de diseño y recursos innecesariamente pesados.
- comprobar estados de carga, vacío, error, interacción y contenido largo cuando sean aplicables.
- no afirmar que una acción descarga, guarda, sincroniza o envía información si el comportamiento no está implementado y verificado.

## VERIFICACION DEL TRABAJO

antes de dar una tarea por terminada:

1. ejecutar desde `android-app/` las comprobaciones disponibles: `./gradlew testDebugUnitTest`, `./gradlew lintDebug` y `./gradlew assembleDebug`, y `./gradlew connectedDebugAndroidTest` cuando haya un emulador o dispositivo conectado.
2. probar el flujo afectado y los estados relevantes en lugar de limitarse a comprobar que compila.
3. revisar la interfaz en vertical y en horizontal, con tamaño de fuente ampliado, cuando haya cambios visuales.
4. comprobar accesibilidad básica con talkback, teclado físico, foco, nombres accesibles y contraste.
5. revisar `git diff --check` y el diff final para detectar espacios, archivos accidentales o cambios fuera de alcance.
6. indicar con claridad qué se validó y qué no pudo validarse.

las pruebas instrumentadas que lean o escriban datos de la aplicación deben usar la regla `AppDataSandbox` de `app/src/androidTest/`, que aparta los ficheros del dispositivo y los restaura al terminar.

## REGLAS PARA MARKDOWN

estas reglas se aplican a todo fichero `.md` creado o modificado en el repositorio.

### FORMATO GENERAL

- todo el contenido se escribe en minúsculas y conserva los acentos ortográficos necesarios. esto incluye párrafos, elementos de listas, celdas, citas y notas.
- todos los títulos y encabezados se escriben en mayúsculas y sin acentos, con independencia de su nivel.
- la primera letra de cada frase permanece en minúscula, también después de un punto.
- las siglas, nombres propios y marcas se escriben en minúsculas dentro del contenido y en mayúsculas dentro de los títulos.
- la `ñ` se conserva: `diseño` en contenido y `DISEÑO` en un título.
- se deja una línea en blanco después de cada encabezado y antes y después de listas, tablas, citas y bloques de código.
- se usa markdown semántico, limpio y compatible con github. se puede usar html compatible con markdown cuando aporte estructura o presentación sin perjudicar la accesibilidad.
- los enlaces deben tener un texto descriptivo y las tablas deben reservarse para datos que se beneficien de una comparación tabular.

### EXCEPCIONES LITERALES

el formato no altera bloques de código, código en línea, comandos, rutas, urls, nombres de ficheros, identificadores, ramas, tags o etiquetas. si un título contiene código entre backticks, ese fragmento conserva su escritura literal.

`license.md` conserva íntegramente el significado y alcance del texto legal: no se resume ni se reformula. su contenido aplica minúsculas y acentos, mientras que sus encabezados siguen la regla general.

### EJEMPLO

```md
## ARQUITECTURA Y DISEÑO

el módulo `android-app/` contiene la aplicación android.
```

## REGLAS PARA COMMITS

### TITULO

el título sigue exactamente este formato:

```text
<prefijo> | <resumen corto en imperativo>
```

solo se admiten estos prefijos:

| prefijo | uso |
|---|---|
| `update` | añadir o mejorar funcionalidad, contenido o documentación; es el valor por defecto. |
| `fix` | corregir un error no urgente detectado durante el desarrollo. |
| `hotfix` | corregir con urgencia un problema crítico o una versión ya publicada. |
| `changes` | reorganizar archivos, configuración, tooling o dependencias sin añadir funcionalidad ni corregir un fallo. |
| `changelog` | modificar exclusivamente `changelog.md` al mantener o cerrar una versión. |

el separador es siempre ` | ` y no se combinan ni inventan prefijos.

### CUERPO

- usar una lista markdown no numerada, sin párrafos sueltos ni líneas en blanco entre elementos.
- escribir cada elemento en imperativo y en una única línea continua.
- marcar rutas, archivos, comandos e identificadores con código en línea.
- explicar qué cambia y por qué, sin narrar cada línea modificada.
- cerrar con una línea en blanco y la atribución exigida por el entorno, si existe.
- al presentar un mensaje propuesto, separar el título y el cuerpo en bloques de código distintos.

## CHANGELOG, PULL REQUESTS, MERGES Y RELEASES

si existe `changelog.md`, constituye la fuente de verdad para pull requests, merges y releases. sus entradas usan las categorías cerradas y en este orden: `NUEVO / ADDED`, `CAMBIOS / CHANGED`, `CORREGIDO / FIXED`, `ELIMINADO / REMOVED` y `SEGURIDAD / SECURITY`. se omiten las categorías vacías.

- mantener bloques `### CASTELLANO` y `### ENGLISH` equivalentes.
- escribir una sola línea en imperativo por cambio.
- usar fechas `aaaa-mm-dd`.
- conservar literalmente los marcadores `[online]` y `[offline]` cuando existan.
- mantener `## SIN PUBLICAR / UNRELEASED` durante el desarrollo y convertirlo en `## [x.y.z] - aaaa-mm-dd` al cerrar una versión.

### PULL REQUESTS

una pull request de trabajo normal usa el formato de título de commit. una pull request de publicación usa:

```text
release version <x.y.z> - <resumen corto en imperativo>
```

la descripción reutiliza el bloque bilingüe correspondiente de `changelog.md`, con sus categorías. si la pull request solo aporta parte de una versión, incluye únicamente esos elementos. el contexto adicional o las capturas se colocan después de las listas.

### MERGES

se usa `create a merge commit`, nunca squash ni rebase, para conservar los commits individuales. un merge manual usa `git merge --no-ff` y el mismo título y cuerpo que tendría la pull request equivalente.

### RELEASES

el tag siempre es `v<x.y.z>`. el título sigue:

```text
v<x.y.z> — <resumen corto en inglés>
```

la descripción de github se publica únicamente en inglés y reutiliza de forma literal el bloque `### ENGLISH` de la versión cerrada, con las categorías en el orden establecido.

## SEGURIDAD, PRIVACIDAD Y LICENCIA

- no incluir secretos, credenciales, tokens, claves, información personal ni datos reales de usuarios.
- validar y normalizar cualquier entrada del usuario; evitar html sin sanear, urls inseguras, permisos y dependencias innecesarias.
- no registrar datos sensibles ni incorporar telemetría sin consentimiento y documentación.
- comunicar vulnerabilidades en privado al autor del repositorio, sin abrir incidencias públicas con detalles explotables. el repositorio todavía no incluye `security.md`.
- todo el repositorio está sujeto a `license.md`. el uso comercial requiere autorización previa y escrita del autor.
- no generar, firmar ni publicar apk de versión sin instrucciones expresas y verificación de procedencia.

## FUENTES DE CONTEXTO

cuando una tarea necesite más detalle, consultar:

- `.docs/info-project/informacion-general.md` para propósito, público y funciones.
- `.docs/info-project/informacion-tecnica.md` para la configuración técnica de la aplicación android.
- `.docs/new-features/` para las funcionalidades propuestas.
- `PRODUCT.md` para el contexto de producto y diseño.
- `.docs/info-project/informacion-ayuda.md` para los recorridos de usuario y la incorporación de desarrolladores.
- `.docs/guidelines/markdown-guidelines.md` para el origen de las reglas editoriales.
- `.docs/guidelines/commit-guidelines.md` para el origen del formato de commits.
- `.docs/guidelines/pr-merge-release-guidelines.md` para el origen del flujo de publicación.

estas fuentes amplían el contexto, pero las normas consolidadas de este documento deben poder aplicarse sin depender de una lectura previa de las guidelines.
