from contextlib import contextmanager
from pathlib import Path

from sqlalchemy import create_engine
from sqlalchemy.orm import Session


@contextmanager
def readonly_session(env_file: Path | None = None):
    """Offline refresh/verification only: consistent MySQL snapshot, no commit."""
    from app.core.settings import Settings

    if env_file is not None and not env_file.is_file():
        raise ValueError("Selected environment file does not exist")
    settings = Settings(_env_file=env_file) if env_file is not None else Settings()
    engine = create_engine(settings.sqlalchemy_database_url, echo=False,
                           connect_args={"connect_timeout": 10})
    try:
        with engine.connect() as connection:
            connection.exec_driver_sql("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ")
            connection.exec_driver_sql("SET TRANSACTION READ ONLY")
            connection.exec_driver_sql("START TRANSACTION WITH CONSISTENT SNAPSHOT")
            try:
                with Session(bind=connection, autoflush=False) as db:
                    yield db, settings
            finally:
                connection.rollback()
    finally:
        engine.dispose()
