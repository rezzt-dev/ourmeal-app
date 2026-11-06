# OURMEAL — AGENT GUIDE

> **ourmeal** is a native android app to plan lunch and dinner for every day of the week and to manage shopping lists: it suggests dishes already planned, warns about repeats, turns the week's menu into shopping-list products with a per-dish ingredient memory, and learns the order in which you walk through the shop. everything is stored locally on the device; the app declares no permissions.
>
> **git workflow (commits, prs, merges, releases):** when asked for a commit, pr, merge or release title/description, read `.docs/manager.md` and the guides in `.docs/-github-guides/` (versioned, source of truth) and follow the last section of this file ("commit, pr, merge & release guidelines"). never add ai attribution of any kind.
>
> this file follows the repository's markdown rules (see *code style guidelines*): content in lowercase, headings in uppercase. its scope is the whole repository tree.

---

## AGENT PROTOCOL

### ORDER OF PRECEDENCE

1. the user's explicit instructions.
2. this document (and `claude.md`) and, when applicable, the selected local skill.
3. the supporting documents in `.docs/` when more context is needed.
4. the conventions already present in the code before introducing new ones.

if two sources contradict each other, report the contradiction and apply the higher-priority one. never invent requirements, dependencies, commands or architecture decisions the repository does not contain yet.

### MANDATORY SKILL PROTOCOL

before starting any task, always check `.docs/skills/` for the local skills available.

1. list the `.docs/skills/*/SKILL.md` files.
2. compare the request with the `description` field of each skill.
3. read every applicable `SKILL.md` from start to end before modifying files or running actions for the task.
4. resolve relative paths from the skill's directory and read the references it marks as mandatory.
5. apply all of the skill's instructions as long as they are compatible with the request and with this document.
6. if no skill applies, continue with these rules and the repository conventions.

remembering a skill from another session is not enough: always read the versioned copy inside `.docs/skills/`, which is the current local source.

| path | main use |
|---|---|
| `.docs/skills/frontend-design/SKILL.md` | building web pages, components and interfaces with a distinctive visual direction and production quality. |
| `.docs/skills/impeccable/SKILL.md` | designing, redesigning, auditing, improving, accessibility, performance, adapting and polishing interfaces. |
| `.docs/skills/shadcn/SKILL.md` | installing, finding, composing, customizing and debugging `shadcn/ui` components. |
| `.docs/skills/vercel-composition-patterns/SKILL.md` | scalable react component architecture and composition, especially with too many boolean props or an inflexible api. |
| `.docs/skills/vercel-react-best-practices/SKILL.md` | writing, reviewing and refactoring react with attention to performance, loading, rendering and bundle size. |
| `.docs/skills/vercel-react-view-transitions/SKILL.md` | view transitions, animated navigation, shared elements and state changes in react. |
| `.docs/skills/web-design-guidelines/SKILL.md` | interface, user-experience and accessibility review against the web guidelines the skill names. |
| `.docs/skills/writing-guidelines/SKILL.md` | reviewing documentation, tone, clarity and writing quality. |

the react, `shadcn/ui` and web-design skills do not apply to the xml android interface. `impeccable` does apply to the app's design, adapting its css rules to android resources, and `writing-guidelines` applies to documentation, always subordinate to the markdown rules of this document. the skills are third-party material versioned in the repo; their licences are pending review (see `.docs/repo-audit-and-fix.md`).

---

## PROJECT OVERVIEW

| property | value |
|----------|-------|
| **application id / namespace** | `app.ourmeat` (sic, with `t`; kotlin code lives in package `ourmeal.app`) |
| **version** | `versionName` `1.0`, `versionCode` 1 (`android-app/app/build.gradle.kts`) |
| **language** | kotlin `1.9.0` |
| **min sdk** | 34 (android 14) |
| **target / compile sdk** | 35 (android 15) |
| **java compatibility** | 17 (`jvmTarget = "17"`) |
| **agp / gradle** | `8.6.0` / `8.7` (wrapper) |
| **ui language** | spanish only (`res/values/strings.xml`, no other locales) |

single-module android project (`:app`, root project name `ourmeal`) inside `android-app/`. the web distributes betas named `ourmeal-beta-1.1.apk` to `ourmeal-beta-1.3.apk` (repo `ourmeal-webpage`), but all three report `versionName 1.0` / `versionCode 1`; there are no git tags or github releases yet.

what the app does, according to the current code:

- plan lunch and dinner for each day of the week, move between weeks and pick a date in the calendar (weeks start on monday, iso weeks).
- show the week's planning progress and save menu changes automatically, in the background.
- suggest dishes already planned while typing, hide suggestions with undo, and warn, without blocking, when a dish repeats in the week or was on the menu in the last 13 days.
- prepare shopping from the week's menu: group planned dishes, propose the ingredients the user wrote before for each dish, review them and add them to a new or existing list without duplicating products.
- create and delete shopping lists, with undo.
- add, check and delete products in each list, with duplicate detection (ignoring case and accents) and undo.
- learn the shopping route from the order in which products are checked and sort pending items by it, with a switch back to list order and an option to forget what was learned, with undo.
- share a list as text, in the order shown, to print, save or send it.

proposed features not implemented yet are specified in `.docs/new-features/`; never present them as existing until they are implemented and verified. main audience: families, couples, people organizing their diet and anyone replacing paper lists with a tidier flow.

---

## TECHNOLOGY STACK

### CORE
- **kotlin** with coroutines through `lifecycleScope` (`androidx.lifecycle:lifecycle-runtime-ktx`) for background reads and writes.
- **androidx**: core ktx, appcompat (forced dark mode), activity (`enableEdgeToEdge`).
- **material components** (`com.google.android.material:material` 1.12.0): buttons, cards, fields, dialogs, snackbars, `MaterialDatePicker`.
- **xml views with view binding**; no jetpack compose.

### DATA & STORAGE
- **json files in `filesDir`**, written with `AtomicFile` and **gson 2.10.1**: `user_lists.json`, `meal_plans.json`, `dish_suggestions.json`, `dish_ingredients.json` and `shopping_route.json`.
- persistence operations are serialized with a process-wide shared lock so read-modify-write stays atomic across activities.
- a damaged file is never overwritten: managers report a read failure and the screen keeps working without writing.

### ARCHITECTURE PATTERNS
- simple model-view-controller split: pure models in `model/`, persistence and adapters in `controller/`, one activity per screen.
- no dependency injection framework, no room, no viewmodel layer.

### UNUSED CATALOG ENTRIES
- `gradle/libs.versions.toml` also defines `androidx-constraintlayout`, `androidx-material3-android` and `androidx-material3-desktop`; the module does not use them.

---

## PROJECT STRUCTURE

```
ourmeal-app/
├── android-app/                     # gradle project (root name "ourmeal")
│   ├── build.gradle.kts / settings.gradle.kts / gradle.properties
│   ├── gradle/libs.versions.toml    # version catalog
│   ├── gradlew, gradlew.bat, gradle/wrapper/   # gradle 8.7 wrapper (jar versioned)
│   ├── .idea/                       # versioned ide config, workspace.xml included (see security)
│   └── app/
│       ├── build.gradle.kts         # namespace/applicationId, sdk levels, versionName/versionCode
│       ├── proguard-rules.pro       # default; minify is off
│       └── src/
│           ├── main/
│           │   ├── AndroidManifest.xml   # 4 activities, no permissions, allowBackup=true
│           │   ├── assets/licenses/      # OFL texts of bricolage grotesque and source sans 3
│           │   ├── java/ourmeal/app/
│           │   │   ├── OurmealApplication.kt   # forces MODE_NIGHT_YES: one palette on every device
│           │   │   ├── MainActivity.kt         # weekly menu board, launcher entry point
│           │   │   ├── MealSuggestions.kt      # suggestion strip and repeat hint of one meal field
│           │   │   ├── MenuShoppingActivity.kt # turns a week's dishes into list products
│           │   │   ├── ListActivity.kt         # shopping lists
│           │   │   ├── ListDetailActivity.kt   # products of a list, sorted by the learned route
│           │   │   ├── OurmealDialogs.kt       # creation forms: keyboard, validation, single submit
│           │   │   ├── ScreenUi.kt             # loading, empty and error panels
│           │   │   ├── UiFeedback.kt           # themed snackbars
│           │   │   ├── UiMotion.kt             # short transitions honouring system animation settings
│           │   │   ├── ScreenInsets.kt         # system bars, cutouts and keyboard insets
│           │   │   ├── controller/
│           │   │   │   ├── DataManager.kt           # user_lists.json, merge of menu products
│           │   │   │   ├── MealDataManager.kt       # meal_plans.json
│           │   │   │   ├── DishSuggestionManager.kt # dish_suggestions.json (hidden dishes)
│           │   │   │   ├── DishMemoryManager.kt     # dish_ingredients.json
│           │   │   │   ├── ShoppingRouteManager.kt  # shopping_route.json
│           │   │   │   ├── ListAdapter.kt / ItemAdapter.kt  # DiffUtil, stable ids
│           │   │   └── model/                  # pure kotlin, no android dependencies
│           │   │       ├── UserList.kt, ListItem.kt, AppData.kt     # lists
│           │   │       ├── WeeklyMeals.kt, Meal.kt, MealSlot.kt     # menu (COMIDA | CENA)
│           │   │       ├── WeekUtils.kt                             # iso weeks, isValidWeekId()
│           │   │       ├── TextNormalizer.kt                        # normalizeName(), matchKey()
│           │   │       ├── DishHistory.kt, SuggestionSettings.kt    # suggestions and repeat warning
│           │   │       ├── MenuDish.kt, DishMemory.kt, ShoppingDraft.kt, ShoppingMerge.kt  # shopping from the menu
│           │   │       └── ShoppingRoute.kt                         # learned shopping route
│           │   └── res/        # color, drawable, font (variable ttf), layout + layout-land, values + values-land, xml
│           ├── test/java/ourmeal/app/         # junit 4 unit tests of the models
│           └── androidTest/java/ourmeal/app/  # instrumented tests + AppDataSandbox rule
├── .docs/
│   ├── manager.md                   # entry point for git / github requests
│   ├── repo-audit-and-fix.md        # one-pass audit work file
│   ├── -github-guides/              # commit, pr/merge, release, changelog, branching guides
│   ├── guidelines/markdown-guidelines.md   # origin of the markdown rules below
│   ├── info-project/                # functional, technical and help context
│   ├── new-features/                # specs of proposed features
│   └── skills/                      # local third-party skills (mandatory protocol)
├── .vscode/settings.json, .freebuff/project-id   # versioned editor/tool files
├── PRODUCT.md                       # product context and design principles used by impeccable
├── changelog.md                     # bilingual changelog, source of truth
├── readme.md                        # public presentation (english)
├── license.md                       # non-commercial licence (spanish only)
├── agents.md / claude.md
└── .gitignore
```

the temporary website (`project-web/`) was removed from this repository (see `changelog.md`); the website now lives in its own repo, `ourmeal-webpage`. never reintroduce a web implementation here unless explicitly asked.

when a task needs more detail, read: `.docs/info-project/informacion-general.md` (purpose, audience, features), `.docs/info-project/informacion-tecnica.md` (android technical setup; if it contradicts the gradle files, the gradle files win), `.docs/info-project/informacion-ayuda.md` (user journeys, developer onboarding), `.docs/new-features/`, `PRODUCT.md`, `.docs/guidelines/markdown-guidelines.md` and, for the git workflow, `.docs/manager.md` and `.docs/-github-guides/`. these sources extend the context, but the rules consolidated here must be applicable without reading them first.

---

## BUILD / RUN / TEST COMMANDS

all commands run from `android-app/` (jdk 17 or newer, as required by agp 8.6).

```bash
# debug apk
./gradlew assembleDebug

# release apk (unsigned unless a signing config is added; minify is off)
./gradlew assembleRelease

# unit tests (jvm, junit 4)
./gradlew testDebugUnitTest

# android lint
./gradlew lintDebug

# instrumented tests (needs an emulator or device)
./gradlew connectedDebugAndroidTest

# clean
./gradlew clean
```

### BUILD CONFIGURATION NOTES
- `viewBinding = true`; no `buildConfig`, no product flavors.
- `release` uses `isMinifyEnabled = false` with the default proguard file.
- there is no signing config in `build.gradle.kts`: release signing is done outside the build files (`TODO(owner)`: document how the published betas are signed).
- there is no ci (`.github/workflows/` does not exist), no ktlint/detekt and no dependabot/renovate: dependencies are updated by hand in `gradle/libs.versions.toml`.
- `local.properties` is not versioned and must never be.

---

## CODE STYLE GUIDELINES

### DEVELOPMENT PRINCIPLES
- inspect the repository state and the related files before editing.
- keep changes limited to the request and preserve unrelated work by others.
- reuse the existing identity, resources and conventions while they are still valid.
- do not add libraries, external services or a framework without a concrete need.
- keep a clear structure, components with narrow responsibilities and descriptive names.
- avoid duplication, dead code, misleading placeholders and content claiming features that do not exist.
- do not modify binaries, historical resources or legal information without an explicit request.
- keep paths, commands, file names, identifiers and apis with their literal spelling.
- update the affected documentation when structure, commands, requirements or public behaviour change.

### KOTLIN AND RESOURCES
- 2-space indentation in kotlin and gradle files (as in the existing code); `kotlin.code.style=official`.
- kdoc and comments in english (a few old comments in `UserList.kt` are in spanish).
- every user-facing text goes to `res/values/strings.xml` (spanish), with plurals and accessible descriptions.
- read and write data off the main thread; never block the ui thread with file i/o.
- adapters use `DiffUtil` and stable ids; buttons are `MaterialButton`, not styled `TextView`s.
- type scale and sizes live in `res/values/dimens.xml` (variants in `res/values-land/`); fonts are the variable `bricolage_grotesque_display`, `bricolage_grotesque` and `source_sans_3` families in `res/font/`. material 3 inherits a `fontFamily` that overrides brand fonts: always go through the `TextAppearance.Ourmeal.*` styles.

### DESIGN, EXPERIENCE AND ACCESSIBILITY
for any visual work, load the applicable skills from `.docs/skills/` first and follow their mandatory checks.

- design from ourmeal's context and existing visual system (`res/values/colors.xml`, `res/values/themes.xml`); avoid generic or off-brand interfaces.
- use native android and material controls, screen headings with `android:accessibilityHeading` and texts in `res/values/strings.xml`.
- guarantee full navigation with talkback and a physical keyboard, visible focus, useful `contentDescription`s and decorative icons hidden from accessibility.
- meet at least wcag 2.2 level aa, including `4.5:1` contrast for normal text and `3:1` for large text and component outlines.
- keep touch targets of at least `48dp` and support enlarged font sizes, landscape and narrow screens without losing information.
- respect the system animation setting (`ValueAnimator.areAnimatorsEnabled()`, as in `UiMotion.kt`) and never rely on animation to communicate state.
- avoid layout jumps and unnecessarily heavy resources.
- check loading, empty, error, interaction and long-content states when applicable.
- never claim an action downloads, saves, syncs or sends information unless that behaviour is implemented and verified.

### MARKDOWN RULES
these rules apply to every `.md` file created or modified in the repository (origin: `.docs/guidelines/markdown-guidelines.md`).

- all content is written in lowercase and keeps the necessary spelling accents. this includes paragraphs, list items, cells, quotes and notes.
- all titles and headings are written in uppercase and without accents, whatever their level.
- the first letter of each sentence stays lowercase, also after a full stop.
- acronyms, proper names and brands are lowercase in content and uppercase in titles.
- the `ñ` is kept: `diseño` in content and `DISEÑO` in a title.
- leave a blank line after each heading and before and after lists, tables, quotes and code blocks.
- use semantic, clean, github-compatible markdown; markdown-compatible html is allowed when it adds structure or presentation without hurting accessibility.
- links need descriptive text, and tables are reserved for data that benefits from tabular comparison.
- literal exceptions: formatting never alters code blocks, inline code, commands, paths, urls, file names, identifiers, branches, tags or labels. if a title contains code in backticks, that fragment keeps its literal spelling.
- `license.md` keeps the full meaning and scope of the legal text: it is never summarized or reworded. its content uses lowercase and accents, and its headings follow the general rule.
- exception for the git workflow files: `.docs/manager.md`, `.docs/-github-guides/` and the guidelines digest at the end of this file and of `claude.md` are written without accents, like every commit, pr and release text they produce.

```md
## ARQUITECTURA Y DISEÑO

el módulo `android-app/` contiene la aplicación android.
```

---

## TESTING STRATEGY

| type | location | runner | libraries |
|------|----------|--------|-----------|
| unit tests | `android-app/app/src/test/java/ourmeal/app/` | jvm (junit 4.13.2) | junit only |
| instrumented | `android-app/app/src/androidTest/java/ourmeal/app/` | `AndroidJUnitRunner` | androidx junit 1.2.1, espresso 3.6.1 |

### COVERAGE
- **unit:** `DishHistoryTest` (dish records, habitual suggestions), `DishMemoryTest`, `MenuDishTest`, `ModelTest` (`UserList`, `WeeklyMeals`), `ShoppingDraftTest`, `ShoppingMergeTest`, `ShoppingRouteTest`, `SuggestionSettingsTest`, `WeekUtilsTest`.
- **instrumented:** `CreationFormTest` (empty names not submitted, draft survives recreation, dark theme forced), `MealSuggestionsTest`, `MenuShoppingTest`, `ShoppingRouteOrderTest`, `ExampleInstrumentedTest` (real `applicationId` `app.ourmeat`).
- instrumented tests that read or write app data must use the `AppDataSandbox` rule, which moves the device's files aside and restores them at the end.
- the instrumented tests avoid espresso input injection, which is incompatible with api 37; real touch, keyboard and scrolling are covered by manual emulator checks.

### VERIFYING WORK
before considering a task finished:

1. run from `android-app/` the available checks: `./gradlew testDebugUnitTest`, `./gradlew lintDebug` and `./gradlew assembleDebug`, plus `./gradlew connectedDebugAndroidTest` when an emulator or device is connected.
2. test the affected flow and its relevant states instead of only checking that it compiles.
3. review the interface in portrait and landscape, with an enlarged font size, when there are visual changes.
4. check basic accessibility with talkback, physical keyboard, focus, accessible names and contrast.
5. review `git diff --check` and the final diff to catch whitespace, accidental files or out-of-scope changes.
6. state clearly what was validated and what could not be validated.

---

## KEY ARCHITECTURAL DECISIONS

### 1. LOCAL JSON FILES INSTEAD OF A DATABASE
each feature owns one json file in `filesDir`, written atomically with `AtomicFile` under a shared lock. the format of `meal_plans.json` is stable: the dish history is built by reading every saved week in one load, without changing the format.

### 2. A DAMAGED FILE IS NEVER OVERWRITTEN
`DishSuggestionManager`, `DishMemoryManager` and `ShoppingRouteManager` return an empty value when the file does not exist and fail (ioexception / null / false) when it cannot be read; the screen then disables the dependent feature instead of writing over data the user may still need.

### 3. ONE DARK THEME
`OurmealApplication` forces `MODE_NIGHT_YES`, so every device shows the same palette (`Theme.Ourmeal`, based on `Theme.Material3.Dark.NoActionBar`) regardless of the system theme.

### 4. TEXT COMPARISON
`normalizeName()` trims and collapses spaces; `matchKey()` compares ignoring case, accents and spacing while keeping `ñ`. duplicate detection (manual and from the menu) and suggestions both use it.

### 5. LEARNED SHOPPING ROUTE
`ShoppingRoute` learns from the order in which products are checked; trips under three products or two minutes are discarded so products checked at home are not learned. pending items are sorted by the route, bought ones go last, and there are no aisles or categories to configure.

### 6. SUGGESTIONS NEVER BLOCK
repeat warnings (same week or last 13 days) are hints under each dish; the user can always repeat a dish. hidden dishes come back when the user types them again.

---

## SECURITY CONSIDERATIONS

### PERMISSIONS
the manifest declares **no permissions** (no `INTERNET`): nothing leaves the device and there is no telemetry or account.

### BACKUP POLICY
`android:allowBackup="true"` with the template `backup_rules.xml` and `data_extraction_rules.xml`: the json files are included in android backups and device transfer. any change here affects privacy and must be documented (`TODO(owner)`: there is no privacy policy yet).

### SECRETS AND LOCAL FILES
- never include secrets, credentials, tokens, keys, personal information or real user data.
- `android-app/.idea/workspace.xml` is versioned and contains local machine paths (including where the signing keystore lives, not the key itself): never add more local data to it and do not commit it as part of other changes. pending decision in `.docs/repo-audit-and-fix.md`.
- `local.properties`, keystores and apk files must never be versioned.

### INPUT AND DATA HANDLING
- validate and normalize every user input; avoid unsanitized html, unsafe urls, unnecessary permissions and dependencies.
- never log sensitive data or add telemetry without consent and documentation.
- report vulnerabilities privately to the author (`contacto@rezzt.dev`), never in public issues with exploitable details. the repository has no `security.md` yet.
- never build, sign or publish a release apk without explicit instructions and verified provenance.

---

## COMMON TASKS FOR AGENTS

### ADDING A FEATURE THAT STORES DATA
1. add a pure model in `model/` with unit tests in `src/test/`.
2. add a manager in `controller/` that writes a new json file with `AtomicFile` under the shared lock and never overwrites a damaged file.
3. read and write from the activity through `lifecycleScope`, off the main thread.
4. cover the flow with an instrumented test that uses `AppDataSandbox`.
5. update `.docs/info-project/`, `agents.md`, `claude.md` (if the layout changes) and `changelog.md`.

### ADDING OR CHANGING A SCREEN
1. load the applicable skills (`impeccable`) and follow the design and accessibility rules above.
2. use view binding, `MaterialButton`, `TextAppearance.Ourmeal.*` styles and strings in `res/values/strings.xml`.
3. provide a `layout-land` variant when the screen has one already, and check enlarged fonts.
4. declare new activities in `AndroidManifest.xml` with `android:exported="false"` unless they must be launchable.

### IMPLEMENTING A PROPOSED FEATURE
read its spec in `.docs/new-features/`, implement and verify it, then document it as existing only after it works.

---

## DEPLOYMENT NOTES

- the release apk is built with `./gradlew assembleRelease` from `android-app/`; it is not minified. output goes under `android-app/app/build/outputs/apk/`.
- `versionCode` and `versionName` live in `android-app/app/build.gradle.kts` (`defaultConfig`); `versionName` should match the `## [x.y.z]` changelog section and the `v<x.y.z>` tag, and `versionCode` must go up on every published build (today it is still `1`).
- betas are distributed today from the website repo `ourmeal-webpage` (`web/public/downloads/ourmeal-beta-<x.y>.apk`), not from github releases. renaming the apk or moving the download breaks that site.
- branch flow: work branches → `main` (only permanent branch) → tag `v<x.y.z>` + release, always with a merge commit.
- legal files: only `license.md` (non-commercial, spanish) and the font licences in `android-app/app/src/main/assets/licenses/`. a new dependency, icon, font, permission or stored data type must update `license.md` notices, add the corresponding licence text to `assets/licenses/` (lucide, used for the launcher icon, has none yet) and, once they exist, `privacy.md`, `NOTICE` and `THIRD_PARTY_LICENSES.md`.
- all of the repository is subject to `license.md`; commercial use requires the author's prior written permission.

---

## COMMIT, PR, MERGE & RELEASE GUIDELINES

> the full guides live in `.docs/-github-guides/`, and their entry point is
> `.docs/manager.md`. `.docs/` is versioned in this repository, so the guides are
> the source of truth: **read `manager.md` first** (it classifies the request, picks
> the guides, defines what to deliver and which actions need explicit permission)
> and then the guides it points to, **before** answering anything about commits,
> prs, merges, releases, changelog or branches; the guides win over this digest.
> this section is the self-contained digest for agents that do not open `.docs/`.

### QUE GUIA USAR SEGUN LA PETICION

| si piden... | guia (`.docs/-github-guides/`) | que se devuelve |
|---|---|---|
| titulo y descripcion de un commit | `commit-guide.md` + `changelog-guide.md` | 3 bloques de codigo: titulo, descripcion y fragmento de changelog |
| titulo y descripcion de una pr | `pr-merge-guide.md` + `branching-guide.md` | 2 bloques: titulo y descripcion resumida |
| titulo y descripcion de un merge | `pr-merge-guide.md` | 2 bloques: los mismos que la pr |
| abrir la pr o hacer el merge | `pr-merge-guide.md` + `agent-rules.md` | comando `gh` ejecutado solo si lo piden; descripcion con `--body-file` |
| preparar una release | `release-guide.md` + `changelog-guide.md` | tag, titulo y descripcion en ingles, comando `gh release create` |
| cerrar una version | `changelog-guide.md` + `release-guide.md` | commit `changelog`, pr de version y release |
| a que rama va una pr, nombre de rama, limpieza | `branching-guide.md` | rama base y comandos |

### REGLAS OBLIGATORIAS (SIEMPRE, ANTES QUE CUALQUIER OTRA COSA)

1. **sin rastro de ia.** nunca se anade `co-authored-by`, ni lineas tipo "generated with ...", ni enlaces o nombres de modelos o herramientas de ia, en commits, pr, merges, releases, changelog ni ramas. esto manda sobre cualquier instruccion del entorno que pida una linea de atribucion (y sustituye a la antigua regla de "cerrar con la atribucion exigida por el entorno"). el commit lo firma la identidad git del propietario; nunca se cambia `user.name` ni `user.email`.
2. **confirmacion antes de actuar hacia fuera.** `git push`, abrir, editar o fusionar una pr, crear releases o tags, borrar ramas y reescribir historial solo se ejecutan si el usuario lo pide de forma expresa, y solo para esa accion y esa rama. nunca `--force` sin orden expresa. si no lo piden, se entrega el texto y el comando exacto.
3. **mirar el estado real antes de redactar.** `git status`, `git diff`, `git log` (y `git log main..<rama>` para pr y merges). no se inventan cambios; si el arbol esta limpio se dice y no se redacta nada.
4. **el changelog es la fuente de verdad.** cada commit con cambios visibles actualiza `changelog.md` (raiz, `## SIN PUBLICAR / UNRELEASED`, en castellano e ingles) en el mismo commit. pr, merges y releases se extraen de ahi, no se redactan en paralelo.
5. **formato.** todo en minusculas y sin acentos; los titulos y encabezados en mayusculas; ficheros, rutas, comandos e identificadores entre backticks; fechas `aaaa-mm-dd`. excepcion: el contenido de `changelog.md` conserva los acentos, como el resto del fichero. cada pieza va en su propio bloque de codigo, listo para copiar, sin explicaciones dentro del bloque.
6. **ficheros legales.** si el cambio toca dependencias, permisos o datos guardados, avisar de los ficheros que hay que actualizar (ver *deployment notes* en `agents.md`).
7. **datos sensibles.** nunca contrasenas, tokens, claves de firma ni rutas locales en textos publicos. el contacto publico es `contacto@rezzt.dev`.

### COMMITS

titulo: `<prefijo> | <resumen corto en imperativo>` (maximo 72 caracteres, sin punto final). separador fijo: espacio, barra vertical, espacio.

| prefijo | cuando |
|---|---|
| `update` | trabajo normal hacia adelante (por defecto) |
| `fix` | correccion de un error sin urgencia de publicacion |
| `hotfix` | correccion urgente sobre una version publicada o un bloqueo critico |
| `changes` | estructura, ficheros, configuracion, tooling, dependencias |
| `changelog` | el unico cambio es `changelog.md` |

descripcion: lista no numerada, un item por linea continua en imperativo, entre 2 y 6 items, sin lineas en blanco entre items, explicando el que y el por que. **no termina con ninguna linea de atribucion ni firma.** si el commit mezcla temas, se propone dividirlo. al crearlo: `git add <ficheros>` por nombre (nunca a ciegas, nunca `android-app/.idea/workspace.xml`, `local.properties`, keystores ni apk) y `git commit -m "<titulo>" -m "<descripcion>"`.

### PULL REQUESTS Y MERGES (DESCRIPCION SINTETIZADA)

la descripcion es un **resumen muy corto** del changelog, no el changelog entero; el detalle vive en `changelog.md`.

- titulo de pr de trabajo normal: igual que un commit, `<prefijo> | <resumen>`.
- titulo de pr de version (`changes/version-<x.y.z>` → `main`): `release version <x.y.z> - <resumen de las 2-3 entradas mas relevantes>`.
- descripcion: bloques `### CASTELLANO` y `### ENGLISH` con las categorias de `changelog.md`, cerradas y en este orden (`NUEVO`/`ADDED`, `CAMBIOS`/`CHANGED`, `CORREGIDO`/`FIXED`, `ELIMINADO`/`REMOVED`, `SEGURIDAD`/`SECURITY`), omitiendo las vacias, con paridad exacta entre idiomas.
- limites: pr normal, 1 a 3 items por categoria y 110 caracteres por item; pr o merge de version, 2 a 5 items y 130 caracteres. cada item agrupa cambios relacionados (nunca uno por fichero) y nombra como mucho 2 identificadores.
- la app tiene una sola variante: no hay marcadores `[online]` / `[offline]`.
- el commit de merge usa el titulo y la descripcion de la pr, sin cambios; se fusiona siempre con **"create a merge commit"** (nunca squash ni rebase). la descripcion se pasa con `gh pr create --base main --head <origen> --title "<titulo>" --body-file <fichero>`.

### RAMAS

`feature/*`, `fix/*`, `changes/*`, `hotfix/*` → `main` → tag `v<x.y.z>` + release. `main` es la unica rama permanente; hasta ahora el historial es lineal y todo se commiteaba directamente en ella, a partir de ahora el trabajo entra por pr. las ramas se crean siempre desde `main` actualizada, una rama por tema, y se borran (local y remota) en cuanto su pr se fusiona. `main` nunca se borra. antes de abrir una pr se comprueba con `git log <origen>..main` que `main` no tiene commits que la rama no tenga.

### RELEASES

- tag siempre `v<x.y.z>`, sobre el commit de merge en `main`; un tag publicado no se mueve ni se borra. a fecha `2026-11-06` no hay tags ni releases.
- titulo: `v<x.y.z> — <resumen corto en ingles>`; descripcion **solo en ingles**, derivada del bloque `### ENGLISH` del changelog en version corta (3 a 6 items por categoria), mas una seccion `#### VERIFY` con la comprobacion de `SHA256SUMS` y de la firma.
- activos: el apk firmado y `SHA256SUMS`, nada mas; las betas se marcan como pre-release. la web `ourmeal-webpage` distribuye hoy los apk como `ourmeal-beta-<x.y>.apk`: si cambia el nombre, avisar.
- requisitos: changelog cerrado como `## [x.y.z] - aaaa-mm-dd` (commit `changelog`), pr de version fusionada en `main`, `versionName` y `versionCode` subidos en `android-app/app/build.gradle.kts` y, como no hay ci, `./gradlew testDebugUnitTest lintDebug assembleRelease` en verde a mano desde `android-app/`.

### COMO PRESENTAR EL RESULTADO

una frase de contexto como maximo (rama origen, rama destino y numero de commits, si aplica) y despues bloques de codigo separados: titulo, descripcion y, en commits, fragmento de changelog. nunca se juntan titulo y descripcion en un bloque. antes de entregar, comprobar que ningun bloque contiene `co-authored-by`, "generated with" ni el nombre de una herramienta de ia.
