FROM python:3.11-slim

COPY requirements.txt requirements.txt

RUN pip install --no-cache-dir -r requirements.txt

COPY ./app /app

WORKDIR /app

ENV PYTHONUNBUFFERED=1
EXPOSE 5000

CMD ["gunicorn", "--bind", "0.0.0.0:5000", "main:create_app()"]
