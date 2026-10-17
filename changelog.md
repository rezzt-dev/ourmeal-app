# CHANGELOG

## SIN PUBLICAR / UNRELEASED

### CASTELLANO

#### NUEVO
- incorpora `project-web/` como sitio publico de OurMeal con una pagina principal, una demo interactiva de menus y listas de la compra, y las paginas legales de aviso legal, privacidad y cookies.
- añade documentacion de proyecto, ayuda, arquitectura y contribucion en `.docs/`, `agents.md` y `claude.md`.

#### CAMBIOS
- reorganiza la aplicacion Android desde la raiz del repositorio hacia `android-app/` para separar el cliente movil del resto de productos.
- añade `android-app/.gitignore` para excluir artefactos de Gradle, compilacion, IDE y configuracion local del proyecto Android.
- configura la aplicacion web con Astro, TypeScript, Tailwind CSS, tipografias locales, iconos Lucide y comandos de comprobacion, compilacion y previsualizacion.
- mantiene `project-web/` como implementacion web temporal importada mientras se completa la pagina antes de retirarla.
- actualiza la estructura de configuracion y desarrollo del repositorio con `.vscode/`, los bloqueos de dependencias web y la configuracion Gradle del proyecto Android.

#### ELIMINADO
- retira la estructura Android anterior ubicada directamente en la raiz del repositorio para sustituirla por la nueva organizacion en `android-app/`.

### ENGLISH

#### ADDED
- add `project-web/` as the public OurMeal website with a home page, an interactive meal and shopping-list demo, and legal notice, privacy, and cookies pages.
- add project, help, architecture, and contribution documentation in `.docs/`, `agents.md`, and `claude.md`.

#### CHANGED
- move the Android application from the repository root to `android-app/` to separate the mobile client from the other products.
- add `android-app/.gitignore` to exclude Gradle, build, IDE, and local configuration artifacts from the Android project.
- configure the web application with Astro, TypeScript, Tailwind CSS, local fonts, Lucide icons, and check, build, and preview commands.
- keep `project-web/` as a temporary imported web implementation while the page is completed before its removal.
- update the repository development and configuration structure with `.vscode/`, web dependency lockfiles, and the Android Gradle configuration.

#### REMOVED
- remove the previous Android structure located directly in the repository root and replace it with the new `android-app/` organization.
