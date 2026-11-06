# OURMEAL — CLAUDE.MD

> this file exists so any ai agent always has the git workflow rules of ourmeal at
> hand. for the project rules (order of precedence, mandatory skill protocol,
> markdown rules, accessibility, verification), the architecture, the tech stack and
> the build commands, see [`agents.md`](./agents.md): those rules are mandatory too
> and are not repeated here. this file only carries the commit / pr / merge /
> release conventions.
>
> **when the user asks for a commit, pr, merge or release title/description,
> follow the section below first. never add ai attribution of any kind.**
>
> repository layout: `changelog.md`, `license.md`, `readme.md`, `PRODUCT.md`,
> `agents.md` and `claude.md` live at the root; the android project lives in
> `android-app/` (module `android-app/app/`). there are no community files
> (`security.md`, `contributing.md`, `code_of_conduct.md`) and no `.github/` folder
> yet. `.docs/` is versioned: `.docs/manager.md` and `.docs/-github-guides/` (git
> workflow), `.docs/repo-audit-and-fix.md` (audit work file),
> `.docs/guidelines/markdown-guidelines.md`, `.docs/info-project/`,
> `.docs/new-features/` and `.docs/skills/`.

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
