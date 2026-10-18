"""Criterios documentados de calidad de datos y categorías visuales.

Criterios (ver docs/ARQUITECTURA.md):
  * insufficient_data : algún equipo tiene menos de MIN_MATCHES partidos previos válidos.
  * high_uncertainty  : calidad de datos 'low' o probabilidad < 0.55.
  * medium            : 0.55 <= p < 0.70 con calidad >= medium.
  * high_probability  : p >= 0.70 con calidad >= medium.
La categoría describe respaldo estadístico del modelo, NO una garantía de acierto.
"""
from __future__ import annotations

MIN_MATCHES = 5          # por debajo: datos insuficientes
MEDIUM_MATCHES = 10
HIGH_MATCHES = 20

CATEGORY_HIGH = "high_probability"
CATEGORY_MEDIUM = "medium_probability"
CATEGORY_UNCERTAIN = "high_uncertainty"
CATEGORY_INSUFFICIENT = "insufficient_data"


def data_quality(n_a: int, n_b: int) -> str:
    n = min(n_a, n_b)
    if n < MIN_MATCHES:
        return "insufficient"
    if n < MEDIUM_MATCHES:
        return "low"
    if n < HIGH_MATCHES:
        return "medium"
    return "high"


def uncertainty_level(quality: str, probability: float) -> str:
    """low | medium | high según calidad de datos y cercanía a 0.5 (binario)."""
    if quality in ("insufficient", "low"):
        return "high"
    if quality == "medium":
        return "medium" if probability >= 0.6 else "high"
    return "low" if probability >= 0.65 else "medium"


def categorize(probability: float | None, quality: str) -> str:
    if probability is None or quality == "insufficient":
        return CATEGORY_INSUFFICIENT
    if quality == "low" or probability < 0.55:
        return CATEGORY_UNCERTAIN
    if probability >= 0.70:
        return CATEGORY_HIGH
    return CATEGORY_MEDIUM
