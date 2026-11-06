# GUIA DE PULL REQUESTS Y MERGES

> como devolver el titulo y la descripcion de una pull request y de su commit
> de merge, en version sintetizada, para que solo haya que copiar, pegar y
> aceptar. se aplica cada vez que se pida el titulo y la descripcion de una pr
> o de un merge. lee antes `agent-rules.md`: nada de rastro de ia.

---

## 1. PRINCIPIO

la descripcion de una pr o de un merge es un **resumen muy corto** del
changelog, no el changelog entero. el detalle completo vive en
`changelog.md`; la pr solo orienta a quien revisa.

- la fuente es siempre `changelog.md` (seccion `## SIN PUBLICAR / UNRELEASED` o version ya cerrada) y el diff real de la rama.
- minusculas y sin acentos; los encabezados en mayusculas.
- categorias cerradas y en este orden, las mismas de `changelog.md`: `NUEVO`, `CAMBIOS`, `CORREGIDO`, `ELIMINADO`, `SEGURIDAD` (en ingles `ADDED`, `CHANGED`, `FIXED`, `REMOVED`, `SECURITY`). se omite la que no tenga items.
- sin lineas de atribucion ni pies de pagina (ver `agent-rules.md`).

---

## 2. LIMITES DE LONGITUD

| tipo | items por categoria | longitud maxima de item |
|---|---|---|
| pr de trabajo normal | 1 a 3 | 110 caracteres |
| pr / merge de version | 2 a 5 | 130 caracteres |

- cada item agrupa varios cambios relacionados en una sola linea ("anadir sugerencias de platos y aviso de repeticiones al menu"), nunca uno por fichero.
- se nombran como mucho 2 identificadores por item; el resto se omite.
- los dos idiomas llevan los mismos items en el mismo orden (paridad exacta).

---

## 3. TITULO

### pr de trabajo normal

rama `feature/...`, `fix/...`, `changes/...` o `hotfix/...` hacia `main`. mismo formato que un commit (ver `commit-guide.md`):

```
<prefijo> | <resumen corto en imperativo>
```

si la pr agrupa varios commits, el resumen describe el conjunto.

### pr de version

la que cierra una version en `changelog.md` (rama `changes/version-<x.y.z>` hacia `main`, ver `branching-guide.md`):

```
release version <x.y.z> - <resumen de las 2-3 entradas mas relevantes>
```

ejemplo:

```
release version 1.4.0 - sugerencias de platos, compra desde el menu y recorrido de compra
```

---

## 4. DESCRIPCION

plantilla:

```
### CASTELLANO

#### <categoria>
- <cambio agrupado en una linea>

### ENGLISH

#### <category>
- <same change, one line>
```

ejemplo de pr de version, ya sintetizada:

```
### CASTELLANO

#### NUEVO
- anadir sugerencias de platos y aviso de repeticiones en el menu semanal
- anadir `Preparar la compra` con memoria de ingredientes por plato

#### CAMBIOS
- aplicar las fuentes variables y una escala tipografica semantica a la interfaz
- redisenar los formularios de creacion y los dialogos

#### CORREGIDO
- corregir el color del texto de los snackbars en el tema oscuro
- corregir `WeekUtils.startOfWeek()` con semanas 53 inexistentes

### ENGLISH

#### ADDED
- add dish suggestions and repeat warnings to the weekly menu
- add `Preparar la compra` with per-dish ingredient memory

#### CHANGED
- apply the variable fonts and a semantic type scale to the interface
- redesign the creation forms and dialogs

#### FIXED
- fix the snackbar text colour on the dark theme
- fix `WeekUtils.startOfWeek()` with missing week 53
```

si la pr solo aporta parte de `## SIN PUBLICAR / UNRELEASED`, la descripcion incluye solo esos items. si hace falta contexto extra (por ejemplo, capturas de cambios visuales), va despues de las listas, nunca en su lugar.

---

## 5. COMMIT DE MERGE

- se fusiona siempre con **"create a merge commit"**; nunca squash ni rebase, para conservar el historial de commits.
- github genera la primera linea (`merge pull request #<n> from <rama>`); el titulo y la descripcion del merge son los de la pr, sin cambios.
- si el merge se hace a mano con `git merge --no-ff`, el mensaje usa el mismo titulo y la misma descripcion, sin atribucion.
- el titulo y la descripcion del merge se entregan en dos bloques de codigo separados.

---

## 6. COMO SE ENTREGA

1. una linea de contexto: rama origen, rama destino y numero de commits.
2. bloque de codigo con el titulo.
3. bloque de codigo con la descripcion.
4. si la pr aun no existe y se pide abrirla, el comando exacto (ver abajo), sin ejecutarlo hasta que lo confirmen.

```
gh pr create --base main --head <origen> --title "<titulo>" --body-file <fichero>
```

la descripcion se pasa con `--body-file` para evitar problemas de comillas y saltos de linea.

---

## 7. LISTA DE COMPROBACION

- [ ] la rama base es la que indica `branching-guide.md` (`main`).
- [ ] se ha mirado `git log main..<rama>` y `git diff main...<rama>`.
- [ ] el changelog esta actualizado y la descripcion sale de el.
- [ ] ambos idiomas tienen los mismos items.
- [ ] ningun item supera el limite de longitud.
- [ ] no hay atribucion de ia en ningun bloque.
