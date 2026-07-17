#!/usr/bin/env python3
"""Genera demo-online.html: un solo archivo con el catálogo y las 30 demos,
navegable por hash (#/slug), pensado para publicarse como página única.

Uso: python3 generar_artifact.py
"""
import json
import html
from pathlib import Path

from generar_sitios import RUBROS

BASE = Path(__file__).parent


def esc(s):
    return html.escape(s or "")


def demo_html(n, perfil):
    if n.get("whatsapp"):
        cta = f'<a class="cta" href="https://wa.me/{n["whatsapp"]}">Escríbenos por WhatsApp</a>'
    elif n.get("telefono"):
        tel = n["telefono"].split("/")[0].split("(")[0].strip().replace(" ", "")
        cta = f'<a class="cta" href="tel:{esc(tel)}">Llámanos: {esc(n["telefono"])}</a>'
    else:
        cta = f'<a class="cta" href="{esc(n["facebook"])}">Contáctanos por Facebook</a>'

    cards = "\n".join(
        f'      <div class="card"><h3>{esc(t)}</h3><p>{esc(d)}</p></div>'
        for t, d in perfil["servicios"]
    )

    contactos = []
    if n.get("telefono"):
        tel = n["telefono"].split("/")[0].split("(")[0].strip().replace(" ", "")
        contactos.append(f'      <div class="item"><strong>📞 Teléfono</strong><a href="tel:{esc(tel)}">{esc(n["telefono"])}</a></div>')
    if n.get("whatsapp"):
        contactos.append(f'      <div class="item"><strong>💬 WhatsApp</strong><a href="https://wa.me/{n["whatsapp"]}">Enviar mensaje</a></div>')
    if n.get("facebook"):
        contactos.append(f'      <div class="item"><strong>📘 Facebook</strong><a href="{esc(n["facebook"])}">Ver página</a></div>')
    if n.get("direccion"):
        contactos.append(f'      <div class="item"><strong>📍 Dirección</strong>{esc(n["direccion"])}</div>')

    return f"""
<article class="demo" id="p-{n["slug"]}" hidden style="--color:{perfil["color"]}; --acento:{perfil["acento"]}">
  <a class="volver" href="#">← Catálogo</a>
  <div class="demo-banner">⚠️ SITIO DE DEMOSTRACIÓN — propuesta de página web para este negocio. No es su sitio oficial.</div>
  <header class="demo-hero">
    <div class="icono">{perfil["icono"]}</div>
    <h1>{esc(n["nombre"])}</h1>
    <div class="ciudad">{esc(n["ciudad"])}</div>
    <p class="tagline">{esc(perfil["tagline"])}</p>
    {cta}
  </header>
  <section>
    <h2>Nuestros servicios</h2>
    <div class="servicios">
{cards}
    </div>
  </section>
  <div class="franja">
    <div class="inner">
      <h2>¿Por qué elegirnos?</h2>
      <p>Somos un negocio local de {esc(n["ciudad"])}, con atención cercana y el compromiso de siempre.
      Contáctanos directamente y recibe atención personalizada, sin intermediarios.</p>
    </div>
  </div>
  <section>
    <h2>Contacto</h2>
    <div class="contacto">
{chr(10).join(contactos)}
    </div>
  </section>
  <footer class="demo-footer">
    <strong>{esc(n["nombre"])}</strong> · {esc(n["ciudad"])}<br>
    Página de demostración creada como propuesta comercial — datos de contacto obtenidos de fuentes públicas.<br>
    Si este negocio es tuyo y quieres este sitio (o pedir cambios/eliminación), contáctanos.
  </footer>
</article>"""


def main():
    negocios = json.loads((BASE / "data" / "negocios.json").read_text(encoding="utf-8"))

    # Agrupar tarjetas por zona para el catálogo
    grupos = {}
    for n in negocios:
        zona = n["ciudad"].split(",")[-1].split("/")[0].split("(")[0].strip()
        grupos.setdefault(zona, []).append(n)

    secciones = []
    for zona, lista in grupos.items():
        tarjetas = []
        for n in lista:
            perfil = RUBROS[n["rubro"]]
            contacto = esc(n["telefono"]) if n.get("telefono") else "Contacto vía Facebook"
            tarjetas.append(
                f'    <a class="tarjeta" href="#/{n["slug"]}">'
                f'<span class="t-icono">{perfil["icono"]}</span>'
                f'<span class="t-info"><strong>{esc(n["nombre"])}</strong>'
                f'<span class="t-meta">{esc(n["ciudad"])}</span>'
                f'<span class="t-tel">{contacto}</span></span></a>'
            )
        secciones.append(
            f'  <h2 class="zona">{esc(zona)}</h2>\n  <div class="grilla">\n'
            + "\n".join(tarjetas) + "\n  </div>"
        )

    demos = "\n".join(demo_html(n, RUBROS[n["rubro"]]) for n in negocios)

    page = f"""<title>Demos web — 30 negocios Chile</title>
<style>
  :root {{
    --fondo:#f4f5f7; --tinta:#1b1f27; --tinta2:#5b6472; --panel:#ffffff;
    --borde:#e3e6ea; --marca:#0e5a4a; --marca-suave:#e7f1ee;
  }}
  @media (prefers-color-scheme: dark) {{
    :root {{ --fondo:#14171c; --tinta:#e8eaee; --tinta2:#9aa3af; --panel:#1d2129;
            --borde:#2b303a; --marca:#4fae95; --marca-suave:#1e2c28; }}
  }}
  :root[data-theme="dark"] {{ --fondo:#14171c; --tinta:#e8eaee; --tinta2:#9aa3af; --panel:#1d2129;
            --borde:#2b303a; --marca:#4fae95; --marca-suave:#1e2c28; }}
  :root[data-theme="light"] {{ --fondo:#f4f5f7; --tinta:#1b1f27; --tinta2:#5b6472; --panel:#ffffff;
            --borde:#e3e6ea; --marca:#0e5a4a; --marca-suave:#e7f1ee; }}
  * {{ margin:0; padding:0; box-sizing:border-box; }}
  body {{ font-family:'Segoe UI', system-ui, -apple-system, sans-serif; background:var(--fondo); color:var(--tinta); line-height:1.6; }}

  /* ---------- Catálogo ---------- */
  #catalogo {{ max-width:62rem; margin:0 auto; padding:2.5rem 1.5rem 4rem; }}
  .cabecera {{ padding:1rem 0 2rem; border-bottom:1px solid var(--borde); margin-bottom:1.5rem; }}
  .cabecera .kicker {{ text-transform:uppercase; letter-spacing:.18em; font-size:.75rem; color:var(--marca); font-weight:700; }}
  .cabecera h1 {{ font-size:1.9rem; letter-spacing:-.02em; text-wrap:balance; margin:.4rem 0; }}
  .cabecera p {{ color:var(--tinta2); max-width:42rem; }}
  .zona {{ font-size:.8rem; text-transform:uppercase; letter-spacing:.14em; color:var(--tinta2); margin:2rem 0 .8rem; }}
  .grilla {{ display:grid; grid-template-columns:repeat(auto-fill, minmax(260px, 1fr)); gap:.8rem; }}
  .tarjeta {{ display:flex; gap:.8rem; align-items:flex-start; background:var(--panel); border:1px solid var(--borde);
             border-radius:.8rem; padding:1rem; text-decoration:none; color:inherit; transition:border-color .15s, transform .15s; }}
  .tarjeta:hover, .tarjeta:focus-visible {{ border-color:var(--marca); transform:translateY(-1px); }}
  .t-icono {{ font-size:1.5rem; background:var(--marca-suave); border-radius:.6rem; padding:.35rem .5rem; }}
  .t-info {{ display:flex; flex-direction:column; gap:.1rem; }}
  .t-info strong {{ font-size:.98rem; }}
  .t-meta {{ font-size:.8rem; color:var(--tinta2); }}
  .t-tel {{ font-size:.85rem; color:var(--marca); font-weight:600; font-variant-numeric:tabular-nums; }}
  .aviso {{ margin-top:2.5rem; font-size:.8rem; color:var(--tinta2); border-top:1px solid var(--borde); padding-top:1rem; }}

  /* ---------- Demos (diseño fijo claro: son maquetas de sitios reales) ---------- */
  .demo {{ background:#fff; color:#1f2937; min-height:100vh; }}
  .volver {{ position:fixed; top:.6rem; left:.6rem; z-index:10; background:#111827; color:#fff; text-decoration:none;
            font-size:.85rem; padding:.4rem .9rem; border-radius:999px; opacity:.92; }}
  .demo-banner {{ background:#111827; color:#fbbf24; text-align:center; font-size:.8rem; padding:.4rem 4.5rem; }}
  .demo-hero {{ background:linear-gradient(135deg, var(--color) 0%, color-mix(in srgb, var(--color) 60%, black) 100%);
               color:#fff; padding:4.5rem 1.5rem 5rem; text-align:center; }}
  .demo-hero .icono {{ font-size:3.5rem; }}
  .demo-hero h1 {{ font-size:2.6rem; margin:.5rem 0 .3rem; letter-spacing:-.02em; text-wrap:balance; }}
  .demo-hero .ciudad {{ font-size:1.05rem; opacity:.85; text-transform:uppercase; letter-spacing:.15em; }}
  .demo-hero .tagline {{ max-width:38rem; margin:1.2rem auto 2rem; font-size:1.2rem; opacity:.95; }}
  .cta {{ display:inline-block; background:var(--acento); color:#fff; text-decoration:none; font-weight:700;
         padding:.9rem 2.2rem; border-radius:999px; font-size:1.05rem; box-shadow:0 6px 20px rgb(0 0 0 / .25); transition:transform .15s; }}
  .cta:hover {{ transform:translateY(-2px); }}
  .demo section {{ padding:3.5rem 1.5rem; max-width:64rem; margin:0 auto; }}
  .demo h2 {{ text-align:center; font-size:1.9rem; color:var(--color); margin-bottom:2rem; }}
  .servicios {{ display:grid; grid-template-columns:repeat(auto-fit, minmax(240px, 1fr)); gap:1.4rem; }}
  .demo .card {{ background:#f9fafb; border:1px solid #e5e7eb; border-radius:1rem; padding:1.6rem; }}
  .demo .card h3 {{ color:var(--color); margin-bottom:.5rem; font-size:1.15rem; }}
  .franja {{ background:color-mix(in srgb, var(--acento) 12%, white); }}
  .franja .inner {{ max-width:64rem; margin:0 auto; padding:3rem 1.5rem; text-align:center; }}
  .franja p {{ max-width:44rem; margin:0 auto; font-size:1.1rem; }}
  .contacto {{ display:grid; grid-template-columns:repeat(auto-fit, minmax(220px, 1fr)); gap:1.2rem; text-align:center; }}
  .contacto .item {{ padding:1.4rem; border-radius:1rem; background:#f9fafb; border:1px solid #e5e7eb; }}
  .contacto .item strong {{ display:block; color:var(--color); margin-bottom:.4rem; }}
  .contacto a {{ color:var(--color); font-weight:600; text-decoration:none; word-break:break-word; }}
  .contacto a:hover {{ text-decoration:underline; }}
  .demo-footer {{ background:#111827; color:#9ca3af; text-align:center; font-size:.85rem; padding:1.6rem 1rem; }}
  .demo-footer strong {{ color:#e5e7eb; }}
  @media (max-width:600px) {{ .demo-hero h1 {{ font-size:2rem; }} }}
  @media (prefers-reduced-motion: reduce) {{ * {{ transition:none !important; }} }}
</style>

<main id="catalogo">
  <div class="cabecera">
    <div class="kicker">Propuestas comerciales · Julio 2026</div>
    <h1>30 negocios chilenos sin página web — demos listas para mostrar</h1>
    <p>Cada tarjeta abre el sitio de demostración creado para ese negocio. Comparte el enlace
    directo de una demo agregando <code>#/nombre-del-negocio</code> a la URL.</p>
  </div>
{chr(10).join(secciones)}
  <p class="aviso">Sitios de demostración no oficiales, creados como propuesta comercial.
  Datos de contacto obtenidos de publicaciones públicas de cada negocio (Facebook y directorios).
  Verifica los datos antes de contactar.</p>
</main>
{demos}
<script>
  const catalogo = document.getElementById('catalogo');
  function mostrar() {{
    const slug = location.hash.startsWith('#/') ? location.hash.slice(2) : null;
    let encontrado = false;
    document.querySelectorAll('.demo').forEach(d => {{
      const activo = d.id === 'p-' + slug;
      d.hidden = !activo;
      if (activo) encontrado = true;
    }});
    catalogo.hidden = encontrado;
    window.scrollTo(0, 0);
  }}
  window.addEventListener('hashchange', mostrar);
  mostrar();
</script>"""

    (BASE / "demo-online.html").write_text(page, encoding="utf-8")
    print(f"Generado demo-online.html ({len(page)//1024} KB) con {len(negocios)} demos")


if __name__ == "__main__":
    main()
