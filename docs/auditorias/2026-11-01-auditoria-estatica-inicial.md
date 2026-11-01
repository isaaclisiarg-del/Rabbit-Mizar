# Auditoría estática inicial · CaraDeVerga 1.1.0

Fecha: 2026-11-01. **Solo lectura; sin builds ni ejecución de pruebas nuevas**.

## Inventario
- Android nativo Kotlin 2.0.21, Compose, Material 3, Room 2.6.1, Gradle 8.10.2 (wrapper), AGP 8.7.3, JDK 17.
- Estructura principal: `MainActivity`, `AppViewModel`, `Repository`/`AppDatabase`, `Models`, `Screens`, `Forms`, `Backup`.
- Cinco tablas (`customers`, `loans`, `installments`, `payments`, `plans`), versión Room 1 y esquema exportado.
- Historial de pruebas proporcionado: `VALIDACION.md` documenta 24 pruebas previas aprobadas, ninguna ejecutada durante esta auditoría.
- El APK original tiene `applicationId=com.aura.caradeverga`, `versionCode=2`, `versionName=1.1.0`. Hash original verificado.
- Firma: el certificado de la clave privada original coincide con el digest de firma registrado en `verificacion/firma-apk.txt`.

## Hallazgos
1. **CRÍTICO / Compatibilidad**: conservar el identificador de paquete y la firma. Para cambios de esquema Room, introducir migraciones explícitas y tests con base v1.
2. **ALTO / Protección de datos**: `allowBackup=false`, respaldos JSON manuales y sin cifrado. Exigir exportación de datos del teléfono, prueba de lectura y copia adicional antes del primer despliegue.
3. **ALTO / Secreto de firma**: `signing/` en la copia original contiene JKS y contraseñas. Excluido del paquete seguro y de `.gitignore`; no subir a GitHub, aunque el repo sea privado.
4. **MEDIO / Cambio de marca**: el marcador `app: CaraDeVerga` del formato JSON es parte del contrato existente y no debe renombrarse por cambiar el título visible de Android. `Backup.encode/validate` necesitan preservar compatibilidad.
5. **MEDIO / Riesgo de cambio visual**: esquema de colores centralizado en `MainActivity.kt` (función `CaradevergaTheme`/MaterialTheme); se puede introducir paleta Rabbit sin cambiar formularios, cálculos ni navegación. Revisar colores de error, superficies, estados y modo oscuro.
6. **MEDIO / Pruebas futuras**: añadir prueba de actualización real con base poblada de v1.1.0, test de exportación/importación cruzada y verificaciones contra efectos en cálculos financieros.

## Preservar sin cambios por ahora
`applicationId`, paquete Kotlin, estructura Room, backup JSON v1, flows de cliente/préstamo/cuota/cobro, almacenamiento offline, consultas SQL y algoritmo de asignación de intereses.

## Próximos pasos
- Crear repositorio privado vacío en GitHub.
- Publicar la copia fuente segura como commit inicial después de verificar destino y que no existan secretos.
- Trabajar en rama de integración con commits pequeños y reversibles.
- Definir nombre estelar final y paleta; primero documentar propuesta y obtener conformidad de usuario final si es posible.
- Validar backup y actualización instalada sobre datos ficticios antes de entregar APK firmado.
