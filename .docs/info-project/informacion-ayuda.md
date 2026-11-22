# OURMEAL | INFORMACION DE AYUDA

> ayuda para usar ourmeal y para empezar a desarrollar en el proyecto. la primera parte está pensada para quien usa la aplicación; la segunda, para quien quiere contribuir al código.

## GUIA PARA USUARIOS

### EL MENU DE LA SEMANA

al abrir la aplicación aparece el menú de la semana actual, con el día de hoy seleccionado:

1. elige un día en la tira de fechas. el día de hoy aparece resaltado.
2. toca `Añadir comida` o `Añadir cena` y escribe el plato. basta con un nombre reconocible, como `lentejas` o `tortilla`.
3. los cambios se guardan solos. bajo el menú verás `Guardando cambios…` y después `Cambios guardados.`.
4. usa las flechas para cambiar de semana, `Elegir fecha` para saltar a cualquier día del calendario y `Volver a hoy` para regresar a la semana actual.

el recuadro de la semana indica cuántas de las 14 comidas y cenas están planificadas.

### SUGERENCIAS Y PLATOS REPETIDOS

ourmeal recuerda los platos que has planificado en otras semanas para que no tengas que escribirlos de nuevo:

1. al tocar un campo vacío, bajo él aparecen tus platos habituales que aún no están en la semana. desliza la fila para ver más.
2. al escribir, la fila muestra los platos que coinciden, aunque los escribieras con mayúsculas o tildes distintas.
3. toca un plato para rellenar el campo. se guarda como cualquier otro cambio.
4. si un plato no debería sugerirse, por ejemplo uno mal escrito, mantenlo pulsado. justo después puedes tocar `Deshacer`. si vuelves a escribirlo tú, se sugerirá de nuevo.

bajo cada plato puede aparecer un aviso como `También está el lunes en la comida.` o `Ya estuvo en el menú hace 3 días.`. es solo una ayuda para variar: puedes repetir el plato si quieres.

### PREPARAR LA COMPRA DESDE EL MENU

cuando la semana tenga algún plato, toca `Preparar la compra` bajo el menú:

1. aparecen los platos de la semana, cada uno con los días en que está. si un plato se repite, aparece una sola vez.
2. escribe los ingredientes de cada plato en `Añadir ingrediente` y toca `+` o la tecla de confirmar. puedes seguir escribiendo el siguiente sin tocar la pantalla. para quitar uno, toca su `✕`.
3. desmarca los platos que no quieras comprar esta vez, por ejemplo una cena fuera de casa.
4. en `Añadir a`, elige `Una lista nueva`, con un nombre propuesto que puedes cambiar, o `Una de mis listas`.
5. abajo verás cuántos productos se añadirán y cuántos ya estaban en la lista. toca `Añadir` para terminar: se abre la lista con los productos nuevos arriba.

la aplicación recuerda los ingredientes que escribes para cada plato. la próxima vez que lo planifiques aparecerán con la etiqueta `Ingredientes recordados` y solo tendrás que revisarlos. si sales sin añadir después de escribir ingredientes, la aplicación te pide confirmación.

### TUS LISTAS DE LA COMPRA

desde el menú, toca `Mis listas` para ver todas tus listas:

1. toca `Nueva lista` y ponle un nombre fácil de reconocer, como `compra de la semana`.
2. toca una lista para abrirla.
3. para eliminar una lista, toca su icono de papelera y confírmalo. justo después puedes tocar `Deshacer`.

cada lista muestra su fecha de creación, cuántos productos tiene y cuántos están comprados.

### LOS PRODUCTOS DE UNA LISTA

dentro de una lista:

1. toca `Añadir` y escribe el producto. si ya está en la lista, aunque lo escribas con mayúsculas o tildes distintas, la aplicación te avisa y no lo repite.
2. mientras compras, toca un producto o su casilla para marcarlo como comprado. el progreso de la parte superior se actualiza.
3. para quitar un producto, toca su papelera y confírmalo. justo después puedes tocar `Deshacer`.

### TU RECORRIDO POR LA TIENDA

ourmeal aprende en qué orden coges los productos para que la lista te acompañe por la tienda:

1. compra como siempre y marca cada producto cuando lo metas en la cesta. el orden en que los marcas es tu recorrido.
2. la próxima vez que abras una lista, cualquier lista, los productos pendientes aparecen en ese orden y lo ya comprado baja al final. un aviso te indica de cuántos productos aprendió el orden.
3. los productos que aún no conoce van después de los ubicados, en el orden de la lista. bajo el nombre de la lista verás cuántos pendientes siguen ya tu recorrido.
4. desactiva `Seguir mi recorrido` para ver la lista en el orden en que la escribiste. la aplicación sigue aprendiendo igualmente.
5. si cambias de tienda, toca `Olvidar` y confírmalo para empezar de cero. justo después puedes tocar `Deshacer`.

para aprender un viaje hacen falta al menos tres productos marcados a lo largo de dos minutos o más; así, marcar de golpe en casa lo que ya compraste no estropea el recorrido. un viaje se da por terminado tras 45 minutos sin marcar nada.

### DONDE ES MAS BARATO CADA PRODUCTO

ourmeal puede decirte, bajo cada producto pendiente, en qué supermercado de tu ciudad sale más barato. compara por kilo, litro o unidad, nunca por envase.

1. abre `Ajustes` con el icono de engranaje del menú o de una lista y activa `Ver dónde es más barato cada producto`.
2. toca `Usar mi ubicación` para detectar tu ciudad o `Buscar ciudad` para escribirla. la ubicación aproximada solo se usa en el teléfono para saber el nombre de la ciudad: no se guarda ni se envía.
3. marca los supermercados en los que compras. solo se compara entre ellos.
4. en tus listas, bajo cada producto pendiente, verás por ejemplo `Más barato en Cadena B · 1,79 €/kg`. toca la línea para ver todos los precios, su fecha y su origen.
5. si un nombre puede ser varios productos, la línea pide `Elige qué producto es para comparar`. la elección se recuerda. si no quieres comparar un producto, elige `Ninguno`, y en el detalle de cualquier producto puedes `Cambiar producto`.

sin conexión, la lista muestra los últimos precios guardados, y los productos nuevos se comparan al volver la red. los precios se actualizan cada seis horas. para dejar de comparar, desactiva el interruptor: la lista vuelve a ser como antes.

### COMPARTIR UNA LISTA

en una lista con productos, toca el botón de compartir. la aplicación prepara un texto con el nombre de la lista, el resumen de lo comprado y cada producto, en el orden en que los ves, marcado como `[x]` comprado o `[ ]` pendiente. elige la aplicación con la que enviarlo o guardarlo.

## GUIA PARA DESARROLLADORES

### REQUISITOS

- git instalado y disponible en el terminal.
- android studio reciente, con el sdk de android 15 (api 35) instalado. el proyecto compila con `compileSdk 35`.
- un emulador o un teléfono con android 14 (api 34) o superior, porque el proyecto usa `minSdk 34`.
- jdk 17, que android studio incluye por defecto. el proyecto compila con `jvmTarget = "17"`.

### PREPARAR EL ENTORNO

1. clona el repositorio:

```bash
git clone https://github.com/rezzt-dev/ourmeal-app.git
```

2. en android studio, usa `File -> Open` y abre la carpeta `ourmeal-app/android-app`, que contiene el proyecto gradle.
3. espera a que termine la sincronización de gradle antes de compilar.

### EJECUTAR Y COMPROBAR

1. crea un dispositivo virtual en `Device Manager` con una imagen de api 34 o superior.
2. ejecuta la configuración `app` con `Run`.
3. desde `android-app/`, las comprobaciones disponibles son:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew connectedDebugAndroidTest
```

la versión de depuración busca el servicio de precios en `http://10.0.2.2:8080/`, la máquina de desarrollo vista desde el emulador. para otra dirección, añade `price.api.url=https://…` a `android-app/local.properties`, que no se versiona, o pasa `-PpriceApiUrl=https://…` a gradle. la versión de publicación solo acepta `https` y, sin dirección, deja el comparador no disponible.

`connectedDebugAndroidTest` necesita un emulador o un dispositivo conectado. las pruebas instrumentadas que abren pantallas con datos usan la regla `AppDataSandbox`, que aparta los datos de la aplicación del dispositivo durante cada prueba y los restaura al terminar.

### CONTRIBUIR

el proyecto sigue el flujo de github: haz un fork, crea una rama para tu cambio y abre una pull request contra `rezzt-dev/ourmeal-app`.

- coloca las entidades y la lógica pura en `model/`, con pruebas unitarias en `app/src/test/`.
- coloca la persistencia y los adaptadores en `controller/`.
- mantén las pantallas (`MainActivity`, `ListActivity` y `ListDetailActivity`) centradas en la interfaz y su estado.
- sigue las reglas de commits, markdown y changelog de `claude.md`.

para ejecutar el servicio local del comparador y sus pruebas de integración, sigue la [guía del backend](../../backend/readme.md). el catálogo de pruebas es ficticio y no se incluye en la distribución. configurar la dirección del servicio no proporciona por sí solo precios: sus fuentes también deben estar preparadas.
