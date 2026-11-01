# Validación de CaraDeVerga 1.1.0

24 pruebas automatizadas aprobadas; 0 fallos, errores u omitidas. Compilación release firmada completada. Lint: 0 errores y 1 advertencias. Los resultados XML y las capturas renderizadas están en `verificacion/`.

## Función agregada

- Flujo real de la app: elegir cliente existente, seleccionar Ya iniciado, cargar capital, tres cuotas, porcentaje histórico y fechas originales usando los selectores de fecha, agregar un pago completo y uno parcial, guardar y comprobar el saldo del préstamo y la deuda del cliente.
- Formulario: varios pagos en una cuota, rechazo de sobrepagos y restauración del borrador al recrear la pantalla. El borrador no escribe en la base antes de guardar.
- Persistencia: cierre y reapertura de Room conservan fechas, interés pactado, cuotas y todos los movimientos anteriores.
- Préstamos simultáneos: un préstamo iniciado y otro del mismo cliente coexisten; el registro no modifica el anterior.
- Caja: los cobros se imputan al mes real del pago y el capital a la fecha original del préstamo. Los intereses cobrados se distinguen de los previstos.
- Estados: cuotas pagadas, parciales, pendientes y vencidas; préstamos anteriores sin pagos o completamente pagados. Continuación con un nuevo pago después de la carga inicial.
- Validación: una cuota inexistente, sobrepagos acumulados, montos inválidos, fechas anteriores al préstamo o futuras y porcentajes inválidos impiden guardar el préstamo completo.
- Backup: exportación, lectura, validación y restauración conservan también préstamos iniciados y pagos anteriores. Se mantienen las pruebas previas de pagos, cálculo, archivo de clientes, navegación y datos de ejemplo.

## Actualización

El APK mantiene `com.aura.caradeverga`, usa `versionCode=2` y la misma firma que el APK 1.0.0. Se comparó el esquema Room con el proyecto 1.0.0 y es idéntico. No hay migración destructiva ni cambios de formato de backup. La actualización está preparada para instalarse sobre 1.0.0 sin desinstalar. No solicita permiso de Internet.

## Alcance

Las pruebas usan Robolectric sobre Android 14, Room/SQLite y la interfaz real de Compose. Se inspeccionaron capturas del formulario, los pagos anteriores y el préstamo guardado, además de los renders de Inicio en tema claro, oscuro y pantalla compacta.

No se probó en un teléfono físico ni se ejecutó una instalación de actualización en un dispositivo. El selector de archivos del fabricante tampoco se prueba aquí; las pruebas de backup sí leen y escriben mediante ContentResolver.

SHA-256 del APK: `36e378dcfed69aff818fb1f418ceccc3c2cd1745ea1c0c69293f9ddbf01459bd`
