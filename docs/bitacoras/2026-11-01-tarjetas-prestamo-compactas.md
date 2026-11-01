# Rabbit: Mizar · Tarjeta compacta de préstamos

**Fecha:** 2026-11-01
**Rama:** `feature/mizar-1.2.0-capital-claro-interes-cero`
**Versión en desarrollo:** 1.2.0

## Motivación

El usuario de la aplicación aprobó la versión de prueba, pero detectó demasiada información repetida en la tarjeta «Préstamo #N» de la ficha de cliente. La tarjeta es un acceso directo al detalle, no un lugar para desplegar calendario y contabilidad completa.

## Intervención

Se simplificó **exclusivamente `LoanCard` en `Screens.kt`**.

Información visible:
- Identificador «Préstamo #N» y una flecha que señala que se puede abrir.
- **Capital prestado**, destacado: `loan.principal`.
- **Ganancia pactada** por intereses: `loan.expectedProfit`.
- **Interés** pactado: `loan.interestRateBps` mediante `Money.rate()`.
- **Cantidad de cuotas**: `loan.installmentCount` con singular/plural.

Se retiraron de esa tarjeta los importes por cuota, vencimientos, fecha de origen, total pactado, saldo actual, estados redundantes y alertas de vencimiento. El bloque resumen de cliente, `LoanScreen`, `InstallmentCard` y las rutas de navegación siguen intactos. La misma presentación compacta se aplica a tarjetas de préstamos activos y finalizados, que ya están agrupadas por sección.

## Garantías

- Se mantiene `Card(onClick = onClick)`; la ficha abre el detalle existente.
- No se modifican las entidades, Room, repositorios, cálculos, pagos, tasas ni copias de seguridad.
- Sin cambios de `applicationId`, firma, versión ni nombre visible.
- Préstamos al 0% muestran ganancia de $0 e interés 0%, con la cantidad de cuotas correspondiente.
- Préstamos parcialmente pagados conservan su capital original y ganancia **pactada**. El saldo real sigue dentro de `LoanScreen`.

## Pruebas

Extendida `UiTest.loanCardHighlightsOriginalCapitalNotAmountWithInterest` para comprobar los cuatro datos, la ausencia de texto redundante y que pulsar la tarjeta dispara el callback. Añadida `loanCardDisplaysInterestFreeContractsWithoutChangingFinancialData` para cobertura visual del 0%.

**Estado:** cambio de código publicado, revisión estática completada. **Pendiente:** ejecutar Robolectric, compilar en Android Build Center y verificar visualmente el caso de importes largos y tamaños de pantalla reducidos. No distribuir como actualización de producción hasta validar compilación y firma.
