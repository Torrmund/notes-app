# 📝 NoteApp

Веб-приложение для создания и управления заметками, разработанное на Flask с использованием PostgreSQL. Поддерживает запуск через Docker, docker-compose и Helm для Kubernetes.

## 📦 Возможности

- Удобный графический интерфейс
- Хранение заметок в базе PostgreSQL
- Поддержка Docker и Kubernetes

---

## 🚀 Быстрый старт

### Локально через docker-compose

```bash
docker compose up --build
```

Приложение будет доступно по адресу http://localhost

### Подготовка Docker образа

```bash
docker build -t yourdockerhubusername/noteapp .
docker push yourdockerhubusername/noteapp
```

---

## ☸️ Развертывание в Kubernetes

### Предварительные шаги

1. Убедитесь, что у вас есть доступ к кластеру Kubernetes.
2. Установите Helm

```bash
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
```

### Установка Helm-чарта

```bash
helm upgrade --install noteapp ./charts/noteapp --create-namespace --namespace noteapp
```

### Удаление

```bash
helm uninstall noteapp -n noteapp
```

---

## ⚙️ Конфигурация

Редактируйте `values.yml` для настройки:

* Docker образов
* Параметров подключения к PostgreSQL
* Ресурсов (CPU, RAM)
* Типа сервиса (ClusterIP, NodePort, LoadBalancer)
