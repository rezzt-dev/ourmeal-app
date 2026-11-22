# LEER PRECIOS DE LAS CADENAS DE UNA CIUDAD

ejecuta el lector de ingesta en un ordenador o servidor con java 17 o posterior. parte del nombre de ciudad seleccionado en android, descubre supermercados y abre sus webs con un navegador real. consulta la [guía del comparador](../.docs/new-features/guia-desarrollo-comparador-precios.md) para el contexto funcional y los componentes todavía pendientes.

## INSTALAR Y COMPROBAR EL LECTOR

desde `android-app/`, instala chromium y genera el ejecutable con sus dependencias:

```bash
./gradlew :price-reader:installBrowser :price-reader:installDist
```

el ejecutable queda en `price-reader/build/install/price-reader/bin/price-reader`. los ejemplos siguientes se ejecutan desde la raíz del repositorio. consulta primero las ciudades candidatas y después las tiendas de la ciudad elegida:

```bash
price-reader/build/install/price-reader/bin/price-reader cities --city Madrid
price-reader/build/install/price-reader/bin/price-reader stores --city Madrid
```

si el nombre corresponde a varias ciudades, añade `--province` con el valor devuelto por `cities`. la búsqueda se restringe a españa. las cadenas se obtienen de los supermercados dentro del límite administrativo de la ciudad, sin un radio que mezcle municipios vecinos.

`stores` devuelve el formato del contrato android `/v1/stores`. agrupa por marca, identificador de marca en openstreetmap u operador; conserva las tiendas independientes con nombre propio. no existe una lista fija de tres cadenas.

## ABRIR LAS WEBS Y LEER PRECIOS

busca un producto en las webs de las cadenas detectadas:

```bash
price-reader/build/install/price-reader/bin/price-reader read \
  --city Madrid --query 'leche entera 1 L'
```

el navegador tiene ventana por defecto. necesita una sesión gráfica; para ejecución sin ventana añade `--headless`. para usar google chrome instalado en vez del chromium descargado, añade `--channel chrome`.

añade `--chain` con uno o varios identificadores de `stores`, separados por comas, para leer solo las cadenas seleccionadas. el lector rechaza identificadores que no pertenecen a la ciudad. cambiar la ciudad obliga a obtener sus tiendas de nuevo.

el lector usa javascript, cookies y almacenamiento persistente del navegador. no sustituye únicamente la cabecera de un cliente http. [playwright para java](https://playwright.dev/java/docs/browsers) controla el proceso de chromium o chrome.

el lanzamiento omite `--enable-automation` y desactiva `AutomationControlled` de blink. así evita la señal `navigator.webdriver`, sin inyectar una identidad ficticia mediante javascript. esto no convierte la automatización en una persona ni garantiza que una web la acepte.

## ELEGIR TIENDA Y CONSERVAR LA SESION

si una web requiere elegir tienda, ciudad o preferencias de cookies, abre su perfil dedicado. sustituye `123456789` por un identificador real obtenido con `stores`:

```bash
price-reader/build/install/price-reader/bin/price-reader setup \
  --city Madrid --chain 123456789
```

realiza la selección en la ventana, sin iniciar sesión, y pulsa intro en la terminal para cerrar. las lecturas posteriores de esa ciudad y cadena reutilizan el perfil. una ventana de verificación se devuelve como `INTERACTION_REQUIRED`; no se interpreta como un catálogo vacío ni se reintenta en bucle.

los perfiles se guardan en `~/.local/state/ourmeal-price-reader/profiles/`, separados por ciudad, cadena y dominio. no utilizan el perfil de tu navegador personal. en sistemas posix, cada directorio de perfil tiene permisos `0700`. usa `--state` para escoger otra carpeta privada, fuera del repositorio; las cookies y el almacenamiento del perfil deben mantenerse fuera de git y de las copias públicas.

## ADAPTAR LA NAVEGACION DE UNA CADENA

el lector obtiene la dirección web de las etiquetas de las tiendas en openstreetmap. si falta, o la web corporativa difiere de la tienda online, añade una regla. también puedes indicar la ruta o el campo de búsqueda que necesita esa web.

el siguiente ejemplo usa un dominio ficticio. reemplaza la marca, el dominio y la ruta con valores comprobados de la cadena detectada:

```json
[
  {
    "chain": "supermercado de ejemplo",
    "website": "https://tienda.example/",
    "searchUrl": "https://tienda.example/buscar?q={query}"
  }
]
```

guarda el archivo fuera del repositorio y pásalo con `--rules /ruta/reglas.json`. `searchUrl` admite `{query}` y `{city}`, escapados como valores de consulta. `searchInput` admite un selector css cuando el campo de búsqueda requiere uno específico. sin reglas, el lector utiliza el primer campo de búsqueda visible y editable, y envía la consulta con la tecla intro.

una regla configura cómo entrar y buscar; no añade esa cadena a ciudades donde no se ha detectado. el lector acepta direcciones https públicas, rechaza credenciales en la dirección y bloquea solicitudes hacia redes privadas. el servidor donde se ejecute también debe restringir el acceso a redes internas mediante su configuración de salida.

## INTERPRETAR EL RESULTADO Y SUS LIMITES

cada cadena devuelve un estado independiente:

| estado | significado |
| --- | --- |
| `OK` | encontró precios comparables en datos estructurados |
| `EMPTY` | no encontró precios comparables; no demuestra que la tienda carezca del producto |
| `NO_WEBSITE` | ni openstreetmap ni las reglas proporcionan una web https utilizable |
| `SEARCH_UNSUPPORTED` | necesita una regla para localizar o abrir la búsqueda |
| `INTERACTION_REQUIRED` | la web pide intervención en el navegador o responde con `401`, `403` o `429` |
| `UNAVAILABLE` | falló la navegación o el perfil estaba ocupado |

el lector espera los datos estructurados `Product` y `Offer` en formato json-ld, incluso cuando javascript los añade después de buscar. descarta precios sin moneda euro, sin cantidad conocida, caducados o de productos no disponibles. no convierte un precio mínimo de `AggregateOffer` en una oferta confirmada. calcula €/kg, €/l o €/ud a partir del envase y conserva la dirección y el momento de lectura.

la detección de tiendas no equivale a disponer de un extractor para cualquier web. si una cadena publica precios solo en componentes visuales o en otro formato, necesita un adaptador adicional verificado. un navegador real tampoco garantiza superar todas las verificaciones de acceso. las pruebas del módulo utilizan páginas controladas; no acreditan cobertura de supermercados reales.

la ciudad determina qué cadenas consultar y qué perfil usar. el resultado sigue siendo un precio online, que puede diferir del precio de una tienda física. comprueba la selección de ciudad o tienda en cada web antes de usar sus precios como referencia local.

este módulo es una biblioteca y un ejecutable de ingesta. el [servicio de precios](../backend/readme.md) lo conecta con android mediante una cola de trabajos y un catálogo postgresql; la app consume `/v1/resolve` y `/v1/compare`. las lecturas web se ejecutan fuera de las peticiones http y la app consulta su progreso. prepara las fuentes antes de usar precios reales; las pruebas con páginas controladas no acreditan cobertura de supermercados.

## VERIFICAR SIN DATOS PERSONALES

desde `android-app/`, ejecuta las pruebas de descubrimiento, validación y navegador:

```bash
./gradlew :price-reader:test :price-reader:browserTest
```

`browserTest` ejecuta chromium sin ventana con páginas https interceptadas y perfiles temporales. comprueba precios añadidos por javascript, conservación de cookies, aislamiento entre ciudades y fallos independientes por cadena. no contacta con supermercados ni lee datos del teléfono.

en la comprobación externa del 22/11/2026, `stores --city Sevilla` devolvió 234 tiendas agrupadas en 60 cadenas. la consulta de «arroz redondo» a la web de alcampo detectada para esa ciudad terminó con `EMPTY`: no obtuvo ofertas comparables. al configurar su tienda online `www.compraonline.alcampo.es`, devolvió `INTERACTION_REQUIRED`. esto verifica el descubrimiento real, pero deja pendiente preparar el acceso y un adaptador de extracción probado para esa fuente; no demuestra ausencia del producto ni permite recomendar precios de alcampo.

## SERVICIOS Y LICENCIAS

el descubrimiento usa [nominatim](https://nominatim.org/release-docs/latest/api/Search/) y [overpass](https://wiki.openstreetmap.org/wiki/Overpass_API/Overpass_QL), con atribución a openstreetmap. conserva las búsquedas de ciudad siete días y las tiendas un día; las peticiones externas de descubrimiento son seriales y se separan al menos `1100 ms`. configura instancias propias mediante `OURMEAL_NOMINATIM_URL` y `OURMEAL_OVERPASS_URL` cuando proceda.

la instancia overpass por defecto es `https://overpass-api.de/api/interpreter`, incluida en la [lista pública de instancias](https://wiki.openstreetmap.org/wiki/Overpass_API#Public_Overpass_API_instances). los errores y las respuestas incompletas no se guardan como catálogos vacíos; el ejecutable informa de un código de diagnóstico sin imprimir consultas ni cuerpos de respuesta.

la [política de nominatim](https://operations.osmfoundation.org/policies/nominatim/) público no permite usarlo para autocompletado. `cities` es una búsqueda enviada expresamente; no lo conectes a cada pulsación del campo de android. los límites se aplican a toda la aplicación: utiliza un único proceso de descubrimiento o coordina las peticiones entre procesos.

consulta las [decisiones de dependencias](../.docs/DECISIONS.md) y el [registro de auditoría](../.docs/repo-audit-and-fix.md) antes de desplegar. no se han modificado las condiciones de `license.md` ni se ha publicado un servicio.
