# OURMEAL | DESCARTADAS Y CONDICIONADAS

> ideas que se valoraron y no entran en las propuestas, o que solo tendrían sentido tras una decisión de producto. se documentan para no volver a analizarlas desde cero. volver al [índice](indice.md).

## DESCARTADAS

| idea | motivo | alternativa dentro del análisis |
|---|---|---|
| comparador de precios entre supermercados | necesita red, el permiso `INTERNET` y una fuente de datos externa que el proyecto no tiene; la versión con servicio propio se añadió y se revirtió (`6956a1e`, `985268d`) | `m9` anota precios a mano y funciona sin conexión |
| sincronización en la nube y cuentas | exige servidor, cuentas y política de privacidad, y contradice que nada salga del dispositivo; `compra-desde-el-menu.md` ya la descartó | `m2` recibe listas y `m6` mueve todos los datos con un archivo |
| menús generados automáticamente | `historial-de-platos.md` fija como no objetivo no generar menús ni rellenar semanas; el usuario decide qué come | `b5` copia lo que el usuario ya planificó y las sugerencias ya proponen sus platos habituales |
| categorías o pasillos de la tienda | exigen mantenimiento manual y chocan con el recorrido aprendido, que existe para evitarlos | `m3` separa el recorrido por tienda sin categorías |
| deducción de ingredientes con una base de recetas | obligaría a inventar o descargar datos; `compra-desde-el-menu.md` ya la descartó | `m5` y `a3` guardan solo lo que el usuario escribe |
| escáner de códigos de barras y datos nutricionales | requiere cámara, red y una base de datos externa, con dos permisos nuevos | ninguna |
| fotos de platos | exige cámara o selector, almacenamiento y multiplica el tamaño de la copia de seguridad | `a3` se queda en texto |
| varios usuarios o perfiles | sin cuentas no hay a quién asignar los datos, y duplicaría todo el modelo | `m2` y `m6` cubren compartir y trasladar |

## CONDICIONADAS A UNA DECISION

### FRANJAS ADICIONALES DEL DIA

añadir desayuno, merienda u otras franjas es una petición razonable, pero afecta a una decisión central del producto: la aplicación planifica comida y cena.

- `Meal` tiene dos campos fijos (`comida` y `cena`) y `MealSlot` solo dos valores, y todo el historial, las sugerencias, los avisos y la preparación de la compra los recorren.
- el formato de `meal_plans.json` es estable según `agents.md`: cambiarlo exige una versión nueva, una migración que lea los ficheros actuales y pruebas con datos reales.
- estimación si se aprueba: de 1 a 2 semanas, con riesgo medio de regresiones en `DishHistory` y `MenuShoppingActivity`.
- se recomienda no empezarla antes de `m6`, para que exista una copia antes de migrar el menú.

### SINCRONIZACION DIRECTA ENTRE DOS MOVILES

enviar los datos de un móvil a otro sin servidor, por ejemplo con comunicación cercana, evitaría la nube pero necesita permisos de proximidad o red local y dos flujos de pantalla en ambos dispositivos.

- no se propone mientras `m2` y `m6` cubran el caso con un gesto de compartir. si las parejas piden algo más inmediato, valorar la idea después de recoger su uso real.

### TEMA CLARO Y OTROS IDIOMAS

no son funcionalidades primarias ni completan el ciclo del menú y la compra. el tema oscuro único (`OurmealApplication` fuerza `MODE_NIGHT_YES`) y la interfaz solo en español son decisiones de identidad documentadas en `agents.md`; cambiarlas es trabajo de diseño y traducción, no de producto.
