# GUIA DE RAMAS

> ramas del repositorio, flujo de versiones y que rama es la base de cada pull
> request. sirve para decidir el destino de una pr sin tener que preguntarlo.

---

## 1. RAMAS PERMANENTES

| rama | funcion |
|---|---|
| `main` | unica rama permanente y rama por defecto en github; recibe todas las ramas de trabajo y de ella salen los tags `v<x.y.z>` |

a fecha `2026-11-06` el remoto solo tiene `main`, el historial es lineal (ningun commit de merge) y no hay tags: hasta ahora todo se ha commiteado directamente en `main`. a partir de esta guia, el trabajo entra por pr.

---

## 2. RAMAS DE TRABAJO

nombre en minusculas, con barra y guiones:

| prefijo | uso | ejemplo |
|---|---|---|
| `feature/` | funcionalidad nueva | `feature/dish-history` |
| `fix/` | correccion de error | `fix/snackbar-dark-text` |
| `changes/` | estructura, tooling, documentacion | `changes/repo-management-guides` |
| `hotfix/` | urgencia sobre una version publicada | `hotfix/crash-on-damaged-file` |

- se crean siempre desde `main` actualizada.
- una rama, un tema; si el tema crece, se divide.
- una rama de trabajo se borra (local y remota) en cuanto su pr esta fusionada.
- el cierre de una version usa una rama `changes/version-<x.y.z>` con el commit `changelog` y, si hace falta, el commit `changes` que sube `versionName` y `versionCode`.

---

## 3. FLUJO DE UNA VERSION

```
feature/* , fix/* , changes/* , hotfix/*   →   main   →   tag v<x.y.z> + release
```

| paso | origen | destino | tipo de pr | titulo |
|---|---|---|---|---|
| 1 | `feature/*`, `fix/*`, `changes/*`, `hotfix/*` | `main` | trabajo normal | `<prefijo> \| <resumen>` |
| 2 | `changes/version-<x.y.z>` | `main` | version | `release version <x.y.z> - <resumen>` |
| 3 | `main` | — | release de github | `v<x.y.z> — <resumen en ingles>` |

- todos los pasos se fusionan con "create a merge commit".
- antes de abrir una pr se comprueba que el destino no tiene commits que la rama no tenga: `git log <origen>..main`. si los tiene, se actualiza la rama primero.

---

## 4. CI

- no hay ci: el repositorio no tiene `.github/workflows/`.
- hasta que exista, antes de fusionar se ejecutan a mano, desde `android-app/`, `./gradlew testDebugUnitTest lintDebug assembleDebug` (y `connectedDebugAndroidTest` si hay dispositivo).
- cuando exista ci, no se fusiona una pr con la ci en rojo.

---

## 5. LIMPIEZA

tras fusionar una pr de trabajo:

```
git checkout main
git pull --ff-only
git branch -d <rama>
git push origin --delete <rama>
```

`main` nunca se borra.
