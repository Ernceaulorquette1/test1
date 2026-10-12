"""Envío de correo. Sin SMTP configurado, el mensaje se registra en el log (sólo desarrollo)."""
from __future__ import annotations

import logging
import smtplib
from email.message import EmailMessage

from ..core.config import get_settings

log = logging.getLogger("pronoscore.mail")
OUTBOX: list[dict] = []   # sólo para pruebas/desarrollo


def send_mail(to: str, subject: str, body: str) -> bool:
    s = get_settings()
    if not s.smtp_host:
        if s.is_dev:
            OUTBOX.append({"to": to, "subject": subject, "body": body})
            log.info("MAIL (sin SMTP; dev) to=%s subject=%s body=%s", to, subject, body)
        else:
            log.warning("SMTP no configurado: no se envió correo a %s", to)
        return False
    msg = EmailMessage()
    msg["From"], msg["To"], msg["Subject"] = s.smtp_from, to, subject
    msg.set_content(body)
    with smtplib.SMTP(s.smtp_host, s.smtp_port, timeout=15) as smtp:
        smtp.starttls()
        if s.smtp_user:
            smtp.login(s.smtp_user, s.smtp_password)
        smtp.send_message(msg)
    return True
