"""Pinned, verified artifact inference; no fitting, services or database runtime."""
from copy import deepcopy
from threading import Lock

from app.ingredient_poc.recommendation.data import ResearchDataset, sha256, utc
from app.ingredient_demo.bom_report import build_report_snapshot
from app.ingredient_poc.recommendation.model import IngredientRecommender
from app.ingredient_poc.synthetic import END, T1


class ArtifactVerificationError(ValueError):
    pass


class IngredientDemoService:
    def __init__(self, settings):
        try:
            data = ResearchDataset.load(settings.dataset_path, settings.dataset_sha256)
            if data.id != settings.dataset_id:
                raise ValueError("Dataset identity mismatch")
            model = IngredientRecommender.load(settings.model_path, settings.model_sha256, data)
            self._pins = {settings.dataset_path: settings.dataset_sha256, settings.model_path: settings.model_sha256}
            for name in ("manifest.json", "validation.json", "artifact-checksums.json"):
                path = settings.dataset_path.parent / name
                self._pins[path] = sha256(path)
            self.verify_integrity()
        except Exception:
            raise ArtifactVerificationError("Ingredient research artifacts could not be verified") from None
        self.data, self._model = data, model
        self._report = None
        self._workbook = None
        self._lock = Lock()

    def verify_integrity(self):
        try:
            if any(sha256(path) != digest for path, digest in self._pins.items()):
                raise ValueError("Changed artifact")
        except Exception:
            raise ArtifactVerificationError("Ingredient research artifacts are unavailable or changed") from None

    def recommend(self, customer_id, *, as_of, mode="UNIFIED", policy="DISCOVERY", top_k=5):
        as_of = utc(as_of)
        if customer_id not in self.data.customers:
            raise KeyError("Unknown research customer")
        if not T1 <= as_of <= END or not 1 <= top_k <= 5:
            raise ValueError("Input outside verified research window or Top-5 contract")
        value = self._model.recommend(customer_id, as_of=as_of, mode=mode, policy=policy, top_k=top_k)
        return {**value, "source_kind": "SYNTHETIC", "research_only": True, "production_approved": False}

    def customers(self):
        return {"dataset_id": self.data.id, "source_kind": "SYNTHETIC", "research_only": True,
                "production_approved": False, "customers": [
                    {"customer_id": customer, "cohort": self.data.cohort(customer, END),
                     "cohort_as_of": END.isoformat(), "cold_at_train_cutoff": not self.data.history(customer, T1)}
                    for customer in sorted(self.data.customers)]}

    def report_snapshot(self):
        with self._lock:
            if self._report is None:
                self._report = build_report_snapshot(self._model)
            return deepcopy(self._report)

    def excel(self):
        snapshot = self.report_snapshot()
        with self._lock:
            if self._workbook is None:
                from app.ingredient_demo.workbook import build_workbook
                self._workbook = build_workbook(self.data, self._model.metadata, snapshot)
            return self._workbook
