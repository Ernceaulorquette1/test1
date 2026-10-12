"""PronoScore360 Analytics: modelos estadísticos para fútbol y baloncesto.

Módulo independiente (sin dependencias de web/BD) para poder entrenarse,
evaluarse con backtesting y versionarse por separado del backend.
"""
from .version import MODEL_FAMILY_VERSIONS

__all__ = ["MODEL_FAMILY_VERSIONS"]
