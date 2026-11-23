# OURMEAL | FUNCIONALIDADES DE DIFICULTAD BAJA

> detalle de las siete funcionalidades que se pueden desarrollar en menos de dos días cada una, sin ficheros de datos nuevos ni dependencias. ordenadas de menor a mayor tiempo. volver al [índice](indice.md).

## RESUMEN

| id | funcionalidad | tiempo | valor para el usuario | toca |
|---|---|---|---|---|
| `b1` | modo tienda | 0,5 a 1 día | medio | `ListDetailActivity` |
| `b2` | compartir el menú | 0,5 a 1 día | medio | `MainActivity`, `model/` |
| `b3` | editar nombres | 1 día | alto | `ListActivity`, `ListDetailActivity`, `OurmealDialogs.kt` |
| `b4` | reutilizar listas | 1 a 2 días | alto | `ListDetailActivity`, `ListActivity`, `DataManager` |
| `b5` | copiar y vaciar el menú | 1 a 2 días | alto | `MainActivity`, `WeeklyMeals` |
| `b6` | notas por producto | 1 a 2 días | medio | `ListDetailActivity`, `ItemAdapter` |
| `b7` | vínculo entre menú y compra | 1 a 2 días | medio | `UserList`, `MainActivity`, `MenuShoppingActivity` |

## B1. MODO TIENDA

- **qué es:** una acción en la barra de la lista que mantiene la pantalla encendida mientras se compra y un filtro que oculta los productos ya comprados.
- **por qué complementa:** el recorrido aprendido ya deja arriba lo siguiente que hay que coger; el modo tienda quita lo que estorba (la pantalla que se apaga con el carro en la mano y lo ya cogido).
- **se apoya en:** `ListDetailActivity` y `displayedItems()`, que ya decide qué productos se muestran y en qué orden.
- **alcance:**
  - activar `FLAG_KEEP_SCREEN_ON` en la ventana: no necesita permiso y se libera solo al salir de la pantalla.
  - filtro `Ocultar comprados` aplicado en `displayedItems()` sin tocar los datos.
  - contador visible de los que quedan, para que ocultar no parezca borrar.
- **estados a cuidar:** al marcar el último producto con el filtro activo se muestra un estado de compra terminada, no una lista vacía; el deshacer de borrados sigue funcionando; el estado sobrevive a girar la pantalla.
- **decisión abierta:** conservar la elección entre sesiones exigiría un ajuste persistente. se recomienda no guardarla y empezar siempre con el modo desactivado.
- **riesgos:** el texto compartido debe seguir incluyendo los comprados aunque estén ocultos.
- **dificultad y tiempo:** baja, de 0,5 a 1 día.
- **depende de:** nada.

## B2. COMPARTIR EL MENU

- **qué es:** una acción en el menú que comparte la semana visible como texto: un día por línea con su comida y su cena.
- **por qué complementa:** una lista ya se comparte como texto; el menú, que es lo que más interesa a quien cocina o come en casa, no.
- **se apoya en:** el patrón de `shareList()`, `WeekUtils` para el rango de fechas y `WeeklyMeals.DAYS` para el orden.
- **alcance:**
  - función pura `WeeklyMeals.toShareText()` en `model/`, con pruebas unitarias.
  - cabecera con el nombre de la aplicación y el rango de la semana, y los huecos vacíos como un guion.
  - misma gestión de error que `share_error` cuando no hay aplicación para compartir.
- **estados a cuidar:** con la semana vacía la acción queda deshabilitada y explica por qué; los platos largos se conservan enteros.
- **riesgos:** ninguno relevante. el texto no debe prometer que el receptor podrá importarlo.
- **dificultad y tiempo:** baja, de 0,5 a 1 día.
- **depende de:** nada.

## B3. EDITAR NOMBRES

- **qué es:** una acción para renombrar una lista o un producto, con el mismo formulario de creación ya relleno.
- **por qué complementa:** hoy un error de escritura obliga a eliminar y volver a crear. es el hueco más básico de las dos pantallas de listas.
- **se apoya en:** `UserList.name` y `ListItem.name` ya son `var`; `OurmealDialogs.kt` ya valida, limita a 80 caracteres y bloquea el envío doble; `matchKey()` detecta duplicados.
- **alcance:**
  - pulsación larga y acción de accesibilidad `Editar`, como la de `Dejar de sugerir`, porque tocar un producto ya lo marca como comprado.
  - detección de duplicados que ignora el propio producto.
  - guardado con `DataManager.updateList()` y confirmación con snackbar y deshacer.
- **estados a cuidar:** nombre vacío, nombre igual al de otro producto, nombre sin cambios (no se guarda) y guardado en curso (acción deshabilitada).
- **riesgos:** renombrar un producto cambia su `matchKey()`; la posición aprendida en `shopping_route.json` queda con la clave antigua. es aceptable, porque el tope de 500 paradas la expulsa con el tiempo, pero debe documentarse.
- **dificultad y tiempo:** baja, 1 día.
- **depende de:** nada. `b6` reutiliza este formulario.

## B4. REUTILIZAR LISTAS

- **qué es:** tres acciones sobre una lista: desmarcar todo, quitar los comprados y duplicarla como lista nueva con todo pendiente.
- **por qué complementa:** muchas compras se repiten casi iguales cada semana; hoy hay que reescribirlas o desmarcar producto a producto.
- **se apoya en:** `DataManager.createListWithItems()`, `UserList.snapshot()` para el deshacer y `ListAdapter` con `DiffUtil`.
- **alcance:**
  - desmarcar todo y quitar comprados piden confirmación cuando son destructivos y ofrecen deshacer.
  - duplicar propone un nombre editable y abre la lista nueva.
  - las tres acciones se agrupan en un menú de la barra, sin sobrecargarla.
- **estados a cuidar:** lista sin comprados (acción deshabilitada), lista vacía y guardado en curso.
- **riesgos:** desmarcar todo en bloque no es un viaje de compra y no debe enseñar nada al recorrido; hay que evitar que dispare el mismo camino que `untick()` y probarlo con `ShoppingRouteTest`.
- **dificultad y tiempo:** baja, de 1 a 2 días.
- **depende de:** nada. aumenta el valor de `m7`.

## B5. COPIAR Y VACIAR EL MENU

- **qué es:** acciones en el menú para copiar un día a otro, copiar la semana anterior sobre la visible y vaciar un día o la semana.
- **por qué complementa:** las sugerencias ayudan plato a plato, pero una semana de rutina fija sigue siendo catorce huecos. `compra-desde-el-menu.md` y `historial-de-platos.md` la descartaron entonces por menor valor frente a las funciones que se elegían; con ellas ya implementadas, es el siguiente paso lógico.
- **se apoya en:** `WeeklyMeals.setMealForDay()`, `WeekUtils`, y el guardado automático en segundo plano de `MealDataManager`.
- **alcance:**
  - por defecto, copiar rellena solo los huecos vacíos; sustituir lo escrito es una opción explícita con confirmación.
  - vaciar ofrece deshacer con el mismo patrón de snackbar del resto de la aplicación.
  - los avisos de repetición se recalculan: copiar la semana anterior mostrará `Ya estuvo en el menú hace N días` en todos los platos, que es información correcta y no un error.
- **estados a cuidar:** semana anterior vacía (acción deshabilitada con motivo), día destino con contenido, guardado en curso.
- **extensión posible:** guardar semanas con nombre como plantillas exigiría un fichero nuevo (`week_templates.json`) y sube la dificultad a media; no se incluye aquí.
- **dificultad y tiempo:** baja, de 1 a 2 días.
- **depende de:** nada.

## B6. NOTAS POR PRODUCTO

- **qué es:** un texto breve opcional bajo cada producto, por ejemplo `2 kg`, `sin sal` o `el de marca blanca`.
- **por qué complementa:** `ListItem` ya tiene un campo `notes` en el modelo y en el json que ninguna pantalla usa. activarlo da cantidades informales sin abrir el debate de las cantidades estructuradas de `a2`.
- **se apoya en:** `ListItem.notes`, el formulario de `b3` con un segundo campo y `ItemAdapter`.
- **alcance:**
  - límite de 60 caracteres, normalizado con `normalizeName()`.
  - la nota se muestra en una segunda línea con contraste aa y no cambia la detección de duplicados, que sigue usando solo el nombre.
  - el texto compartido añade la nota tras el nombre, y su formato se fija a la vez que el de `m2` para que se pueda importar.
- **estados a cuidar:** nota larga con fuente ampliada, producto sin nota (sin segunda línea vacía) y lectura por lectores de pantalla como una sola frase.
- **riesgos:** el json existente ya trae `notes` como nulo, así que no hay migración, pero hay que probar listas antiguas sin el campo. no mezclar con cantidades: son texto libre.
- **dificultad y tiempo:** baja, de 1 a 2 días.
- **depende de:** `b3` recomendado, para compartir el formulario.

## B7. VINCULO ENTRE MENU Y COMPRA

- **qué es:** cada lista creada o ampliada desde el menú recuerda de qué semana viene; el botón del menú indica si la compra de esa semana ya está preparada y en qué lista.
- **por qué complementa:** el menú y las listas solo se relacionan en el momento de pulsar `Preparar la compra`. después, la aplicación no sabe que existen una de la otra, y el usuario no ve si ya compró para la semana.
- **se apoya en:** `DataManager.createListWithItems()` y `mergeIntoList()`, `WeekUtils.isValidWeekId()` y el aviso `announceMenuResult()` de `ListDetailActivity`.
- **alcance:**
  - campo opcional `source_weeks` (lista de ids de semana) en `UserList`. Gson lo lee como nulo en listas antiguas, por lo que se sanea al leer.
  - el menú cambia el texto del botón a `Compra preparada` con el nombre de la lista cuando existe, y sigue permitiendo añadir más.
  - la tarjeta de la lista muestra `Del menú del 24 al 30 de noviembre`.
- **estados a cuidar:** lista borrada (el vínculo desaparece con ella), una lista que sirve a varias semanas, semanas pasadas y futuras.
- **riesgos:** añadir un campo a `user_lists.json` debe mantener la lectura de ficheros antiguos y la copia de `m6`. se prueba con un fichero sin el campo.
- **dificultad y tiempo:** baja, de 1 a 2 días.
- **depende de:** nada. es la base de la mejor variante de `a1`.
