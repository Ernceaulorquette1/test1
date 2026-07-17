#!/usr/bin/env python3
"""Genera un sitio web de demostración (one-page) para cada negocio de data/negocios.json.

Uso:  python3 generar_sitios.py
Salida: sitios/<slug>/index.html  +  index.html (catálogo general)
"""
import json
import html
from pathlib import Path

BASE = Path(__file__).parent

# Perfil visual y de contenido por rubro
RUBROS = {
    "barberia": {
        "icono": "💈",
        "color": "#1f2937", "acento": "#d97706",
        "tagline": "Cortes clásicos y modernos, afeitado tradicional y estilo para el hombre de hoy.",
        "servicios": [
            ("Corte de cabello", "Cortes clásicos, fade, degradados y estilos a tu medida."),
            ("Barba y afeitado", "Perfilado de barba y afeitado tradicional con toalla caliente."),
            ("Atención personalizada", "Te asesoramos para encontrar el look que mejor te acomoda."),
        ],
    },
    "peluqueria": {
        "icono": "✂️",
        "color": "#4c1d95", "acento": "#ec4899",
        "tagline": "Corte, color y peinado con dedicación y años de experiencia.",
        "servicios": [
            ("Corte y peinado", "Cortes de dama y varón, peinados para toda ocasión."),
            ("Color y mechas", "Tintura, balayage y mechas con productos de calidad."),
            ("Tratamientos", "Hidratación y cuidado capilar profesional."),
        ],
    },
    "gasfiteria": {
        "icono": "🔧",
        "color": "#0c4a6e", "acento": "#0ea5e9",
        "tagline": "Urgencias de gasfitería las 24 horas, atención rápida a domicilio.",
        "servicios": [
            ("Destapes y filtraciones", "Destape de cañerías y reparación de filtraciones."),
            ("Instalación de artefactos", "Calefont, WC, lavaplatos, llaves y más."),
            ("Urgencias 24/7", "Atención de emergencias a domicilio todos los días."),
        ],
    },
    "comida": {
        "icono": "🥟",
        "color": "#7c2d12", "acento": "#ea580c",
        "tagline": "Sabor casero de verdad: empanadas y comida chilena hecha con cariño.",
        "servicios": [
            ("Empanadas artesanales", "De pino, queso, napolitana y variedades de la casa."),
            ("Pedidos por encargo", "Para cumpleaños, reuniones y eventos familiares."),
            ("Atención por WhatsApp", "Encarga hoy y retira o recibe en tu casa."),
        ],
    },
    "panaderia": {
        "icono": "🥖",
        "color": "#78350f", "acento": "#f59e0b",
        "tagline": "Pan amasado, pasteles y productos frescos todos los días.",
        "servicios": [
            ("Pan fresco diario", "Pan amasado, marraqueta, hallulla y especiales."),
            ("Pastelería", "Tortas, kuchen y dulces para toda ocasión."),
            ("Encargos especiales", "Pedidos para eventos, celebraciones y negocios."),
        ],
    },
    "pasteleria": {
        "icono": "🎂",
        "color": "#831843", "acento": "#f472b6",
        "tagline": "Tortas personalizadas y dulzura artesanal para tus celebraciones.",
        "servicios": [
            ("Tortas personalizadas", "Diseños a pedido para cumpleaños y matrimonios."),
            ("Cupcakes y dulces", "Mesas dulces, cupcakes y galletas decoradas."),
            ("Reparto a domicilio", "Llevamos tu pedido donde lo necesites."),
        ],
    },
    "belleza": {
        "icono": "💅",
        "color": "#581c87", "acento": "#e879f9",
        "tagline": "Tu espacio de belleza y cuidado personal en el corazón de la ciudad.",
        "servicios": [
            ("Peluquería", "Corte, color y peinados profesionales."),
            ("Manicure y pedicure", "Esmaltado tradicional y permanente."),
            ("Tratamientos de belleza", "Depilación, cejas, pestañas y más."),
        ],
    },
    "unas": {
        "icono": "💅",
        "color": "#9d174d", "acento": "#fb7185",
        "tagline": "Manicure profesional, esmaltado permanente y nail art con estilo.",
        "servicios": [
            ("Esmaltado permanente", "Duración y brillo impecable por semanas."),
            ("Uñas acrílicas y softgel", "Extensiones y sistemas de última tendencia."),
            ("Nail art y diseños", "Diseños personalizados para cada ocasión."),
        ],
    },
    "mecanica": {
        "icono": "🔩",
        "color": "#111827", "acento": "#ef4444",
        "tagline": "Mecánica automotriz confiable: diagnóstico, mantención y reparación.",
        "servicios": [
            ("Mantención preventiva", "Cambio de aceite, frenos, correas y afinamiento."),
            ("Diagnóstico computarizado", "Detección de fallas con equipos especializados."),
            ("Reparaciones generales", "Motor, suspensión, embrague y sistema eléctrico."),
        ],
    },
    "desabolladura": {
        "icono": "🚗",
        "color": "#134e4a", "acento": "#2dd4bf",
        "tagline": "Desabolladura y pintura automotriz con terminaciones de calidad.",
        "servicios": [
            ("Desabolladura", "Recuperamos la carrocería de tu vehículo."),
            ("Pintura automotriz", "Igualación de color y pintura completa o parcial."),
            ("Pulido y detailing", "Tu auto como nuevo, por dentro y por fuera."),
        ],
    },
    "cerrajeria": {
        "icono": "🔑",
        "color": "#3f3f46", "acento": "#eab308",
        "tagline": "Copias de llaves, aperturas y soluciones de cerrajería al instante.",
        "servicios": [
            ("Copia de llaves", "Llaves de casa, auto y candados en minutos."),
            ("Apertura de puertas", "Urgencias y aperturas sin dañar tu cerradura."),
            ("Cambio de cerraduras", "Instalación y reparación de cerraduras y chapas."),
        ],
    },
    "desarmaduria": {
        "icono": "⚙️",
        "color": "#1c1917", "acento": "#f97316",
        "tagline": "Repuestos usados garantizados para todas las marcas y modelos.",
        "servicios": [
            ("Repuestos originales usados", "Motores, cajas, focos, espejos y más."),
            ("Compra de vehículos", "Compramos autos para desarme, pago inmediato."),
            ("Envíos a regiones", "Despachamos repuestos a todo Chile."),
        ],
    },
    "floreria": {
        "icono": "💐",
        "color": "#14532d", "acento": "#f43f5e",
        "tagline": "Flores frescas y arreglos con reparto a domicilio para sorprender.",
        "servicios": [
            ("Arreglos florales", "Ramos y arreglos para toda ocasión."),
            ("Reparto a domicilio", "Entregamos tus flores el mismo día."),
            ("Eventos y condolencias", "Decoración floral y coronas de condolencia."),
        ],
    },
    "peluqueria_canina": {
        "icono": "🐩",
        "color": "#155e75", "acento": "#fbbf24",
        "tagline": "Baño y peluquería para tu mascota, con cariño y a domicilio.",
        "servicios": [
            ("Baño y secado", "Baño completo con productos adecuados a cada pelaje."),
            ("Corte de pelo y uñas", "Cortes de raza y de verano, corte de uñas."),
            ("Atención a domicilio", "Vamos donde tu mascota, sin estrés ni traslados."),
        ],
    },
    "petshop": {
        "icono": "🐾",
        "color": "#7c2d12", "acento": "#22c55e",
        "tagline": "Todo para tu mascota: alimentos, accesorios y mucho cariño.",
        "servicios": [
            ("Alimentos", "Las mejores marcas para perros, gatos y más."),
            ("Accesorios y juguetes", "Correas, camas, ropa y entretención."),
            ("Despacho a domicilio", "Recibe tu pedido en la puerta de tu casa."),
        ],
    },
    "veterinaria": {
        "icono": "🩺",
        "color": "#065f46", "acento": "#34d399",
        "tagline": "Cuidamos la salud de tu mascota con atención profesional y cercana.",
        "servicios": [
            ("Consulta veterinaria", "Medicina general y control sano."),
            ("Vacunas y desparasitación", "Plan de vacunación al día para tu mascota."),
            ("Cirugías y procedimientos", "Esterilización y cirugías con equipo profesional."),
        ],
    },
    "lavanderia": {
        "icono": "🧺",
        "color": "#1e3a8a", "acento": "#38bdf8",
        "tagline": "Lavado, secado y planchado con retiro y entrega a domicilio.",
        "servicios": [
            ("Lavado por kilo", "Ropa de diario lavada, secada y doblada."),
            ("Lavado industrial", "Servicio para empresas, hoteles y residencias."),
            ("Retiro y entrega", "Retiramos y entregamos en tu casa u oficina."),
        ],
    },
    "ferreteria": {
        "icono": "🛠️",
        "color": "#7f1d1d", "acento": "#fbbf24",
        "tagline": "Todo para la construcción, el hogar y tus proyectos.",
        "servicios": [
            ("Materiales de construcción", "Cemento, fierro, madera y áridos."),
            ("Herramientas", "Herramientas manuales y eléctricas de las mejores marcas."),
            ("Despacho a obra", "Llevamos tus materiales directo a la obra o a tu casa."),
        ],
    },
}

PAGE = """<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{nombre} — {ciudad}</title>
<meta name="description" content="{nombre} en {ciudad}. {tagline}">
<style>
  :root {{ --color: {color}; --acento: {acento}; }}
  * {{ margin:0; padding:0; box-sizing:border-box; }}
  body {{ font-family: 'Segoe UI', system-ui, -apple-system, sans-serif; color:#1f2937; line-height:1.6; }}
  .demo-banner {{ background:#111827; color:#fbbf24; text-align:center; font-size:.8rem; padding:.4rem 1rem; }}
  header {{ background: linear-gradient(135deg, var(--color) 0%, color-mix(in srgb, var(--color) 60%, black) 100%); color:#fff; padding:4.5rem 1.5rem 5rem; text-align:center; }}
  header .icono {{ font-size:3.5rem; }}
  header h1 {{ font-size:2.6rem; margin:.5rem 0 .3rem; letter-spacing:-.02em; }}
  header .ciudad {{ font-size:1.05rem; opacity:.85; text-transform:uppercase; letter-spacing:.15em; }}
  header p.tagline {{ max-width:38rem; margin:1.2rem auto 2rem; font-size:1.2rem; opacity:.95; }}
  .cta {{ display:inline-block; background:var(--acento); color:#fff; text-decoration:none; font-weight:700; padding:.9rem 2.2rem; border-radius:999px; font-size:1.05rem; box-shadow:0 6px 20px rgb(0 0 0 / .25); transition:transform .15s; }}
  .cta:hover {{ transform:translateY(-2px); }}
  section {{ padding:3.5rem 1.5rem; max-width:64rem; margin:0 auto; }}
  h2 {{ text-align:center; font-size:1.9rem; color:var(--color); margin-bottom:2rem; }}
  .servicios {{ display:grid; grid-template-columns:repeat(auto-fit, minmax(240px, 1fr)); gap:1.4rem; }}
  .card {{ background:#f9fafb; border:1px solid #e5e7eb; border-radius:1rem; padding:1.6rem; }}
  .card h3 {{ color:var(--color); margin-bottom:.5rem; font-size:1.15rem; }}
  .franja {{ background:color-mix(in srgb, var(--acento) 12%, white); }}
  .franja .inner {{ max-width:64rem; margin:0 auto; padding:3rem 1.5rem; text-align:center; }}
  .franja p {{ max-width:44rem; margin:0 auto; font-size:1.1rem; }}
  .contacto {{ display:grid; grid-template-columns:repeat(auto-fit, minmax(220px, 1fr)); gap:1.2rem; text-align:center; }}
  .contacto .item {{ padding:1.4rem; border-radius:1rem; background:#f9fafb; border:1px solid #e5e7eb; }}
  .contacto .item strong {{ display:block; color:var(--color); margin-bottom:.4rem; }}
  .contacto a {{ color:var(--color); font-weight:600; text-decoration:none; word-break:break-word; }}
  .contacto a:hover {{ text-decoration:underline; }}
  footer {{ background:#111827; color:#9ca3af; text-align:center; font-size:.85rem; padding:1.6rem 1rem; }}
  footer strong {{ color:#e5e7eb; }}
  @media (max-width:600px) {{ header h1 {{ font-size:2rem; }} }}
</style>
</head>
<body>
<div class="demo-banner">⚠️ SITIO DE DEMOSTRACIÓN — propuesta de página web para este negocio. No es su sitio oficial.</div>
<header>
  <div class="icono">{icono}</div>
  <h1>{nombre}</h1>
  <div class="ciudad">{ciudad}</div>
  <p class="tagline">{tagline}</p>
  {cta}
</header>
<section id="servicios">
  <h2>Nuestros servicios</h2>
  <div class="servicios">
{cards}
  </div>
</section>
<div class="franja">
  <div class="inner">
    <h2>¿Por qué elegirnos?</h2>
    <p>Somos un negocio local de {ciudad}, con atención cercana y el compromiso de siempre.
    Contáctanos directamente y recibe atención personalizada, sin intermediarios.</p>
  </div>
</div>
<section id="contacto">
  <h2>Contacto</h2>
  <div class="contacto">
{contactos}
  </div>
</section>
<footer>
  <strong>{nombre}</strong> · {ciudad}<br>
  Página de demostración creada como propuesta comercial — datos de contacto obtenidos de fuentes públicas.<br>
  Si este negocio es tuyo y quieres este sitio (o pedir cambios/eliminación), contáctanos.
</footer>
</body>
</html>
"""

CATALOGO = """<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Demos: 30 negocios chilenos sin página web</title>
<style>
  * {{ margin:0; padding:0; box-sizing:border-box; }}
  body {{ font-family:'Segoe UI', system-ui, sans-serif; background:#f3f4f6; color:#1f2937; }}
  header {{ background:#111827; color:#fff; padding:3rem 1.5rem; text-align:center; }}
  header h1 {{ font-size:2rem; }}
  header p {{ opacity:.8; margin-top:.5rem; }}
  main {{ max-width:70rem; margin:0 auto; padding:2.5rem 1.5rem; display:grid; grid-template-columns:repeat(auto-fill, minmax(280px, 1fr)); gap:1.2rem; }}
  a.card {{ background:#fff; border:1px solid #e5e7eb; border-radius:1rem; padding:1.3rem; text-decoration:none; color:inherit; transition:box-shadow .15s; display:block; }}
  a.card:hover {{ box-shadow:0 8px 24px rgb(0 0 0 / .1); }}
  .card .icono {{ font-size:1.8rem; }}
  .card h2 {{ font-size:1.1rem; margin:.4rem 0 .2rem; }}
  .card .meta {{ font-size:.85rem; color:#6b7280; }}
  .card .tel {{ margin-top:.5rem; font-size:.9rem; font-weight:600; color:#065f46; }}
</style>
</head>
<body>
<header>
  <h1>30 negocios chilenos sin página web — demos listas para vender</h1>
  <p>Cada tarjeta abre el sitio de demostración creado para ese negocio.</p>
</header>
<main>
{tarjetas}
</main>
</body>
</html>
"""


def esc(s):
    return html.escape(s or "")


def main():
    negocios = json.loads((BASE / "data" / "negocios.json").read_text(encoding="utf-8"))
    out_root = BASE / "sitios"
    tarjetas = []

    for n in negocios:
        perfil = RUBROS[n["rubro"]]

        if n.get("whatsapp"):
            cta = f'<a class="cta" href="https://wa.me/{n["whatsapp"]}">Escríbenos por WhatsApp</a>'
        elif n.get("telefono"):
            tel = n["telefono"].split("/")[0].split("(")[0].strip().replace(" ", "")
            cta = f'<a class="cta" href="tel:{esc(tel)}">Llámanos: {esc(n["telefono"])}</a>'
        else:
            cta = f'<a class="cta" href="{esc(n["facebook"])}">Contáctanos por Facebook</a>'

        cards = "\n".join(
            f'    <div class="card"><h3>{esc(t)}</h3><p>{esc(d)}</p></div>'
            for t, d in perfil["servicios"]
        )

        contactos = []
        if n.get("telefono"):
            tel = n["telefono"].split("/")[0].split("(")[0].strip().replace(" ", "")
            contactos.append(
                f'    <div class="item"><strong>📞 Teléfono</strong><a href="tel:{esc(tel)}">{esc(n["telefono"])}</a></div>'
            )
        if n.get("whatsapp"):
            contactos.append(
                f'    <div class="item"><strong>💬 WhatsApp</strong><a href="https://wa.me/{n["whatsapp"]}">Enviar mensaje</a></div>'
            )
        if n.get("facebook"):
            contactos.append(
                f'    <div class="item"><strong>📘 Facebook</strong><a href="{esc(n["facebook"])}">Ver página</a></div>'
            )
        if n.get("direccion"):
            contactos.append(
                f'    <div class="item"><strong>📍 Dirección</strong>{esc(n["direccion"])}</div>'
            )

        page = PAGE.format(
            nombre=esc(n["nombre"]),
            ciudad=esc(n["ciudad"]),
            tagline=esc(perfil["tagline"]),
            icono=perfil["icono"],
            color=perfil["color"],
            acento=perfil["acento"],
            cta=cta,
            cards=cards,
            contactos="\n".join(contactos),
        )

        dest = out_root / n["slug"]
        dest.mkdir(parents=True, exist_ok=True)
        (dest / "index.html").write_text(page, encoding="utf-8")

        tel_line = f'<div class="tel">{esc(n["telefono"])}</div>' if n.get("telefono") else '<div class="tel">Contacto vía Facebook</div>'
        tarjetas.append(
            f'  <a class="card" href="sitios/{n["slug"]}/index.html">'
            f'<div class="icono">{perfil["icono"]}</div>'
            f'<h2>{esc(n["nombre"])}</h2>'
            f'<div class="meta">{esc(n["ciudad"])}</div>'
            f'{tel_line}</a>'
        )

    (BASE / "index.html").write_text(
        CATALOGO.format(tarjetas="\n".join(tarjetas)), encoding="utf-8"
    )
    print(f"Generados {len(negocios)} sitios en {out_root}/ + catálogo index.html")


if __name__ == "__main__":
    main()
