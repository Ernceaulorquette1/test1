from __future__ import annotations

from datetime import datetime, timezone
from typing import Optional

from fastapi import APIRouter, Depends, Response
from pronoscore_analytics.joint import Selection, joint_probability
from pydantic import BaseModel, Field
from sqlalchemy import func, select, update
from sqlalchemy.orm import Session

from ...core.errors import AppError, Conflict, Forbidden, NotFound, PaymentRequired, QuotaExceeded
from ...core.ratelimit import check_rate
from ...db.models import (Fixture, FixtureAnalysis, Favorite, HistoricalResult, League, Notification, Plan, Prediction, PredictionOutcome,
                          SavedPrediction, SelectionGroup, SelectionItem, Team, User)
from ...db.session import get_db
from ...services.analysis import get_or_create_analysis, selection_key
from ...services.plans import require_feature
from ..deps import Page, current_plan, get_current_user, paginated
from .predictions import DISCLAIMER, prediction_out
from .serializers import fixture_out, team_out

router = APIRouter(tags=["Favoritos, selecciones y notificaciones"], dependencies=[Depends(get_current_user)])


# ---------------- favoritos ----------------
class FavoriteIn(BaseModel):
    kind: str = Field(pattern="^(team|league|fixture)$")
    ref_id: int


@router.get("/favorites", summary="Favoritos del usuario")
def list_favorites(user: User = Depends(get_current_user), db: Session = Depends(get_db), plan: Plan = Depends(current_plan)):
    favs = db.scalars(select(Favorite).where(Favorite.user_id == user.id).order_by(Favorite.created_at.desc())).all()
    items = []
    for f in favs:
        obj = None
        if f.kind == "team" and (t := db.get(Team, f.ref_id)):
            obj = team_out(t)
        elif f.kind == "league" and (l := db.get(League, f.ref_id)):
            obj = {"id": l.id, "name": l.name, "sport": l.sport.code}
        elif f.kind == "fixture" and (x := db.get(Fixture, f.ref_id)):
            obj = fixture_out(x, user.profile.timezone)
        items.append({"id": f.id, "kind": f.kind, "ref_id": f.ref_id, "item": obj})
    return {"items": items, "limit": plan.favorites_limit, "used": len(favs)}


@router.post("/favorites", status_code=201)
def add_favorite(body: FavoriteIn, user: User = Depends(get_current_user), db: Session = Depends(get_db), plan: Plan = Depends(current_plan)):
    model = {"team": Team, "league": League, "fixture": Fixture}[body.kind]
    if not db.get(model, body.ref_id):
        raise NotFound("Elemento no encontrado")
    if db.scalar(select(Favorite.id).where(Favorite.user_id == user.id, Favorite.kind == body.kind, Favorite.ref_id == body.ref_id)):
        raise Conflict("Ya está en favoritos", code="already_favorite")
    n = db.scalar(select(func.count()).select_from(Favorite).where(Favorite.user_id == user.id)) or 0
    if plan.favorites_limit is not None and n >= plan.favorites_limit:
        raise QuotaExceeded(f"Límite de {plan.favorites_limit} favoritos en el plan {plan.name}. Pro amplía el límite.",
                            code="favorites_limit")
    f = Favorite(user_id=user.id, kind=body.kind, ref_id=body.ref_id)
    db.add(f); db.flush()
    return {"id": f.id, "kind": f.kind, "ref_id": f.ref_id}


@router.delete("/favorites/{favorite_id}", status_code=204)
def remove_favorite(favorite_id: int, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    f = db.get(Favorite, favorite_id)
    if not f or f.user_id != user.id:
        raise NotFound("Favorito no encontrado")
    db.delete(f)


# ---------------- pronósticos guardados ----------------
@router.post("/saved-predictions/{prediction_id}", status_code=201, summary="Guardar un pronóstico")
def save_prediction(prediction_id: int, user: User = Depends(get_current_user), db: Session = Depends(get_db), plan: Plan = Depends(current_plan)):
    p = db.get(Prediction, prediction_id)
    if not p:
        raise NotFound("Pronóstico no encontrado")
    if p.market_group not in (plan.allowed_market_groups or []):
        raise PaymentRequired("Este mercado requiere el plan Pro")
    if db.scalar(select(SavedPrediction.id).where(SavedPrediction.user_id == user.id, SavedPrediction.prediction_id == p.id)):
        raise Conflict("Ya guardado", code="already_saved")
    s = SavedPrediction(user_id=user.id, prediction_id=p.id); db.add(s); db.flush()
    return {"id": s.id}


@router.get("/saved-predictions")
def saved_predictions(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    rows = db.execute(select(SavedPrediction, Prediction, Fixture, PredictionOutcome)
                      .join(Prediction, Prediction.id == SavedPrediction.prediction_id).join(Fixture, Fixture.id == Prediction.fixture_id)
                      .outerjoin(PredictionOutcome, PredictionOutcome.prediction_id == Prediction.id)
                      .where(SavedPrediction.user_id == user.id).order_by(SavedPrediction.created_at.desc())).all()
    return [{"id": s.id, "prediction": prediction_out(p, o), "fixture": fixture_out(f, user.profile.timezone)} for s, p, f, o in rows]


@router.delete("/saved-predictions/{saved_id}", status_code=204)
def delete_saved(saved_id: int, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    s = db.get(SavedPrediction, saved_id)
    if not s or s.user_id != user.id:
        raise NotFound("No encontrado")
    db.delete(s)


# ---------------- selecciones / combinadas ----------------
class ItemIn(BaseModel):
    fixture_id: int
    key: str = Field(description="Clave de selección, p. ej. '1X2:home', 'OU:2.5:over', 'BTTS:yes', 'ML:home', 'SPREAD:home:-3.5'")


class EvaluateIn(BaseModel):
    items: list[ItemIn] = Field(min_length=1, max_length=20)


class GroupIn(BaseModel):
    name: str = Field(min_length=1, max_length=100)
    items: list[ItemIn] = Field(default_factory=list, max_length=20)


def _split_key(key: str) -> tuple[str, str]:
    if key.startswith("SPREAD:"):
        return key, "cover"
    market, sel = key.rsplit(":", 1)
    return market, sel


def _evaluate(db: Session, user: User, plan: Plan, items: list[ItemIn]) -> dict:
    if not plan.selections_enabled:
        raise PaymentRequired("La herramienta de selecciones es una función Pro.")
    if len(items) > plan.max_selection_items:
        raise AppError(f"Máximo {plan.max_selection_items} selecciones por grupo", code="too_many_items", status_code=422)
    check_rate(f"heavy:{user.id}", plan.heavy_calcs_per_hour or 1, 3600)
    out, sels = [], []
    for it in items:
        fx = db.get(Fixture, it.fixture_id)
        if not fx:
            raise NotFound(f"Partido {it.fixture_id} no encontrado")
        if fx.status == "finished":
            raise AppError(f"El partido {fx.id} ya finalizó; no se pueden crear selecciones sobre él", code="fixture_finished", status_code=422)
        an = get_or_create_analysis(db, fx)
        market, sel = _split_key(it.key)
        pred = db.scalar(select(Prediction).where(Prediction.analysis_id == an.id, Prediction.market == market,
                                                  Prediction.selection == sel)) if an.status == "ok" else None
        if pred is None:
            out.append({"fixture": fixture_out(fx, user.profile.timezone), "key": it.key, "probability": None,
                        "status": "insufficient_data" if an.status != "ok" else "market_unavailable"})
            sels.append(None)
            continue
        out.append({"fixture": fixture_out(fx, user.profile.timezone), "key": it.key, "probability": round(float(pred.probability), 4),
                    "category": pred.category, "status": "ok"})
        sels.append(Selection(fx.id, fx.league.sport.code, it.key, an.payload))
    if any(s is None for s in sels):
        joint = {"computable": False, "probability": None,
                 "reason": "Alguna selección no tiene datos suficientes o el mercado no está disponible."}
    else:
        joint = joint_probability(sels)
    return {"items": out, "joint": joint, "disclaimer": DISCLAIMER}


@router.post("/selections/evaluate", summary="Probabilidad individual y conjunta (sin guardar)")
def evaluate_selection(body: EvaluateIn, user: User = Depends(get_current_user), db: Session = Depends(get_db), plan: Plan = Depends(current_plan)):
    return _evaluate(db, user, plan, body.items)


def _group_out(g: SelectionGroup, db: Session, tz: str) -> dict:
    items = []
    for it in g.items:
        fx = db.get(Fixture, it.fixture_id)
        market, sel = _split_key(it.market)
        res = None
        if fx.status == "finished":
            from ...services.settlement import evaluate
            hist = db.scalar(select(HistoricalResult).where(HistoricalResult.fixture_id == fx.id))
            res = evaluate(fx.league.sport.code, market, sel, fx.home_score, fx.away_score, hist.extra if hist else {})
        items.append({"id": it.id, "fixture": fixture_out(fx, tz), "key": it.market,
                      "probability_at_save": None if it.probability_at_save is None else float(it.probability_at_save),
                      "result": None if res is None else ("hit" if res else "miss")})
    results = [i["result"] for i in items]
    overall = None if (not results or None in results) else ("hit" if all(r == "hit" for r in results) else "miss")
    return {"id": g.id, "name": g.name, "created_at": g.created_at.isoformat(), "items": items,
            "joint_computable": g.joint_computable, "joint_probability_at_save": None if g.joint_probability is None else float(g.joint_probability),
            "result": overall}


@router.post("/selections", status_code=201, summary="Guardar selección (Pro)")
def create_group(body: GroupIn, user: User = Depends(get_current_user), db: Session = Depends(get_db), plan: Plan = Depends(current_plan)):
    ev = _evaluate(db, user, plan, body.items) if body.items else {"items": [], "joint": {"computable": False, "probability": None}}
    g = SelectionGroup(user_id=user.id, name=body.name, joint_computable=ev["joint"]["computable"], joint_probability=ev["joint"]["probability"])
    for it, e in zip(body.items, ev["items"]):
        g.items.append(SelectionItem(fixture_id=it.fixture_id, market=it.key, probability_at_save=e.get("probability")))
    db.add(g); db.flush()
    return {**_group_out(g, db, user.profile.timezone), "evaluation": ev}


@router.get("/selections")
def list_groups(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    gs = db.scalars(select(SelectionGroup).where(SelectionGroup.user_id == user.id).order_by(SelectionGroup.created_at.desc())).all()
    return [_group_out(g, db, user.profile.timezone) for g in gs]


def _own_group(db: Session, user: User, gid: int) -> SelectionGroup:
    g = db.get(SelectionGroup, gid)
    if not g or g.user_id != user.id:
        raise NotFound("Selección no encontrada")
    return g


@router.get("/selections/{group_id}")
def get_group(group_id: int, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    return _group_out(_own_group(db, user, group_id), db, user.profile.timezone)


@router.delete("/selections/{group_id}", status_code=204)
def delete_group(group_id: int, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    db.delete(_own_group(db, user, group_id))


@router.delete("/selections/{group_id}/items/{item_id}", status_code=204, summary="Eliminar una selección del grupo")
def delete_item(group_id: int, item_id: int, user: User = Depends(get_current_user), db: Session = Depends(get_db), plan: Plan = Depends(current_plan)):
    g = _own_group(db, user, group_id)
    it = next((i for i in g.items if i.id == item_id), None)
    if not it:
        raise NotFound("Elemento no encontrado")
    g.items.remove(it)
    db.flush()
    if g.items:
        ev = _evaluate(db, user, plan, [ItemIn(fixture_id=i.fixture_id, key=i.market) for i in g.items])
        g.joint_computable, g.joint_probability = ev["joint"]["computable"], ev["joint"]["probability"]
    else:
        g.joint_computable, g.joint_probability = False, None


@router.get("/selections/{group_id}/share", summary="Resumen en texto para compartir")
def share_group(group_id: int, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    g = _own_group(db, user, group_id)
    lines = [f"PronoScore360 · {g.name}"]
    for it in g.items:
        fx = db.get(Fixture, it.fixture_id)
        p = "" if it.probability_at_save is None else f" — {float(it.probability_at_save) * 100:.1f} %"
        lines.append(f"• {fx.home_team.name} vs {fx.away_team.name}: {it.market}{p}")
    lines.append(f"Probabilidad conjunta estimada: {float(g.joint_probability) * 100:.1f} %" if g.joint_computable and g.joint_probability is not None
                 else "Probabilidad conjunta: no calculable con precisión")
    lines.append("Estimación estadística; no garantiza resultados. Datos DEMO si el partido es de demostración.")
    return {"text": "\n".join(lines)}


# ---------------- notificaciones ----------------
@router.get("/notifications")
def notifications(unread_only: bool = False, page: int = 1, page_size: int = 30, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    pg = Page(page, page_size)
    stmt = select(Notification).where((Notification.user_id == user.id) | (Notification.user_id.is_(None)))
    if unread_only:
        stmt = stmt.where(Notification.read_at.is_(None))
    total = db.scalar(select(func.count()).select_from(stmt.subquery()))
    rows = db.scalars(stmt.order_by(Notification.created_at.desc()).offset(pg.offset).limit(pg.page_size)).all()
    unread = db.scalar(select(func.count()).select_from(Notification).where(Notification.user_id == user.id, Notification.read_at.is_(None)))
    return {**paginated([{"id": n.id, "kind": n.kind, "title": n.title, "body": n.body, "data": n.data,
                          "read": n.read_at is not None, "created_at": n.created_at.isoformat()} for n in rows], total, pg), "unread": unread}


@router.post("/notifications/{nid}/read", status_code=204)
def read_notification(nid: int, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    n = db.get(Notification, nid)
    if not n or n.user_id != user.id:
        raise NotFound("No encontrada")
    n.read_at = datetime.now(timezone.utc)


@router.post("/notifications/read-all", status_code=204)
def read_all(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    db.execute(update(Notification).where(Notification.user_id == user.id, Notification.read_at.is_(None)).values(read_at=datetime.now(timezone.utc)))
