from datetime import datetime, timedelta, timezone
from zoneinfo import ZoneInfo

import pytest
from sqlalchemy import select

from app.core.errors import QuotaExceeded
from app.db.models import Fixture, Prediction, User

B = "/api/v1"


def test_free_user_five_distinct_fixtures_per_day(client, user, fixtures_info):
    h, ids = user["headers"], fixtures_info["football"]
    for i in range(5):
        r = client.get(f"{B}/fixtures/{ids[i]}/analysis", headers=h)
        assert r.status_code == 200, r.text
        assert r.json()["usage"]["used"] == i + 1
    sixth = client.get(f"{B}/fixtures/{ids[5]}/analysis", headers=h)
    assert sixth.status_code == 429 and sixth.json()["error"]["code"] == "quota_exceeded"
    assert sixth.json()["error"]["details"]["usage"]["remaining"] == 0
    # el mismo partido otra vez NO descuenta ni se bloquea
    again = client.get(f"{B}/fixtures/{ids[0]}/analysis", headers=h)
    assert again.status_code == 200 and again.json()["usage"]["used"] == 5
    # varios mercados / marcador exacto del mismo partido = misma consulta
    assert client.get(f"{B}/fixtures/{ids[1]}/exact-score", headers=h).status_code == 200
    assert client.get(f"{B}/me/usage", headers=h).json()["remaining"] == 0


def test_same_analysis_returned_on_repeat(client, user, fixtures_info):
    fid = fixtures_info["football"][0]
    a = client.get(f"{B}/fixtures/{fid}/analysis", headers=user["headers"]).json()
    b = client.get(f"{B}/fixtures/{fid}/analysis", headers=user["headers"]).json()
    assert a["analysis"]["id"] == b["analysis"]["id"] and a["predictions"] == b["predictions"]


def test_quota_renews_at_account_midnight_server_clock(db, make_user):
    """Renovación calculada con el reloj del servidor y la zona de la cuenta (el dispositivo no interviene)."""
    from app.services.plans import consume_analysis, get_plan, next_reset, usage_summary
    u = make_user(timezone="Asia/Tokyo")
    user = db.get(User, u["id"])
    plan = get_plan(db, "FREE")
    fx_ids = [f for (f,) in db.execute(select(Fixture.id).limit(7))]
    tokyo = ZoneInfo("Asia/Tokyo")
    t0 = datetime(2026, 3, 10, 22, 0, tzinfo=tokyo).astimezone(timezone.utc)           # 22:00 Tokio
    for fid in fx_ids[:5]:
        consume_analysis(db, user, fid, None, plan, now=t0)
    with pytest.raises(QuotaExceeded):
        consume_analysis(db, user, fx_ids[5], None, plan, now=t0 + timedelta(minutes=30))   # 22:30 aún mismo día
    assert next_reset(user, t0).astimezone(tokyo).hour == 0
    nxt = datetime(2026, 3, 11, 0, 1, tzinfo=tokyo).astimezone(timezone.utc)                # 00:01 Tokio día siguiente
    assert usage_summary(db, user, plan, nxt)["remaining"] == 5
    consume_analysis(db, user, fx_ids[5], None, plan, now=nxt)
    assert usage_summary(db, user, plan, nxt)["used"] == 1


def test_free_vs_pro_content(client, user, pro_h, fixtures_info):
    fid = fixtures_info["football"][2]
    free = client.get(f"{B}/fixtures/{fid}/analysis", headers=user["headers"]).json()
    pro = client.get(f"{B}/fixtures/{fid}/analysis", headers=pro_h).json()
    assert {p["group"] for p in free["predictions"]} == {"basic"}
    assert {"goals", "corners", "cards"} <= {p["group"] for p in pro["predictions"]}
    assert free["locked_groups"] and not pro["locked_groups"]
    assert len(free["exact_score"]["top_scores"]) == 1 and len(pro["exact_score"]["top_scores"]) == 5
    assert free["drivers"] is None and pro["drivers"]
    assert pro["usage"]["limit"] is None and pro["usage"]["remaining"] is None


def test_football_probabilities_are_coherent(client, pro_h, fixtures_info):
    d = client.get(f"{B}/fixtures/{fixtures_info['football'][3]}/analysis", headers=pro_h).json()
    P = {(p["market"], p["selection"]): p["probability"] for p in d["predictions"]}
    assert P[("1X2", "home")] + P[("1X2", "draw")] + P[("1X2", "away")] == pytest.approx(1, abs=2e-3)
    assert P[("DC", "1X")] == pytest.approx(P[("1X2", "home")] + P[("1X2", "draw")], abs=2e-3)
    for line in ("0.5", "1.5", "2.5", "3.5", "4.5"):
        assert P[(f"OU:{line}", "over")] + P[(f"OU:{line}", "under")] == pytest.approx(1, abs=2e-3)
    assert P[("OU:0.5", "over")] >= P[("OU:1.5", "over")] >= P[("OU:2.5", "over")] >= P[("OU:3.5", "over")] >= P[("OU:4.5", "over")]
    assert ("CORNERS_TOTAL:9.5", "over") in P and ("CARDS_TOTAL:3.5", "under") in P and ("BTTS", "yes") in P
    a = d["analysis"]
    assert a["n_home"] >= 5 and a["generated_at"] and a["is_pre_match"] is True and a["is_demo"] is True
    top = d["exact_score"]["top_scores"]
    assert [t["probability"] for t in top] == sorted([t["probability"] for t in top], reverse=True)
    assert all(t["probability"] < 0.2 for t in top)           # un marcador exacto nunca luce "seguro"
    assert d["fixture"]["is_demo"] and "DEMO" in d["fixture"]["data_source"]


def test_nba_analysis_and_honest_exact_score(client, pro_h, user, fixtures_info):
    fid = fixtures_info["basketball"][0]
    d = client.get(f"{B}/nba/games/{fid}/analysis", headers=pro_h).json()
    ex = d["exact_score"]
    assert ex["type"] == "basketball" and ex["p_home_win"] + ex["p_away_win"] == pytest.approx(1, abs=1e-3)
    assert ex["central_score"]["exact_hit_probability"] < 0.05
    assert "estimación" in ex["central_score"]["note"].lower()
    assert ex["range_total_80"][0] < ex["expected_total"] < ex["range_total_80"][1]
    assert {"ML", "SPREAD:home:-3.5"} & {p["market"] for p in d["predictions"]}
    free = client.get(f"{B}/nba/games/{fid}/analysis", headers=user["headers"]).json()
    assert "range_total_80" not in free["exact_score"] and {p["group"] for p in free["predictions"]} == {"basic"}
    # fútbol a través del alias NBA => 404
    assert client.get(f"{B}/nba/games/{fixtures_info['football'][0]}/analysis", headers=pro_h).status_code == 404


def test_nba_custom_lines_pro_only(client, pro_h, user, fixtures_info):
    fid = fixtures_info["basketball"][1]
    assert client.get(f"{B}/nba/games/{fid}/lines?total=220.5", headers=user["headers"]).status_code == 402
    r = client.get(f"{B}/nba/games/{fid}/lines?total=215.5&home_total=108.5&spread=-4.5", headers=pro_h).json()
    t = r["lines"]["total"]
    assert t["over"] + t["under"] == pytest.approx(1, abs=0.02)         # el empate exacto en la línea .5 es imposible
    lo = client.get(f"{B}/nba/games/{fid}/lines?total=190.5", headers=pro_h).json()["lines"]["total"]["over"]
    hi = client.get(f"{B}/nba/games/{fid}/lines?total=250.5", headers=pro_h).json()["lines"]["total"]["over"]
    assert lo > t["over"] > hi
    assert client.get(f"{B}/nba/games/{fid}/lines?total=50", headers=pro_h).status_code == 422


def test_insufficient_data_is_rejected_and_not_charged(client, user, db):
    from app.db.models import Team, League
    from app.services.analysis import create_analysis
    lg = db.scalar(select(League).where(League.is_demo.is_(True), League.name.ilike("%Atlántica%")))
    t1 = Team(sport_id=lg.sport_id, provider="demo", external_id="NEW1", name="Demo Nuevo A", is_demo=True)
    t2 = Team(sport_id=lg.sport_id, provider="demo", external_id="NEW2", name="Demo Nuevo B", is_demo=True)
    db.add_all([t1, t2]); db.flush()
    fx = Fixture(sport_id=lg.sport_id, league_id=lg.id, provider="demo", external_id="NEWFX", home_team_id=t1.id, away_team_id=t2.id,
                 kickoff=datetime.now(timezone.utc) + timedelta(days=2), is_demo=True)
    db.add(fx); db.commit()
    r = client.get(f"{B}/fixtures/{fx.id}/analysis", headers=user["headers"]).json()
    assert r["message"] == "Datos insuficientes" and r["predictions"] == [] and r["reason"]
    assert client.get(f"{B}/me/usage", headers=user["headers"]).json()["used"] == 0


def test_analysis_after_kickoff_is_not_a_pre_match_prediction(client, db, pro_h, fixtures_info):
    """Un análisis creado cuando el partido ya terminó NO cuenta como predicción previa ni se evalúa."""
    fid = fixtures_info["finished_football"]
    fx = db.get(Fixture, fid)
    from app.services.analysis import create_analysis
    an = create_analysis(db, fx)                      # sin force_pre_match: partido ya jugado
    db.commit()
    assert an.is_pre_match is False
    preds = db.scalars(select(Prediction).where(Prediction.analysis_id == an.id)).all()
    assert preds and all(p.is_pre_match is False for p in preds)


def test_training_never_uses_future_matches(db, fixtures_info):
    from app.services.analysis import compute_payload
    fx = db.get(Fixture, fixtures_info["finished_football"])
    early = compute_payload(db, fx, fx.kickoff - timedelta(days=100))
    late = compute_payload(db, fx, fx.kickoff)
    assert early["n_training"] < late["n_training"]
    from app.db.models import HistoricalResult
    n_before = len(db.scalars(select(HistoricalResult).where(HistoricalResult.league_id == fx.league_id, HistoricalResult.played_at < fx.kickoff)).all())
    assert late["n_training"] == n_before


def test_predictions_list_pro_only_with_filters(client, user, pro_h):
    assert client.get(f"{B}/predictions", headers=user["headers"]).status_code == 402
    r = client.get(f"{B}/predictions?min_probability=0.7&page_size=50", headers=pro_h).json()
    assert r["items"] and all(i["probability"] >= 0.7 for i in r["items"])
    assert [i["probability"] for i in r["items"]] == sorted((i["probability"] for i in r["items"]), reverse=True)
    assert all(i["fixture"]["status"] == "scheduled" for i in r["items"])
    f = client.get(f"{B}/predictions?sport=basketball&market=ML&min_probability=0.6", headers=pro_h).json()
    assert all(i["market"] == "ML" and i["fixture"]["sport"] == "basketball" for i in f["items"])
    q = client.get(f"{B}/predictions?uncertainty=low&min_matches=15", headers=pro_h).json()
    assert all(i["uncertainty"] == "low" and i["data_points"] >= 15 for i in q["items"])
    cat = client.get(f"{B}/predictions?category=high_probability", headers=pro_h).json()
    assert all(i["category"] == "high_probability" and i["probability"] >= 0.7 for i in cat["items"])


def test_probability_filter_bounds_and_never_forced(client, pro_h):
    assert client.get(f"{B}/predictions?min_probability=0.5", headers=pro_h).status_code == 422
    assert client.get(f"{B}/predictions?min_probability=0.99", headers=pro_h).status_code == 422
    r = client.get(f"{B}/predictions?min_probability=0.95", headers=pro_h).json()
    assert all(i["probability"] >= 0.95 for i in r["items"])        # si no hay ninguno, la lista queda vacía (no se fuerzan resultados)
    assert r["total"] == len(r["items"]) if r["total"] <= 30 else True
    meta = client.get(f"{B}/predictions/meta", headers=pro_h).json()
    assert meta["probability_range"] == {"min": 0.6, "max": 0.95} and meta["categories"]


def test_fixtures_filters_pagination_and_timezone(client, user):
    h = user["headers"]
    allf = client.get(f"{B}/fixtures?page_size=100", headers=h).json()
    assert allf["total"] > 100 and len(allf["items"]) == 100
    fb = client.get(f"{B}/fixtures?sport=football&status=scheduled", headers=h).json()
    assert fb["items"] and all(i["sport"] == "football" and i["status"] == "scheduled" for i in fb["items"])
    t = fb["items"][0]["home_team"]
    by_team = client.get(f"{B}/fixtures?team_id={t['id']}", headers=h).json()
    assert by_team["items"] and all(t["id"] in (i["home_team"]["id"], i["away_team"]["id"]) for i in by_team["items"])
    by_q = client.get(f"{B}/fixtures?q={t['name'].split()[-1]}", headers=h).json()
    assert by_q["total"] >= by_team["total"] > 0
    k = datetime.fromisoformat(fb["items"][0]["kickoff"]).astimezone(ZoneInfo("Pacific/Auckland")).date().isoformat()
    day = client.get(f"{B}/fixtures?date={k}&tz=Pacific/Auckland&sport=football", headers=h).json()
    assert day["items"] and all(i["kickoff_local"].startswith(k) for i in day["items"])
    assert client.get(f"{B}/fixtures?tz=Mars/Base", headers=h).status_code == 422


def test_stats_standings_search(client, user, fixtures_info):
    h = user["headers"]
    fid = fixtures_info["football"][0]
    st = client.get(f"{B}/fixtures/{fid}/stats", headers=h).json()
    assert set(st["form"]) == {"5", "10", "20"} and st["form"]["10"]["home_team"]["matches"] == 10
    assert all(r["date"] for r in st["head_to_head"])
    team = client.get(f"{B}/fixtures/{fid}", headers=h).json()["home_team"]["id"]
    ts = client.get(f"{B}/teams/{team}/stats?last=5", headers=h).json()
    assert ts["matches"] == 5 and ts["wins"] + ts["draws"] + ts["losses"] == 5 and ts["corners"]
    assert client.get(f"{B}/teams/{team}/stats?last=7", headers=h).status_code == 422
    lg = client.get(f"{B}/leagues?sport=football", headers=h).json()["items"][0]
    s = client.get(f"{B}/standings?league_id={lg['id']}", headers=h).json()
    assert [r["position"] for r in s["rows"]] == sorted(r["position"] for r in s["rows"]) and s["rows"][0]["points"] >= s["rows"][-1]["points"]
    assert client.get(f"{B}/teams?q=aurora", headers=h).json()["total"] == 1
    assert {x["code"] for x in client.get(f"{B}/sports", headers=h).json()} == {"football", "basketball"}


def test_settlement_and_model_performance(client, user, pro_h):
    assert client.get(f"{B}/models/performance", headers=user["headers"]).status_code == 402
    r = client.get(f"{B}/models/performance", headers=pro_h).json()
    allrows = [i for i in r["items"] if i["market"] == "ALL" and i["league"] == "Todas"]
    assert {i["sport"] for i in allrows} == {"football", "basketball"}
    for i in allrows:
        assert i["n"] > 100 and 0 < i["brier"] < 0.3 and 0 < i["log_loss"] < 0.8
        assert abs(i["hit_rate"] - i["mean_probability"]) < 0.06      # calibración razonable sobre datos sintéticos
