# Configuración de pagos y suscripciones

## Principios (implementados)
1. **Pro se activa SÓLO por verificación del servidor**: Google Play (consulta a la API) o eventos verificados de una pasarela. El teléfono/navegador nunca lo activa.
2. El plan efectivo se calcula en el servidor con el reloj del servidor (`active`/`canceled` hasta fin de periodo/`grace_period` hasta `grace_until`); `on_hold`, `expired`, `refunded`, `pending` ⇒ Gratis. Android y web leen el mismo valor (`GET /me`).
3. No se guardan tarjetas. `payment_transactions` guarda monto/estado/id externo; las transacciones de prueba llevan `is_test=true`.
4. Los eventos son **idempotentes** (`billing_events UNIQUE(provider, event_id)`).

## Estados de suscripción
`active` · `canceled` (cancelada, con acceso hasta `current_period_end`) · `grace_period` · `on_hold` (retención de cuenta; sin acceso) · `expired` · `refunded` · `pending` (pago pendiente; sin acceso). Job `expire_subscriptions` cada 15 min cierra periodos vencidos.

## Simulador de desarrollo (nunca en producción)
`PAYMENTS_PROVIDER=demo` y `ENV=local|test`: `POST /subscriptions/demo/activate` y `/subscriptions/demo/event {renew|cancel|grace|hold|expire|refund}`. Con `ENV` distinto de `local/test` responden **403** y la configuración de producción **no arranca** con `PAYMENTS_PROVIDER=demo` (`Settings` lo valida). Probado en `test_billing.py`.

## Google Play (Android)
Ver [CONFIGURACION_GOOGLE_PLAY.md](CONFIGURACION_GOOGLE_PLAY.md). Flujo: la app pide `GET /billing/account-id` (id ofuscado) → `launchBillingFlow(setObfuscatedAccountId)` → recibe `purchaseToken` → `POST /subscriptions/google/verify` → el servidor consulta `purchases.subscriptionsv2.get`, valida producto y cuenta, activa y **confirma (acknowledge)**. Cambios posteriores (renovación, cancelación, gracia, retención, reembolso `REVOKED`, vencimiento) llegan por **RTDN** (`POST /webhooks/google-play`, JWT de Pub/Sub verificado) y se re-consultan en Google (no se confía en el payload).

## Pasarela web (Chile + internacional) — PENDIENTE de elegir
Hay una interfaz `PaymentGateway` (`backend/app/services/billing/gateway.py`) con `create_checkout` y `parse_webhook` (que **debe verificar la firma** del proveedor). Hoy en producción devuelve **501** ("no hay pasarela configurada"). Para integrar una (p. ej. Stripe, Mercado Pago, Flow, Transbank/Webpay Oneclick — evalúa cuál soporta suscripciones recurrentes en CLP y USD):
1. Implementa `PaymentGateway` y registra su clase en `get_gateway()`.
2. Crea el endpoint de webhook que llame a `parse_webhook` y luego a una función análoga a `handle_rtdn` (crear `BillingEvent`, actualizar `Subscription`, registrar `PaymentTransaction`).
3. Precios por país en `plans.prices` (editable en Admin → Planes). Referencia: Chile 9.990 CLP/mes; EE. UU. 9,99 USD/mes; resto: `DEFAULT` + precios regionales definidos en cada plataforma.
4. **Separación de flujos (política de Google Play):** dentro de la app Android los bienes digitales deben venderse con Google Play Billing; el pago web es un flujo distinto (web). No dirijas desde la app a un pago externo sin verificar la política y programas vigentes de tu región.
