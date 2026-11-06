# OurMeal — auditoría y corrección completa del repositorio

> fichero de trabajo para un agente de ia (claude code) que opera en el clon local de la app android
> (`ourmeal-app/`, código en `android-app/`). contiene todo lo necesario para auditar y corregir
> documentación, aspectos legales, higiene del repositorio y versión/release en una sola pasada.
>
> cómo usarlo: abre claude code en la raíz de este repositorio y escribe
> `lee .docs/repo-audit-and-fix.md y ejecútalo de principio a fin`.

---

## 0. PROMPT DE LANZAMIENTO

```text
Eres el agente responsable de dejar el repositorio OurMeal (app android de menús semanales y
listas de la compra) profesional, completo y consistente con las betas que distribuye su web,
en inglés y castellano. Sigue este documento fase por fase, en orden. No es asesoría legal: todo
texto legal que redactes se marca para revisión de un abogado.

Reglas de ejecución:
1. Fase 1 es de solo lectura. Al terminarla, haz TODAS las preguntas de la sección 3 en una sola
   interacción (AskUserQuestion, agrupadas) y espera. No preguntes nada más después, salvo bloqueo real.
   Lo que no se responda se resuelve con el valor "por defecto" de la sección 3 y se anota como
   TODO(owner) en el informe final.
2. Lee antes `claude.md`, `agents.md`, `.docs/manager.md`, `.docs/-github-guides/` y
   `.docs/guidelines/markdown-guidelines.md`. Son la fuente de verdad: si contradicen este
   documento, gana el repositorio y lo indicas en el informe. Para trabajo visual, aplica antes el
   protocolo de skills de `agents.md`.
3. Nunca ejecutes sin confirmación expresa: push (menos aún forzado), reescritura de historial,
   crear/mover/borrar tags, crear/editar/publicar releases, cambiar ajustes del repo en GitHub,
   cambiar la rama por defecto, tocar el repositorio `ourmeal-webpage`. Prepara el comando exacto
   y lístalo en el informe final.
4. Verifica toda afirmación contra el código y contra GitHub (`gh`). No inventes datos legales,
   técnicos, cifras ni capturas. Si falta un dato: `TODO(owner): <qué falta>`.
5. Trabaja en una rama nueva `changes/repo-audit-and-fix` creada desde `main`.
   Commits pequeños y temáticos según la sección 6. Sin push ni PR hasta que yo lo apruebe.
6. Todo documento nuevo o modificado debe existir en inglés y en castellano con paridad exacta.
7. Termina con el informe de la sección 8.
```

---

## 1. CONTEXTO VERIFICADO (a 2026-11-06)

hechos comprobados con `git` y `gh`. el agente debe **re-verificarlos**, no darlos por buenos.

| tema | hallazgo |
|---|---|
| repositorio | `https://github.com/rezzt-dev/ourmeal-app`, público, rama por defecto `main`. descripción: "Application to keep track of meals and dinners, and also to create and export shopping lists.". homepage vacía. sin topics. |
| ramas | solo `main` en el remoto. historial lineal de 20 commits, sin ningún commit de merge: todo se ha commiteado directamente en `main`. los cuatro primeros commits (2025-09-10) no siguen el formato `<prefijo> \| <resumen>` (`Initial commit`, `Create README.md`, `udpated \| ...`, `update`). |
| tags / releases | ninguno (`gh release list` vacío, `git ls-remote --tags` vacío). |
| versión en código | `versionName = "1.0"`, `versionCode = 1` en `android-app/app/build.gradle.kts`. |
| versiones publicadas | la web (`ourmeal-webpage`, repo privado) distribuye `ourmeal-beta-1.1.apk`, `ourmeal-beta-1.2.apk` y `ourmeal-beta-1.3.apk` desde `web/public/downloads/`. `aapt2 dump badging` de los tres: `package app.ourmeat`, `versionCode 1`, `versionName 1.0`. ninguna de esas versiones existe en el changelog ni como tag. |
| `applicationId` | `app.ourmeat` (con `t`), mientras el paquete kotlin es `ourmeal.app`. cambiarlo rompería las actualizaciones de los apk ya instalados. |
| changelog | `changelog.md` en la raíz, con todo el historial en `## SIN PUBLICAR / UNRELEASED` y ninguna versión cerrada. categorías `NUEVO` / `CAMBIOS` / `CORREGIDO` / `ELIMINADO` (no `ANADIDO` / `CAMBIADO`). contenido con acentos y algunas mayúsculas (`OurMeal`, `Android`). |
| ci | no hay `.github/` ni workflows. tampoco dependabot ni renovate. |
| licencia | `license.md`: licencia de uso no comercial, © 2026 Juan García Cazallas ("rezzt-dev"), **solo en castellano**. GitHub la detecta como `Other` (`licenseInfo.key = other`), pero el perfil de comunidad (`/community/profile`) devuelve `license: null` y `readme: null`. |
| readme | `readme.md` en inglés, desfasado: dice "kotlin (100%)", no describe sugerencias, compra desde el menú ni recorrido de compra, y enlaza la licencia como `./LICENSE`, fichero que no existe (es `license.md`): enlace roto. |
| ficheros de comunidad | no existen `security.md`, `contributing.md`, `code_of_conduct.md`, plantillas de issue/pr ni `SUPPORT.md`. salud de comunidad en GitHub: 42 %. |
| privacidad (código) | el manifest no declara ningún permiso (ni `INTERNET`). los datos viven en `filesDir` como json (`user_lists.json`, `meal_plans.json`, `dish_suggestions.json`, `dish_ingredients.json`, `shopping_route.json`). `android:allowBackup="true"` con `backup_rules.xml` y `data_extraction_rules.xml` de plantilla: los datos entran en la copia de seguridad y la transferencia entre dispositivos. no hay política de privacidad. |
| terceros | fuentes Bricolage Grotesque y Source Sans 3 con su OFL en `android-app/app/src/main/assets/licenses/`. el icono del lanzador procede de Lucide (`utensils`, ISC) y no tiene aviso de licencia en el repo ni en la app. dependencias androidx, material y gson (Apache 2.0) sin `NOTICE` ni `THIRD_PARTY_LICENSES`. |
| ficheros versionados que no deberían | `android-app/.idea/` completo, incluido `workspace.xml` con rutas locales de la máquina del propietario (avd, carpeta de descargas y la ubicación de la keystore de firma; no contiene la clave). `.freebuff/project-id` (identificador de una herramienta) y `.vscode/settings.json`. `local.properties` y keystores **no** están versionados. |
| `.gitignore` | `.gitignore` raíz con reglas de un proyecto android en la raíz (`/.idea/workspace.xml`, `/local.properties`) que ya no casan con `android-app/`. el changelog dice que se añadió `android-app/.gitignore`, pero solo existe `android-app/app/.gitignore`. |
| `.docs/` versionado | se versiona por decisión del propietario: `.docs/manager.md`, `.docs/-github-guides/`, `.docs/guidelines/markdown-guidelines.md`, `.docs/info-project/`, `.docs/new-features/` y `.docs/skills/` (214 ficheros de 8 skills de terceros: `impeccable`, `shadcn`, `vercel-*`, `frontend-design`, `web-design-guidelines`, `writing-guidelines`), ninguna con fichero de licencia propio: **punto a revisar de licencias**. |
| dependencias sin uso | `gradle/libs.versions.toml` define `androidx-constraintlayout`, `androidx-material3-android` y `androidx-material3-desktop`, que el módulo no usa. `isMinifyEnabled = false` en `release`. |
| enlaces externos | `ourmeal-webpage` sirve los apk por ruta relativa (`/downloads/ourmeal-beta-1.x.apk`), enlaza `https://github.com/rezzt-dev` y su aviso legal remite a `.../ourmeal-webpage/blob/main/license.md`, no a este repo. no hay enlaces a releases de este repo. **no romper los nombres de apk** sin avisar. |
| firma del apk | no verificada. existe `apksigner` en `~/Android/Sdk/build-tools/`; hay que comprobar que las tres betas comparten certificado. |

---

## 2. FASES

### Fase 1 — auditoría (solo lectura)

1. `git fetch --all --tags`; revisa `git log --oneline --graph --decorate --all -n 40` y confirma que no hay ramas ni tags nuevos.
2. Copia los apk de `ourmeal-webpage/web/public/downloads/` a un directorio nuevo y vacío fuera del repo; trátalos como no confiables. Calcula `sha256sum`, lista el manifest (`aapt2 dump badging`) y comprueba `apksigner verify --print-certs`. Averigua de qué commit salió cada beta (fecha del fichero, clases y recursos frente a `git log`).
3. Inventario real: manifest, dependencias de `android-app/gradle/libs.versions.toml`, fuentes (`res/font/`), avisos de `assets/licenses/`, iconos (Lucide), `backup_rules.xml`, `data_extraction_rules.xml`, ficheros json que escribe la app.
4. Estado de `.docs/info-project/` y `.docs/new-features/` frente al código (qué funciones existen de verdad).
5. Busca secretos y datos personales: keystores, tokens, `local.properties`, correos, rutas locales (`git grep -nIE`). Revisa `android-app/.idea/`, `.vscode/` y `.freebuff/` versionados.
6. Entrega el informe de hallazgos (confirmados / refutados / nuevos) y pasa a la sección 3.

### Fase 2 — higiene del repositorio

- `.gitignore`: reglas para `android-app/` (`android-app/.idea/` salvo lo que se decida conservar, `android-app/local.properties`, `android-app/build/`, `android-app/.gradle/`), `.vscode/`, `.freebuff/`, keystores (`*.jks`, `*.keystore`), `*.apk`, `*.aab`.
- `git rm -r --cached android-app/.idea/workspace.xml .freebuff .vscode` según proceda (los ficheros locales se conservan). el propietario tiene cambios sin commitear en `workspace.xml`: no los pierdas.
- `.docs/` se queda versionado. sobre `.docs/skills/`, solo **anotar** la procedencia y licencia de cada skill para revisión; no se purga ni se deja de versionar.
- Comprobar que `claude.md`/`agents.md` siguen siendo autosuficientes (el digest de guidelines no depende de abrir `.docs/`).

### Fase 3 — documentos legales (EN + ES, cada uno con paridad)

formato bilingüe: un único fichero con inglés primero y castellano después, separados por encabezados, o `X.md` + `X.es.md` enlazados entre sí. elige uno, justifícalo en una línea y aplícalo a todos.

| fichero | contenido mínimo |
|---|---|
| `license.md` | traducción fiel al inglés de la licencia no comercial actual, cláusula de prevalencia (preguntar), garantía/ausencia de garantía, limitación de responsabilidad, terminación, ley aplicable y jurisdicción (preguntar), contacto `contacto@rezzt.dev`. Aclarar que **no** es una licencia de código abierto aprobada por la OSI. Evaluar si renombrar a `LICENSE.md` mejora la detección de GitHub y arreglar el enlace `./LICENSE` del readme. |
| `NOTICE` y `THIRD_PARTY_LICENSES.md` | cada dependencia usada (androidx, material, gson), fuente (Bricolage Grotesque, Source Sans 3: OFL, **verifica la versión**) e icono (Lucide, ISC): nombre, versión, licencia, URL y texto o enlace. Añadir el aviso de Lucide a `assets/licenses/`. |
| `privacy.md` | datos tratados (nada sale del dispositivo: sin permisos, sin `INTERNET`, sin telemetría ni cuentas), qué ficheros json se guardan y qué contienen, copia de seguridad y transferencia (hoy activas por `allowBackup`), derechos del usuario (RGPD / LOPDGDD si aplica), menores, cambios de la política, contacto. |
| `security.md` | versiones soportadas (tabla real), canal privado de informe (preguntar; sugerir GitHub private vulnerability reporting o `contacto@rezzt.dev`), plazos, divulgación coordinada, alcance. Sin recompensas. Huella del certificado de firma si se verifica. |
| `code_of_conduct.md` | Contributor Covenant 2.1 con contacto de cumplimiento. |
| `contributing.md` | setup (`android-app/`, JDK 17, Android Studio), convención de commits, changelog bilingüe, comprobaciones de gradle, reglas de accesibilidad y de markdown del repo. |
| `.github/` | plantillas de issue y PR bilingües, `CODEOWNERS`, `SUPPORT.md`; proponer un workflow de ci mínimo (`testDebugUnitTest`, `lintDebug`, `assembleDebug`) solo si el propietario lo acepta. |

### Fase 4 — readme y changelog

- `readme.md` EN + ES: funciones verificadas (menú semanal, sugerencias y aviso de repeticiones, preparar la compra, listas, recorrido de compra, compartir como texto), requisitos (android 14+), instalación del apk desde la web, verificación del SHA-256, compilación desde `android-app/`, estructura, privacidad, licencia no comercial (sin la palabra "open source"), estado (beta). Enlace correcto a `license.md`.
- `changelog.md`: decidir con el propietario cómo reflejar las betas 1.1-1.3 ya distribuidas (sección 3) y cerrar `## SIN PUBLICAR / UNRELEASED` como `## [x.y.z] - aaaa-mm-dd` (commit con prefijo `changelog`). Comprobar que cada ítem existe en el código. Mantener categorías (`NUEVO` / `CAMBIOS` / ...) y estilo del fichero.

### Fase 5 — documentación técnica y de usuario (EN + ES)

auditar `.docs/info-project/` contra el código y completar lo que falte; si el propietario quiere documentación pública, proponer una carpeta versionada bilingüe con paridad exacta:

- menú semanal: navegación por semanas, selector de fecha con inicio en lunes, progreso de la semana, guardado en segundo plano
- sugerencias de platos, `Dejar de sugerir` con deshacer, aviso de repetición en la semana o en los últimos 13 días
- preparar la compra desde el menú: agrupación de platos, memoria de ingredientes (`dish_ingredients.json`), fusión sin duplicados
- listas y productos: duplicados sin mayúsculas ni acentos, deshacer, compartir como texto
- recorrido de compra: viajes mínimos (3 productos, 2 minutos), `Seguir mi recorrido`, `Olvidar`
- accesibilidad (talkback, teclado, 48 dp, horizontal) y tema oscuro fijo
- capturas: solo si puedes generarlas; si no, `TODO(owner): screenshot <pantalla>`

### Fase 6 — versión y release (solo preparar)

preparar, sin ejecutar:

1. cambio de `versionName`/`versionCode` según lo acordado (sección 3), con nota de que exige reconstruir el apk.
2. texto de release en inglés: título `v<x.y.z> — <summary en minúsculas, sin acentos>`, cuerpo = bloque `### ENGLISH` del changelog con categorías `ADDED / CHANGED / FIXED / REMOVED / SECURITY`, marcada como pre-release.
3. plan para las betas ya distribuidas con `versionCode 1`: (a) publicar la siguiente con `versionCode` > 1 y dejar las anteriores como históricas; (b) etiquetar retroactivamente los commits de los que salieron, si se identifican; (c) solo documentarlo. recomendar una.
4. `SHA256SUMS` del apk y nota de verificación de firma.
5. impacto en `ourmeal-webpage`: nombres `ourmeal-beta-<x.y>.apk` en `web/src/pages/index.astro` y `web/src/components/SiteFooter.astro`, copias en `web/public/downloads/` y `.docs/resources/`; listar los cambios concretos.
6. comandos `gh` exactos listos para pegar (tag, release, upload), sin ejecutarlos.

### Fase 7 — metadatos de GitHub (lista para que los aplique el propietario)

descripción en inglés actualizada, homepage (preguntar: no hay dominio público verificado de la web), topics, licencia detectable, social preview, private vulnerability reporting, protección de `main`, comandos `gh api` exactos.

---

## 3. DECISIONES A PREGUNTAR (una sola vez, tras la fase 1)

| # | pregunta | por defecto si no hay respuesta |
|---|---|---|
| 1 | número de la primera versión cerrada y cómo tratar las betas 1.1-1.3 de la web | `1.4.0`, `versionName = "1.4.0-beta"`, `versionCode = 2`; las betas anteriores se citan como históricas en el readme |
| 2 | publicar releases en GitHub o seguir distribuyendo solo desde la web | releases en GitHub con apk y `SHA256SUMS`; la web enlaza o copia el mismo apk |
| 3 | `applicationId` `app.ourmeat` | mantenerlo (cambiarlo rompe actualizaciones); documentarlo |
| 4 | idioma y formato de los docs raíz | `X.md` bilingüe (EN primero, ES después) |
| 5 | cláusula de prevalencia | inglés prevalece |
| 6 | jurisdicción y ley aplicable | `TODO(owner)` |
| 7 | correo de contacto para privacidad, seguridad y conducta | `contacto@rezzt.dev`; sugerir además GitHub private vulnerability reporting |
| 8 | copia de seguridad (`allowBackup`) | mantenerla y documentarla en `privacy.md` |
| 9 | dejar de versionar `android-app/.idea/`, `.vscode/` y `.freebuff/` | sí, conservando las copias locales |
| 10 | añadir un workflow de ci | no por ahora; documentar las comprobaciones manuales |
| 11 | nombre de la entidad titular | "Juan García Cazallas (rezzt-dev)" |

---

## 4. REGLAS DE ESTILO

- commits, changelog, PR y release: `.docs/-github-guides/`. títulos de commit en minúsculas y sin acentos; el contenido de `changelog.md` conserva acentos (`markdown-guidelines.md`); fechas `aaaa-mm-dd`.
- documentación y readme: reglas de `.docs/guidelines/markdown-guidelines.md` (contenido en minúsculas con acentos, encabezados en mayúsculas sin acentos); castellano e inglés con el mismo orden, mismos títulos traducidos, mismas tablas y cifras.
- nombres de ficheros, rutas, comandos e identificadores entre backticks.
- sin emojis decorativos, sin superlativos de marketing, sin afirmar que la app guarda, sincroniza o envía algo que no hace.

---

## 5. COMPROBACIONES (ejecutar y pegar resultados en el informe)

```bash
# enlaces relativos de todos los markdown (excluye las skills de terceros)
git ls-files '*.md' | grep -v '^.docs/skills/' | while read f; do grep -oE '\]\((\./|\.\./)?[^)#h][^)]*\)' "$f" \
  | sed -E 's/^\]\(|\)$//' | while read l; do
    [ -e "$(dirname "$f")/$l" ] || echo "ROTO: $f -> $l"; done; done

# paridad de items castellano / ingles en el changelog (los dos numeros deben coincidir)
awk '/^### CASTELLANO/{s=1} /^### ENGLISH/{s=2} /^- /{c[s]++} END{print c[1], c[2]}' changelog.md

# permisos y backup reales vs documentados
grep -n 'uses-permission\|allowBackup\|dataExtractionRules\|fullBackupContent' android-app/app/src/main/AndroidManifest.xml

# rutas locales o secretos versionados
git grep -nIE '/home/|/mnt/|\.jks|\.keystore|storePassword' -- ':!.docs/skills'

# verificación del proyecto (si se tocó código o recursos)
cd android-app && ./gradlew testDebugUnitTest lintDebug assembleDebug --stacktrace

# apk distribuidos por la web (copias fuera del repo)
sha256sum ourmeal-beta-*.apk
~/Android/Sdk/build-tools/36.1.0/aapt2 dump badging ourmeal-beta-1.3.apk | head -1
~/Android/Sdk/build-tools/36.1.0/apksigner verify --print-certs ourmeal-beta-1.3.apk
```

---

## 6. PLAN DE COMMITS (rama `changes/repo-audit-and-fix`)

orden sugerido; ajusta al estado real del repo.

1. `changes | dejar de versionar idea, vscode y freebuff y rehacer el gitignore`
2. `update | anadir la licencia bilingue, NOTICE y THIRD_PARTY_LICENSES`
3. `update | anadir la politica de privacidad bilingue`
4. `update | anadir security, code of conduct y contributing en ingles y castellano`
5. `update | anadir plantillas de issue y pr bilingues y SUPPORT`
6. `update | rehacer el readme bilingue con datos verificados`
7. `changes | alinear versionName y versionCode con la version acordada` (solo si se acordó)
8. `changelog | cerrar la seccion de changelog para la version <x.y.z>`

cada cuerpo: lista `-` de una línea por ítem, qué y por qué. sin línea de atribución.

---

## 7. DEFINICIÓN DE HECHO

- [ ] ningún hallazgo de la sección 1 queda sin resolver, propuesto o anotado como `TODO(owner)`
- [ ] `license.md`, `NOTICE`, `THIRD_PARTY_LICENSES.md`, `privacy.md`, `security.md`, `code_of_conduct.md`, `contributing.md` y `readme.md` existen en EN y ES con paridad
- [ ] el readme no dice "open source", describe solo funciones existentes y su enlace a la licencia funciona
- [ ] Lucide y las fuentes tienen su aviso de licencia en el repo y en la app
- [ ] `android-app/.idea/workspace.xml`, `.vscode/` y `.freebuff/` ya no están versionados (o la decisión contraria está anotada)
- [ ] las comprobaciones de la sección 5 pasan o sus fallos están explicados
- [ ] `changelog.md` cerrado con versión y fecha, bilingüe, con sus categorías
- [ ] propuesta de release en inglés, formato de categorías, pre-release, plan de `versionCode` y `SHA256SUMS` listos
- [ ] ninguna acción prohibida de la sección 0 se ha ejecutado sin confirmación
- [ ] los nombres de apk que usa `ourmeal-webpage` siguen siendo válidos o el cambio está documentado

---

## 8. INFORME FINAL (formato de entrega)

1. **resumen**: qué se hizo, en cinco líneas como máximo.
2. **hallazgos**: tabla confirmado / refutado / nuevo, con evidencia (comando o ruta).
3. **ficheros**: creados, modificados y eliminados del índice.
4. **decisiones aplicadas**: las respondidas y las resueltas por defecto.
5. **`TODO(owner)`**: lista numerada de datos que faltan.
6. **para revisión legal**: puntos concretos que debe revisar un abogado.
7. **acciones pendientes de mi confirmación**: comandos exactos (push, PR, tag, release, ajustes de GitHub).
8. **título y descripción de la PR** en el formato del repositorio, en bloques de código separados.
9. **cambios necesarios en `ourmeal-webpage`**: ficheros y valores concretos.
