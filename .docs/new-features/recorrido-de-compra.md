# OURMEAL | RECORRIDO DE COMPRA

> especificación funcional, diseño técnico y decisiones de la funcionalidad que aprende en qué orden el usuario coge los productos en la tienda y ordena las listas según ese recorrido. el documento es autónomo.

## ESTADO DEL DOCUMENTO

| campo | valor |
|---|---|
| estado | implementada el 2026-10-22 |
| fecha del análisis | 2026-10-22 |
| ámbito | aplicación android en `android-app/` |
| commit de referencia | `b1180bd`, último commit de `main` en el momento del análisis |
| funcionalidades relacionadas | `.docs/new-features/compra-desde-el-menu.md`, cuyos productos se benefician del orden aprendido sin cambios en su código |

## RESUMEN

ourmeal ya lleva al usuario del menú a la lista de la compra, pero la lista se queda en la puerta de la tienda: los productos aparecen en el orden en que se escribieron, los comprados se quedan entre los pendientes y quien compra recorre la lista de arriba abajo una y otra vez buscando lo siguiente.

esta funcionalidad convierte el gesto que el usuario ya hace, marcar cada producto al cogerlo, en conocimiento: el orden de los marcados es el recorrido real por su tienda. a partir del primer viaje, los pendientes de cualquier lista aparecen en ese orden y lo comprado baja al final, de modo que el siguiente producto está siempre arriba. no hay pasillos, categorías ni configuración.

## PROBLEMA Y OBJETIVOS

### PROBLEMA

- **existente**: `ListDetailActivity` muestra `list.items` en el orden guardado; los productos nuevos se insertan arriba y los marcados se quedan en su sitio.
- **existente**: al marcar un producto solo se guardan `is_completed` y `updated_at`; la secuencia de marcados, que describe el recorrido, se pierde.
- **existente**: las apps de lista habituales resuelven el orden con categorías o pasillos que el usuario tiene que mantener a mano; ourmeal no tiene categorías y añadirlas contradiría su personalidad tranquila.

### OBJETIVOS

1. que el siguiente producto que hay que coger esté siempre arriba de la lista.
2. aprender sin pedir nada nuevo: solo a partir de los marcados que el usuario ya hace.
3. compartir lo aprendido entre listas, porque la tienda es la misma.
4. no estropear el recorrido con marcados que no reflejan un paseo por la tienda.
5. que el usuario pueda volver al orden de la lista y olvidar lo aprendido.

### NO OBJETIVOS

- no se gestionan varias tiendas: un único recorrido, que se adapta si cambia la tienda y se puede olvidar.
- no se usan ubicación, permisos, red ni servicios externos.
- no se crean categorías ni se infiere el sitio de un producto nunca marcado a partir de su nombre.

## SELECCION Y JUSTIFICACION

| alternativa | valor cotidiano | originalidad en la app | encaje y viabilidad | decisión |
|---|---|---|---|---|
| recorrido de compra aprendido | alto: cada viaje a la tienda | alta: aprende de un gesto existente, sin configuración | alto: modelo puro, un fichero json, una pantalla | elegida |
| rellenar la semana con platos habituales | medio | media | contradice el no objetivo de no generar menús automáticos | descartada |
| despensa con lo que ya hay en casa | medio | media | exige mantener un inventario a mano | descartada |
| plantillas o copia de semanas | medio | baja | sencillo, pero incremental | descartada |
| categorías por pasillo | medio | baja | exige mantenimiento y choca con la personalidad del producto | descartada |

**decisión tomada**: el recorrido aprendido cierra el ciclo menú → lista → tienda, que es el tramo que la aplicación aún no acompañaba, y lo hace con el gesto que el usuario ya realiza. los ingredientes que añade «preparar la compra» quedan ordenados en cuanto se han comprado una vez.

## COMPORTAMIENTO

### APRENDIZAJE

- cada producto marcado como comprado se añade al viaje abierto con su clave `matchKey()` y la hora. si se vuelve a marcar, pasa al final; si se desmarca, sale del viaje sin tocar lo aprendido antes.
- un viaje termina tras 45 minutos sin marcados (`ROUTE_TRIP_GAP_MS`). se cierra al marcar el siguiente producto o al abrir una lista.
- al cerrarse, cada producto del viaje se observa en su rango, de 0 al primero a 1 al último, y su posición guardada se acerca a esa observación con peso `max(1 / (viajes + 1), 0.3)`: los primeros viajes pesan igual y los siguientes nunca menos de 0,3, para seguir un cambio de tienda en pocos viajes.
- **decisión tomada**: un viaje con menos de 3 productos (`ROUTE_MIN_TRIP_STOPS`) o de menos de 2 minutos (`ROUTE_MIN_TRIP_MS`) no se aprende. así no cuentan los productos marcados de golpe en casa después de comprar.
- se aprende con cualquier orden en pantalla, también con el interruptor desactivado.

### ORDEN DE LA LISTA

- con `Seguir mi recorrido` activo: primero los pendientes ubicados, por posición; después los pendientes que el recorrido aún no conoce, en el orden de la lista; al final los comprados, en el orden de la lista.
- con el interruptor desactivado, la lista se ve exactamente como antes de esta funcionalidad.
- al marcar un producto, la lista conserva su desplazamiento por posición, de modo que el siguiente pendiente ocupa el lugar del marcado. con teclado físico, el foco pasa a la fila que ocupa ese lugar.
- compartir usa el orden mostrado, así quien recibe la lista también sigue el recorrido.

### TEXTOS E INTERFAZ

- bajo el nombre de la lista, una línea resume el estado: aprendiendo, productos sin ubicar, cuántos pendientes siguen el recorrido, todos ubicados, orden de la lista o recorrido no disponible.
- bajo `Productos`, el interruptor `Seguir mi recorrido` y, cuando hay algo aprendido, la acción `Olvidar`, con confirmación destructiva y deshacer.
- al abrir una lista tras un viaje terminado, un snackbar indica de cuántos productos se aprendió el orden, salvo que la lista anuncie el resultado de «preparar la compra».

## DISEÑO TECNICO

| pieza | responsabilidad |
|---|---|
| `model/ShoppingRoute.kt` | `ShoppingRoute`, `RouteStop`, `RouteTick` y `RouteChange`; `tick()`, `untick()`, `settle()`, `forget()` y `sanitized()`; `inRouteOrder()` y `routePlacement()` para la lista. lógica pura probada en jvm. |
| `controller/ShoppingRouteManager.kt` | lectura y escritura de `shopping_route.json` con `AtomicFile`, `gson` y un bloqueo de proceso. devuelve `null` si no puede leer o escribir y nunca sobrescribe un fichero dañado. |
| `ListDetailActivity.kt` | carga y cierra el viaje junto a la lista, ordena lo mostrado, registra los marcados tras guardarlos y aplica el interruptor y el olvido en orden de llamada con un `Mutex`. |
| `res/layout/view_route_order.xml` | interruptor y acción `Olvidar`, incluidos en las variantes vertical y horizontal. |

formato de `shopping_route.json`:

```json
{
  "version": 1,
  "list_order": false,
  "stops": [{ "key": "platano", "position": 0.05, "trips": 3, "updated_at": 1792663058754 }],
  "trip": [{ "key": "leche", "at": 1792663058754 }]
}
```

- **decisión tomada**: la preferencia se guarda invertida (`list_order`) porque `gson` crea los objetos sin el constructor de kotlin y un booleano ausente llegaría como `false`; así un fichero sin el campo sigue el recorrido.
- el recorrido guarda como máximo 500 productos (`ROUTE_STOP_CAP`) y 200 marcados en el viaje abierto (`ROUTE_TRIP_TICK_CAP`).
- los cambios de un marcado solo actualizan `stops` y `trip` en memoria, y los del interruptor solo `list_order`, para que una escritura no pise la otra.

## PRUEBAS

- `ShoppingRouteTest`: viaje abierto, desmarcado, cierre por intervalo, descarte de viajes cortos o rápidos, pesos del aprendizaje, olvido, orden y ubicación de productos, saneado y serialización.
- `ShoppingRouteOrderTest`: orden según el recorrido, interruptor recordado, producto marcado que baja y entra en el viaje, aprendizaje al abrir la lista, fichero dañado que no se usa ni se sobrescribe, y olvido con deshacer.

## RIESGOS Y LIMITES CONOCIDOS

- **supuesto**: con el orden aprendido en pantalla, el usuario tiende a marcar en ese orden, lo que refuerza el recorrido existente. los productos se marcan al cogerlos, así que el efecto debería ser pequeño; un cambio real de recorrido se corrige con el peso mínimo de 0,3 o con `Olvidar`.
- el rango normalizado estira los viajes parciales: un viaje con pocos productos sitúa el último en 1 aunque no esté al fondo de la tienda. la media entre viajes lo compensa.
- un único recorrido no distingue dos tiendas distintas que se alternen.
