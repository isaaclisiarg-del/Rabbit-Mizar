# Rabbit: Mizar · Integración del icono aprobado

Fecha: 2026-11-01
Rama: `feature/mizar-1.2.0-capital-claro-interes-cero`

## Identidad elegida

El icono 2D aprobado en esta conversación muestra dos estrellas de distinto tamaño (Mizar y Alcor), un conejo abstracto formado en el espacio negativo y dos flechas que expresan la relación de préstamo y devolución. Fondo ciruela, acentos magenta, ámbar, marfil y lavanda. **No** se reutilizó el icono de Mintaka ni se modificó la ilustración aprobada.

## Integración Android

- `AndroidManifest.xml`: reemplaza la referencia al icono verde original `@drawable/ic_launcher` por `@mipmap/ic_launcher` y añade `@mipmap/ic_launcher_round`. No modifica la etiqueta visible `Mati Créditos`, `applicationId`, permisos, lógica financiera ni esquema Room.
- Icono adaptativo Android 8+ en `mipmap-anydpi-v26`: fondo ciruela y primer plano PNG transparente extraído del arte aprobado; permite recortes circular, squircle u otros del launcher.
- Iconos de compatibilidad PNG por densidad `mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi`, con variantes redondas.
- Archivo maestro raster sin margen blanco: `docs/branding/rabbit-mizar-icon-master-512.png`.

**Nota de formato:** el diseño aprobado se recibió como PNG, no como SVG editable. Los recursos se derivaron de esa imagen; no se afirma que sea vectorial. Al preparar los iconos se eliminó únicamente el fondo blanco de presentación y se separó el fondo ciruela del primer plano para Android adaptativo.

## Garantías y validación pendiente

- Mantener `com.aura.caradeverga` y la firma original, custodiada por separado en Drive. No subir la clave a GitHub.
- Verificación estática: manifiesto XML bien formado, referencias existentes, tamaños y tipos PNG correctos, colores declarados y archivos accesibles.
- Pendiente: compilar Android, probar el icono en el launcher real (incluido el recorte adaptativo), actualizar sobre la versión 1.1.0 firmada y revisar los datos antes de distribuir a Mati.
