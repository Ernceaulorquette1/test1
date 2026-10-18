"""Probabilidad conjunta de selecciones (combinadas) SIN asumir independencia falsa.

Reglas:
  * Selecciones de partidos distintos -> producto (aproximación; se marca como tal).
  * Varias selecciones del MISMO partido -> se calcula con la distribución conjunta
    real (matriz de marcador / muestras Monte Carlo) si todos los mercados se pueden
    expresar con ella. Si no (p. ej. córners + goles), NO se calcula y se informa.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Optional

import numpy as np

from . import basketball as bb
from . import football as fb


@dataclass
class Selection:
    fixture_id: int
    sport: str          # 'football' | 'basketball'
    market: str         # ver sintaxis más abajo
    analysis: dict      # payload del análisis (lambda_home, rho / params)


def football_mask(market: str, shape=(fb.MAX_GOALS + 1, fb.MAX_GOALS + 1)) -> Optional[np.ndarray]:
    """Máscara booleana sobre la matriz de marcador. Formato: '1X2:home', 'DC:1X', 'OU:2.5:over', 'BTTS:yes'."""
    n = shape[0]
    i, j = np.indices(shape)
    parts = market.split(":")
    try:
        if parts[0] == "1X2":
            return {"home": i > j, "draw": i == j, "away": i < j}[parts[1]]
        if parts[0] == "DC":
            return {"1X": i >= j, "12": i != j, "X2": i <= j}[parts[1]]
        if parts[0] == "OU":
            line = float(parts[1])
            return (i + j > line) if parts[2] == "over" else (i + j < line)
        if parts[0] == "BTTS":
            both = (i > 0) & (j > 0)
            return both if parts[1] == "yes" else ~both
    except (KeyError, IndexError, ValueError):
        return None
    return None


def _same_fixture_prob(sels: list[Selection]) -> Optional[float]:
    first = sels[0]
    if first.sport == "football":
        a = first.analysis
        if a.get("status") != "ok":
            return None
        m = fb.score_matrix(a["lambda_home"], a["lambda_away"], a["rho"])
        mask = np.ones(m.shape, bool)
        for s in sels:
            mk = football_mask(s.market, m.shape)
            if mk is None:
                return None
            mask &= mk
        return float(m[mask].sum())
    if first.sport == "basketball":
        a = first.analysis
        if a.get("status") != "ok":
            return None
        h, aw = bb.samples_from_params(a["params"])
        mask = np.ones(len(h), bool)
        for s in sels:
            mk = bb.market_mask(h, aw, s.market)
            if mk is None:
                return None
            mask &= mk
        return float(mask.mean())
    return None


def joint_probability(selections: list[Selection]) -> dict:
    if not selections:
        return {"computable": False, "probability": None, "reason": "Sin selecciones."}
    by_fx: dict[int, list[Selection]] = {}
    for s in selections:
        by_fx.setdefault(s.fixture_id, []).append(s)
    # misma selección repetida
    seen = {(s.fixture_id, s.market) for s in selections}
    if len(seen) != len(selections):
        return {"computable": False, "probability": None, "reason": "Hay selecciones duplicadas."}
    prob = 1.0
    notes = []
    for fx, group in by_fx.items():
        p = _same_fixture_prob(group)
        if p is None:
            return {"computable": False, "probability": None,
                    "reason": ("No se puede calcular con precisión la probabilidad total: no existe un modelo "
                               f"conjunto confiable para los mercados seleccionados del partido {fx}.")}
        if len(group) > 1:
            notes.append(f"Partido {fx}: probabilidad conjunta calculada con la distribución común "
                         f"(eventos correlacionados, sin multiplicar).")
        prob *= p
    if len(by_fx) > 1:
        notes.append("Partidos distintos: se asume independencia entre partidos (aproximación).")
    return {"computable": True, "probability": float(prob), "notes": notes,
            "naive_product_warning": "No equivale al producto de probabilidades individuales cuando hay mercados del mismo partido."}
