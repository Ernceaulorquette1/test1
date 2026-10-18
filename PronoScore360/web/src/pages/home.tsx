import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Link } from "react-router-dom";
import { Catalog, Lists } from "../api/endpoints";
import { useAuth } from "../auth/AuthContext";
import { Disclaimer, Empty, ErrorBox, MatchCard, PageTitle, Pager, Spinner, TeamBadge } from "../components/ui";
import { today } from "../components/format";

export function Home() {
  const { user, isPro } = useAuth();
  const fx = useQuery({ queryKey: ["fixtures", "upcoming"], queryFn: () => Catalog.fixtures({ status: "scheduled", page_size: 6 }) });
  const u = user!.usage;
  return (
    <>
      <div className="ps-hero p-4 mb-4">
        <h1 className="h3">Hola, {user!.display_name} 👋</h1>
        <p className="mb-2">{isPro ? "Plan Pro: análisis ilimitados y todos los mercados." : `Plan Gratis: ${u.remaining} de ${u.limit} partidos disponibles hoy.`}</p>
        {!isPro && <div className="progress mb-2" style={{ height: 8, maxWidth: 360 }}><div className="progress-bar bg-success" role="progressbar" aria-label="Consultas usadas" style={{ width: `${((u.used) / (u.limit ?? 1)) * 100}%` }} /></div>}
        <small className="opacity-75">Las consultas se renuevan a las 00:00 ({u.timezone}).</small>
      </div>
      <div className="row g-3 mb-4">
        <div className="col-6 col-md-3"><Link to="/football" className="ps-card p-3 d-block text-decoration-none text-reset text-center clickable"><div style={{ fontSize: 30 }}>⚽</div>Fútbol</Link></div>
        <div className="col-6 col-md-3"><Link to="/nba" className="ps-card p-3 d-block text-decoration-none text-reset text-center clickable"><div style={{ fontSize: 30 }}>🏀</div>NBA</Link></div>
        <div className="col-6 col-md-3"><Link to="/predictions" className="ps-card p-3 d-block text-decoration-none text-reset text-center clickable"><div style={{ fontSize: 30 }}>📈</div>Pronósticos{!isPro && " 🔒"}</Link></div>
        <div className="col-6 col-md-3"><Link to="/selections" className="ps-card p-3 d-block text-decoration-none text-reset text-center clickable"><div style={{ fontSize: 30 }}>🧮</div>Selecciones{!isPro && " 🔒"}</Link></div>
      </div>
      <h2 className="h5">Próximos partidos</h2>
      {fx.isLoading ? <Spinner /> : fx.error ? <ErrorBox error={fx.error} onRetry={() => fx.refetch()} /> : !fx.data?.items.length ? <Empty /> :
        <div className="row g-3">{fx.data.items.map((f) => <div className="col-md-6 col-lg-4" key={f.id}><MatchCard f={f} tz={user!.timezone} /></div>)}</div>}
      <Disclaimer />
    </>
  );
}

export function SportSelect() {
  return (<><PageTitle sub="Elige un deporte para ver su calendario">Deportes</PageTitle>
    <div className="row g-3"><div className="col-md-6"><Link to="/football" className="ps-hero p-4 d-block text-decoration-none"><div style={{ fontSize: 44 }}>⚽</div><h2 className="h4">Fútbol internacional</h2><p className="mb-0 opacity-75">Calendario, clasificación, goles, córners, tarjetas y marcadores.</p></Link></div>
      <div className="col-md-6"><Link to="/nba" className="ps-hero p-4 d-block text-decoration-none"><div style={{ fontSize: 44 }}>🏀</div><h2 className="h4">NBA y baloncesto</h2><p className="mb-0 opacity-75">Ganador, totales, hándicap, rangos y simulaciones.</p></Link></div></div></>);
}

export function Calendar({ sport }: { sport: "football" | "basketball" }) {
  const { user } = useAuth();
  const [day, setDay] = useState(today());
  const [league, setLeague] = useState<string>("");
  const [page, setPage] = useState(1);
  const leagues = useQuery({ queryKey: ["leagues", sport], queryFn: () => Catalog.leagues({ sport }) });
  const fx = useQuery({ queryKey: ["fixtures", sport, day, league, page], placeholderData: keepPreviousData,
    queryFn: () => Catalog.fixtures({ sport, date: day, league_id: league || undefined, page, page_size: 12 }) });
  const standings = useQuery({ queryKey: ["standings", league || leagues.data?.items[0]?.id], enabled: !!(league || leagues.data?.items[0]),
    queryFn: () => Catalog.standings(Number(league || leagues.data!.items[0].id)) });
  const shift = (n: number) => { const d = new Date(day + "T12:00:00"); d.setDate(d.getDate() + n); setDay(d.toISOString().slice(0, 10)); setPage(1); };
  return (
    <>
      <PageTitle sub="Fecha y hora en tu zona horaria">{sport === "football" ? "Calendario de fútbol" : "Calendario NBA"}</PageTitle>
      <div className="d-flex gap-2 flex-wrap align-items-end mb-3">
        <button className="btn btn-outline-primary" onClick={() => shift(-1)} aria-label="Día anterior">‹</button>
        <div><label className="form-label small mb-0" htmlFor="day">Fecha</label><input id="day" type="date" className="form-control" value={day} onChange={(e) => { setDay(e.target.value); setPage(1); }} /></div>
        <button className="btn btn-outline-primary" onClick={() => shift(1)} aria-label="Día siguiente">›</button>
        <button className="btn btn-outline-secondary" onClick={() => setDay(today())}>Hoy</button>
        <div><label className="form-label small mb-0" htmlFor="lg">Liga</label><select id="lg" className="form-select" value={league} onChange={(e) => { setLeague(e.target.value); setPage(1); }}><option value="">Todas</option>{leagues.data?.items.map((l: any) => <option key={l.id} value={l.id}>{l.name}</option>)}</select></div>
      </div>
      {fx.isLoading ? <Spinner /> : fx.error ? <ErrorBox error={fx.error} onRetry={() => fx.refetch()} /> : !fx.data?.items.length ? <Empty title="No hay partidos este día"><div>Prueba otra fecha.</div></Empty> : (
        <><div className="row g-3">{fx.data.items.map((f) => <div className="col-md-6 col-lg-4" key={f.id}><MatchCard f={f} tz={user!.timezone} /></div>)}</div>
          <Pager page={page} total={fx.data.total} pageSize={12} onChange={setPage} /></>)}
      {standings.data && (<><h2 className="h5 mt-4">Clasificación · {standings.data.league.name}</h2>
        <div className="table-responsive"><table className="table table-sm"><thead><tr><th>#</th><th>Equipo</th><th>PJ</th><th>G</th><th>E</th><th>P</th><th>{sport === "football" ? "GF-GC" : "PF-PC"}</th><th>Pts</th><th>Forma</th></tr></thead>
          <tbody>{standings.data.rows.map((r: any) => (<tr key={r.team.id}><td>{r.position}</td><td><TeamBadge name={r.team.name} /> {r.team.name}</td><td>{r.played}</td><td>{r.won}</td><td>{r.drawn}</td><td>{r.lost}</td><td>{r.points_for}-{r.points_against}</td><td><strong>{r.points}</strong></td><td>{r.form}</td></tr>))}</tbody></table></div></>)}
    </>
  );
}

export function Teams() {
  const [q, setQ] = useState("");
  const [sel, setSel] = useState<any>(null);
  const [last, setLast] = useState(10);
  const { user } = useAuth();
  const teams = useQuery({ queryKey: ["teams", q], queryFn: () => Catalog.teams({ q: q || undefined, page_size: 24 }), placeholderData: keepPreviousData });
  const stats = useQuery({ queryKey: ["teamstats", sel?.id, last], enabled: !!sel, queryFn: () => Catalog.teamStats(sel.id, last) });
  const upcoming = useQuery({ queryKey: ["teamfx", sel?.id], enabled: !!sel, queryFn: () => Catalog.fixtures({ team_id: sel.id, status: "scheduled", page_size: 3 }) });
  const fav = async () => { try { await Lists.addFavorite("team", sel.id); alert("Añadido a favoritos"); } catch (e: any) { alert(e?.response?.data?.error?.message ?? "No se pudo añadir"); } };
  return (
    <>
      <PageTitle sub="Busca por nombre de equipo">Equipos</PageTitle>
      <label className="visually-hidden" htmlFor="q">Buscar equipo</label>
      <input id="q" className="form-control mb-3" placeholder="Buscar equipo…" value={q} onChange={(e) => setQ(e.target.value)} />
      <div className="row g-3">
        <div className="col-lg-5">{teams.isLoading ? <Spinner /> : teams.error ? <ErrorBox error={teams.error} /> : !teams.data?.items.length ? <Empty /> :
          <div className="list-group">{teams.data.items.map((t: any) => (<button key={t.id} className={`list-group-item list-group-item-action d-flex align-items-center gap-2 ${sel?.id === t.id ? "active" : ""}`} onClick={() => setSel(t)}><TeamBadge name={t.name} />{t.name}</button>))}</div>}</div>
        <div className="col-lg-7">{!sel ? <Empty title="Selecciona un equipo" /> : (
          <div className="ps-card p-3"><div className="d-flex justify-content-between align-items-center"><h2 className="h5 mb-0"><TeamBadge name={sel.name} /> {sel.name}</h2><button className="btn btn-sm btn-outline-warning" onClick={fav}>☆ Favorito</button></div>
            <div className="btn-group my-3" role="group" aria-label="Ventana de partidos">{[5, 10, 20].map((n) => <button key={n} className={`btn btn-sm ${last === n ? "btn-primary" : "btn-outline-primary"}`} onClick={() => setLast(n)}>Últimos {n}</button>)}</div>
            {stats.isLoading ? <Spinner /> : stats.error ? <ErrorBox error={stats.error} /> : stats.data && (
              <><div className="mb-2">{stats.data.form.map((r: string, i: number) => <span key={i} className={`badge me-1 text-bg-${r === "W" ? "success" : r === "D" ? "warning" : "danger"}`}>{r}</span>)}</div>
                <ul className="list-unstyled mb-3"><li>Partidos: <strong>{stats.data.matches}</strong> · G {stats.data.wins} · E {stats.data.draws} · P {stats.data.losses}</li>
                  <li>A favor / en contra (promedio): <strong>{stats.data.avg_for}</strong> / <strong>{stats.data.avg_against}</strong></li>
                  <li>En casa: {stats.data.home.matches} partidos, {stats.data.home.wins} victorias · Fuera: {stats.data.away.matches} partidos, {stats.data.away.wins} victorias</li>
                  {stats.data.corners && <li>Córners a favor/en contra: {stats.data.corners.avg_for} / {stats.data.corners.avg_against}</li>}
                  {stats.data.cards && <li>Tarjetas a favor/en contra: {stats.data.cards.avg_for} / {stats.data.cards.avg_against}</li>}</ul></>)}
            <h3 className="h6">Próximos partidos</h3>{upcoming.data?.items.length ? <div className="row g-2">{upcoming.data.items.map((f) => <div className="col-md-6" key={f.id}><MatchCard f={f} tz={user!.timezone} /></div>)}</div> : <span className="ps-muted">Sin partidos programados.</span>}
          </div>)}</div>
      </div>
    </>
  );
}
