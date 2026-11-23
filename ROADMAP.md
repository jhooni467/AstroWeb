# AstroWeb — Roadmap de desarrollo

Este documento recoge el plan general del proyecto. Las etapas son objetivos de desarrollo, no funciones ya implementadas. Las versiones indicadas son objetivos orientativos; pueden ajustarse según el alcance real y los resultados de las pruebas.

## Etapa 1 — Estabilidad y rendimiento
**Versión objetivo: v0.19**

Objetivo: conseguir una navegación fiable y evitar cierres inesperados.

- Investigar y corregir las detenciones recurrentes de pestañas / procesos de GeckoView al abrir páginas nuevas.
- Revisar el ciclo de vida de GeckoRuntime, GeckoSession y GeckoView, incluida la creación, activación, pausa, reanudación y cierre de pestañas.
- Corregir errores de navegación, cambio entre pestañas y recuperación tras fallos.
- Evitar recargas, reinicios o recreaciones inesperadas.
- Mejorar la carga de páginas, de la página de inicio y de la vista de pestañas.
- Revisar el consumo de memoria y las capturas de previsualización.
- Probar varios sitios y escenarios de navegación.
- Añadir registros de diagnóstico útiles para identificar la causa de cierres.

**Criterio para avanzar:** las pruebas repetidas de navegación no deben provocar detenciones inesperadas; los fallos reales deben poder diagnosticarse y recuperarse de forma controlada.

## Etapa 2 — Identidad visual y personalización
**Versión objetivo: v0.20**

Objetivo: renovar la apariencia de AstroWeb y permitir que el usuario la adapte.

- Hacer los colores de la aplicación más vibrantes sin perder la identidad visual.
- Integrar y pulir los iconos añadidos al proyecto.
- Mejorar barras, botones, menús, ventanas, fondos y estados activos.
- Crear una sección completa de Ajustes.
- Añadir tres modos de apariencia: **System** (predeterminado), **Light** y **Night**.
- Hacer que System siga la apariencia del sistema Android.
- Incorporar paletas adaptables al modo seleccionado.
- Añadir una selección de color de acento.
- Aplicar los temas de forma coherente a toda la aplicación y a la página de inicio.
- Pulir animaciones y transiciones.

Paletas propuestas para evaluar: Predeterminada, Esmeralda, Océano, Violeta y Ámbar.

## Etapa 3 — Inicio, búsqueda y descubrimiento
**Versión objetivo: v0.21**

Objetivo: convertir la página de inicio en un centro personal de navegación.

- Personalizar accesos directos.
- Añadir, eliminar y ordenar sitios favoritos.
- Mostrar sitios visitados recientemente.
- Mejorar las recomendaciones y sugerencias de búsqueda.
- Mejorar la tolerancia a errores ortográficos.
- Permitir elegir el motor de búsqueda predeterminado.
- Mostrar sugerencias relacionadas con el historial.
- Mejorar las tendencias y el descubrimiento.
- Proponer una sección de sitios recomendados controlable y desactivable.

## Etapa 4 — Pestañas y navegación avanzada
**Versión objetivo: v0.22**

Objetivo: hacer más cómoda la navegación con muchas páginas abiertas.

- Rediseñar la vista de pestañas.
- Mejorar miniaturas y previsualizaciones.
- Restaurar pestañas cerradas accidentalmente.
- Reordenar y agrupar pestañas.
- Distinguir visualmente las pestañas privadas.
- Mejorar navegación hacia adelante y atrás.
- Añadir gestos de navegación configurables.
- Mejorar el modo escritorio.
- Evaluar la opción de fijar pestañas importantes.

## Etapa 5 — Privacidad y seguridad
**Versión objetivo: v0.23**

Objetivo: dar al usuario más control sobre sus datos y permisos.

- Mejorar la navegación privada.
- Crear un panel de permisos por sitio.
- Facilitar la limpieza de historial, caché y cookies.
- Evaluar controles de protección contra rastreadores.
- Mostrar indicadores de conexión HTTPS.
- Mejorar la configuración de permisos de cámara, micrófono y ubicación.
- Gestionar el almacenamiento de los sitios.
- Mejorar la privacidad de las búsquedas personales.
- Evaluar perfiles de protección Normal y Estricto, documentando sus efectos y límites.

## Etapa 6 — Lectura y herramientas web
**Versión objetivo: v0.24**

Objetivo: ofrecer herramientas para consumir contenido web con comodidad.

- Implementar Reader Mode (modo lectura).
- Detectar artículos compatibles.
- Permitir ajustar tamaño y tipografía del texto.
- Ofrecer fondos claros, oscuros y cálidos para lectura.
- Ajustar espaciado y ancho de lectura.
- Mejorar la búsqueda dentro de páginas.
- Mejorar la visualización de documentos PDF.
- Guardar páginas para consultarlas sin conexión.
- Evaluar lectura en voz alta mediante las funciones de Android.

## Etapa 7 — Biblioteca y administración
**Versión objetivo: v0.25**

Objetivo: reunir y organizar los datos y contenidos guardados.

- Mejorar los marcadores.
- Crear carpetas para organizar favoritos.
- Mejorar el historial con filtros por fecha y sitio.
- Permitir eliminar entradas individuales.
- Mejorar la administración de descargas y los detalles de archivos.
- Exportar e importar marcadores.
- Organizar páginas guardadas.
- Evaluar colecciones de páginas importantes.

## Etapa 8 — Optimización y expansión
**Versión objetivo: v0.26 en adelante**

Objetivo: consolidar la experiencia y ampliar la compatibilidad.

- Optimizar memoria, batería e inicio.
- Mejorar accesibilidad.
- Adaptar la interfaz a teléfonos y tablets.
- Comprobar compatibilidad con distintos tamaños de pantalla.
- Realizar pruebas generales de estabilidad y seguridad.
- Pulir la interfaz y el comportamiento de todas las funciones.
- Evaluar una experiencia más amplia para tablets y pantallas grandes.

## Ideas complementarias para evaluar

Estas ideas no están comprometidas como funciones ni tienen una etapa fija todavía:

- Modo concentración para leer sin distracciones.
- Panel personal con accesos y colecciones.
- Centro de privacidad con resumen de permisos y protección.
- Centro de descargas mejor organizado.
- Lectura en voz alta.

## Estado actual

- Etapa activa: **Etapa 1 — Estabilidad y rendimiento**.
- Versión de referencia: **v0.18**.
- Problema prioritario reportado: las páginas pueden mostrarse con normalidad, pero unos segundos después de entrar a una página nueva la pestaña se detiene con frecuencia; al pulsar **Reintentar**, continúa funcionando.
- La causa exacta todavía no está confirmada.
- La v0.18 compiló correctamente en GitHub Actions, pero el comportamiento en el teléfono debe verificarse mediante pruebas reales.
- No se debe marcar ninguna versión como terminada hasta confirmar que la compilación de GitHub Actions terminó correctamente. Una compilación exitosa no sustituye las pruebas en el dispositivo.

