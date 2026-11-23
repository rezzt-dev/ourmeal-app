# OURMEAL | FUNCIONALIDADES DE DIFICULTAD MEDIA

> detalle de las nueve funcionalidades que añaden una pantalla, un fichero json nuevo o la migración de uno existente, de 3 a 7 días cada una. ordenadas de menor a mayor tiempo. volver al [índice](indice.md).

## RESUMEN

| id | funcionalidad | tiempo | valor para el usuario | fichero de datos |
|---|---|---|---|---|
| `m1` | productos habituales | 3 a 4 días | alto | `product_suggestions.json` (productos ocultos) |
| `m2` | recibir listas compartidas | 3 a 4 días | alto | ninguno |
| `m3` | recorrido por tienda | 3 a 5 días | medio | `shopping_route.json` (migración) |
| `m4` | básicos de casa | 3 a 5 días | medio | `pantry_staples.json` |
| `m5` | mis platos | 4 a 6 días | alto | ninguno nuevo |
| `m6` | copia de seguridad | 4 a 6 días | muy alto | ninguno |
| `m7` | historial de compras | 4 a 6 días | alto | `purchase_history.json` |
| `m8` | widget del menú de hoy | 4 a 6 días | medio | ninguno |
| `m9` | precios anotados | 5 a 7 días | medio | `product_prices.json` |

## M1. PRODUCTOS HABITUALES

- **qué es:** bajo el formulario de añadir producto, una tira de chips con los productos que el usuario ya añadió en otras listas, filtrados al escribir sin distinguir mayúsculas ni acentos.
- **por qué complementa:** repite con los productos el patrón que ya funciona con los platos. quien compra cada semana lo mismo deja de escribirlo.
- **se apoya en:** el patrón de `MealSuggestions.kt` y `DishHistory.suggestions()`, `matchKey()`, `user_lists.json` y los ingredientes de `dish_ingredients.json`.
- **alcance:**
  - modelo puro `ProductHistory` con pruebas unitarias; excluye los productos que ya están en la lista abierta.
  - ordenación por frecuencia y recencia, con un máximo de chips visibles y desplazamiento horizontal.
  - pulsación larga para dejar de sugerir un producto, con deshacer, en `product_suggestions.json` siguiendo el comportamiento de `dish_suggestions.json`.
- **estados a cuidar:** sin historial (no se muestra la tira), fichero de ocultos dañado (se desactiva la función sin sobrescribir), formulario en horizontal y con fuente ampliada.
- **riesgos:** el historial depende de las listas guardadas, así que borrar una lista borra su historia. `m7` lo resuelve de forma duradera. se evita el desplegable flotante, que se recorta con el teclado abierto.
- **dificultad y tiempo:** media, de 3 a 4 días.
- **depende de:** nada. mejora con `m7`.

## M2. RECIBIR LISTAS COMPARTIDAS

- **qué es:** ourmeal aparece como destino al compartir texto desde otra aplicación y ofrece crear una lista nueva o añadir los productos a una existente, tras revisarlos.
- **por qué complementa:** compartir una lista sin poder recibirla obliga a la otra persona a reescribirla. cierra el circuito entre dos móviles sin cuentas ni servidor, que es lo más cercano a una compra compartida compatible con el principio de que nada sale del dispositivo.
- **se apoya en:** el formato de `shareList()` (nombre, cabecera, resumen, líneas `[x]` y `[ ]`), `withMergedItems()` y `view_shopping_destination.xml` de `Preparar la compra`.
- **alcance:**
  - actividad nueva con filtro `ACTION_SEND` de `text/plain`, la única exportada además de `MainActivity`.
  - analizador tolerante: reconoce el formato de ourmeal y, si no, una línea por producto con o sin viñetas.
  - pantalla de revisión con los productos detectados, casillas para excluir y destino, y resultado con productos añadidos y ya existentes.
- **estados a cuidar:** texto vacío o sin productos reconocidos, más de 200 líneas (se recorta con aviso), nombres de más de 80 caracteres y guardado en curso.
- **riesgos:** es entrada no confiable de otra aplicación: se trata siempre como texto plano, se limita en longitud y número y nunca se escribe sin pasar por la revisión. el formato compartido pasa a ser un contrato y se prueba con textos de ejemplo.
- **dificultad y tiempo:** media, de 3 a 4 días.
- **depende de:** `b6` si las notas deben importarse; se define el formato conjuntamente.

## M3. RECORRIDO POR TIENDA

- **qué es:** perfiles de recorrido con nombre, por ejemplo `Mercadona` o `Carrefour`, cada uno con su propio orden aprendido y asignado a las listas que el usuario quiera.
- **por qué complementa:** `recorrido-de-compra.md` dejó fuera varias tiendas y propuso `Olvidar` al cambiar de una a otra. quien compra en dos sitios pierde lo aprendido cada vez.
- **se apoya en:** `ShoppingRoute`, `ShoppingRouteManager` y el selector de estado de `renderRoute()`.
- **alcance:**
  - `shopping_route.json` pasa a la versión 2 con una lista de perfiles; el recorrido actual se migra a un perfil inicial sin perder paradas ni ajustes.
  - cada lista guarda un perfil opcional y aprende solo en él.
  - selector del perfil en la barra de la lista, con crear, renombrar y olvidar por perfil.
- **estados a cuidar:** usuario con un solo perfil (la interfaz no cambia respecto a hoy), perfil borrado con listas asignadas, fichero de la versión 1 y fichero dañado.
- **riesgos:** es una migración de un fichero que ya tienen los usuarios y que `agents.md` protege (el orden de compra); se prueba con ficheros reales de la versión 1 y se conserva la lectura segura.
- **dificultad y tiempo:** media, de 3 a 5 días.
- **depende de:** nada.

## M4. BASICOS DE CASA

- **qué es:** una lista corta de ingredientes que siempre hay en casa, como la sal o el aceite, que `Preparar la compra` no propone por defecto.
- **por qué complementa:** `recorrido-de-compra.md` descartó una despensa completa porque exige mantener un inventario. esta versión no tiene cantidades, caducidades ni existencias: solo un nombre marcado como `siempre en casa`.
- **se apoya en:** `MenuShoppingActivity`, `ShoppingDraft.ingredientsToAdd()` y `matchKey()`.
- **alcance:**
  - fichero `pantry_staples.json` con versión, claves y nombres, con tope y lectura segura.
  - pulsación larga en un ingrediente para marcarlo; los ingredientes marcados aparecen atenuados con `Ya en casa` y un interruptor para incluirlos esa vez.
  - un resumen `N ingredientes en casa` bajo el botón de confirmar y una gestión sencilla para quitar básicos.
- **estados a cuidar:** todos los ingredientes de un plato son básicos (se explica en lugar de dejar vacío el plato), fichero dañado y accesibilidad del estado atenuado, que no debe depender solo del color.
- **riesgos:** el usuario puede olvidar que marcó un básico y quedarse sin él. el resumen visible y la reversibilidad lo mitigan.
- **dificultad y tiempo:** media, de 3 a 5 días.
- **depende de:** nada. es la base de la despensa con cantidades que `a2` haría posible.

## M5. MIS PLATOS

- **qué es:** una pantalla accesible desde el menú con todos los platos del historial, cuántas veces y cuándo se cocinaron, y sus ingredientes recordados, con búsqueda.
- **por qué complementa:** `compra-desde-el-menu.md` ya preveía una vista de `mis platos` para editar ingredientes fuera del recorrido semanal. hoy un ingrediente mal escrito solo se corrige durante la preparación de una compra y un plato ocultado solo vuelve si se escribe de nuevo.
- **se apoya en:** `toDishRecords()`, `DishHistory`, `DishMemory`, `SuggestionSettings.unhide()` y el editor de ingredientes de `ShoppingDraft`.
- **alcance:**
  - actividad nueva `MyDishesActivity` con `android:exported="false"`, adaptador con `DiffUtil` y variante horizontal.
  - ficha de plato con editar ingredientes, `Volver a sugerir`, `Añadir a una lista` y olvidar los ingredientes recordados.
  - búsqueda con `matchKey()` y orden por última vez cocinado.
- **estados a cuidar:** sin historial, sin resultados, errores de lectura de cualquiera de los tres ficheros, y centenares de platos.
- **decisión previa:** revisa el no objetivo `no se añade una pantalla de gestión del historial` de `historial-de-platos.md`. la persona propietaria debe aprobarlo antes de empezar.
- **dificultad y tiempo:** media, de 4 a 6 días.
- **depende de:** nada. es la base de `a3`.

## M6. COPIA DE SEGURIDAD

- **qué es:** exportar todos los datos a un único archivo que el usuario elige dónde guardar, y restaurarlos desde él tras revisar un resumen.
- **por qué complementa:** todo vive solo en el teléfono: listas, historial de platos, ingredientes y recorrido. la copia automática de android es opaca y no se puede comprobar; perder o cambiar el móvil borra todo.
- **se apoya en:** los cinco ficheros json, los métodos `sanitized()` de cada modelo y el bloqueo compartido de los gestores.
- **alcance:**
  - exportar con el selector de documentos del sistema (`ACTION_CREATE_DOCUMENT`) y restaurar con `ACTION_OPEN_DOCUMENT`: no necesita ningún permiso.
  - archivo con versión de la copia y de la aplicación, y validación completa antes de escribir nada: todo o nada.
  - resumen previo a restaurar con número de listas, semanas y platos, y confirmación explícita porque sustituye los datos actuales.
- **estados a cuidar:** archivo ajeno o dañado, archivo de una versión más nueva, copia que falla a medias (no se toca nada) y un fichero local dañado al exportar (se informa y se exportan los demás).
- **riesgos:** la restauración debe tomar el bloqueo de todos los gestores y mantener intacto lo actual si algo falla. la interfaz no debe prometer sincronización ni guardado en la nube: guarda un archivo donde el usuario decida. el archivo contiene hábitos domésticos y se debe indicar.
- **dificultad y tiempo:** media, de 4 a 6 días.
- **depende de:** nada. conviene hacerlo antes de cambiar ningún formato de datos (`m3`, `a2`).

## M7. HISTORIAL DE COMPRAS

- **qué es:** cada viaje a la tienda queda registrado con su fecha, su lista, el número de productos y la duración, y se puede repetir con un gesto como lista nueva.
- **por qué complementa:** hoy lo comprado se pierde al borrar la lista. el recorrido ya detecta los viajes, pero los descarta una vez aprendidos.
- **se apoya en:** `ShoppingRoute.settle()` y los viajes cerrados, `DataManager.createListWithItems()` y `matchKey()`.
- **alcance:**
  - `RouteChange` devuelve el viaje cerrado y un gestor nuevo lo guarda en `purchase_history.json` con tope de 100 viajes y lectura segura.
  - pantalla `Mis compras` accesible desde `Mis listas`, con detalle del viaje y la acción `Repetir esta compra`.
  - los productos del historial alimentan `m1` aunque la lista original ya no exista.
- **estados a cuidar:** sin compras, fichero dañado y viaje con productos que ya no existen.
- **decisión abierta:** un viaje solo se registra si cumple las reglas del recorrido (tres productos y dos minutos). se debe decidir si una lista que se completa por debajo de esos umbrales también cuenta.
- **dificultad y tiempo:** media, de 4 a 6 días.
- **depende de:** nada. refuerza `m1`, `m9` y `b4`.

## M8. WIDGET DEL MENU DE HOY

- **qué es:** un widget de la pantalla de inicio con la comida y la cena de hoy que abre el menú al tocarlo.
- **por qué complementa:** la información más consultada del menú es qué toca hoy y obliga a abrir la aplicación. el widget la lleva a la pantalla de inicio sin permisos ni notificaciones.
- **se apoya en:** `MealDataManager`, `WeekUtils`, `MainActivity` (ya exportada) y el guardado automático, que es el momento de refrescar el widget.
- **alcance:**
  - `AppWidgetProvider` con `RemoteViews` en dos tamaños, con la paleta oscura de la aplicación y descripciones accesibles.
  - actualización tras cada guardado del menú y al abrir la aplicación; el cambio de día se resuelve sin alarmas exactas.
  - lectura de `meal_plans.json` fuera del hilo principal y bajo el bloqueo compartido.
- **estados a cuidar:** sin platos hoy, fichero ilegible, widget colocado antes de abrir la aplicación por primera vez y texto largo recortado con elipsis y descripción completa.
- **riesgos:** actualizar a medianoche sin permisos de alarma exacta debe verificarse en un dispositivo real; no hay prueba unitaria que lo cubra. los widgets no usan view binding ni material y exigen recursos aparte.
- **dificultad y tiempo:** media, de 4 a 6 días.
- **depende de:** nada.

## M9. PRECIOS ANOTADOS

- **qué es:** un precio opcional por producto, recordado para la siguiente vez, y el total estimado de la lista con los productos sin precio contados aparte.
- **por qué complementa:** es la versión local de una idea ya probada. el comparador de precios con servicio externo se añadió y se revirtió (`6956a1e`, `985268d`) porque necesitaba red y una fuente de datos que no existe. anotar el precio es manual, pero funciona sin conexión y suma valor al historial.
- **se apoya en:** `ListItem` (campo opcional `price_cents`), `matchKey()` para recordar el último precio y el resumen de `updateSummary()`.
- **alcance:**
  - precio como entero en céntimos para evitar errores de coma flotante; entrada tolerante a `2,5` y `2.50`.
  - fichero `product_prices.json` con el último precio por producto y tope de tamaño.
  - total estimado con `N sin precio` en el resumen; el gasto de cada compra se guarda en `m7` si existe.
- **estados a cuidar:** precio no válido, cero, vacío (se quita), importes grandes y lectura accesible del total como una sola frase.
- **riesgos:** la moneda es el euro por defecto y la interfaz no la convierte. el texto compartido no debe incluir precios salvo que el usuario lo elija.
- **dificultad y tiempo:** media, de 5 a 7 días.
- **depende de:** `m7` para el gasto por compra, de forma opcional.
