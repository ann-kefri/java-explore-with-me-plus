# Explore With Me

Временная инструкция по локальному запуску проекта. Полноценный README будет подготовлен после интеграции командных
модулей.

## Требования

- Java 21
- Maven
- Docker и Docker Compose

## Запуск

Из корневого каталога проекта собрать Maven-модули:

```bash
mvn clean package -DskipTests
```

Собрать Docker-образы и запустить контейнеры:

```bash
docker compose up -d --build
```

Проверить состояние контейнеров:

```bash
docker compose ps
```

## Доступные сервисы

| Сервис                       | Адрес                 |
|------------------------------|-----------------------|
| Основной API                 | http://localhost:8080 |
| API статистики               | http://localhost:9090 |
| PostgreSQL основного сервиса | localhost:6542        |
| PostgreSQL статистики        | localhost:6541        |

## Проверка

Проверить доступность приложений:

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:9090/actuator/health
```

Ожидаемый ответ:

```json
{
  "status": "UP"
}
```

Проверить публичный API мероприятий:

```bash
curl http://localhost:8080/events
```

## Остановка

```bash
docker compose down
```

Команда останавливает и удаляет контейнеры проекта без явного удаления томов с данными.
