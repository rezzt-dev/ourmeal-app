# OURMEAL | ANALISIS DE FUNCIONALIDADES PRIMARIAS

> índice del análisis de las funcionalidades primarias que podrían desarrollarse en ourmeal para completar el producto actual. cada funcionalidad se resume en dos líneas y se detalla en el documento de su nivel de dificultad.

## ESTADO DEL DOCUMENTO

| campo | valor |
|---|---|
| estado | propuesta; ninguna de estas funcionalidades está implementada |
| fecha del análisis | 2026-11-23 |
| ámbito | aplicación android en `android-app/` |
| commit de referencia | `141d9bd`, último commit de `main` en el momento del análisis |
| funcionalidades analizadas | 19: 7 de dificultad baja, 9 de media y 3 de alta |
| tiempo total estimado | de 64 a 100 días de trabajo de una persona (13 a 20 semanas) |

## RESUMEN

ourmeal ya cubre el ciclo completo de una compra doméstica: planifica el menú, sugiere platos, avisa de repeticiones, convierte los platos en productos, aprende el recorrido de la tienda y comparte la lista como texto. lo que le falta no es otro ciclo, sino cerrar los huecos de ese ciclo: editar lo escrito, reutilizar listas, recibir lo que se comparte, proteger los datos locales, conservar el historial de compras y llevar el menú fuera de la aplicación.

las 19 propuestas parten de datos y piezas que la aplicación ya tiene (`meal_plans.json`, `user_lists.json`, `dish_ingredients.json`, `shopping_route.json`, `matchKey()`, `DishHistory`, `withMergedItems()`) y respetan sus principios: todo local, sin cuentas, sin categorías que mantener a mano y sin impedir nunca al usuario repetir un plato. las que revisan una decisión anterior o añaden un permiso lo indican de forma expresa.

## COMO LEER LAS ESTIMACIONES

| nivel | criterio | tiempo |
|---|---|---|
| baja | cambia una pantalla o un modelo sin crear ficheros de datos nuevos ni dependencias | hasta 2 días |
| media | añade una pantalla, un fichero json nuevo o una migración de un fichero existente | de 3 a 7 días |
| alta | cambia el modelo de datos de forma transversal, añade un permiso o una dependencia, o revisa una decisión de producto | más de una semana |

- el tiempo es de una persona que ya conoce el repositorio e incluye lo que exigen `agents.md` y `claude.md`: pruebas unitarias del modelo, prueba instrumentada con `AppDataSandbox`, variante `layout-land`, revisión con fuente ampliada, accesibilidad, documentación y `changelog.md`.
- la estimación no incluye revisión de diseño por parte de la persona propietaria ni publicación de una versión.

## DOCUMENTOS

| documento | contenido |
|---|---|
| [estado actual](01-estado-actual.md) | funciones primarias existentes, datos reutilizables, restricciones heredadas y huecos detectados |
| [dificultad baja](02-dificultad-baja.md) | detalle de las funcionalidades `B1` a `B7` |
| [dificultad media](03-dificultad-media.md) | detalle de las funcionalidades `M1` a `M9` |
| [dificultad alta](04-dificultad-alta.md) | detalle de las funcionalidades `A1` a `A3` |
| [descartadas y condicionadas](05-descartadas-y-condicionadas.md) | ideas que no se proponen y por qué, o que dependen de una decisión previa |
| [hoja de ruta](06-hoja-de-ruta.md) | orden recomendado, dependencias y riesgos transversales |

## FUNCIONALIDADES DE DIFICULTAD BAJA

ordenadas de menor a mayor tiempo. detalle en [02-dificultad-baja.md](02-dificultad-baja.md).

- **b1 · modo tienda** · 0,5 a 1 día · [detalle](02-dificultad-baja.md#b1-modo-tienda)
  mantiene la pantalla encendida mientras se compra y permite ocultar lo ya comprado.
  hace cómodo el último tramo del ciclo, el de la tienda, sin tocar los datos.
- **b2 · compartir el menú** · 0,5 a 1 día · [detalle](02-dificultad-baja.md#b2-compartir-el-menu)
  comparte el menú de la semana como texto legible, igual que ya se comparte una lista.
  sirve para avisar a quien cocina o come en casa sin que tenga la aplicación.
- **b3 · editar nombres** · 1 día · [detalle](02-dificultad-baja.md#b3-editar-nombres)
  permite corregir el nombre de una lista o de un producto sin borrarlo y volver a crearlo.
  hoy un error de escritura obliga a eliminar y empezar de nuevo.
- **b4 · reutilizar listas** · 1 a 2 días · [detalle](02-dificultad-baja.md#b4-reutilizar-listas)
  añade desmarcar todo, quitar lo comprado y duplicar una lista, con deshacer.
  convierte una lista ya usada en la base de la siguiente compra.
- **b5 · copiar y vaciar el menú** · 1 a 2 días · [detalle](02-dificultad-baja.md#b5-copiar-y-vaciar-el-menu)
  copia un día o la semana anterior sobre la semana visible y vacía un día o la semana entera.
  cubre las semanas con rutina fija que hoy obligan a escribir catorce huecos a mano.
- **b6 · notas por producto** · 1 a 2 días · [detalle](02-dificultad-baja.md#b6-notas-por-producto)
  usa el campo `notes` que ya existe en `ListItem` para anotar una cantidad o una marca bajo el producto.
  la nota viaja en el texto compartido y no cambia cómo se detectan los duplicados.
- **b7 · vínculo entre menú y compra** · 1 a 2 días · [detalle](02-dificultad-baja.md#b7-vinculo-entre-menu-y-compra)
  recuerda de qué semana viene cada lista y avisa en el menú de si su compra ya está preparada.
  une las dos mitades de la aplicación, que hoy solo se relacionan al pulsar `Preparar la compra`.

## FUNCIONALIDADES DE DIFICULTAD MEDIA

ordenadas de menor a mayor tiempo. detalle en [03-dificultad-media.md](03-dificultad-media.md).

- **m1 · productos habituales** · 3 a 4 días · [detalle](03-dificultad-media.md#m1-productos-habituales)
  sugiere bajo el formulario de añadir producto los que el usuario ya compró en otras listas.
  repite con los productos el patrón de las sugerencias de platos, con la misma comparación sin acentos.
- **m2 · recibir listas compartidas** · 3 a 4 días · [detalle](03-dificultad-media.md#m2-recibir-listas-compartidas)
  permite compartir un texto con ourmeal y convertirlo en una lista nueva o en productos de una existente.
  cierra el circuito de compartir sin cuentas ni servidor y sirve a las parejas con dos móviles.
- **m3 · recorrido por tienda** · 3 a 5 días · [detalle](03-dificultad-media.md#m3-recorrido-por-tienda)
  guarda un recorrido aprendido distinto para cada tienda en lugar de uno solo.
  sustituye la acción de olvidar al cambiar de tienda por perfiles que no se pisan entre sí.
- **m4 · básicos de casa** · 3 a 5 días · [detalle](03-dificultad-media.md#m4-basicos-de-casa)
  deja marcar ingredientes que siempre hay en casa para que `Preparar la compra` no los proponga.
  es la versión ligera de una despensa: sin cantidades, sin caducidades y sin inventario que mantener.
- **m5 · mis platos** · 4 a 6 días · [detalle](03-dificultad-media.md#m5-mis-platos)
  añade una pantalla con todos los platos del historial, sus ingredientes recordados y cuándo se cocinaron.
  permite editar ingredientes fuera de la semana, volver a sugerir platos ocultos y añadir un plato a una lista.
- **m6 · copia de seguridad** · 4 a 6 días · [detalle](03-dificultad-media.md#m6-copia-de-seguridad)
  exporta todos los datos a un archivo elegido por el usuario y los restaura desde él.
  protege los datos que solo viven en el teléfono y facilita cambiar de dispositivo sin ningún permiso.
- **m7 · historial de compras** · 4 a 6 días · [detalle](03-dificultad-media.md#m7-historial-de-compras)
  guarda cada viaje a la tienda y permite repetir una compra anterior con un solo gesto.
  conserva lo que hoy se pierde al borrar una lista y alimenta las sugerencias de productos.
- **m8 · widget del menú de hoy** · 4 a 6 días · [detalle](03-dificultad-media.md#m8-widget-del-menu-de-hoy)
  muestra en la pantalla de inicio la comida y la cena de hoy y abre el menú al tocarlo.
  lleva el menú fuera de la aplicación sin permisos ni notificaciones.
- **m9 · precios anotados** · 5 a 7 días · [detalle](03-dificultad-media.md#m9-precios-anotados)
  permite anotar el precio de cada producto, recordarlo y ver el total estimado de la lista.
  es la alternativa local y sin red al comparador de precios que se probó y se revirtió.

## FUNCIONALIDADES DE DIFICULTAD ALTA

ordenadas de menor a mayor tiempo. detalle en [04-dificultad-alta.md](04-dificultad-alta.md).

- **a1 · recordatorios locales** · 1 a 2 semanas · [detalle](04-dificultad-alta.md#a1-recordatorios-locales)
  avisa con notificaciones de planificar el menú o de que la compra está pendiente, si el usuario lo activa.
  exige el permiso de notificaciones y una dependencia nueva, por lo que cambia la promesa de no pedir permisos.
- **a2 · cantidades y raciones** · 2 a 3 semanas · [detalle](04-dificultad-alta.md#a2-cantidades-y-raciones)
  convierte los ingredientes en nombre, cantidad y unidad, y suma los repetidos entre platos.
  revisa un no objetivo de `compra-desde-el-menu.md` y migra el fichero de ingredientes recordados.
- **a3 · fichas de plato** · 2 a 3 semanas · [detalle](04-dificultad-alta.md#a3-fichas-de-plato)
  añade a cada plato un tiempo, unas raciones y unos pasos opcionales, con un modo cocinar.
  convierte la memoria de platos en un recetario ligero sin fotos ni red; revisa otro no objetivo previo.

## IDEAS QUE NO SE PROPONEN

las ideas que contradicen los principios del producto o necesitan una decisión previa están en [05-descartadas-y-condicionadas.md](05-descartadas-y-condicionadas.md): comparador de precios con red, sincronización en la nube, menús automáticos, categorías por pasillo, escáner de códigos de barras y franjas adicionales como el desayuno.
