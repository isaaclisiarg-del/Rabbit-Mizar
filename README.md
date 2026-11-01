# Rabbit: Mizar

Aplicación Android de gestión de préstamos para uso personal. Código recuperado de CaraDeVerga 1.1.0.

**Estado:** migración inicial del código fuente; sin cambios funcionales aprobados.

- Paquete Android: `com.aura.caradeverga` (se conserva para permitir actualizaciones sobre la app existente).
- Versión base: `1.1.0` (`versionCode` 2).
- Kotlin, Jetpack Compose, Room.
- Claves de firma y contraseñas: almacenadas por separado, nunca en este repositorio público.
- Antes de distribuir una actualización: respaldo de datos, prueba de migración Room y firma con clave original.

No publicar bases de datos de clientes, archivos de préstamos reales, ni claves privadas.
