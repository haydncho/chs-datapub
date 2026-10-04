FROM python:3.11-slim
WORKDIR /app
COPY pyproject.toml ./
COPY dpub_engine ./dpub_engine
RUN pip install --no-cache-dir .
EXPOSE 8091
CMD ["uvicorn", "dpub_engine.main:app", "--host", "0.0.0.0", "--port", "8091"]
