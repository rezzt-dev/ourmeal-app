#  ANDROID PROJECTS  |  OURMEAL
> pagina de informacion tecnica del proyecto, pensada para desarrolladores o usuarios experimentados para poder ver la configuracion e infraestructura de la aplicacion, ademas de funciones o partes esenciales que la componen.
---
##   OURMEAL  |  INFORMACION TECNICA
<div align="justify">
Este documento extrae, cataloga y detalla de manera extensa las especificaciones concretas del entorno de desarrollo, la elección de librerías y las decisiones de arquitectura de la aplicación móvil **OurMeal**. Esta información es especialmente valiosa para ingenieros de software, analistas de calidad o para facilitar transiciones técnicas en el código fuente.
</div>
---
##   OURMEAL  |  INFORMACION PILA TECNOLOGICA (TECH STACK)
<div align="justify">
El proyecto ha sido concebido desde cero para el ecosistema moderno de desarrollo móvil de Google y JetBrains.
</div>
- **Lenguaje Base: Kotlin**(usado al 100% en todo el código fuente), garantizando seguridad frente a _NullPointerExceptions_ así como el uso de características de modernidad funcional (como corrutinas, etc.).
- **Plataforma Objetivo:** **Android Nativo**.
- **Gestor de Dependencias y Sistema de Construcción:** Sistema **Gradle** implementado fuertemente mediante **Kotlin DSL** (archivos `.gradle.kts`). Esta es la práctica y sintaxis moderna recomendada actualmente para obtener un fuerte tipado y autocompletado directamente sobre la configuración de compilación del proyecto.
---
##   OURMEAL  |  CONFIGURACION DE SDK Y ESPECIFICACIONES DE COMPILACION
<div align="justify">
Revisando la configuración central a nivel módulo en <b>app/build.gradle.kts</b>, encontramos pautas muy determinantes respecto a compatibilidad de mercado y consumo de APIs.
</div>
- **Identificador Universal (Application ID):** `app.ourmeat`
- **Mínima Versión Compatible (Min SDK):** Configurado a **SDK 34**. Esto se traduce a un requisito inquebrantable: la app exige que los dispositivos del consumidor corran sobre al menos **Android 14 (Upside Down Cake)**. Esta es una limitación muy restrictiva sobre la compatibilidad con móviles de hace tan solo unos años, pero indica un enfoque muy estricto e inflexible hacia la máxima modernidad de interfaces y de políticas de privacidad integradas en el sistema.
- **Plataforma de Compilación (Target y Compile SDK):** Fijado a nivel **35**, indicando preparación del entorno directo para integrarse con **Android 15 (Vanilla Ice Cream)**. Se certifica que la aplicación no romperá cambios deprecados por este último gran parche de SO.
- **Nivel de Bytecode (Java Version):** Compilado exportando bajo la plataforma general **Java 17 (JVM Target 17)**.
---
##   OURMEAL  |  TIPOLOGIA DE CODIGO, ARQUITECTURA Y PAQUETES
<div align="justify">
A través del análisis detallado del directorio raíz de su código matriz (<b>src/main/java/ourmeal/app</b>), evidenciamos que la arquitectura de software utilizada sigue un patrón estructural similar al clásico <b>Model-View-Controller (MVC)</b> o posiblemente un <b>MVP</b> ligero. Su paquete interno se desgrana en las siguientes ramas.
</div>
- **Paquete `model/`:** Directorio encargado de salvaguardar las entidades fundamentales del modelo de dominio. Aquí residirán invariablemente las _Data Classes_ que conforman los "Ingredientes", las "Listas" o los "Platos".
- **Paquete `controller/`:** Ente supervisor. Aquí se alojarán los controladores centrales en los flujos de negocio encargados de orquestar la agregación de datos y la mutación de las listas de compras desde inputs visuales.
- **Pantallas / Nodos UI (Activities):** Consta de 3 clases fundamentales derivadas en el ciclo de vida:
  - `MainActivity.kt`: Sirve intrínsecamente como el punto de inicio que se lanza cuando el usuario pincha el ícono principal.
  - `ListActivity.kt`: El controlador de vista encargado de proporcionar y estructurar un entorno o historial centralizado con las múltiples las listas generadas.
  - `ListDetailActivity.kt`: Vista dedicada y acotada al CRUD o manipulación atómica de los elementos en bruto que conforman un listado específico a explorar en profundidad.
---
##   OURMEAL  |  UI RENDERING (INTERACCION GRAFICA)
<div align="justify">
En la parte visual se ha optado por estabilidades tradicionales en el desarrollo móvil:
</div>
- **Vista-Clásica en vez de Jetpack Compose:** El proyecto utiliza interacciones imperativas tradicionales XML.
- **ViewBinding Control:** El soporte de _viewBinding_ está habilitado (`buildFeatures { viewBinding = true }`). Así se evitan los obsoletos `findViewById()`, permitiendo al código Kotlin inyectar datos sobre las plantillas XML de forma tipada, segura y automatizada desde memoria.
- **Estilos Material 3:** Enlace a las librerías punteras `libs.material` y `libs.androidx.material3.android` que implementan las animaciones, _Ripple effects_, elevaciones y espaciados dictados por las últimas guidelines de _Google Material You_.
- **Motores de Contenidos y Maquetación:** Se percibe una integración completa de contenedores `androidx.constraintlayout`, recomendados usualmente para desplegar la UI sin impactar el frame rate (_frames dropping_) debido a anidaciones costosas de las vistas.
---
##   OURMEAL  |  CADENAS DE PROCESAMIENTO DE DATOS Y TESTING
1. **Serialización (Gson):** Incorpora la famosa librería de Google, **Gson** (`libs.gson`). Puesto que la app tiene lógica para resguardar listas de comida para su persistencia entre reinicios de la app, el uso de Gson sugiere la ejecución de conversiones bidireccionales de Objetos Kotlin nativos a largas cadenas de texto formato `JSON`. Esto prepara los objetos de la memoria RAM para inyectarlos en almacenes como un archivo del Storage o en los sencillos _SharedPreferences_ locales de la ROM del dispositivo.
2. **Entornos de Testing y Aseguramiento Cualitativo:** La arquitectura asimila de base las dependencias `JUnit` y `androidx.junit` destinadas a las pruebas unitarias de su lógica en local. Por último, también añade inyección a `androidx.espresso.core` diseñado explícitamente y al milímetro para lanzar test en escenarios orquestados simulando el click y arrastre de la pantalla (_UI Testing/End-To-End Testing_).
---
