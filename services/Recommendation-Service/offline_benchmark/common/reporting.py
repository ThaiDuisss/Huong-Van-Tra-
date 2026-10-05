from dataclasses import asdict
import json

from offline_benchmark.common.types import PredictionReport


def render_report(report: PredictionReport) -> str:
    return "\n".join((
        f"MODEL: {report.model}", f"CUSTOMER: {report.customer_id}",
        "Top-5:", json.dumps(report.top5),
        "Test:", json.dumps(report.test),
        "Hits:", json.dumps(report.hits),
    ))


def serialize_reports(reports: tuple[PredictionReport, ...], manifest: dict) -> str:
    return json.dumps({"split": manifest, "predictions": [asdict(report) for report in reports]}, indent=2)
