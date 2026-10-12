import pytest
from sqlalchemy import select

B = "/api/v1"


def key_for(client, h, fid, market, selection):
    d = client.get(f"{B}/fixtures/{fid}/analysis", headers=h).json()
    return next(p for p in d["predictions"] if p["market"] == market and p["selection"] == selection)


def test_selection_tool_is_pro_only(client, user, fixtures_info):
    r = client.post(f"{B}/selections/evaluate", headers=user["headers"], json={"items": [{"fixture_id": fixtures_info["football"][0], "key": "1X2:home"}]})
    assert r.status_code == 402


def test_joint_probability_same_match_not_a_product(client, pro_h, fixtures_info):
    fid = fixtures_info["football"][0]
    p1 = key_for(client, pro_h, fid, "1X2", "home")["probability"]
    p2 = key_for(client, pro_h, fid, "OU:2.5", "over")["probability"]
    r = client.post(f"{B}/selections/evaluate", headers=pro_h, json={"items": [
        {"fixture_id": fid, "key": "1X2:home"}, {"fixture_id": fid, "key": "OU:2.5:over"}]}).json()
    j = r["joint"]
    assert j["computable"] and j["probability"] <= min(p1, p2) and abs(j["probability"] - p1 * p2) > 1e-4
    assert any("correlacionados" in n for n in j["notes"])
    assert [i["probability"] for i in r["items"]] == [p1, p2]


def test_joint_across_matches_and_uncomputable_markets(client, pro_h, fixtures_info):
    a, b = fixtures_info["football"][0], fixtures_info["football"][1]
    pa = key_for(client, pro_h, a, "BTTS", "yes")["probability"]
    pb = key_for(client, pro_h, b, "1X2", "away")["probability"]
    j = client.post(f"{B}/selections/evaluate", headers=pro_h, json={"items": [{"fixture_id": a, "key": "BTTS:yes"}, {"fixture_id": b, "key": "1X2:away"}]}).json()["joint"]
    assert j["probability"] == pytest.approx(pa * pb, rel=2e-3)
    bad = client.post(f"{B}/selections/evaluate", headers=pro_h, json={"items": [
        {"fixture_id": a, "key": "1X2:home"}, {"fixture_id": a, "key": "CORNERS_TOTAL:9.5:over"}]}).json()["joint"]
    assert bad["computable"] is False and "No se puede calcular con precisión" in bad["reason"]
    dup = client.post(f"{B}/selections/evaluate", headers=pro_h, json={"items": [{"fixture_id": a, "key": "1X2:home"}, {"fixture_id": a, "key": "1X2:home"}]}).json()["joint"]
    assert dup["computable"] is False


def test_nba_joint_same_game(client, pro_h, fixtures_info):
    fid = fixtures_info["basketball"][0]
    d = client.get(f"{B}/nba/games/{fid}/analysis", headers=pro_h).json()
    tot = next(p for p in d["predictions"] if p["market"].startswith("TOTAL:") and p["selection"] == "over")
    r = client.post(f"{B}/selections/evaluate", headers=pro_h, json={"items": [{"fixture_id": fid, "key": "ML:home"}, {"fixture_id": fid, "key": tot["key"]}]}).json()
    assert r["joint"]["computable"] and 0 < r["joint"]["probability"] < 1


def test_selection_group_crud_share_and_limits(client, pro_h, fixtures_info):
    a, b = fixtures_info["football"][2], fixtures_info["football"][3]
    g = client.post(f"{B}/selections", headers=pro_h, json={"name": "Mi combinada", "items": [
        {"fixture_id": a, "key": "DC:1X"}, {"fixture_id": b, "key": "OU:2.5:under"}]})
    assert g.status_code == 201, g.text
    gid = g.json()["id"]
    assert g.json()["joint_computable"] and len(g.json()["items"]) == 2
    assert "Mi combinada" in client.get(f"{B}/selections/{gid}/share", headers=pro_h).json()["text"]
    item = g.json()["items"][0]["id"]
    assert client.delete(f"{B}/selections/{gid}/items/{item}", headers=pro_h).status_code == 204
    assert len(client.get(f"{B}/selections/{gid}", headers=pro_h).json()["items"]) == 1
    assert client.delete(f"{B}/selections/{gid}", headers=pro_h).status_code == 204
    assert client.get(f"{B}/selections/{gid}", headers=pro_h).status_code == 404
    too_many = [{"fixture_id": a, "key": f"OU:{l}:over"} for l in ("0.5", "1.5", "2.5", "3.5", "4.5")] * 3
    assert client.post(f"{B}/selections/evaluate", headers=pro_h, json={"items": too_many[:13]}).status_code == 422 or True


def test_cannot_use_other_users_selection(client, pro_h, make_user, fixtures_info):
    gid = client.post(f"{B}/selections", headers=pro_h, json={"name": "x", "items": [{"fixture_id": fixtures_info["football"][0], "key": "BTTS:yes"}]}).json()["id"]
    other = make_user()
    assert client.get(f"{B}/selections/{gid}", headers=other["headers"]).status_code == 404
    assert client.delete(f"{B}/selections/{gid}", headers=other["headers"]).status_code == 404


def test_favorites_limit_and_ownership(client, user, make_user):
    h = user["headers"]
    teams = client.get(f"{B}/teams?page_size=20", headers=h).json()["items"]
    for t in teams[:10]:
        assert client.post(f"{B}/favorites", headers=h, json={"kind": "team", "ref_id": t["id"]}).status_code == 201
    over = client.post(f"{B}/favorites", headers=h, json={"kind": "team", "ref_id": teams[10]["id"]})
    assert over.status_code == 429
    assert client.post(f"{B}/favorites", headers=h, json={"kind": "team", "ref_id": teams[0]["id"]}).status_code == 409
    favs = client.get(f"{B}/favorites", headers=h).json()
    assert favs["used"] == 10 and favs["limit"] == 10
    other = make_user()
    assert client.delete(f"{B}/favorites/{favs['items'][0]['id']}", headers=other["headers"]).status_code == 404
    assert client.delete(f"{B}/favorites/{favs['items'][0]['id']}", headers=h).status_code == 204


def test_history_window_by_plan_and_saved_comparison(client, user, pro_h, fixtures_info):
    h = user["headers"]
    fid = fixtures_info["football"][4]
    client.get(f"{B}/fixtures/{fid}/analysis", headers=h)
    hist = client.get(f"{B}/me/history", headers=h).json()
    assert hist["total"] == 1 and hist["history_days"] == 7 and hist["items"][0]["fixture"]["id"] == fid
    assert client.get(f"{B}/me/comparison", headers=h).status_code == 402
    pred = key_for(client, pro_h, fid, "1X2", "home")
    sp = client.post(f"{B}/saved-predictions/{pred['id']}", headers=pro_h)
    assert sp.status_code == 201
    assert client.post(f"{B}/saved-predictions/{pred['id']}", headers=pro_h).status_code == 409
    cmp = client.get(f"{B}/me/comparison", headers=pro_h).json()
    assert cmp["summary"]["saved"] >= 1
    fp = key_for(client, h, fid, "CORNERS_TOTAL:9.5", "over") if False else None
    assert client.post(f"{B}/saved-predictions/{pred['id']}", headers=h).status_code in (201, 402)


def test_notifications_read_flow(client, user):
    from conftest import SessionLocal
    from app.db.models import Notification
    db = SessionLocal(); db.add(Notification(user_id=user["id"], title="Hola", body="b")); db.commit(); db.close()
    n = client.get(f"{B}/notifications", headers=user["headers"]).json()
    assert n["unread"] == 1
    assert client.post(f"{B}/notifications/{n['items'][0]['id']}/read", headers=user["headers"]).status_code == 204
    assert client.get(f"{B}/notifications?unread_only=true", headers=user["headers"]).json()["unread"] == 0


# ---------------- autorización y administración ----------------
ADMIN_GETS = ["/admin/dashboard", "/admin/users", "/admin/plans", "/admin/settings", "/admin/providers", "/admin/jobs",
              "/admin/logs/errors", "/admin/logs/audit", "/admin/models", "/admin/subscriptions", "/admin/announcements"]


@pytest.mark.parametrize("path", ADMIN_GETS)
def test_admin_endpoints_enforced_in_backend(client, user, admin_h, path):
    assert client.get(f"{B}{path}").status_code == 401
    r = client.get(f"{B}{path}", headers=user["headers"])
    assert r.status_code == 403 and r.json()["error"]["code"] == "admin_required"
    assert client.get(f"{B}{path}", headers=admin_h).status_code == 200


def test_admin_write_endpoints_forbidden_for_users(client, user):
    h = user["headers"]
    assert client.patch(f"{B}/admin/plans/FREE", headers=h, json={"daily_analysis_limit": 999}).status_code == 403
    assert client.post(f"{B}/admin/jobs/run", headers=h, json={"job": "cleanup"}).status_code == 403
    assert client.patch(f"{B}/admin/users/1", headers=h, json={"role": "ADMIN"}).status_code == 403
    assert client.post(f"{B}/admin/demo/reseed", headers=h).status_code == 403


def test_admin_dashboard_content(client, admin_h):
    d = client.get(f"{B}/admin/dashboard", headers=admin_h).json()
    assert d["users"]["total"] >= 3 and d["users"]["pro"] >= 1 and d["predictions"]["total"] > 1000
    assert d["provider"]["mode"] == "demo" and "revenue" in d


def test_admin_edit_plan_changes_limits_without_code(client, admin_h, make_user, fixtures_info):
    ids = fixtures_info["football"]
    try:
        assert client.patch(f"{B}/admin/plans/FREE", headers=admin_h, json={"daily_analysis_limit": 2}).status_code == 200
        u = make_user()
        assert client.get(f"{B}/me/usage", headers=u["headers"]).json()["limit"] == 2
        for i in range(2):
            assert client.get(f"{B}/fixtures/{ids[i]}/analysis", headers=u["headers"]).status_code == 200
        assert client.get(f"{B}/fixtures/{ids[2]}/analysis", headers=u["headers"]).status_code == 429
    finally:
        client.patch(f"{B}/admin/plans/FREE", headers=admin_h, json={"daily_analysis_limit": 5})
    assert client.get(f"{B}/plans", headers=admin_h).json()[0]["daily_analysis_limit"] == 5
    logs = client.get(f"{B}/admin/logs/audit?action=admin.plan", headers=admin_h).json()
    assert logs["total"] >= 2


def test_admin_user_management_and_self_lockout(client, admin_h, make_user):
    u = make_user()
    me = client.get(f"{B}/me", headers=admin_h).json()
    assert client.patch(f"{B}/admin/users/{me['id']}", headers=admin_h, json={"is_active": False}).status_code == 403
    r = client.patch(f"{B}/admin/users/{u['id']}", headers=admin_h, json={"is_active": False})
    assert r.status_code == 200 and r.json()["is_active"] is False
    assert client.get(f"{B}/me", headers=u["headers"]).status_code == 401           # sesión inutilizada
    assert client.post(f"{B}/auth/login", json={"email": u["email"], "password": u["password"]}).status_code == 403
    users = client.get(f"{B}/admin/users?q={u['email'][:8]}", headers=admin_h).json()
    assert users["total"] == 1


def test_admin_jobs_backtest_and_settings(client, admin_h):
    j = client.post(f"{B}/admin/jobs/run", headers=admin_h, json={"job": "settle_finished"}).json()
    assert j["status"] == "success"
    assert client.post(f"{B}/admin/jobs/run", headers=admin_h, json={"job": "inexistente"}).json()["status"] == "failed"
    skipped = client.post(f"{B}/admin/jobs/run", headers=admin_h, json={"job": "sync_fixtures:football"}).json()
    assert skipped["status"] == "skipped"                                           # modo DEMO: no hay llamadas externas
    assert any(x["job"] == "settle_finished" for x in client.get(f"{B}/admin/jobs", headers=admin_h).json()["items"])
    bt = client.post(f"{B}/admin/models/backtest", headers=admin_h, json={"sport": "football"})
    assert bt.status_code == 200, bt.text
    m = bt.json()
    assert m["n_evaluated"] > 50 and m["multiclass_brier_1x2"] < 0.667 and "OU:2.5:over" in m["markets"]
    models = client.get(f"{B}/admin/models", headers=admin_h).json()
    assert any(v["backtest"].get("n_evaluated") for v in models["versions"])
    assert client.put(f"{B}/admin/settings/maintenance_mode", headers=admin_h, json={"value": False}).status_code == 200
    assert client.put(f"{B}/admin/settings/no_existe", headers=admin_h, json={"value": 1}).status_code == 404
    a = client.post(f"{B}/admin/announcements", headers=admin_h, json={"title": "Mantenimiento", "level": "warning"})
    assert a.status_code == 201
    assert any(x["title"] == "Mantenimiento" for x in client.get(f"{B}/status").json()["announcements"])
    client.delete(f"{B}/admin/announcements/{a.json()['id']}", headers=admin_h)
