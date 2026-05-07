from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    qwenpaw_endpoint: str = "http://localhost:8088"
    trace_header: str = "X-Trace-Id"
    log_level: str = "INFO"
    cors_origins: list[str] = ["http://localhost:5173", "http://localhost:8080"]

    class Config:
        env_file = ".env"


settings = Settings()
