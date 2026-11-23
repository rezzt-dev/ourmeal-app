# OURMEAL | FUNCIONALIDADES DE DIFICULTAD ALTA

> detalle de las tres funcionalidades que cambian el modelo de datos de forma transversal, añaden un permiso o una dependencia, o revisan una decisión de producto anterior. más de una semana cada una. ordenadas de menor a mayor tiempo. volver al [índice](indice.md).

## RESUMEN

| id | funcionalidad | tiempo | valor para el usuario | motivo de la dificultad |
|---|---|---|---|---|
| `a1` | recordatorios locales | 1 a 2 semanas | medio | permiso de notificaciones, dependencia nueva y límites del sistema |
| `a2` | cantidades y raciones | 2 a 3 semanas | alto | migración del modelo de ingredientes y de la fusión de productos |
| `a3` | fichas de plato | 2 a 3 semanas | medio | modelo nuevo, edición larga y revisión de un no objetivo |

## A1. RECORDATORIOS LOCALES

- **qué es:** avisos opcionales por notificación: planificar el menú de la semana, ver qué toca hoy y recordar que la compra de la semana sigue pendiente.
- **por qué complementa:** la aplicación solo ayuda mientras está abierta. los hábitos que sostienen el ciclo (planificar el domingo, comprar a mitad de semana) dependen de que el usuario se acuerde.
- **se apoya en:** `b7` para saber si la compra está preparada, `meal_plans.json` para el contenido y una pantalla de ajustes nueva.
- **alcance:**
  - pantalla de ajustes con un interruptor por tipo de aviso, desactivados por defecto, con día y hora configurables.
  - permiso de notificaciones (`POST_NOTIFICATIONS`), que en android 14 siempre se pide en tiempo de ejecución, con explicación previa y estado si se rechaza.
  - programación con `WorkManager` (dependencia nueva) y canales de notificación diferenciados; reprogramación tras reiniciar el teléfono.
  - contenido privado en pantalla de bloqueo: no muestra los platos hasta desbloquear.
- **estados a cuidar:** permiso rechazado o revocado, ajustes en pausa por el ahorro de batería y notificación cuyo contenido ya cambió.
- **cambios de documentación:** deja de ser cierto que la aplicación no declare permisos; hay que actualizar `agents.md`, `claude.md`, `.docs/info-project/` y el apartado de privacidad, y valorar la política de privacidad que aún no existe.
- **riesgos:** la entrega no es puntual en dispositivos con ahorro de batería agresivo; la interfaz no debe prometer una hora exacta. no se usan alarmas exactas.
- **dificultad y tiempo:** alta, de 1 a 2 semanas.
- **depende de:** `b7` para el aviso de compra pendiente; el resto funciona sin él.

## A2. CANTIDADES Y RACIONES

- **qué es:** cada ingrediente pasa de ser un nombre a tener nombre, cantidad y unidad opcionales. al preparar la compra se suman los iguales entre platos y se pueden escalar por raciones.
- **por qué complementa:** hoy la lista dice qué comprar, pero no cuánto. dos platos con cebolla producen un producto, sin saber si hacen falta una o cuatro.
- **se apoya en:** `DishMemory.version`, que ya anticipa la migración a objetos con cantidad, `ShoppingDraft` y `withMergedItems()`.
- **alcance:**
  - `dish_ingredients.json` pasa a la versión 2 con cantidad y unidad; los nombres antiguos se leen como ingredientes sin cantidad.
  - analizador de entradas como `2 kg`, `500 g`, `1/2 l` o `3`, tolerante a coma y a punto decimales.
  - suma entre platos solo de unidades compatibles (`g` con `kg`, `ml` con `l`, unidades con unidades); si no son compatibles, conserva ambas líneas.
  - raciones por plato en la preparación de la compra; el total se muestra antes de confirmar con la cuenta visible.
- **estados a cuidar:** ingredientes sin cantidad, cantidad inválida, unidades mezcladas y redondeos; el resultado debe poder revisarse antes de añadirse a la lista.
- **decisión previa:** revisa el no objetivo `no se calculan cantidades ni se suman unidades` de `compra-desde-el-menu.md`. necesita aprobación de la persona propietaria.
- **riesgos:** un total equivocado destruye la confianza en la lista, por lo que nunca se convierte una unidad en silencio ni se redondea a la baja. la memoria de ingredientes está protegida por `agents.md`: la migración se prueba con ficheros de la versión 1, y `m6` debería existir antes.
- **dificultad y tiempo:** alta, de 2 a 3 semanas.
- **depende de:** `m6` recomendada; mejora con `b6`, `m4` y `m9`.

## A3. FICHAS DE PLATO

- **qué es:** cada plato puede tener, además de ingredientes, un tiempo, unas raciones, unas notas y unos pasos ordenados, con un modo cocinar de texto grande y pantalla encendida.
- **por qué complementa:** convierte la memoria de platos en un recetario ligero de uso propio, sin fotos, sin red y sin recetas inventadas: solo lo que el usuario escribe.
- **se apoya en:** `m5` como pantalla de entrada, `dish_ingredients.json`, el modo pantalla encendida de `b1` y, para las raciones, `a2`.
- **alcance:**
  - modelo y fichero para la ficha, por ejemplo una ampliación de la versión de `dish_ingredients.json` o `dish_cards.json`, con topes de longitud y lectura segura.
  - editor de pasos con añadir, reordenar y quitar, en un formulario que soporte fuente ampliada.
  - modo cocinar de un paso por pantalla, con botones grandes y pantalla encendida.
  - compartir una ficha como texto, igual que se comparte una lista.
- **estados a cuidar:** plato sin ficha, ficha muy larga con fuente ampliada y en horizontal, y fichero dañado.
- **decisión previa:** revisa el no objetivo `no se crea un recetario completo con pasos, raciones o fotos` de `compra-desde-el-menu.md`. se propone mantener las fotos fuera, porque exigen almacenamiento, cámara o selector y aumentan mucho el tamaño de la copia.
- **riesgos:** es la funcionalidad con más texto libre de la aplicación y la que más puede inflar los ficheros; los topes son parte del diseño, no un añadido.
- **dificultad y tiempo:** alta, de 2 a 3 semanas.
- **depende de:** `m5` obligatoria; `a2` recomendada para las raciones.
