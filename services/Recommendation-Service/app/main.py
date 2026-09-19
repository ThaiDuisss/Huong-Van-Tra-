from fastapi import FastAPI

app = FastAPI(title="HVTra Recommendation Service")


@app.get("/health")
def health():
    return {
        "service": "recommendation-service",
        "status": "UP"
    }