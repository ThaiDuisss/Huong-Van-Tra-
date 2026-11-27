from collections import OrderedDict
from concurrent.futures import Future
from threading import Lock
from time import monotonic
from typing import Callable, Generic, TypeVar

K = TypeVar("K")
V = TypeVar("V")


class RecommendationCache(Generic[K, V]):
    """Bounded TTL cache. Identical concurrent reads share one load."""

    def __init__(self, ttl: float, capacity: int):
        self.ttl = ttl
        self.capacity = capacity
        self._lock = Lock()
        self._values: OrderedDict[K, tuple[float, V]] = OrderedDict()
        self._pending: dict[K, Future[V]] = {}

    def get(self, key: K, load: Callable[[], V]) -> V:
        if not self.ttl:
            return load()
        with self._lock:
            entry = self._values.pop(key, None)
            if entry is not None and entry[0] > monotonic():
                self._values[key] = entry
                return entry[1]
            pending = self._pending.get(key)
            leader = pending is None
            if pending is None:
                pending = Future()
                self._pending[key] = pending
            started = monotonic()
        if not leader:
            return pending.result()
        try:
            value = load()
        except BaseException as exc:
            pending.set_exception(exc)
            raise
        else:
            with self._lock:
                self._values[key] = (started + self.ttl, value)
                if len(self._values) > self.capacity:
                    self._values.popitem(last=False)
            pending.set_result(value)
            return value
        finally:
            with self._lock:
                del self._pending[key]
