# Rabbit: Mizar · Nombre definitivo y compilación firmada con Build Center

Fecha: 2026-11-01

## Nombre

El nombre público y de pantalla de inicio ahora es **Rabbit: Mizar** en `AndroidManifest.xml` y `MainActivity.kt`.

Se mantienen:
- `applicationId=com.aura.caradeverga`, que identifica a la instalación original en Android.
- `versionName=1.2.0`, `versionCode=3`.
- Esquema Room, cálculos, navegación, backup, firma y datos sin cambios.

El nombre de archivo por defecto del respaldo sigue usando `CaraDeVerga-<fecha>.json` por compatibilidad y trazabilidad; no es el nombre visible de la aplicación.

## Firma original y la importación aislada

El Build Center de Rabbit Sentinel ofrece un importador GitHub que registra proyectos con estrategia **isolated**; cada compilación usa un worktree separado. Por eso, guardar una clave únicamente en el checkout `~/Rabbit-Mizar/signing/` no garantiza que esté accesible al importador inteligente.

Se admite una ubicación externa y persistente en el HOME privado de Termux:

```text
~/.local/share/rabbit-mizar/signing/
  caradeverga-release.jks
  keystore.properties
```

El Gradle prioriza `./signing/` para compilaciones locales clásicas si ya existe la configuración, y si no, utiliza la ubicación privada de Termux. La clave no está en GitHub y no se guarda en los worktrees.

Para `:app:assembleRelease` y `:app:bundleRelease`, Gradle **se detiene antes de compilar** si faltan las credenciales o el almacén original. El modo `debug` no requiere firma release.

## Preparación en Termux

1. Descargar desde la carpeta privada Google Drive `Rabbit: mizar` ambos archivos (no compartir).
2. Autorizar acceso al almacenamiento con `termux-setup-storage` si no se hizo antes.
3. Copiar los archivos de Descargas a:

```bash
mkdir -p "$HOME/.local/share/rabbit-mizar/signing"
cp "$HOME/storage/downloads/caradeverga-release.jks" "$HOME/.local/share/rabbit-mizar/signing/"
cp "$HOME/storage/downloads/keystore.properties" "$HOME/.local/share/rabbit-mizar/signing/"
chmod 700 "$HOME/.local/share/rabbit-mizar/signing"
chmod 600 "$HOME/.local/share/rabbit-mizar/signing/"*
ls -l "$HOME/.local/share/rabbit-mizar/signing/"
```

Si Drive guardó los archivos en otro directorio o cambió el nombre, usar su ubicación real. Evitar pegar contraseñas en el chat, historial de shell o GitHub. Tras copiar, eliminar las copias sueltas de Descargas desde el administrador de archivos; conservar las originales privadas de Drive.

## Configuración del importador GitHub de Build Center

- Nombre: `Rabbit: Mizar`
- Repositorio: `https://github.com/isaaclisiarg-del/Rabbit-Mizar.git`
- Rama: `feature/mizar-1.2.0-capital-claro-interes-cero`
- Módulo: `app`
- Variante: `release`
- Ruta local: asignada por Build Center (estrategia isolated).
- Salida: `auto` para importación GitHub (Build Center lee metadatos Gradle).
- Identificador Android verificado: `com.aura.caradeverga`.
- Script: vacío.
- Carpeta de exportación: `RabbitMizar`.

Build Center ejecuta `:app:assembleRelease` en el worktree aislado. No mover las claves a ese árbol.

## Validaciones obligatorias antes de entrega

1. Compilación Android release exitosa.
2. Certificado del APK nuevo idéntico al de `CaraDeVerga-v1.1.0.apk`, no simplemente «alguna firma válida».
3. `applicationId=com.aura.caradeverga`, `versionCode=3`, versión 1.2.0.
4. Prueba de actualización sobre una instalación de prueba 1.1.0 con datos ficticios.
5. Respaldo exportado y verificado de la instalación real de Mati, antes de instalar una actualización.

**Estado:** cambios de código y configuración publicados; no se ha ejecutado Gradle ni generado una APK firmada nueva aquí.
