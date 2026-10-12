from datetime import datetime, timedelta
import numpy as np
import pytest
from pronoscore_analytics import football as fb, basketball as bb, metrics as M, backtest as bt, joint as J, quality as Q
from synth import football_league, basketball_league


def test_score_matrix_normalised_and_markets_consistent():
    m = fb.score_matrix(1.6, 1.1, rho=-0.08)
    assert m.sum() == pytest.approx(1.0)
    p = fb.market_1x2(m)
    assert sum(p.values()) == pytest.approx(1.0)
    assert p["home"] > p["away"]
    ou = fb.market_over_under(m, 2.5)
    assert ou["over"] + ou["under"] == pytest.approx(1.0)
    assert fb.market_btts(m)["yes"] + fb.market_btts(m)["no"] == pytest.approx(1.0)
    dc = fb.market_double_chance(p)
    assert dc["1X"] == pytest.approx(p["home"] + p["draw"])


def test_poisson_independent_matches_analytic():
    m = fb.score_matrix(1.0, 1.0, rho=0.0)
    # P(0-0) = e^-2
    assert m[0, 0] == pytest.approx(np.exp(-2), rel=1e-3)
    assert fb.top_scores(m, 5)[0]["probability"] >= fb.top_scores(m, 5)[4]["probability"]


def test_fit_recovers_strength_ordering_and_no_future_leak():
    ms, att, dfn = football_league(rounds=8)
    as_of = ms[-1].date + timedelta(days=1)
    model = fb.FootballModel().fit(ms, as_of)
    best = max(att, key=att.get); worst = min(att, key=att.get)
    assert model.teams[best].attack > model.teams[worst].attack
    assert model.home_adv > 1.0
    # fecha antes de todos los partidos -> sin entrenamiento
    early = fb.FootballModel().fit(ms, ms[0].date)
    assert early.n_training == 0
    # sólo usa partidos anteriores
    mid = ms[len(ms) // 2].date
    m2 = fb.FootballModel().fit(ms, mid)
    assert m2.n_training == sum(1 for x in ms if x.date < mid)


def test_predict_match_full_markets_and_insufficient():
    ms, *_ = football_league()
    model = fb.FootballModel().fit(ms, ms[-1].date + timedelta(days=1))
    r = fb.predict_match(model, "T0", "T1")
    assert r["status"] == "ok"
    assert len(r["top_scores"]) == 5
    assert "CORNERS_TOTAL:9.5" in r["markets"] and "CARDS_TOTAL:3.5" in r["markets"]
    assert "HT_1X2" not in r["markets"]          # sin datos de descanso -> no se inventa
    for mk in r["markets"].values():
        assert all(0 <= v <= 1 for v in mk.values())
    r2 = fb.predict_match(model, "T0", "DESCONOCIDO")
    assert r2["status"] == "insufficient_data"


def test_negative_binomial_overdispersion_wider():
    p = fb.nb_pmf(10, 1.0); q = fb.nb_pmf(10, 3.0)
    ks = np.arange(len(p))
    assert (q * ks**2).sum() - (q * ks).sum()**2 > (p * ks**2).sum() - (p * ks).sum()**2


def test_basketball_prediction_and_exact_score_honesty():
    gs = basketball_league(rounds=8)
    model = bb.BasketballModel(bb.BasketballParams(n_sims=5000)).fit(gs, gs[-1].date + timedelta(days=1))
    r = bb.predict_game(model, "B0", "B1", gs[-1].date + timedelta(days=1))
    assert r["status"] == "ok"
    s = r["sim"]
    assert s["p_home_win"] + s["p_away_win"] == pytest.approx(1.0, abs=1e-9)
    assert 190 < s["expected_total"] < 260
    assert s["central_score"]["exact_hit_probability"] < 0.05     # nunca presentar como probable
    assert s["range_total_80"][0] < s["expected_total"] < s["range_total_80"][1]
    h, a = bb.samples_from_params(r["params"])
    assert bb.market_probability(h, a, "ML:home") == pytest.approx(s["p_home_win"])
    # determinismo
    h2, a2 = bb.samples_from_params(r["params"])
    assert (h == h2).all()


def test_rest_penalty_lowers_expectation():
    gs = basketball_league(rounds=6)
    model = bb.BasketballModel(bb.BasketballParams(n_sims=2000)).fit(gs, gs[-1].date + timedelta(days=1))
    last = model._last_game["B0"]
    fresh = bb.predict_game(model, "B0", "B1", last + timedelta(days=4))
    tired = bb.predict_game(model, "B0", "B1", last + timedelta(days=1))
    assert tired["params"]["mean_home"] < fresh["params"]["mean_home"]


def test_metrics_and_calibration():
    rng = np.random.default_rng(0)
    p = rng.uniform(0.05, 0.95, 4000); y = (rng.uniform(size=4000) < p).astype(int)
    assert M.brier_score(p, y) < 0.22 and M.expected_calibration_error(p, y) < 0.05
    assert M.log_loss([0.5] * 4, [1, 0, 1, 0]) == pytest.approx(np.log(2))
    biased = np.clip(p * 0.5 + 0.4, 0, 1)
    cal = M.IsotonicCalibrator().fit(biased[:2000], y[:2000])
    assert M.brier_score(cal.predict(biased[2000:]), y[2000:]) < M.brier_score(biased[2000:], y[2000:])


def test_backtest_football_temporal_and_beats_baseline():
    ms, *_ = football_league(n_teams=10, rounds=8, seed=7)
    cutoff = ms[int(len(ms) * 0.7)].date
    res = bt.backtest_football(ms, cutoff)
    assert res["n_evaluated"] > 50
    # baseline: frecuencias uniformes (1/3 cada una) => Brier multiclase = 2/3
    assert res["multiclass_brier_1x2"] < 0.667
    assert all(datetime.fromisoformat(r["date"]) >= cutoff for r in res["predictions"])


def test_backtest_basketball_runs():
    gs = basketball_league(n_teams=8, rounds=8)
    res = bt.backtest_basketball(gs, gs[int(len(gs) * 0.7)].date)
    assert res["n_evaluated"] > 20 and res["markets"]["ML:home"]["brier"] < 0.25
    assert res["mae_total_points"] < 25


def test_joint_same_match_not_product():
    ms, *_ = football_league()
    model = fb.FootballModel().fit(ms, ms[-1].date + timedelta(days=1))
    a = fb.predict_match(model, "T0", "T1")
    s1 = J.Selection(1, "football", "1X2:home", a); s2 = J.Selection(1, "football", "OU:2.5:over", a)
    r = J.joint_probability([s1, s2])
    p1 = a["markets"]["1X2"]["home"]; p2 = a["markets"]["OU:2.5"]["over"]
    assert r["computable"] and abs(r["probability"] - p1 * p2) > 1e-4   # correlacionados
    assert 0 < r["probability"] <= min(p1, p2) + 1e-9


def test_joint_cross_match_and_uncomputable():
    ms, *_ = football_league()
    model = fb.FootballModel().fit(ms, ms[-1].date + timedelta(days=1))
    a = fb.predict_match(model, "T0", "T1"); b = fb.predict_match(model, "T2", "T3")
    r = J.joint_probability([J.Selection(1, "football", "1X2:home", a), J.Selection(2, "football", "BTTS:yes", b)])
    assert r["probability"] == pytest.approx(a["markets"]["1X2"]["home"] * b["markets"]["BTTS"]["yes"])
    bad = J.joint_probability([J.Selection(1, "football", "1X2:home", a),
                               J.Selection(1, "football", "CORNERS_TOTAL:9.5:over", a)])
    assert bad["computable"] is False and "No se puede calcular" in bad["reason"]


def test_quality_categories():
    assert Q.categorize(0.8, "insufficient") == Q.CATEGORY_INSUFFICIENT
    assert Q.categorize(0.8, "high") == Q.CATEGORY_HIGH
    assert Q.categorize(0.6, "medium") == Q.CATEGORY_MEDIUM
    assert Q.categorize(0.5, "high") == Q.CATEGORY_UNCERTAIN
    assert Q.categorize(0.9, "low") == Q.CATEGORY_UNCERTAIN
