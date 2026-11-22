# DECISIONES DEL COMPARADOR DE PRECIOS

este registro cubre `shared/`, `price-reader/` y `backend/`. conserva kotlin `1.9.0`, el catálogo de versiones existente y la interfaz xml de android. la [guía del servicio](../backend/readme.md) y la [guía del lector](../price-reader/readme.md) describen instalación, pruebas y límites.

## DEPENDENCIAS

| dependencia | decisión y motivo | licencia |
| --- | --- | --- |
| `com.microsoft.playwright:playwright:1.65.1` | controla chromium o chrome real, ejecuta javascript y conserva perfiles separados por ciudad y cadena | apache-2.0; distribución y avisos incluidos en los artefactos de playwright |
| `org.jetbrains.kotlin.jvm:1.9.0` | añade el módulo jvm con la misma versión de kotlin del cliente; genera bytecode para java 17 | apache-2.0 |
| `com.squareup.okhttp3:okhttp:4.12.0` | reutiliza la dependencia existente para nominatim y overpass | apache-2.0 |
| `com.google.code.gson:gson:2.10.1` | reutiliza la dependencia existente para respuestas de descubrimiento y productos json-ld | apache-2.0 |
| `io.ktor:*:2.3.13` | api, errores estructurados y límite por instalación; utiliza la rama compatible con kotlin 1.9 sin migrar android a kotlin 2 | apache-2.0 |
| `io.netty:netty-bom:4.1.141.Final` | alinea el transporte de ktor con la versión compatible de la rama 4.1, sin heredar sus versiones antiguas | apache-2.0 |
| `org.postgresql:postgresql:42.7.14` | catálogo, historial y aislamiento por ciudad mediante consultas parametrizadas | bsd-2-clause |
| `com.zaxxer:HikariCP:7.1.0` | limita y reutiliza las conexiones con la base de datos | apache-2.0 |
| `org.slf4j:slf4j-simple:2.0.20` | registro de avisos del servidor, sin cuerpos de peticiones ni claves | mit |
| `org.testcontainers:postgresql:1.21.4` | pruebas con postgresql desechable; solo se incluye en pruebas | mit |

las versiones se declaran en `android-app/gradle/libs.versions.toml`. playwright solo se incorpora a `:price-reader`, no al apk android. sus textos de licencia y aviso se incluyen también en los recursos del lector; el paquete del controlador conserva sus avisos de componentes transitivos.

## ALCANCE DEL MODULO

el lector descubre cadenas desde la ciudad elegida y permite limitar la lectura a las cadenas seleccionadas. no fija nombres de supermercados ni un número de cadenas. usa el límite administrativo de openstreetmap, con caché y atribución, y no recibe coordenadas del teléfono.

el servicio ktor reutiliza el lector y los modelos compartidos. postgresql conserva grupos genéricos, productos con marca y formato, precios históricos y votos de alias sin guardar la clave original de instalación. las coincidencias exactas y únicas se resuelven solas; las dudosas necesitan elección. el emparejamiento conserva restricciones de dieta, marca, unidad y formato. no se incorpora un modelo de embeddings ni se afirma que estén medidas las métricas de la especificación ampliada.

las lecturas web se ejecutan en una cola limitada, fuera de las llamadas de la app. los datos locales del teléfono siguen en los mismos ficheros json; mover sus modelos a `:shared` conserva paquetes y nombres serializados. las firmas de caché incluyen también las tiendas seleccionadas. la ingesta compara precios unitarios y aparta saltos anómalos antes de recomendar.

la variante de pruebas admite una base local cuyo nombre empieza por `ourmeal_test` cuando no hay docker disponible. se rechazan destinos remotos o nombres ordinarios antes de cualquier vaciado. los datos ficticios del recorrido completo permanecen en `src/test/`, separados de las fuentes reales y de los artefactos de distribución.

## LICENCIAS Y PRIVACIDAD PENDIENTES

la revisión de `license.md` no cambia sus condiciones ni autoriza usos comerciales. siguen pendientes la política de privacidad, el inventario completo de terceros y la revisión de fuentes y condiciones de cada cadena. el lector añade perfiles con cookies en el equipo donde se ejecute; exclúyelos de git, registros y artefactos públicos. estas tareas se registran en la [auditoría del repositorio](repo-audit-and-fix.md).
