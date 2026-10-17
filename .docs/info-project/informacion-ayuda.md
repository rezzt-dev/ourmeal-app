#  ANDROID PROJECTS  |  OURMEAL
> pagina con informacion de ayuda tanto para los usuarios como para el desarrollador, informacion mas pensada en saber como funciona la aplicacion o aspectos superficiales de la aplicacion.
---
##   OURMEAL  |  INFORMACION DE AYUDA
<div align="justify">
Bienvenido al centro oficial de recursos y solución de dudas sobre el proyecto de software <b>OurMeal</b>. Hemos redactado y dividido estos manuales extensos e interactivos en dos bloques, proporcionando claridad independiente tanto para el usuario ordinario sin habilidades técnicas como para el contribuidor ágil
</div>
---
##   OURMEAL  |  GUIA MANUAL PARA USUARIOS (COMO USAR LA APLICACION)
<div align="justify">
Has instalado <i>OurMeal</i> en tu móvil con éxito, ¿y ahora qué? Esta guía pasito a pasito te explica el ciclo central de la app.
</div>
###    **GUIA MANUAL  |  CONOCE LA PANTALLA PRINCIPAL**
<div align="justify">
En cuanto lanzas la app por primera vez, serás recibido por el módulo planificador. Su misión en la vista es sencilla: dejarte anotar y planificar tus dietas. Desde los desayunos rápidos hasta esa cena especial que harás el fin de semana. No necesitas entrar en hiper-detalles sobre qué tipo de marca comprar ni qué cantidades, sólo especifica el nombre genérico y fundamental de tus recetas.
</div>
###    **GUIA MANUAL  |  VISITA LA CENTRAL DE TUS LISTAS**
<div align="justify">
Navegando hasta la vista del inventario de listas, la aplicación hace magia transparente por detrás procesando todos los menús de tu paso anterior y mostrándolos en formato de <i>registros en listas</i>. Aquí puedes tener la lista de la compra de la semana unificada, y si tocas sobre cualquiera de ellas, se desplegará.
</div>
###    **GUIA MANUAL  |  GESTIONA TUS INGREDIENTES CLAVE**
<div align="justify">
Al hacer 'tap' (toque) en una lista, saltarás al escrupuloso _Detalle de la lista_. Este es el inventario bruto puro de elementos de tu supermercado organizados individualmente para cumplir con la planificación. Comprueba cuántas cosas tienes ya en la nevera de tu cocina, revisa cantidades y pon en orden tus prioridades.
</div>
###    **GUIA MANUAL  |  FUNCION DE EXPORTAR**
<div align="justify">
Con todo a punto y ordenado, podrás usar el botón de exportación. Este botón copia y manda lo registrado de forma transparente como fragmentos de texto fáciles de leer que podrás imprimir, remitir por sistemas de mensajería SMS/WhatsApp con tu pareja o familiares, haciéndolo ideal para la comunicación sin malentendidos en las idas al súper local.
</div>
---
##   OURMEAL  |  GUIA ONBOARDING (ORIENTADA A SOPORTE A DESARROLLADORES)
<div align="justify">
Si eres parte de la comunidad de apoyo de desarrollo, un miembro reciente del equipo o quieres mandar un aporte independiente desde un perfil de GitHub, asegúrate de seguir los estándares del proyecto de esta manera:
</div>
###    **GUIA ONBOARDING  |  ELEMENTOS REQUERIDOS EN TU ORDENADOR**
  - Es imperativo que tengas tu **Git** enlazado terminal y con el path operativo.
  - Un IDE completo de JetBrains. La opción lógica es disponer de la iteración más reciente de tu **Android Studio** (versiones como _Iguana_, _Jellyfish_, o _Koala_).
  - Entrar en la suite del `SDK Manager` en las propiedades del entorno Studio y confirmar que dispones instaladas plataformas tanto del `API Level 34` (Android 14) como preferiblemente del `API Level 35`.
###    **GUIA ONBOARDING  |  CONFIGURAR Y DESCARGAR EL ENTORNO INCIAL LOCALMENTE**
  1. Corre este comando en los entornos del terminal de comandos para volcar la copia completa sobre tu explorador de archivos:
```bash
git clone https://github.com/rezzt-dev/ourmeal-app.git
```
  1. Pon en arranque tu interfaz de lado cliente de **Android Studio** y dentro de tu portal inicial elige expresamente `File -> Open` y localiza meticulosamente la raíz del proyecto recién copiado de nombre exacto `ourmeal-app`.
  2. Inmediatamente el robot daemon Gradle detectará todos los esquemas `.kts` (Kotlin Scripts). Durante este intervalo (y sobre todo en tu primera pasada) no alteres nada ni detengas al asistente para no romper el _Gradle Build Sync_ mientras descarga todas sus utilidades (tales como _ViewBinding, Material Core, Appcompat, JUnit y los plugins Android KTX_).
###    **GUIA ONBOARDING  |  SIMULAR E INTERVENIR PRUEBAS EFECTIVAS SOBRE EMULADOR**
<div align="justify">
Al ser un proyecto moderno cerrado bajo un <b>minSdk 34</b> debes evitar intentar correrlo en tus teléfonos antiguos del día a día, ya que dará Error inmediato de incompatibilidad (Minimum SDK Required).
</div>
  1. Accede al gestor virtualizado en la barra de tareas superior abriendo el gestor interno de dispositivos `Device Manager`.
  2. Otorga una orden de crear nueva interfaz (Device Setup) y modela uno a semejanza de un dispositivo oficial reciente (como pueden ser un Pixel 8). Al escoger el mapa de imagen escoge descargar siempre el **API 34 + SysImage**, si es necesario, de las opciones x86 para agilidades de CPU.
  3. Arrácalo con un Play, deja que encienda la terminal simulada y después oprime repetidamente `Run` (o Mayús-F10). Podrás ver la compilación en los terminales `Build Output`, culminando de integrarse el fichero APK en el teléfono emulado.
###    **GUIA ONBOARDING  |  CONTRIBUCION Y CARGAS SEGURAS HACIA EL CODIGO MATRIZ**
<div align="justify">
La contribución respeta flujos de trabajo profesionales genéricos de Git y Github (GitHub Flow): Realiza siempre bifurcaciones completas del código principal hacia tu cuenta (<i>Fork</i>). Altere el comportamiento respetando los apartados limpios <b>model/</b> (elementos crudos), y operando las nuevas funcionalidades o cambios de UI desde clases inmersas en el envolvente <b>controller/</b> sin mezclar la vista estática por encima de esta y finalmente abra <i>_Pull Request</i> limpios explicando su aportación frente al repositorio matriz de <i>rezzt-dev/ourmeal-app</i>
</div>
---
