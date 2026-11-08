# Intake sync server: Python standard library only, nothing to install.
FROM python:3.12-slim
RUN useradd --system --uid 10001 --create-home intake && mkdir /data && chown intake /data
WORKDIR /app
COPY intake_sync.py .
USER intake
ENV INTAKE_SYNC_DB=/data/intake-sync.db INTAKE_SYNC_HOST=0.0.0.0 INTAKE_SYNC_PORT=8787 PYTHONUNBUFFERED=1
VOLUME /data
EXPOSE 8787
HEALTHCHECK --interval=60s --timeout=5s CMD python3 -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8787/v1/health').status==200 else 1)"
ENTRYPOINT ["python3", "intake_sync.py"]
CMD ["serve"]
