# GUIA DEL CHANGELOG

> como se escribe y se cierra `changelog.md` (raiz del repositorio). es la
> fuente de verdad de commits, pull requests, merges y releases: nada se
> redacta en paralelo, todo se extrae de aqui.

---

## 1. ESTRUCTURA

```
# CHANGELOG

## SIN PUBLICAR / UNRELEASED

## [x.y.z] - aaaa-mm-dd

### CASTELLANO

#### NUEVO
- <cambio>

### ENGLISH

#### ADDED
- <change>
```

- la seccion `## SIN PUBLICAR / UNRELEASED` esta siempre arriba y recoge lo que aun no se ha publicado.
- las versiones cerradas van de la mas reciente a la mas antigua.
- cada version lleva `### CASTELLANO` y `### ENGLISH` con paridad exacta de items.
- a fecha `2026-11-06` no hay ninguna version cerrada: todo el historial esta en `## SIN PUBLICAR / UNRELEASED`.

---

## 2. CATEGORIAS (CERRADAS Y EN ESTE ORDEN)

| castellano | english | que va |
|---|---|---|
| `NUEVO` | `ADDED` | funcionalidades, ficheros o documentos nuevos |
| `CAMBIOS` | `CHANGED` | cambios en comportamiento, estructura o documentacion existente |
| `CORREGIDO` | `FIXED` | errores corregidos |
| `ELIMINADO` | `REMOVED` | codigo, ficheros o funciones retirados |
| `SEGURIDAD` | `SECURITY` | correcciones o endurecimiento con impacto de seguridad |

se omite la categoria que no tenga items. no se crean otras. los nombres `NUEVO` y `CAMBIOS` son los que ya usa `changelog.md`; no se renombran.

---

## 3. REGLAS DE ESTILO

- minusculas en el contenido; titulos y encabezados en mayusculas.
- a diferencia de las guias, el contenido conserva los acentos y la `ñ`, como exige `.docs/guidelines/markdown-guidelines.md` y como esta escrito el resto del fichero.
- un item por linea, en imperativo (`añade`, `corrige`, `retira`, como el resto del changelog), empezando por `-`.
- ficheros, rutas, comandos e identificadores entre backticks.
- explica el efecto visible o el motivo, no el detalle de implementacion linea a linea.
- la app tiene una sola variante: no se usan marcadores `[online]` / `[offline]`.
- sin menciones a herramientas de ia ni a quien escribio el item.

---

## 4. CUANDO SE ACTUALIZA

1. **en cada commit** con cambios visibles: se anaden los items a `## SIN PUBLICAR / UNRELEASED`, en los dos idiomas, en el mismo commit.
2. **al preparar una pr o un merge**: se comprueba que el changelog cubre todo el diff; si falta algo, se anade antes de abrirla.
3. **al cerrar una version**: ver seccion 5.

---

## 5. CERRAR UNA VERSION

1. comprobar que `## SIN PUBLICAR / UNRELEASED` esta completo y con paridad entre idiomas.
2. renombrarlo a `## [x.y.z] - aaaa-mm-dd` (fecha de cierre) y dejar debajo una seccion `## SIN PUBLICAR / UNRELEASED` vacia.
3. subir `versionName` y `versionCode` en `android-app/app/build.gradle.kts` si aun no se ha hecho (commit `changes`).
4. commit con prefijo `changelog`:

```
changelog | cerrar la seccion de changelog para la version <x.y.z>
```

5. abrir la pr de version (ver `pr-merge-guide.md`) y, al fusionar en `main`, crear la release (ver `release-guide.md`).

---

## 6. NUMERACION

- esquema `<x.y.z>-beta` en `versionName` mientras el proyecto este en beta (la web publica `ourmeal-beta-1.1.apk` a `ourmeal-beta-1.3.apk`); `x.y.z` en estable. el numero que toca al primer cierre es una decision del propietario (ver `.docs/repo-audit-and-fix.md`).
- `x` cambia con rupturas grandes; `y` con funcionalidades nuevas; `z` con correcciones.
- el `versionCode` solo sube; nunca se reutiliza. hoy es `1` y los tres apk publicados en la web lo comparten.

---

## 7. LISTA DE COMPROBACION

- [ ] los dos idiomas tienen los mismos items.
- [ ] solo hay categorias de la lista cerrada.
- [ ] ningun item queda sin backticks en identificadores.
- [ ] la fecha de la version cerrada usa `aaaa-mm-dd`.
- [ ] no hay atribucion de ia.
