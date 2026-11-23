# AUDITORIA UI/UX DE OURMEAL

cuaderno de seguimiento del pulido visual y de experiencia de la aplicación android (`android-app/`). lo mantiene cada agente o persona que toque la interfaz: lo pendiente, lo dudoso y lo resuelto viven aquí, no en la memoria de quien trabaja.

## RESUMEN

- **última revisión:** 2026-11-23.
- **alcance cubierto:** las cuatro pantallas (`MainActivity`, `ListActivity`, `ListDetailActivity`, `MenuShoppingActivity`), los dos formularios de creación, los diálogos de confirmación, el selector de fecha, las snackbars y los paneles compartidos de carga, vacío y error, en vertical y con datos desechables en el emulador `Pixel_10_Pro`.
- **estado general:** la base es sólida. existe un sistema de diseño real (`res/values/colors.xml`, `res/values/dimens.xml`, `res/values/themes.xml`) con escala tipográfica, estilos de botón, campo, chip, diálogo y snackbar, y todo el texto cumple contraste aa (el par más bajo, `input_border` sobre `surface_raised`, da 4,01:1 y es un borde, no texto). los problemas encontrados son de coherencia (radios, contornos, terminología), de foco de teclado y de algunos estados concretos, no de lenguaje visual.
- **guía aplicada:** `.docs/skills/impeccable/` (`SKILL.md`, `reference/product.md`, `reference/polish.md`), adaptada a recursos xml de android. la ruta `.docs/agent-skills/` citada en la petición no existe; su equivalente en este repositorio es `.docs/skills/`.
- **sin verificar todavía:** horizontal (`layout-land/`), fuente ampliada al 200 % y talkback real. se registran en pendiente.

### INVENTARIO

| pantalla o componente | fichero principal | estado |
| --- | --- | --- |
| menú semanal (inicio) | `layout/activity_main.xml`, `layout/view_week_menu.xml` | pulido, con una duda de copy |
| selector de días | `layout/day_selector_chip.xml`, `MainActivity.kt` | pulido |
| campos de comida y cena, sugerencias y aviso de repetición | `layout/view_week_menu.xml`, `MealSuggestions.kt` | pulido |
| selector de fecha (`MaterialDatePicker`) | `ThemeOverlay.Ourmeal.Calendar` | pulido |
| mis listas | `layout/activity_list.xml`, `layout/item_list.xml` | pulido |
| detalle de lista | `layout/activity_list_detail.xml`, `layout/item_list_item.xml` | pulido; horizontal sin revisar |
| recorrido de compra | `layout/view_route_order.xml` | pulido |
| preparar la compra | `layout/activity_menu_shopping.xml`, `layout/item_menu_dish.xml`, `layout/view_shopping_destination.xml` | pulido; horizontal y fuente ampliada sin revisar |
| formularios de creación | `layout/dialog_create_list.xml`, `layout/dialog_add_item.xml`, `OurmealDialogs.kt` | pulido |
| diálogos destructivos | `ThemeOverlay.Ourmeal.DestructiveDialog` | pulido |
| carga, vacío y error | `layout/view_loading.xml`, `layout/view_state_panel.xml`, `ScreenUi.kt` | pulido |
| snackbars | `UiFeedback.kt`, `Widget.Ourmeal.Snackbar` | pulido |
| botones | `Widget.Ourmeal.Button.*` | pulido; queda el foco del botón de volver |
| iconos | `res/drawable/ic_*.xml` | pulido |
| movimiento | `UiMotion.kt` | pulido |

## PENDIENTE

### TRANSVERSAL

- **baja · ritmo vertical entre cabecera y primera sección.** el margen superior de la primera sección varía entre pantallas equivalentes (18 dp en mis listas, 22 dp en detalle y preparar la compra, 24 dp en el menú), igual que el espacio entre titular e introducción (12, 10 y 6 dp). conviene fijar dos o tres pasos con nombre en `dimens.xml` antes de igualarlos, para no cambiar el ritmo de una sola pantalla.
- **baja · foco del botón de volver.** `Widget.Ourmeal.Button.Text.Muted` hereda la onda teñida de marca: con teclado aparece una píldora marrón y, como `paddingStart` es 0 para alinear la flecha con el margen, la píldora toca el icono. propuesta: onda neutra (`hairline`) y un relleno inicial pequeño compensado con margen negativo.
- **baja · vacío y error comparten la misma pastilla salvia.** `view_state_panel.xml` distingue los dos estados solo por el icono. es coherente con el tono tranquilo de la marca, pero se podría valorar un fondo neutro para el error.

### PENDIENTE DE VERIFICAR

- **media · horizontal.** verificados el menú y mis listas en `layout-land/`; faltan el detalle de lista y preparar la compra.
- **media · fuente ampliada.** verificados el menú y mis listas con escala 2,0; faltan el detalle de lista (barra de acciones) y preparar la compra (botón de confirmar de dos líneas).
- **media · talkback real.** comprobar con talkback el orden de lectura, los anuncios de las regiones vivas y los nombres corregidos de los botones de volver y de `Elegir fecha`.

## EN DUDA

- **«El menú» bajo «El menú de la semana».** recomendación: cambiar el rótulo de la tarjeta salvia a «Semana» y dejar «El menú» como título de sección, o eliminar el título de sección. afecta a la voz de la pantalla principal, así que queda para la persona propietaria.
- **antetítulos en naranja sobre cada titular.** cada pantalla abre con un antetítulo de marca. es una decisión de identidad existente y se mantiene; solo se ha corregido la repetición casi literal entre mis listas y el detalle.

## HECHO

| fecha | cambio | motivo |
| --- | --- | --- |
| 2026-11-23 | escala de radios con nombre en `res/values/dimens.xml` (`radius_control` 8 dp, `radius_card` 12 dp, `radius_container` 16 dp) aplicada a estilos, fondos y tarjetas; las tarjetas de producto y de plato pasan de 10 a 12 dp y la ficha de día normal de 8 a 12 dp. | había cinco radios sueltos y la misma ficha cambiaba de radio según su estado. |
| 2026-11-23 | margen lateral `screen_gutter` (24 dp en vertical, 32 dp en horizontal) en las ocho pantallas y en las snackbars, desde `UiFeedback.kt`. | las snackbars usaban el margen de material (8 dp) y no se alineaban con el contenido; el estilo no basta porque material lo ignora. |
| 2026-11-23 | `button_outline_stroke` usa `input_border` en reposo. | el contorno de los botones secundarios daba 1,84:1 y el botón de compartir se perdía; ahora comparte contorno con campos y chips (4,5:1). |
| 2026-11-23 | anillo de foco de 2 dp: `focus_ring_on_brand` en botones principales, `focus_ring_on_cream` en botones crema y tarjetas de lista, trazo `ink` en `ripple_day_chip.xml` y `card_outline` en las tarjetas de producto. | con teclado físico el foco solo era una capa de estado al 12 %, casi invisible. verificado con tabulador en el emulador. |
| 2026-11-23 | las flechas de semana pasan de `ImageButton` con `bg_cream.xml` a `MaterialButton` con el nuevo estilo `Widget.Ourmeal.Button.Cream.Icon`; se elimina `bg_cream.xml`. | tenían otro radio que el botón contiguo y su onda sin límites se salía de la forma. |
| 2026-11-23 | `spreadDayChips()` en `MainActivity.kt` da a cada ficha de día un ancho mínimo de un séptimo de la fila. | dejaban un hueco a la derecha. un primer intento con pesos rompía «Dom» en dos líneas con fuente al 200 %; la solución final conserva el desplazamiento horizontal con fuente ampliada. |
| 2026-11-23 | el campo enfocado vuelve a desplazarse cuando el teclado reduce la ventana. | el aviso de repetición y las sugerencias quedaban debajo del teclado. |
| 2026-11-23 | los campos de comida y cena usan `TextAppearance.Ourmeal.BodyLarge` en lugar de atributos sueltos. | duplicaban el estilo, que lint marcaba como recurso sin uso. |
| 2026-11-23 | `ic_edit_meal.xml` pasa al icono de edición de material relleno, en `sage_deep` y a 18 dp. | era el único icono de trazo fino dibujado a mano. |
| 2026-11-23 | los botones de volver usan su texto visible como nombre accesible y `open_calendar` pasa a «Elegir fecha en el calendario»; se elimina el texto `back`. | talkback y el control por voz anunciaban «Volver» o «Elegir semana…» mientras la pantalla decía otra cosa (wcag 2.5.3). |
| 2026-11-23 | el borrado de productos dice «Eliminar producto» y «¿Eliminar «…» de esta lista?». | el diálogo decía «Quitar» y el botón, el icono y la snackbar, «Eliminar». |
| 2026-11-23 | antetítulo del detalle «Lista de la compra» y rango de preparar la compra «Menú de la semana: …». | «Tu compra, en orden» repetía casi literalmente «La compra, en orden»; «Semana del 23 nov – 29 nov» no se leía bien. |
| 2026-11-23 | las snackbars de una frase terminan en punto. | se mezclaban mensajes con y sin punto. los errores de campo siguen sin punto, como en material. |
| 2026-11-23 | el contador de mis listas y de productos y la tarjeta de progreso se ocultan con la lista vacía; se elimina `progress_empty`. | repetían lo que ya explica el panel vacío. |

### VERIFICACION

- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` sin errores; lint solo conserva avisos de versiones de sdk y dependencias, ajenos a la interfaz.
- `./gradlew :app:connectedDebugAndroidTest` en el emulador `Pixel_10_Pro`: 27 de 27 pruebas superadas.
- capturas antes y después en vertical, horizontal (menú y mis listas) y fuente al 200 % (menú y mis listas), y foco con tabulador en el menú.
