#!/usr/bin/env python3
"""Genera MENSAJES.md: mensaje de WhatsApp y correo personalizados por negocio.

Para los que tienen WhatsApp incluye un enlace wa.me con el texto ya precargado
(solo falta pulsar enviar). Uso: python3 generar_mensajes.py
"""
import json
import urllib.parse
from pathlib import Path

BASE = Path(__file__).parent
ARTIFACT = "https://claude.ai/code/artifact/c4e5b2a7-4ffa-414a-bf29-7bb2fb7f6381"

# Gancho comercial por rubro: qué gana ese tipo de negocio con una web
GANCHOS = {
    "barberia": "que sus clientes los encuentren en Google y reserven hora sin tener que escribirles",
    "peluqueria": "que sus clientas los encuentren en Google y agenden hora directo desde el celular",
    "gasfiteria": "aparecer en Google cuando alguien busca \"gasfiter urgente\" en su comuna",
    "comida": "recibir pedidos directo desde Google, sin depender solo de Facebook",
    "panaderia": "mostrar sus productos y recibir encargos sin depender solo de Facebook",
    "pasteleria": "mostrar su catálogo de tortas y recibir encargos por WhatsApp directo desde Google",
    "belleza": "que las encuentren en Google y les agenden hora directo desde el celular",
    "unas": "mostrar sus diseños y que les agenden hora directo desde Google",
    "mecanica": "aparecer en Google cuando buscan \"mecánico cerca\" y recibir consultas al tiro",
    "desabolladura": "que les lleguen cotizaciones desde Google, no solo por el boca a boca",
    "cerrajeria": "aparecer en Google cuando alguien busca \"cerrajero urgente\" en su ciudad",
    "desarmaduria": "que les coticen repuestos desde Google en todo Chile",
    "floreria": "recibir pedidos con reparto directo desde Google, no solo por Facebook",
    "peluqueria_canina": "que los dueños de mascotas los encuentren en Google y agenden al tiro",
    "petshop": "recibir pedidos con despacho directo desde Google",
    "veterinaria": "que los vecinos los encuentren en Google y agenden consulta sin llamar",
    "lavanderia": "recibir pedidos de retiro y entrega directo desde Google",
    "ferreteria": "que les coticen materiales desde Google y llegar a más obras",
}


def whatsapp_msg(n):
    gancho = GANCHOS[n["rubro"]]
    return (
        f"Hola! 👋 Soy [Tu nombre]. Encontré a *{n['nombre']}* por Facebook y vi que "
        f"no tienen página web propia.\n\n"
        f"Les preparé una *de muestra, sin costo ni compromiso*, para que vean cómo se vería:\n"
        f"{ARTIFACT}#/{n['slug']}\n\n"
        f"Una web propia les serviría para {gancho}.\n\n"
        f"Si les gusta, la dejo funcionando con dominio propio (.cl) en 48 horas por [$precio]. "
        f"¿Le echan un vistazo? Y si no les interesa, me avisan y no los molesto más. 🙂"
    )


def email_msg(n):
    gancho = GANCHOS[n["rubro"]]
    asunto = f"Les hice una página web de muestra para {n['nombre']} (gratis, para que la vean)"
    cuerpo = (
        f"Hola, equipo de {n['nombre']}:\n\n"
        f"Mi nombre es [Tu nombre] y me dedico a crear páginas web para negocios locales. "
        f"Los encontré por su página de Facebook y noté que aún no tienen sitio web propio.\n\n"
        f"En vez de mandarles un folleto, preferí prepararles una página de muestra ya lista, "
        f"para que la vean funcionando:\n\n"
        f"    {ARTIFACT}#/{n['slug']}\n\n"
        f"¿Para qué les serviría? Principalmente para {gancho}. Hoy, cuando alguien busca en "
        f"Google un negocio como el suyo en {n['ciudad']}, aparecen los que tienen página web; "
        f"la idea es que ustedes también estén ahí.\n\n"
        f"La propuesta:\n"
        f"  - Dejo el sitio funcionando con dominio propio .cl en 48 horas.\n"
        f"  - Incluye sus fotos reales, horarios, precios y botón de WhatsApp.\n"
        f"  - Valor: [$precio] (pago único) + [$mantención] anual por dominio y hosting.\n\n"
        f"La muestra es gratis y sin compromiso: si no les interesa, basta con que me lo digan "
        f"y elimino la página y sus datos.\n\n"
        f"Quedo atento a sus comentarios.\n\n"
        f"Saludos,\n"
        f"[Tu nombre]\n"
        f"[Tu teléfono] · [Tu correo]"
    )
    return asunto, cuerpo


def main():
    negocios = json.loads((BASE / "data" / "negocios.json").read_text(encoding="utf-8"))

    partes = [
        "# Mensajes de contacto por negocio\n",
        "Mensajes listos para copiar y pegar. Antes de enviar:\n",
        "1. Reemplaza los campos `[Tu nombre]`, `[$precio]`, `[$mantención]`, `[Tu teléfono]` y `[Tu correo]`.",
        "2. **Comparte primero el artifact** (menú de compartir de la página) o los enlaces no abrirán para el negocio. "
        "Si publicas las demos en otro hosting, reemplaza el enlace.",
        "3. En los negocios con WhatsApp, el enlace \"Enviar por WhatsApp\" abre el chat con el mensaje ya escrito.",
        "4. Envía en horario comercial, de a pocos por día, y respeta al que diga que no le interesa (Ley 19.628).\n",
        "---\n",
    ]

    for i, n in enumerate(negocios, 1):
        wsp = whatsapp_msg(n)
        asunto, cuerpo = email_msg(n)
        partes.append(f"## {i}. {n['nombre']} — {n['ciudad']}\n")
        contacto = n.get("telefono") or "sin teléfono publicado (contactar por Facebook)"
        partes.append(f"**Contacto:** {contacto}\n")
        partes.append(f"**Demo:** {ARTIFACT}#/{n['slug']}\n")
        partes.append("### WhatsApp\n")
        partes.append("```")
        partes.append(wsp)
        partes.append("```")
        if n.get("whatsapp"):
            enlace = f"https://wa.me/{n['whatsapp']}?text={urllib.parse.quote(wsp)}"
            partes.append(f"\n➡️ [Enviar por WhatsApp (mensaje precargado)]({enlace})\n")
        else:
            partes.append("\n_(Sin número de WhatsApp publicado: envía este mismo texto por Messenger a su página de Facebook.)_\n")
        partes.append("### Correo\n")
        partes.append(f"**Asunto:** {asunto}\n")
        partes.append("```")
        partes.append(cuerpo)
        partes.append("```")
        partes.append("\n---\n")

    (BASE / "MENSAJES.md").write_text("\n".join(partes), encoding="utf-8")
    print(f"Generado MENSAJES.md con mensajes para {len(negocios)} negocios")


if __name__ == "__main__":
    main()
