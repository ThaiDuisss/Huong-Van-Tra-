"""Authenticated operator control for isolated training jobs."""
from pathlib import Path
import sqlite3
import subprocess
import sys
from threading import Thread
from typing import Literal
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Request, Response
from pydantic import BaseModel, ConfigDict

from app.core.recommendation_auth import verify_context
from app.recommendation.level2_demo_registry import APPROVED, checked_document
from app.training.level2_demo_training import Jobs

router = APIRouter(prefix="/recommendations/training/jobs", tags=["isolated recommendation training"])


class TrainingRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    mode: Literal["EXPLORE", "FAMILIAR"]


def _training_jobs(request: Request):
    identity, roles, _, settings = verify_context(request)
    if identity.startswith("service:") or not (roles & {"SUPER_ADMIN", "ADMIN"}):
        raise HTTPException(403, "Training operator access denied")
    if (getattr(request.app.state, "level2_demo", None) is None
            or not settings.level2_demo_training_directory):
        raise HTTPException(404, "Isolated training is disabled")
    try:
        jobs = Jobs(Path(settings.level2_demo_training_directory))
    except (OSError, ValueError, sqlite3.Error):
        raise HTTPException(503, "Isolated training storage unavailable") from None
    return jobs, settings


def _wait(jobs, job, process):
    process.wait()
    # No-op when the worker has already recorded completion.
    jobs.complete(job, error="ISOLATED_TRAINING_WORKER_EXITED")


@router.post("", status_code=202)
def start_training(command: TrainingRequest, response: Response, access=Depends(_training_jobs)):
    jobs, settings = access
    try:
        job = jobs.reserve(command.mode)
    except ValueError:
        raise HTTPException(409, "An isolated training job is already running") from None
    try:
        process = subprocess.Popen(
            [sys.executable, "-B", "-m", "app.training.level2_demo_training", "--jobs", str(jobs.root),
             "worker", job, "--manifest", settings.level2_demo_manifest,
             "--sha256", settings.level2_demo_manifest_sha256],
            stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
        )
    except OSError:
        jobs.complete(job, error="ISOLATED_TRAINING_WORKER_UNAVAILABLE")
        raise HTTPException(503, "Could not start isolated training") from None
    Thread(target=_wait, args=(jobs, job, process), daemon=True).start()
    response.headers["Cache-Control"] = "private, no-store"
    return jobs.state(job)


@router.get("")
def training_overview(request: Request, response: Response, access=Depends(_training_jobs)):
    jobs, settings = access
    jobs.recover()
    try:
        config = checked_document(Path(settings.level2_demo_manifest), settings.level2_demo_manifest_sha256)
        sources = config.get("training_datasets", {})
    except (OSError, ValueError):
        sources = {}
    demo = request.app.state.level2_demo
    modes = {}
    for mode in APPROVED:
        model, error = demo.models[mode]
        versions = {"active": model.version if model else None, "previous": None}
        if demo.lifecycle:
            versions = demo.lifecycle.versions(mode, demo.models[mode])
            model, error = demo.lifecycle.resolve(mode, demo.models[mode])
        source = sources.get(mode)
        configured = bool(source and Path(source["evidence"]).is_file() and Path(source["packets"]).is_dir())
        modes[mode] = {"dataset": "CONFIGURED" if configured else "UNAVAILABLE", "job": jobs.latest(mode),
                       "serving": {"version": model.version if model and demo.enabled else None,
                                   "status": "ACTIVE" if model and demo.enabled else error or "DISABLED", **versions}}
    response.headers["Cache-Control"] = "private, no-store"
    return modes


@router.post("/approval", status_code=204)
def record_test_approval(envelope: dict, request: Request, access=Depends(_training_jobs)):
    lifecycle = request.app.state.level2_demo.lifecycle
    if lifecycle is None:
        raise HTTPException(404, "Lifecycle test registry is disabled")
    try:
        lifecycle.change(envelope)
    except sqlite3.IntegrityError:
        raise HTTPException(409, "Approval already recorded") from None
    except (OSError, KeyError, TypeError, ValueError):
        raise HTTPException(422, "Isolated lifecycle approval rejected") from None
    return Response(status_code=204, headers={"Cache-Control": "private, no-store"})


@router.get("/{job_id}")
def training_status(job_id: UUID, response: Response, access=Depends(_training_jobs)):
    jobs = access[0]
    jobs.recover()
    state = jobs.state(str(job_id))
    if state is None:
        raise HTTPException(404, "Unknown isolated training job")
    response.headers["Cache-Control"] = "private, no-store"
    return state
