# GUIA DE RELEASES

> como preparar el titulo, la descripcion y el tag de una release de github.
> se aplica cuando una pr de version ya esta fusionada en `main`. lee antes
> `agent-rules.md`: la release no lleva ningun rastro de ia.

---

## 1. REQUISITOS PREVIOS

1. `changelog.md` tiene la version cerrada como `## [x.y.z] - aaaa-mm-dd` (ver `changelog-guide.md`).
2. la pr de version → `main` esta fusionada con "create a merge commit".
3. `android-app/app/build.gradle.kts` tiene `versionName` `x.y.z-beta` (o el sufijo que corresponda) y un `versionCode` mayor que el de la release anterior. hoy vale `versionName = "1.0"` y `versionCode = 1`.
4. el apk esta firmado con la clave de release del propietario y existe su `SHA256SUMS`.
5. no hay ci en el repositorio (no existe `.github/workflows/`): antes de etiquetar se ejecutan a mano, desde `android-app/`, `./gradlew testDebugUnitTest lintDebug assembleRelease`. cuando exista ci, debe estar en verde sobre el commit que se va a etiquetar.

---

## 2. TAG

- siempre `v<x.y.z>`, mismo numero que la cabecera del changelog, sin sufijos extra (ejemplo: `v1.4.0`).
- se crea sobre el commit de merge en `main`, nunca sobre una rama de trabajo.
- no se mueve ni se borra un tag ya publicado; si hay un error, se publica una version nueva.
- a fecha `2026-11-06` el repositorio no tiene tags ni releases: el primer tag que se cree abre la serie.

```
git tag -a v<x.y.z> -m "v<x.y.z>"
git push origin v<x.y.z>
```

---

## 3. TITULO

```
v<x.y.z> — <resumen corto en ingles>
```

- en ingles, minusculas y sin acentos; resume las 2-3 entradas mas relevantes.
- ejemplo: `v1.4.0 — dish suggestions, shopping from the menu & learned shopping route`.

---

## 4. DESCRIPCION

- **solo en ingles**: la release es la cara publica del proyecto.
- reutiliza el bloque `### ENGLISH` de la version cerrada, con sus encabezados `####` en mayusculas, pero en version corta: de 3 a 6 items por categoria, agrupando cambios relacionados (misma regla de sintesis que `pr-merge-guide.md`, seccion 2).
- se omite la categoria sin items; no se reordenan las categorias.
- cierra con una seccion de verificacion:

```
#### VERIFY

- download `ourmeal-<x.y.z>-beta.apk` and `SHA256SUMS` from the assets
- check the checksum with `sha256sum -c SHA256SUMS`
- check the signature with `apksigner verify --print-certs ourmeal-<x.y.z>-beta.apk`
```

- la huella del certificado de firma aun no esta publicada en ningun fichero del repositorio (`TODO(owner)`): cuando exista `security.md`, se anade una linea que remita a ella.
- sin lineas de atribucion ni enlaces a herramientas de ia.

---

## 5. ACTIVOS

- el apk firmado y `SHA256SUMS`; nada mas (sin keystores, sin `local.properties`, sin mapeos privados).
- si la release se marca como pre-release (versiones beta), se usa `--prerelease`.
- la web `ourmeal-webpage` distribuye hoy los apk desde `web/public/downloads/ourmeal-beta-<x.y>.apk`, no desde github. si cambia el nombre del apk o el canal de descarga, se avisa de que hay que actualizar esa web.

---

## 6. COMANDO

```
gh release create v<x.y.z> <apk> SHA256SUMS --title "<titulo>" --notes-file <fichero> --prerelease
```

se entrega el comando sin ejecutar; publicar una release es visible para todos y exige confirmacion (ver `agent-rules.md`).

---

## 7. LISTA DE COMPROBACION

- [ ] el tag coincide con la cabecera del changelog.
- [ ] titulo y descripcion estan en ingles.
- [ ] los items salen del bloque `### ENGLISH` del changelog.
- [ ] el apk y `SHA256SUMS` corresponden a la misma compilacion, y `versionName` del apk coincide con el tag.
- [ ] no hay atribucion de ia ni datos sensibles.
