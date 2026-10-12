import { Link } from "react-router-dom";
import { PageTitle } from "../components/ui";

const Wrap = ({ title, children }: { title: string; children: React.ReactNode }) => (<div className="container py-4" style={{ maxWidth: 820 }}><PageTitle>{title}</PageTitle><div className="ps-card p-4">{children}</div><p className="mt-3"><Link to="/welcome">← Volver</Link></p></div>);

export function Privacy() {
  return (<Wrap title="Política de privacidad">
    <p className="small ps-muted">Borrador de trabajo: debe ser revisado por un profesional legal antes de publicar (ver docs/PUBLICACION).</p>
    <h2 className="h6">Datos que tratamos</h2><p>Correo, nombre opcional, país, idioma, zona horaria, preferencias, historial de partidos analizados, favoritos y selecciones guardadas, y datos de suscripción (estado y fechas; nunca números de tarjeta).</p>
    <h2 className="h6">Finalidad</h2><p>Prestar el servicio, aplicar límites de uso del plan, procesar suscripciones verificadas con Google Play u otra pasarela y mejorar la calidad de los modelos con datos agregados.</p>
    <h2 className="h6">Seguridad</h2><p>Contraseñas con Argon2id, comunicaciones cifradas (HTTPS), tokens de sesión revocables y registros de auditoría de acciones administrativas.</p>
    <h2 className="h6">Tus derechos</h2><p>Puedes acceder, corregir y <strong>eliminar tu cuenta y datos asociados</strong> desde Perfil → Eliminar cuenta, o escribiendo a soporte.</p>
    <h2 className="h6">Terceros</h2><p>Datos deportivos de API-SPORTS (sujetos a sus condiciones), Google Play Billing y Firebase Cloud Messaging cuando se configuren.</p>
    <h2 className="h6">Contacto</h2><p>soporte@pronoscore360.example (reemplazar por el correo real antes de publicar).</p>
  </Wrap>);
}

export function Terms() {
  return (<Wrap title="Términos de servicio">
    <p className="small ps-muted">Borrador de trabajo: requiere revisión legal antes de publicar.</p>
    <h2 className="h6">Naturaleza del servicio</h2><p>PronoScore360 ofrece análisis estadístico con fines informativos y de entretenimiento. <strong>No es una casa de apuestas</strong>, no acepta depósitos ni apuestas y no enlaza a operadores de apuestas.</p>
    <h2 className="h6">Sin garantías</h2><p>Las probabilidades son estimaciones de modelos y pueden fallar. No prometemos aciertos ni resultados. Usa la información bajo tu responsabilidad. Si el juego te afecta, busca ayuda profesional.</p>
    <h2 className="h6">Planes y pagos</h2><p>El plan Gratis incluye 5 partidos analizados al día. El plan Pro es una suscripción mensual gestionada por Google Play (Android) o la pasarela web habilitada; se renueva automáticamente hasta cancelarla. Las cancelaciones y reembolsos siguen las políticas de la plataforma de pago.</p>
    <h2 className="h6">Uso aceptable</h2><p>Se prohíbe automatizar el acceso para eludir límites, revender datos o vulnerar la seguridad del servicio.</p>
    <h2 className="h6">Datos de terceros</h2><p>Los datos deportivos provienen de proveedores externos y pueden contener retrasos o errores. En modo DEMO los datos son ficticios.</p>
  </Wrap>);
}

export function Help() {
  const faq: [string, string][] = [
    ["¿Qué cuenta como una consulta?", "Analizar un partido cuenta una vez al día, sin importar cuántos mercados revises. Volver a abrir el mismo análisis el mismo día no descuenta otra."],
    ["¿Cuándo se renuevan mis 5 consultas?", "A las 00:00 de la zona horaria de tu cuenta, calculado por el servidor (cambiar la hora del dispositivo no sirve)."],
    ["¿Por qué veo «Datos insuficientes»?", "El modelo exige al menos 5 partidos previos por equipo. Preferimos no publicar antes que inventar una probabilidad."],
    ["¿Un 70 % significa que acertará?", "No. Significa que, en situaciones similares, el modelo estima que ocurre ~7 de cada 10 veces. Puede fallar."],
    ["¿Puedo apostar en la app?", "No. PronoScore360 es sólo informativa."],
    ["¿Cómo cancelo Pro?", "En Android: Google Play → Suscripciones. Mantienes Pro hasta el fin del periodo pagado."],
  ];
  return (<Wrap title="Ayuda y soporte">
    {faq.map(([q, a]) => (<details key={q} className="mb-2"><summary className="fw-semibold">{q}</summary><p className="ps-muted mt-1">{a}</p></details>))}
    <hr /><p>¿Necesitas más ayuda? Escribe a <strong>soporte@pronoscore360.example</strong> (reemplazar por el correo real).</p>
  </Wrap>);
}

export function DeleteAccountInfo() {
  return (<Wrap title="Eliminar cuenta y datos">
    <p>Puedes eliminar tu cuenta y todos los datos asociados en cualquier momento:</p>
    <ol>
      <li><strong>En la app (Android) o la web:</strong> Perfil → «Eliminar mi cuenta». Confirma con tu contraseña.</li>
      <li><strong>Sin acceso a la app:</strong> escribe a <strong>soporte@pronoscore360.example</strong> desde el correo de tu cuenta (reemplazar por el correo real).</li>
    </ol>
    <p><strong>Se elimina:</strong> perfil, preferencias, sesiones, favoritos, selecciones guardadas, historial de uso y notificaciones.<br />
    <strong>Se conserva (anonimizado, por obligación legal/seguridad):</strong> registros de auditoría y transacciones de pago sin vínculo a tu identidad.</p>
    <p>Si tienes una suscripción activa, cancélala antes en Google Play → Suscripciones (eliminar la cuenta no cancela el cobro en la tienda).</p>
  </Wrap>);
}
