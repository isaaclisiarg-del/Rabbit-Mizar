# CaraDeVerga 1.1.0

App Android nativa para clientes recurrentes, préstamos independientes, cuotas y cobros. Funciona offline y no solicita permiso de Internet. Android 8.0 o posterior.

## Uso

1. Instalá `CaraDeVerga-v1.1.0.apk` y abrí la app. Si ya tenés la versión 1.0.0, instalá esta actualización encima: conserva la base. Una instalación nueva arranca vacía.
2. Tocá **Nuevo préstamo**, elegí un cliente o crealo, completá capital, cuotas y fechas, y revisá el total antes de guardar.
3. Tocá una cuota en Inicio o en el préstamo para registrar el monto recibido y su fecha. Podés agregar varios pagos a la misma cuota.
4. **Clientes** conserva todos los préstamos de cada persona. Archivar oculta al cliente de la lista habitual, pero conserva historial y deudas en Inicio y Resumen.
5. **Resumen** separa los cobros reales de las cuotas previstas del mes.
6. **Ajustes** permite modificar los planes y exportar/importar una copia JSON. Hay ejemplos opcionales cuando la base está vacía.

## Cargar un préstamo ya iniciado

1. Tocá **Nuevo préstamo**, elegí o creá el cliente y seleccioná **Ya iniciado**.
2. Ingresá el capital original y la cantidad **total** de cuotas, incluidas las que ya pagó. Indicá el interés total que pactaron, aunque sea diferente al plan actual.
3. Elegí la fecha original del préstamo, el vencimiento de su primera cuota original y la frecuencia. El cronograma conserva esas fechas.
4. En **Pagos anteriores**, agregá cada cobro a su cuota con la fecha real. El formulario propone el saldo completo; podés cambiarlo para cargar un pago parcial. Una cuota admite varios movimientos. Si aún no hubo pagos, dejá esa sección sin movimientos.
5. Revisá **Ya cobrado** y **Saldo pendiente** y tocá **Guardar préstamo iniciado**.

Por ejemplo, un préstamo de $ 200.000 en 3 cuotas al 45 % conserva su total de $ 290.000. Si ya se cobró la primera cuota de $ 96.666 y $ 20.000 de la segunda, solo quedan pendientes $ 173.334. Las cuotas pagadas conservan sus movimientos; las cuotas con saldo y fecha pasada figuran vencidas automáticamente. También se pueden cargar préstamos completamente pagados para conservar el historial.

Los pagos anteriores se incluyen en el Resumen del mes en que se recibieron. El capital prestado corresponde a la fecha original, no al día en que se cargó en la app. No se vuelve a cobrar interés sobre el saldo. Todo se guarda en una única transacción: un error en cualquier pago impide guardar registros incompletos. Los borradores todavía no afectan la caja y se conservan al recrear la pantalla, por ejemplo al rotar el teléfono.

La versión 1.1.0 conserva el esquema de base de datos y el formato de backup de 1.0.0. La misma firma permite actualizar sin desinstalar.

## Reglas de dinero y fechas

- Capitales y pagos en pesos enteros (`Long`); porcentajes con dos decimales como máximo, almacenados en centésimas de punto porcentual: 3000 = 30 %.
- Interés simple aplicado una sola vez al capital. Si el interés da una fracción de peso, se redondea hacia arriba al peso siguiente. Las primeras cuotas usan división entera y la última absorbe el resto.
- Cada préstamo conserva capital, porcentaje aplicado, cantidad de cuotas, total y ganancia prevista. Modificar los planes no altera préstamos anteriores.
- No se almacenan saldos, estado ni total pagado redundantes. Se calculan a partir de cuotas y movimientos. Una cuota con saldo pasa a vencida al día siguiente del vencimiento, incluso si recibió un pago parcial.
- Un préstamo solo se muestra Activo o Pagado. Las cuotas vencidas se identifican por separado.
- Semanal = 7 días, quincenal = 15 días. Mensual conserva el día de la primera cuota: 31/01 → 28/02 → 31/03. No se desplaza permanentemente al día 28.
- Los pagos deben ser positivos, no superar el saldo y tener fecha entre el préstamo y hoy. Se permiten pagos anticipados respecto al vencimiento. La fecha de un préstamo no puede ser futura.

## Qué significa caja

Se utiliza **reparto proporcional de cada pago entre capital e intereses**. Es una regla explícita de esta app, no una declaración de ganancia que todavía no se cobró.

El interés acumulado cobrado de un préstamo es `floor(cobradoAcumulado × gananciaEsperada / totalACobrar)`. Cada movimiento aporta la diferencia respecto al acumulado anterior; el resto recupera capital. El cálculo usa enteros de precisión arbitraria para evitar desbordamiento en la multiplicación. Al terminar, capital recuperado y ganancia cobrada coinciden exactamente con los importes del préstamo. El orden es fecha de pago, fecha de registro e ID. Al eliminar o registrar un pago con fecha anterior, los ajustes de un peso pueden redistribuirse entre movimientos, sin cambiar las reglas ni duplicar dinero.

- **Capital actualmente prestado**: capital original menos capital recuperado.
- **Pendiente de cobro**: suma de saldos de todas las cuotas.
- **Intereses pendientes**: ganancia esperada menos intereses efectivamente cobrados.
- En Resumen, **Capital prestado** sigue la fecha de los préstamos; cobros y ganancia cobrada siguen la fecha de los pagos.
- **Total previsto** y **Ganancia prevista** corresponden a cuotas con vencimiento dentro del mes seleccionado, incluidas las ya pagadas. **Pendiente** y **Vencido** son el saldo actual de esas cuotas, al día indicado en pantalla, no una reconstrucción de su saldo al último día de ese mes.

## Copias y eliminación

JSON versionado con clientes, préstamos, cuotas, pagos y planes. La importación valida tipos, IDs únicos, relaciones, fechas, montos, planes y cuotas antes de tocar la base. La restauración completa es una transacción: reemplaza los datos actuales después de mostrar cantidades y pedir confirmación. **Exportá una copia antes de restaurar otra.** Se rechazan formatos desconocidos, cuotas inconsistentes y sobrepagos. Límite de archivo: 20 MB.

Archivar conserva todo. El borrado definitivo de un cliente solo se ofrece sin préstamos. Eliminar un préstamo avisa cuántas cuotas y pagos se borrarán. Eliminar un pago vuelve a abrir su saldo. No hay eliminación automática de historial.

La base está en el almacenamiento privado de la app. No se activa Android Auto Backup: las copias son manuales. Al desinstalar, Android elimina la base local. El JSON no está cifrado; guardalo en un lugar privado.

## Código y compilación

Kotlin 2.0.21, Jetpack Compose 1.7.6, Material 3 1.3.1 y Room 2.6.1. Una Activity, un ViewModel, un repositorio con transacciones y cinco tablas. Sin servicios, login ni servidor.

Requisitos: JDK 17 completo, Gradle 8.10.2, Android SDK Platform 35 Extension 15 y Build Tools 35.0.0. Android Studio puede abrir el directorio raíz. Configurá tu SDK en `local.properties` o `ANDROID_HOME`.

```sh
./gradlew :app:assembleRelease
./gradlew :app:testDebugUnitTest
./gradlew :app:lintRelease
```

Las pruebas de Room se ejecutan sobre Android simulado con Robolectric y SQLite real; las de Compose ejecutan formularios y botones. No equivalen a una prueba en un teléfono físico. Los resultados concretos de esta entrega están en `VALIDACION.md`.

IMPORTACIÓN SEGURA AL REPOSITORIO: la carpeta local `signing/` con la clave privada original y sus contraseñas está excluida deliberadamente de este paquete para GitHub. La copia completa original se conserva por separado; para compilar una actualización release firmada hay que reponer esa carpeta SOLO en el entorno local y nunca subirla al repositorio. Las actualizaciones requieren la misma firma, mismo `applicationId`, `versionCode` creciente y una migración de base de datos segura cuando cambie el esquema. Conservá una exportación JSON de los datos reales antes de instalar nuevas versiones.

Referencias técnicas: [Room y sus pruebas](https://developer.android.com/training/data-storage/room/testing-db), [compatibilidad de Kotlin y herramientas Android](https://developer.android.com/build/kotlin-support).


## Desarrollo 1.2.0 (rama separada)

La mejora de fichas de capital, modo claro/oscuro manual y préstamos al 0% está en `feature/mizar-1.2.0-capital-claro-interes-cero`, **no liberada**. No sustituir la versión que usa Mati sin probar una instalación sobre 1.1.0 firmada con la clave original y sin respaldar previamente sus datos.

[Bitácora y checklist de la iteración](docs/bitacoras/2026-11-01-mizar-1.2.0-capital-tema-interes-cero.md).
