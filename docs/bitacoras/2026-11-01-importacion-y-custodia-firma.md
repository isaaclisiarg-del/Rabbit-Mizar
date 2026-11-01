# Rabbit: Mizar · Bitácora de importación y custodia

**Fecha:** 2026-11-01  
**Repositorio:** `isaaclisiarg-del/Rabbit-Mizar` (público)  
**Rama:** `main`  
**Origen:** paquete recuperado `CaraDeVerga-v1.1.0-proyecto.zip`  
**Versión de partida:** `1.1.0` (`versionCode=2`)  
**Paquete Android original:** `com.aura.caradeverga`

## Trabajo completado

1. Recuperado el proyecto Android completo de la versión 1.1.0, con código Kotlin, Compose, Room, pruebas, Gradle y documentación de validación.
2. Preparado un paquete seguro para repositorio público: 41 archivos, **sin** almacén de claves, contraseñas, bases de datos reales ni respaldos financieros.
3. Importados los 41 archivos a `main` en GitHub, incluido el binario original `gradle-wrapper.jar` y `gradlew.bat` con saltos de línea Windows originales.
4. Verificada la integridad exacta del árbol Git importado contra la copia local reconstruida. Árbol SHA: `8f80710de60a1a197ac12a254b17c43e7f8a109b`.
5. Depositados los dos archivos de firma originales (`caradeverga-release.jks` y `keystore.properties`) **solo en Google Drive**, en la carpeta privada `Rabbit: mizar`. Confirmado mediante metadatos que carpeta y archivos no están compartidos; acceso exclusivo del propietario al momento de la comprobación.
6. El código funcional no se modificó. No se generó ni distribuyó una nueva APK.

## Reglas de compatibilidad

- **No cambiar** `applicationId=com.aura.caradeverga`, ni la clave privada de firma, ni el certificado.
- Cada nueva APK instalable debe tener `versionCode` superior al de la 1.1.0 y estar firmada con la identidad original.
- Proteger los datos de clientes, préstamos, intereses, cuotas y pagos. Mantener migraciones de Room explícitas si el esquema cambia.
- Solicitar respaldo real desde la app instalada de Mati y comprobar restauración en entorno de pruebas **antes de distribuir**.
- No publicar secretos, contraseñas, datos financieros reales, APK firmadas ni registros de clientes en el repositorio público.
- No editar diseño, navegación o fórmulas hasta contar con una especificación de cambios y pruebas de regresión.

## Próxima fase: identidad Rabbit

- Nombre técnico de proyecto: **Rabbit: Mizar**.
- Cambios visuales propuestos: paleta cromática del ecosistema Rabbit e identidad gráfica coherente.
- Mantener flujos de trabajo, posiciones de acciones y reconocibilidad para Mati. La identidad pública y etiqueta visible se decidirán antes de implementarla.
- Hacer una auditoría enfocada en matemática financiera, registros de pago, estados de crédito, persistencia, exportación y restauración, y compatibilidad de actualización.

## Estado

**Base importada y verificada a nivel de archivos.** Pendientes: compilación real, pruebas automatizadas en entorno Android disponible, pruebas manuales con datos sintéticos, copia de seguridad de la app en uso y aprobación del rediseño visual.

**Advertencia:** almacenar el JKS y el archivo de contraseñas juntos permite recuperar la firma, pero exige proteger especialmente la cuenta de Drive. No compartir la carpeta; preferir copia offline cifrada como respaldo independiente.
