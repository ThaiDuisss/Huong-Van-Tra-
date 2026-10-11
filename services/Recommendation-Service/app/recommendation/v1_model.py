from collections import Counter
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
import hashlib
import json
import re
from typing import Mapping
import unicodedata

import numpy as np
from scipy.sparse import csr_matrix, diags


CONTRACT_VERSION = "hvtra-personalized-hybrid-v1-60-40-tfidf"
MODEL_TYPE = "personalized_hybrid"
FEATURE_CONTRACT = "name-words+category-code+type1-active-bom-ingredient-codes"
NORMALIZATION_CONTRACT = "minmax-per-component-customer-eligible-candidates"


class V1ModelError(RuntimeError):
    """Recoverable unavailable/incompatible V1 model; DB errors are separate."""


@dataclass(frozen=True, slots=True)
class CatalogProduct:
    product_id: str
    product_code: str
    name: str
    category_code: str | None
    ingredient_codes: tuple[str, ...] = ()


@dataclass(frozen=True, slots=True)
class PurchaseCount:
    customer_id: str
    product_id: str
    count: int


def fingerprint(value) -> str:
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False,
                                     separators=(",", ":")).encode("utf-8")).hexdigest()


def catalog_fingerprint(products: tuple[CatalogProduct, ...]) -> str:
    return fingerprint([asdict(product) for product in sorted(products, key=lambda p: p.product_id)])


def feature_tokens(product: CatalogProduct) -> Counter:
    """Raw TF, smooth IDF, deterministic Unicode words and namespaced codes."""
    normalized_name = unicodedata.normalize("NFKC", product.name).casefold()
    tokens = Counter(f"name:{word}" for word in re.findall(r"[^\W_]+", normalized_name))
    if product.category_code and product.category_code.strip():
        tokens[f"category:{unicodedata.normalize('NFKC', product.category_code.strip()).casefold()}"] += 1
    for code in sorted(set(product.ingredient_codes)):
        if code and code.strip():
            tokens[f"ingredient:{unicodedata.normalize('NFKC', code.strip()).casefold()}"] += 1
    return tokens


def normalize_scores(scores: Mapping[str, float]) -> dict[str, float]:
    if not scores:
        return {}
    values = np.asarray(tuple(scores.values()), dtype=float)
    if not np.isfinite(values).all():
        raise V1ModelError("Non-finite component scores")
    low, high = float(values.min()), float(values.max())
    return {product: (float(score) - low) / (high - low) if high > low else 0.0
            for product, score in scores.items()}


def hybrid_scores(content: Mapping[str, float], cf: Mapping[str, float],
                  candidates: tuple[str, ...]) -> tuple[dict[str, float], bool]:
    # Normalize after purchased filtering, before Top-K.
    left = normalize_scores({p: content.get(p, 0.0) for p in candidates})
    right = normalize_scores({p: cf.get(p, 0.0) for p in candidates})
    both_flat = not any(left.values()) and not any(right.values())
    return {p: 0.6 * left[p] + 0.4 * right[p] for p in candidates}, both_flat


@dataclass(frozen=True, slots=True)
class V1Artifact:
    contract_version: str
    created_at: datetime
    products: tuple[CatalogProduct, ...]
    vocabulary: tuple[str, ...]
    idf: np.ndarray
    content: csr_matrix
    similarity: csr_matrix
    popularity: np.ndarray
    catalog_sha256: str
    history_sha256: str
    missing_purchase_order_events: int = 0

    @property
    def product_ids(self) -> tuple[str, ...]:
        return tuple(p.product_id for p in self.products)

    def validate(self) -> None:
        size, width = len(self.products), len(self.vocabulary)
        ids = self.product_ids
        if self.contract_version != CONTRACT_VERSION:
            raise V1ModelError("Incompatible V1 artifact contract")
        if not size or ids != tuple(sorted(set(ids))) or any(not p.strip() for p in ids):
            raise V1ModelError("Invalid V1 product mapping")
        if self.vocabulary != tuple(sorted(set(self.vocabulary))):
            raise V1ModelError("Invalid V1 feature vocabulary")
        if (self.content.shape != (size, width) or self.similarity.shape != (size, size)
                or self.idf.shape != (width,) or self.popularity.shape != (size,)):
            raise V1ModelError("Incompatible V1 artifact matrix dimensions")
        if any(not np.isfinite(a).all() for a in
               (self.content.data, self.similarity.data, self.idf, self.popularity)):
            raise V1ModelError("Non-finite V1 artifact values")
        if (np.any(self.content.data < 0) or np.any(self.similarity.data < 0)
                or np.any(self.similarity.data > 1 + 1e-10) or np.any(self.idf < 1)
                or np.any(self.popularity < 0)):
            raise V1ModelError("Invalid V1 artifact score domain")
        row_norms = np.sqrt(np.asarray(self.content.power(2).sum(axis=1)).ravel())
        if not np.all(np.isclose(row_norms, 0) | np.isclose(row_norms, 1)):
            raise V1ModelError("Content feature rows must be L2 normalized")
        difference = self.similarity - self.similarity.T
        if (np.any(self.similarity.diagonal() != 0)
                or (difference.nnz and np.max(np.abs(difference.data)) > 1e-10)):
            raise V1ModelError("CF must be symmetric with zero diagonal")
        if self.catalog_sha256 != catalog_fingerprint(self.products):
            raise V1ModelError("Artifact catalog fingerprint mismatch")
        if (self.created_at.tzinfo is None or self.missing_purchase_order_events < 0
                or not re.fullmatch(r"[0-9a-f]{64}", self.history_sha256)):
            raise V1ModelError("Invalid V1 artifact metadata")

    def components(self, history: Mapping[str, int]) -> tuple[dict[str, float], dict[str, float]]:
        vector = np.asarray([history.get(p, 0) for p in self.product_ids], dtype=float)
        if not np.isfinite(vector).all() or np.any(vector < 0):
            raise V1ModelError("Invalid purchase strength")
        profile = np.asarray(vector @ self.content).ravel()
        total = float(vector.sum())
        content = np.asarray(self.content @ profile).ravel() / total if total else np.zeros(len(vector))
        cf = np.asarray(vector @ self.similarity).ravel()
        if not np.isfinite(content).all() or not np.isfinite(cf).all():
            raise V1ModelError("Non-finite model predictions")
        return dict(zip(self.product_ids, map(float, content))), dict(zip(self.product_ids, map(float, cf)))


def build_artifact(products: tuple[CatalogProduct, ...], purchases: tuple[PurchaseCount, ...],
                   missing_purchase_order_events: int = 0) -> V1Artifact:
    products = tuple(sorted(products, key=lambda p: p.product_id))
    ids = tuple(p.product_id for p in products)
    if not products or len(set(ids)) != len(ids):
        raise V1ModelError("Build requires unique active catalog products")
    index = {p: i for i, p in enumerate(ids)}
    tokens = [feature_tokens(product) for product in products]
    vocabulary = tuple(sorted({token for row in tokens for token in row}))
    columns = {token: i for i, token in enumerate(vocabulary)}
    df = Counter(token for row in tokens for token in row)
    idf = np.asarray([1 + np.log((1 + len(products)) / (1 + df[t])) for t in vocabulary])
    coordinates = [(i, columns[t], float(tf) * idf[columns[t]])
                   for i, row in enumerate(tokens) for t, tf in sorted(row.items())]
    rows, cols, values = zip(*coordinates) if coordinates else ((), (), ())
    content = csr_matrix((values, (rows, cols)), shape=(len(ids), len(vocabulary)), dtype=float)
    norms = np.sqrt(np.asarray(content.power(2).sum(axis=1)).ravel())
    inverse = np.divide(1, norms, out=np.zeros_like(norms), where=norms != 0)
    content = (diags(inverse) @ content).tocsr()
    counts = Counter()
    for pair in purchases:
        if not pair.customer_id.strip() or pair.count <= 0:
            raise V1ModelError("Build requires positive PURCHASE counts and customer IDs")
        if pair.product_id in index:
            counts[(pair.customer_id, pair.product_id)] += pair.count
    users = {user: i for i, user in enumerate(sorted({u for u, _ in counts}))}
    coordinates = [(users[u], index[p], float(n)) for (u, p), n in sorted(counts.items())]
    rows, cols, values = zip(*coordinates) if coordinates else ((), (), ())
    matrix = csr_matrix((values, (rows, cols)), shape=(len(users), len(ids)), dtype=float)
    norms = np.sqrt(np.asarray(matrix.power(2).sum(axis=0)).ravel())
    inverse = np.divide(1, norms, out=np.zeros_like(norms), where=norms != 0)
    normalized = matrix.multiply(inverse)
    similarity = (normalized.T @ normalized).tocsr()
    similarity.setdiag(0)
    similarity.eliminate_zeros()
    artifact = V1Artifact(
        CONTRACT_VERSION, datetime.now(timezone.utc), products, vocabulary, idf, content,
        similarity, np.asarray(matrix.sum(axis=0)).ravel(), catalog_fingerprint(products),
        fingerprint([(u, p, n) for (u, p), n in sorted(counts.items())]),
        missing_purchase_order_events,
    )
    artifact.validate()
    return artifact
