"""Caché y contadores: Redis si REDIS_URL está definido; si no, memoria (sólo desarrollo)."""
from __future__ import annotations

import json
import threading
import time
from typing import Any, Optional

from .config import get_settings


class MemoryBackend:
    def __init__(self):
        self._d: dict[str, tuple[float | None, Any]] = {}
        self._lock = threading.Lock()

    def _alive(self, k):
        item = self._d.get(k)
        if item is None:
            return None
        exp, v = item
        if exp is not None and exp < time.time():
            self._d.pop(k, None)
            return None
        return item

    def get(self, k):
        with self._lock:
            it = self._alive(k)
            return it[1] if it else None

    def set(self, k, v, ttl=None):
        with self._lock:
            self._d[k] = ((time.time() + ttl) if ttl else None, v)

    def delete(self, k):
        with self._lock:
            self._d.pop(k, None)

    def incr(self, k, ttl):
        with self._lock:
            it = self._alive(k)
            val = int(it[1]) + 1 if it else 1
            exp = it[0] if it else time.time() + ttl
            self._d[k] = (exp, val)
            return val

    def delete_prefix(self, prefix):
        with self._lock:
            for k in [k for k in self._d if k.startswith(prefix)]:
                self._d.pop(k, None)

    def flush(self):
        with self._lock:
            self._d.clear()


class RedisBackend:
    def __init__(self, client):
        self.r = client

    def get(self, k):
        v = self.r.get(k)
        return v.decode() if isinstance(v, bytes) else v

    def set(self, k, v, ttl=None):
        self.r.set(k, v, ex=ttl)

    def delete(self, k):
        self.r.delete(k)

    def incr(self, k, ttl):
        pipe = self.r.pipeline()
        pipe.incr(k)
        pipe.expire(k, ttl, nx=True)
        return int(pipe.execute()[0])

    def delete_prefix(self, prefix):
        for key in self.r.scan_iter(match=prefix + "*", count=500):
            self.r.delete(key)

    def flush(self):
        self.r.flushdb()


class Cache:
    def __init__(self, backend):
        self.b = backend

    def get_json(self, key: str) -> Optional[Any]:
        raw = self.b.get(key)
        return json.loads(raw) if raw else None

    def set_json(self, key: str, value: Any, ttl: int | None = None) -> None:
        self.b.set(key, json.dumps(value, default=str), ttl)

    def delete(self, key: str) -> None:
        self.b.delete(key)

    def invalidate_prefix(self, prefix: str) -> None:
        self.b.delete_prefix(prefix)

    def incr(self, key: str, ttl: int) -> int:
        return self.b.incr(key, ttl)

    def get(self, key: str):
        return self.b.get(key)

    def set(self, key: str, value: str, ttl: int | None = None):
        self.b.set(key, value, ttl)


_cache: Cache | None = None


def get_cache() -> Cache:
    global _cache
    if _cache is None:
        url = get_settings().redis_url
        if url:
            import redis
            _cache = Cache(RedisBackend(redis.Redis.from_url(url)))
        else:
            _cache = Cache(MemoryBackend())
    return _cache


def set_cache(c: Cache | None) -> None:
    global _cache
    _cache = c
