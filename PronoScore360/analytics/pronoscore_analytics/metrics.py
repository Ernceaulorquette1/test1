"""Métricas de calidad probabilística y calibración."""
from __future__ import annotations

import math
from dataclasses import dataclass
from typing import Sequence

import numpy as np

EPS = 1e-12


def brier_score(probs: Sequence[float], outcomes: Sequence[int]) -> float:
    p = np.asarray(probs, float); y = np.asarray(outcomes, float)
    return float(np.mean((p - y) ** 2)) if len(p) else float("nan")


def log_loss(probs: Sequence[float], outcomes: Sequence[int]) -> float:
    p = np.clip(np.asarray(probs, float), EPS, 1 - EPS); y = np.asarray(outcomes, float)
    return float(-np.mean(y * np.log(p) + (1 - y) * np.log(1 - p))) if len(p) else float("nan")


def hit_rate(probs: Sequence[float], outcomes: Sequence[int], threshold: float = 0.5) -> float:
    """Tasa de acierto de las predicciones con p >= threshold (histórica, no promesa futura)."""
    p = np.asarray(probs, float); y = np.asarray(outcomes, float)
    sel = p >= threshold
    return float(y[sel].mean()) if sel.any() else float("nan")


def reliability_bins(probs: Sequence[float], outcomes: Sequence[int], n_bins: int = 10) -> list[dict]:
    p = np.asarray(probs, float); y = np.asarray(outcomes, float)
    out = []
    for i in range(n_bins):
        lo, hi = i / n_bins, (i + 1) / n_bins
        sel = (p >= lo) & ((p < hi) if i < n_bins - 1 else (p <= hi))
        if sel.any():
            out.append({"bin": [lo, hi], "n": int(sel.sum()), "mean_pred": float(p[sel].mean()),
                        "observed": float(y[sel].mean())})
    return out


def expected_calibration_error(probs, outcomes, n_bins: int = 10) -> float:
    bins = reliability_bins(probs, outcomes, n_bins)
    n = sum(b["n"] for b in bins)
    return float(sum(b["n"] * abs(b["mean_pred"] - b["observed"]) for b in bins) / n) if n else float("nan")


@dataclass
class IsotonicCalibrator:
    """Calibración isotónica (PAV). Se entrena sólo con predicciones pasadas ya resueltas."""
    xs: np.ndarray | None = None
    ys: np.ndarray | None = None

    def fit(self, probs: Sequence[float], outcomes: Sequence[int]) -> "IsotonicCalibrator":
        order = np.argsort(probs)
        x = np.asarray(probs, float)[order]; y = np.asarray(outcomes, float)[order]
        blocks = [[yi, 1.0, xi, xi] for xi, yi in zip(x, y)]       # [sum, n, x_min, x_max]
        merged: list[list[float]] = []
        for b in blocks:
            merged.append(b)
            while len(merged) > 1 and merged[-2][0] / merged[-2][1] > merged[-1][0] / merged[-1][1]:
                b2 = merged.pop(); b1 = merged.pop()
                merged.append([b1[0] + b2[0], b1[1] + b2[1], b1[2], b2[3]])
        self.xs = np.array([(b[2] + b[3]) / 2 for b in merged])
        self.ys = np.array([b[0] / b[1] for b in merged])
        return self

    def predict(self, probs: Sequence[float]) -> np.ndarray:
        if self.xs is None:
            raise RuntimeError("Calibrador no entrenado")
        return np.clip(np.interp(np.asarray(probs, float), self.xs, self.ys), 0.0, 1.0)
