# Bitácora · CaraDeVerga 1.1.0 → ecosistema Rabbit

Fecha: 2026-11-01. Estado: **preparado para importación a GitHub; NO publicado ni compilado en esta sesión**.

## Origen verificado
- Archivo fuente original: `CaraDeVerga-v1.1.0-proyecto.zip` (13/09/2026).
- APK original: `CaraDeVerga-v1.1.0.apk` (13/09/2026).
- SHA-256 APK: `36e378dcfed69aff818fb1f418ceccc3c2cd1745ea1c0c69293f9ddbf01459bd`.
- Paquete Android / `applicationId`: `com.aura.caradeverga`.
- `versionCode=2`, `versionName=1.1.0`; Room schema v1; Android minSdk 26, targetSdk 35.
- Clave original: archivo JKS presente en el ZIP privado ORIGINAL. Se abrió con `keytool`; su huella SHA-256 coincide con la reportada por el APK firmado. La clave, la contraseña y el APK se OMITIERON del paquete de importación GitHub.
- El ZIP fuente pasó comprobación de integridad; el hash APK coincidió con el registro de la versión 1.1.0.

## Invariantes de actualización
1. No modificar `applicationId` ni borrar el espacio privado de la app.
2. No regenerar ni reemplazar la clave de firma. Nunca incluirla en el control de versiones.
3. Incrementar `versionCode` en la próxima versión; no asumir que un APK nuevo conserva datos sin validar instalación real.
4. Mantener intactas las cinco tablas Room existentes o proveer una migración probada; PROHIBIDO `fallbackToDestructiveMigration`.
5. Mantener compatibilidad de respaldos JSON `schemaVersion=1` y el identificador de exportación original, salvo migración explícita con pruebas.
6. Obtener y verificar una exportación de préstamos, cuotas, cobros y clientes del teléfono del usuario final ANTES de actualizar.
7. No cambiar orden, navegación, cálculos, flujos de cobro ni términos reconocibles durante la fase cosmética.
8. Compilar, ejecutar pruebas y ensayar actualización sobre una instalación con datos de prueba antes de entregar a Mati.

## Alcance inicial autorizado
- Preparar repositorio privado y bitácoras.
- Auditar arquitectura, lógica financiera, exportación/restauración, permisos, pruebas y actualización.
- **Propuesta sin implementar:** nueva identidad Rabbit con nombre estelar provisional `Mizar`, aspecto y colores Rabbit conservando controles y navegación de 1.1.0.
- Nombre visible final pendiente de aprobación; propuesta de transición `Mati Créditos` con firma visual Rabbit: Mizar.

## Riesgos y tareas
- No existe una copia de los datos reales de la instalación de Mati dentro de este paquete.
- `android:allowBackup=false`; la exportación JSON manual es esencial.
- Los respaldos JSON contienen datos de clientes y no están cifrados: jamás subir respaldos reales a GitHub.
- El ZIP original contiene credenciales de firma: mantenerlo privado y fuera del repo, respaldado en medios seguros separados.
- La captura, verificación `lint`, tests y actualizaciones en dispositivo son posteriores; los 24 tests aprobados corresponden al registro histórico del 13/09, NO a una ejecución actual.
- Cualquier rediseño visual debe conservar contraste, tamaños, accesibilidad y las rutas anteriores.
