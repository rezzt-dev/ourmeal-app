# OURMEAL | HOJA DE RUTA

> orden recomendado de las funcionalidades, dependencias entre ellas y riesgos que comparten. es una propuesta de secuencia, no un compromiso: cada funcionalidad se implementa tras su aprobación. volver al [índice](indice.md).

## ORDEN RECOMENDADO

| fase | objetivo | funcionalidades | tiempo |
|---|---|---|---|
| 1 | pulir lo cotidiano sin tocar formatos de datos | `b1`, `b3`, `b4`, `b6`, `b2`, `b5`, `b7` | de 6 a 11 días |
| 2 | proteger los datos y cerrar el circuito de compartir | `m6`, `m2`, `m1`, `m5` | de 14 a 20 días |
| 3 | completar el ciclo de la compra | `m7`, `m3`, `m4`, `m8`, `m9` | de 19 a 29 días |
| 4 | revisar decisiones de producto y añadir permisos | `a1`, `a2`, `a3` | de 25 a 40 días |

### POR QUE ESTE ORDEN

- **la fase 1 va primero** porque no cambia ningún formato, entrega mejoras que el usuario nota enseguida y deja listos `b3` (formulario de edición) y `b7` (vínculo) que otras reutilizan.
- **`m6` abre la fase 2** porque las migraciones de `m3`, `a2` y `a3` tocan ficheros con datos del usuario que `agents.md` manda preservar. conviene que exista una forma de copiar antes de migrar.
- **`m2` sigue a `m6`** porque comparten la validación de entrada no confiable, y a `b6`, porque la nota viaja en el texto compartido y su formato se fija una sola vez.
- **`m5` antes de `a3`** porque es su pantalla de entrada, y porque resuelve la gestión del historial que `m1` y `m7` también necesitan.
- **la fase 4 queda al final** porque las tres revisan una decisión previa o un permiso y necesitan aprobación de la persona propietaria.

## DEPENDENCIAS

| funcionalidad | necesita | mejora con |
|---|---|---|
| `b6` | nada | `b3` (formulario compartido) |
| `b7` | nada | `a1` |
| `m1` | nada | `m7` (historial duradero) |
| `m2` | nada | `b6`, `b2` (formato de texto) |
| `m3` | nada | `m6` antes de migrar |
| `m5` | aprobación de la persona propietaria | `m1` |
| `m7` | nada | `b4`, `m1`, `m9` |
| `m9` | nada | `m7` (gasto por compra) |
| `a1` | aprobación del permiso | `b7` |
| `a2` | aprobación del no objetivo | `m6`, `b6`, `m4` |
| `a3` | `m5` y aprobación del no objetivo | `a2` |

las no listadas (`b1`, `b2`, `b3`, `b4`, `b5`, `m4`, `m6` y `m8`) no dependen de ninguna otra.

## FUNCIONALIDADES QUE CONVIENE EMPEZAR PRIMERO

si hay que elegir cinco por valor frente a coste, estas son las recomendadas:

1. **`m6` copia de seguridad:** protege todo lo que el usuario ha construido y desbloquea con seguridad las migraciones posteriores.
2. **`b3` editar nombres:** es el hueco más elemental, cuesta un día y lo usan las dos pantallas de listas.
3. **`b4` reutilizar listas:** convierte compras repetidas en un gesto.
4. **`b5` copiar y vaciar el menú:** el equivalente para las semanas de rutina.
5. **`m2` recibir listas compartidas:** da sentido real a la acción de compartir que ya existe.

## RIESGOS TRANSVERSALES

| riesgo | afecta a | mitigación |
|---|---|---|
| migrar ficheros con datos del usuario | `m3`, `a2`, `a3` | prueba con ficheros de la versión anterior, lectura segura y `m6` antes |
| las tres pantallas principales ya superan las 580 líneas | `b1`, `b3`, `b4`, `b5`, `b7`, `m1`, `m4` | extraer cada funcionalidad a una clase propia, como `MealSuggestions.kt` |
| permisos y componentes exportados | `a1`, `m2` | declararlos en la documentación, validar toda entrada externa y probar el rechazo |
| cambios del formato del texto compartido | `b2`, `b6`, `m2` | tratar el formato como un contrato con pruebas de textos de ejemplo |
| conflictos entre funcionalidades que tocan `ListItem` | `b6`, `m9`, `a2` | añadir campos opcionales, leer con `sanitized()` y probar con listas antiguas |
| interfaz sobrecargada en las barras de acciones | `b1`, `b4`, `m3` | agrupar acciones secundarias en un menú y mantener el objetivo táctil de 48 dp |

## COMO CERRAR CADA FUNCIONALIDAD

cada una sigue las tareas comunes de `agents.md` y la verificación de la aplicación:

1. modelo puro con pruebas unitarias en `app/src/test/`.
2. gestor con `AtomicFile` y bloqueo compartido, si hay un fichero nuevo; nunca sobrescribe uno dañado.
3. lectura y escritura con `lifecycleScope` fuera del hilo principal.
4. prueba instrumentada con `AppDataSandbox`.
5. `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` desde `android-app/`.
6. revisión en vertical, en horizontal y con fuente ampliada, y comprobación con teclado físico y lector de pantalla.
7. actualización de `.docs/info-project/`, `agents.md`, `claude.md` y `changelog.md`, y documentar como existente solo cuando funcione y se haya verificado.
