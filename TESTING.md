# Тестирование сервисов

## Быстрый старт — порядок запуска всех тестов

```bash
# Шаг 1. Python unit-тесты (самые быстрые, ~1 сек)
cd apps/temperature-api
pip install -r requirements.txt
pytest tests/test_resolve.py -v

# Шаг 2. Python API-тесты (~1 сек)
pytest tests/test_api.py -v

# Шаг 3. Java unit-тесты device-service (без Docker, ~10 сек)
cd ../device-service
mvn test -Dtest="DeviceServiceTest,DeviceControllerTest"

# Шаг 4. Java unit-тесты telemetry-service (без Docker, ~10 сек)
cd ../telemetry-service
mvn test -Dtest="TelemetryServiceImplTest,TelemetryEventConsumerTest,TelemetryControllerTest"

# Шаг 5. Интеграционные тесты device-service (требует Docker, ~30 сек)
cd ../device-service
mvn test -Dtest="DeviceRepositoryIT"

# Шаг 6. Интеграционные тесты telemetry-service (требует Docker, ~30 сек)
cd ../telemetry-service
mvn test -Dtest="TelemetryReadingRepositoryIT"
```

> Шаги 1–4 не требуют Docker и запускаются в любой момент.
> Шаги 5–6 требуют запущенного Docker — Testcontainers сам поднимет PostgreSQL.

---

В проекте три сервиса с тестами:

| Сервис | Язык | Тип тестов |
|---|---|---|
| `apps/device-service` | Java 21 / Spring Boot | Unit + MockMvc + Testcontainers |
| `apps/telemetry-service` | Java 21 / Spring Boot | Unit + MockMvc + Testcontainers |
| `apps/temperature-api` | Python / Flask | Unit + Flask test client |

---

## Предварительные требования

| Инструмент | Версия | Зачем |
|---|---|---|
| JDK | 21+ | Компиляция и запуск Java-тестов |
| Maven | 3.9+ | Сборка и запуск тестов Java |
| Docker | 24+ | Testcontainers поднимают PostgreSQL в контейнере |
| Python | 3.10+ | Запуск тестов temperature-api |
| pip | — | Установка зависимостей Python |

> Testcontainers сами управляют Docker-контейнером с PostgreSQL — вручную ничего поднимать не нужно.

> **Java 21+ и Mockito.** Mockito по умолчанию использует ByteBuddy-агент, который не работает на Java 21+. В проекте это решено через файл `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` со значением `mock-maker-subclass` — Mockito создаёт моки через наследование без агента. Если добавляешь новый сервис, скопируй этот файл в его тестовые ресурсы.

---

## 1. device-service

### 1.1. Структура тестов

```
apps/device-service/src/test/java/com/warmhouse/device/
├── service/
│   └── DeviceServiceTest.java          ← unit-тесты бизнес-логики
├── controller/
│   └── DeviceControllerTest.java       ← тесты HTTP-слоя (MockMvc)
└── repository/
    └── DeviceRepositoryIT.java         ← интеграционные тесты с реальным PostgreSQL
```

### 1.2. Запуск

```bash
cd apps/device-service

# Все тесты
mvn test

# Только unit-тесты (без Testcontainers, без Docker)
mvn test -Dtest="DeviceServiceTest,DeviceControllerTest"

# Только интеграционные тесты
mvn test -Dtest="DeviceRepositoryIT"
```

---

### 1.3. DeviceServiceTest — unit-тесты бизнес-логики

**Что тестируем:** класс `DeviceService`. Зависимости (`DeviceRepository`, `DeviceEventPublisher`) заменены моками Mockito.

| # | Метод теста | Сценарий | Ожидаемый результат |
|---|---|---|---|
| 1 | `findAll_noFilters_returnsAll` | Вызов без фильтров | Возвращает все устройства из репозитория |
| 2 | `findAll_byHomeId_delegatesToRepository` | Передан `homeId` | Вызывается `findByHomeId`, `findAll` не вызывается |
| 3 | `findById_existingId_returnsDevice` | Устройство есть в БД | Возвращает устройство с нужным `id` |
| 4 | `findById_missingId_throwsNoSuchElement` | Устройство не найдено | Выбрасывает `NoSuchElementException` с id в сообщении |
| 5 | `register_newDevice_savesAndPublishesEvent` | Новый серийный номер | Устройство сохранено, вызван `publishDeviceRegistered` |
| 6 | `register_duplicateSerial_throwsIllegalArgument` | Серийный номер уже есть | Выбрасывает `IllegalArgumentException`, `save` не вызван |
| 7 | `update_statusChange_publishesStatusChangedEvent` | Статус изменился | Вызван `publishDeviceStatusChanged` со старым статусом |
| 8 | `delete_existingId_deletesDevice` | Устройство есть | `deleteById` вызван |
| 9 | `delete_missingId_throwsNoSuchElement` | Устройства нет | Выбрасывает `NoSuchElementException`, `deleteById` не вызван |

**Пример успешного вывода:**
```
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
```

---

### 1.4. DeviceControllerTest — тесты HTTP-слоя

**Что тестируем:** класс `DeviceController`. Поднимается только веб-слой (`@WebMvcTest`), `DeviceService` — мок.

| # | Метод теста | HTTP-запрос | Ожидаемый статус | Ожидаемое тело |
|---|---|---|---|---|
| 1 | `list_returnsDevices` | `GET /api/v1/devices` | 200 | JSON-массив устройств |
| 2 | `list_withHomeIdFilter_passesFilterToService` | `GET /api/v1/devices?homeId=home-1` | 200 | Устройства отфильтрованы по homeId |
| 3 | `getById_found_returns200` | `GET /api/v1/devices/1` | 200 | JSON устройства с `id=1` |
| 4 | `getById_notFound_returns404` | `GET /api/v1/devices/99` | 404 | Пустое тело |
| 5 | `register_validRequest_returns201` | `POST /api/v1/devices` (валидное тело) | 201 | JSON созданного устройства |
| 6 | `register_duplicateSerial_returns409` | `POST /api/v1/devices` (дублирующий serial) | 409 | `{"error": "..."}` |
| 7 | `register_missingRequiredFields_returns400` | `POST /api/v1/devices` с `{}` | 400 | Ошибка валидации |
| 8 | `update_found_returns200` | `PUT /api/v1/devices/1` | 200 | JSON обновлённого устройства |
| 9 | `update_notFound_returns404` | `PUT /api/v1/devices/99` | 404 | Пустое тело |
| 10 | `delete_found_returns204` | `DELETE /api/v1/devices/1` | 204 | Пустое тело |
| 11 | `delete_notFound_returns404` | `DELETE /api/v1/devices/99` | 404 | Пустое тело |
| 12 | `health_returns200` | `GET /api/v1/devices/health` | 200 | `{"status":"ok","service":"device-service"}` |

---

### 1.5. DeviceRepositoryIT — интеграционные тесты

**Что тестируем:** `DeviceRepository` против реального PostgreSQL. Testcontainers запускает контейнер `postgres:15-alpine`, Flyway накатывает миграцию `V1__create_devices_table.sql`.

**Последовательность при запуске:**
1. JUnit запускает тест-класс
2. Testcontainers поднимает контейнер PostgreSQL (20–30 сек при первом запуске, пока не скачан образ)
3. `init-test.sql` создаёт схему `device_service`
4. Flyway накатывает `V1__create_devices_table.sql` — создаётся таблица `devices`
5. Выполняются тесты
6. Контейнер останавливается и удаляется

| # | Метод теста | Сценарий | Ожидаемый результат |
|---|---|---|---|
| 1 | `saveAndFindById_roundtrip` | Сохранить устройство → найти по id | Устройство найдено, serialNumber совпадает |
| 2 | `findByHomeId_returnsMatchingDevices` | Два устройства с разными homeId | Возвращается только устройство из запрошенного дома |
| 3 | `existsBySerialNumber_existingSerial_returnsTrue` | Проверка уникальности | `true` для существующего, `false` для несуществующего |
| 4 | `findByStatus_returnsOnlyMatchingStatus` | Устройства с разными статусами | Возвращаются только `online`-устройства |

**Пример успешного вывода:**
```
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
```

> При первом запуске Docker-образ `postgres:15-alpine` скачивается (~80 МБ), последующие запуски используют кеш.

---

## 2. telemetry-service

### 2.1. Структура тестов

```
apps/telemetry-service/src/test/java/com/warmhouse/telemetry/
├── service/
│   └── TelemetryServiceImplTest.java       ← unit-тесты бизнес-логики
├── messaging/
│   └── TelemetryEventConsumerTest.java     ← unit-тесты консьюмера RabbitMQ
├── controller/
│   └── TelemetryControllerTest.java        ← тесты HTTP-слоя (MockMvc)
└── repository/
    └── TelemetryReadingRepositoryIT.java   ← интеграционные тесты с PostgreSQL
```

### 2.2. Запуск

```bash
cd apps/telemetry-service

# Все тесты
mvn test

# Только unit-тесты
mvn test -Dtest="TelemetryServiceImplTest,TelemetryEventConsumerTest,TelemetryControllerTest"

# Только интеграционные тесты
mvn test -Dtest="TelemetryReadingRepositoryIT"
```

---

### 2.3. TelemetryServiceImplTest — unit-тесты бизнес-логики

**Что тестируем:** класс `TelemetryServiceImpl`. `TelemetryReadingRepository` — мок.

| # | Метод теста | Сценарий | Ожидаемый результат |
|---|---|---|---|
| 1 | `ingest_savesReading` | Входящий запрос на запись | `repository.save` вызван, возвращается сохранённый объект |
| 2 | `getLatest_existingReading_returnsResponse` | Показания для сенсора есть | Возвращается `Optional` с `ReadingResponse`, значения совпадают |
| 3 | `getLatest_noReading_returnsEmpty` | Показаний нет | Возвращается `Optional.empty()` |
| 4 | `getHistory_multipleReadings_computesStats` | 3 показания: 10, 20, 30 | `average=20`, `min=10`, `max=30` |
| 5 | `getHistory_emptyReadings_nullStats` | Показаний нет | `average`, `min`, `max` — `null` |
| 6 | `getHistory_withDateRange_passesParamsToRepository` | Передан диапазон `from`/`to` | Параметры переданы в репозиторий без изменений |

---

### 2.4. TelemetryEventConsumerTest — тесты RabbitMQ-консьюмера

**Что тестируем:** класс `TelemetryEventConsumer`. `TelemetryService` — мок.

| # | Метод теста | Сценарий | Ожидаемый результат |
|---|---|---|---|
| 1 | `handleEvent_validEvent_callsIngest` | Валидное событие с `sensorId=42` | `telemetryService.ingest` вызван с правильными `sensorId`, `value`, `unit` |
| 2 | `handleEvent_nullEvent_doesNotCallIngest` | Пришёл `null` вместо события | `ingest` не вызван |
| 3 | `handleEvent_nullSensorId_doesNotCallIngest` | Событие с `sensorId=null` | `ingest` не вызван |

---

### 2.5. TelemetryControllerTest — тесты HTTP-слоя

**Что тестируем:** класс `TelemetryController`. Поднимается только веб-слой, `TelemetryService` — мок.

| # | Метод теста | HTTP-запрос | Ожидаемый статус | Ожидаемое тело |
|---|---|---|---|---|
| 1 | `ingest_validRequest_returns201` | `POST /api/v1/telemetry` (sensorId, value, unit) | 201 | JSON с `sensorId` и `value` |
| 2 | `ingest_missingRequiredFields_returns400` | `POST /api/v1/telemetry` с `{}` | 400 | Ошибка валидации |
| 3 | `getLatest_existingReading_returns200` | `GET /api/v1/telemetry/42/latest` | 200 | JSON показания |
| 4 | `getLatest_noReading_returns404` | `GET /api/v1/telemetry/99/latest` | 404 | `{"error": "No readings for sensor 99"}` |
| 5 | `getHistory_returnsHistoryWithStats` | `GET /api/v1/telemetry/42/history` | 200 | JSON с `readings`, `average`, `min`, `max` |
| 6 | `getHistory_withLimitParam_passesLimit` | `GET /api/v1/telemetry/42/history?limit=10` | 200 | Сервис вызван с `limit=10` |

---

### 2.6. TelemetryReadingRepositoryIT — интеграционные тесты

**Что тестируем:** `TelemetryReadingRepository`. Testcontainers + Flyway, аналогично device-service.

**Последовательность при запуске:**
1. Testcontainers поднимает `postgres:15-alpine`
2. `init-test.sql` создаёт схему `telemetry_service`
3. Flyway накатывает `V1__create_readings_table.sql` — создаётся таблица `readings` с индексами
4. Выполняются тесты
5. Контейнер останавливается

| # | Метод теста | Сценарий | Ожидаемый результат |
|---|---|---|---|
| 1 | `saveAndFindLatest_returnsNewest` | 3 показания с разным временем | Возвращается то, что было записано позже всего |
| 2 | `findLatest_noReadings_returnsEmpty` | Показаний для сенсора нет | `Optional.empty()` |
| 3 | `findHistory_withDateRange_returnsFiltered` | 3 показания, запрос с `from` | Возвращаются только показания после `from` |
| 4 | `findHistory_sensorIsolation_returnsOnlyRequestedSensor` | Показания двух разных сенсоров | Возвращаются только показания запрошенного сенсора |

---

## 3. temperature-api

### 3.1. Структура тестов

```
apps/temperature-api/
├── tests/
│   ├── __init__.py
│   ├── test_resolve.py     ← unit-тесты функции resolve()
│   └── test_api.py         ← интеграционные тесты Flask-эндпоинтов
└── requirements.txt        ← flask==3.0.0, pytest==8.1.1
```

### 3.2. Запуск

```bash
cd apps/temperature-api

# Установка зависимостей (один раз)
pip install -r requirements.txt

# Все тесты
pytest tests/ -v

# Только unit-тесты
pytest tests/test_resolve.py -v

# Только API-тесты
pytest tests/test_api.py -v
```

---

### 3.3. test_resolve.py — unit-тесты функции resolve()

**Что тестируем:** функцию `resolve(location, sensor_id)` из `main.py`. Без HTTP, без Flask — чистая логика маппинга.

| # | Тест | Входные данные | Ожидаемый результат |
|---|---|---|---|
| 1 | `test_resolve_by_sensor_id_1` | `sensor_id="1"`, location="" | `location="Living Room"`, `sensor_id="1"` |
| 2 | `test_resolve_by_sensor_id_2` | `sensor_id="2"`, location="" | `location="Bedroom"`, `sensor_id="2"` |
| 3 | `test_resolve_by_sensor_id_3` | `sensor_id="3"`, location="" | `location="Kitchen"`, `sensor_id="3"` |
| 4 | `test_resolve_by_location_living_room` | `location="Living Room"`, sensor_id="" | `sensor_id="1"` |
| 5 | `test_resolve_by_location_bedroom` | `location="Bedroom"`, sensor_id="" | `sensor_id="2"` |
| 6 | `test_resolve_unknown_sensor_id` | `sensor_id="99"`, location="" | `location="Unknown"`, `sensor_id="99"` |
| 7 | `test_resolve_unknown_location` | `location="Garage"`, sensor_id="" | `location="Garage"`, `sensor_id="0"` |
| 8 | `test_resolve_both_provided_preserves_values` | `location="Living Room"`, `sensor_id="1"` | Значения не меняются |

---

### 3.4. test_api.py — интеграционные тесты Flask

**Что тестируем:** HTTP-эндпоинты через тестовый клиент Flask (`app.test_client()`). Реальный сетевой порт не открывается.

| # | Тест | HTTP-запрос | Ожидаемый статус | Ожидаемое тело |
|---|---|---|---|---|
| 1 | `test_get_temperature_by_sensor_id_param` | `GET /temperature?sensor_id=1` | 200 | `location="Living Room"`, `unit="celsius"`, `value` в диапазоне 15–35 |
| 2 | `test_get_temperature_by_location_param` | `GET /temperature?location=Bedroom` | 200 | `location="Bedroom"`, `sensor_id="2"` |
| 3 | `test_get_temperature_by_path_sensor_id` | `GET /temperature/3` | 200 | `location="Kitchen"`, `sensor_id="3"` |
| 4 | `test_get_temperature_unknown_sensor_id` | `GET /temperature?sensor_id=99` | 200 | `location="Unknown"`, `sensor_id="99"` |
| 5 | `test_get_temperature_no_params_returns_unknown` | `GET /temperature` | 200 | `location="Unknown"`, `sensor_id="0"` |
| 6 | `test_response_has_required_fields` | `GET /temperature/1` | 200 | Все поля присутствуют: `value`, `unit`, `timestamp`, `location`, `status`, `sensor_id`, `sensor_type`, `description` |
| 7 | `test_response_status_is_active` | `GET /temperature/2` | 200 | `status="active"` |

**Пример успешного вывода:**
```
tests/test_resolve.py::test_resolve_by_sensor_id_1 PASSED
tests/test_resolve.py::test_resolve_by_sensor_id_2 PASSED
...
tests/test_api.py::test_get_temperature_by_sensor_id_param PASSED
...
8 passed, 7 passed in 0.12s
```

---

## Итоговая сводка

| Сервис | Файл | Кол-во тестов | Тип | Требует Docker |
|---|---|---|---|---|
| device-service | DeviceServiceTest | 9 | Unit | Нет |
| device-service | DeviceControllerTest | 12 | MockMvc | Нет |
| device-service | DeviceRepositoryIT | 4 | Integration | Да |
| telemetry-service | TelemetryServiceImplTest | 6 | Unit | Нет |
| telemetry-service | TelemetryEventConsumerTest | 3 | Unit | Нет |
| telemetry-service | TelemetryControllerTest | 6 | MockMvc | Нет |
| telemetry-service | TelemetryReadingRepositoryIT | 4 | Integration | Да |
| temperature-api | test_resolve | 8 | Unit | Нет |
| temperature-api | test_api | 7 | Integration | Нет |
| **Итого** | | **59** | | |




-------------------------------