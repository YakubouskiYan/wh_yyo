# Project_template

Это шаблон для решения проектной работы. Структура этого файла повторяет структуру заданий. Заполняйте его по мере работы над решением.

# Задание 1. Анализ и планирование

<<<<<<< HEAD
### 1. Описание функциональности монолитного приложения

**Управление датчиками (отопление):**

- Пользователи могут просматривать список всех зарегистрированных датчиков
- Пользователи могут регистрировать новый датчик, указав имя, тип, локацию и единицу измерения
- Пользователи могут обновлять параметры датчика (имя, тип, локация, статус)
- Пользователи могут вручную обновить текущее значение и статус датчика через отдельный PATCH-эндпоинт
- Пользователи могут удалить датчик из системы

**Мониторинг температуры:**

- При каждом запросе списка датчиков система автоматически обращается к внешнему сервису `temperature-api` и обновляет актуальные показания для датчиков типа `temperature`
- Пользователи могут получить показания температуры по названию локации (комнаты)
- Последнее известное значение температуры хранится в базе данных вместе с данными датчика

### 2. Анализ архитектуры монолитного приложения

- **Язык:** Go 1.23
- **Веб-фреймворк:** Gin — маршрутизация и обработка HTTP-запросов
- **База данных:** PostgreSQL, низкоуровневый драйвер pgx/v5 (без ORM), SQL-запросы вручную
- **Структура кода:** плоская — `handlers/`, `models/`, `services/`, `db/` в одном приложении
- **Внешние зависимости:** синхронные HTTP-вызовы к `temperature-api` при каждом GET-запросе датчиков типа `temperature`
- **Конфигурация:** через переменные окружения (`DATABASE_URL`, `TEMPERATURE_API_URL`)
- **Взаимодействие:** только синхронное — REST HTTP между клиентом и монолитом, REST HTTP от монолита к `temperature-api`
- **Единственный тип сущности:** `Sensor` с полями: id, name, type, location, value, unit, status, last_updated

### 3. Определение доменов и границы контекстов

| Домен | Bounded Context | Описание |
|---|---|---|
| **Управление датчиками** | Sensor Management | Регистрация, обновление, удаление датчиков и их состояний |
| **Телеметрия** | Telemetry | Сбор, хранение и отдача показаний датчиков (температура и др.) |
| **Управление устройствами** | Device Management | Регистрация и конфигурация IoT-устройств в доме |
| **Пользователи и дома** | Identity & Housing | Учётные записи пользователей, привязка домов и устройств |

В текущем монолите все домены смешаны в одном приложении. Домены «Управление устройствами» и «Пользователи и дома» явно не реализованы — их функции выполняет выездной специалист вручную.

### 4. Проблемы монолитного решения

- **Масштабируемость:** невозможно масштабировать отдельные части системы — телеметрия и управление датчиками масштабируются только вместе со всем монолитом
- **Деплой:** любое изменение в любом домене требует полного перезапуска всего приложения, что увеличивает риск и время простоя
- **Единая точка отказа:** падение монолита делает недоступными сразу все функции системы
- **Привязка к выездным специалистам:** нет механизма самообслуживания — пользователи не могут самостоятельно добавить устройство, нужен специалист
- **Технологическая привязанность:** весь код на Go, невозможно применить другой язык или фреймворк для отдельного домена без переписывания монолита
- **Расширяемость:** добавление нового типа датчика (влажность, движение и т.д.) требует изменений по всему коду монолита

### 5. Визуализация контекста системы — диаграмма С4

Диаграмма контекста (C4 Level 1) в формате PlantUML:

[schemas/c4_context.puml](schemas/c4_context.puml)

# Задание 2. Проектирование микросервисной архитектуры

### Декомпозиция на микросервисы

Монолит разбивается на сервисы по доменным границам, выделенным в Задании 1:

| Сервис | Язык / стек | Домен | Порт |
|---|---|---|---|
| **Smart Home Monolith** | Go 1.23 / Gin | Sensor Management (As-Is) | 8080 |
| **Temperature API** | Python 3.12 / Flask | Мок-источник температуры | 8081 |
| **Device Service** | Java 21 / Spring Boot | Device Management (To-Be) | 8082 |
| **Telemetry Service** | Java 21 / Spring Boot | Telemetry (To-Be) | 8083 |

### Хранилища данных

Все сервисы используют одну СУБД PostgreSQL, но с изолированными схемами — каждый сервис работает только со своей схемой:

| Сервис | Схема | Таблица |
|---|---|---|
| Smart Home Monolith | `public` | `sensors` |
| Device Service | `device_service` | `devices` |
| Telemetry Service | `telemetry_service` | `readings` |

### Взаимодействие между сервисами

**Синхронное (REST HTTP):**
- Клиент → Smart Home Monolith: управление датчиками, просмотр температуры
- Клиент → Device Service: регистрация и управление IoT-устройствами
- Клиент → Telemetry Service: запрос последних показаний и истории
- Smart Home Monolith → Temperature API: получение актуального значения температуры при каждом GET-запросе датчиков

**Асинхронное (RabbitMQ, exchange `warmhouse.events`, тип `topic`):**
- Device Service публикует события при регистрации устройства (`device.registered`) и смене статуса (`device.status_changed`)
- Telemetry Service подписывается на routing key `telemetry.reading` через очередь `telemetry.readings.queue` и сохраняет входящие показания

### Диаграмма контейнеров (Containers)

Показывает все сервисы платформы, базы данных, брокер сообщений и связи между ними:

[schemas/c4_containers.puml](schemas/c4_containers.puml)

### Диаграммы компонентов (Components)

Device Service — внутренняя структура сервиса управления устройствами
(DeviceController → DeviceService → DeviceRepository / DeviceEventPublisher):

[schemas/c4_components_device_service.puml](schemas/c4_components_device_service.puml)

Telemetry Service — внутренняя структура сервиса телеметрии
(TelemetryController → TelemetryServiceImpl → Repository / TelemetryEventConsumer ← RabbitMQ):

[schemas/c4_components_telemetry_service.puml](schemas/c4_components_telemetry_service.puml)

### Диаграмма кода (Code)

Классы Device Service: контроллер, сервис, репозиторий, модель, publisher и DTO с полями и методами:

[schemas/c4_code.puml](schemas/c4_code.puml)

# Задание 3. Разработка ER-диаграммы

### Сущности

В диаграмме представлены реализованные таблицы трёх сервисов и концептуальные сущности будущей платформы (`User`, `House`), которые пока не реализованы, но необходимы для полной модели данных.

**User** — пользователь платформы:

| Атрибут | Тип | Описание |
|---|---|---|
| id | SERIAL PK | Идентификатор |
| name | VARCHAR(100) | Имя пользователя |
| email | VARCHAR(150) UNIQUE | Email для входа |
| role | VARCHAR(20) | Роль: owner, installer |
| created_at | TIMESTAMPTZ | Дата регистрации |

**House** — умный дом, принадлежащий пользователю:

| Атрибут | Тип | Описание |
|---|---|---|
| id | SERIAL PK | Идентификатор |
| user_id | INTEGER FK → User | Владелец дома |
| name | VARCHAR(100) | Название дома |
| address | VARCHAR(255) | Физический адрес |
| created_at | TIMESTAMPTZ | Дата добавления |

**sensors** (схема `public`, монолит) — датчики в доме:

| Атрибут | Тип | Описание |
|---|---|---|
| id | SERIAL PK | Идентификатор |
| name | VARCHAR(100) | Название датчика |
| type | VARCHAR(50) | Тип: temperature и др. |
| location | VARCHAR(100) | Локация / комната |
| value | FLOAT | Последнее значение |
| unit | VARCHAR(20) | Единица измерения |
| status | VARCHAR(20) | inactive / active |
| last_updated | TIMESTAMPTZ | Время последнего обновления |
| created_at | TIMESTAMPTZ | Дата создания |

**devices** (схема `device_service`) — IoT-устройства:

| Атрибут | Тип | Описание |
|---|---|---|
| id | SERIAL PK | Идентификатор |
| name | VARCHAR(100) | Название устройства |
| serial_number | VARCHAR(100) UNIQUE | Серийный номер |
| device_type | VARCHAR(50) | Тип: thermostat, sensor и др. |
| home_id | VARCHAR(50) | Привязка к дому (House.id) |
| room_id | VARCHAR(50) | Привязка к комнате |
| firmware_version | VARCHAR(20) | Версия прошивки |
| status | VARCHAR(20) | offline / online / error |
| registered_at | TIMESTAMPTZ | Дата регистрации |

**readings** (схема `telemetry_service`) — показания датчиков:

| Атрибут | Тип | Описание |
|---|---|---|
| id | BIGSERIAL PK | Идентификатор |
| sensor_id | INTEGER FK → sensors | Датчик-источник |
| value | DOUBLE PRECISION | Значение показания |
| unit | VARCHAR(20) | Единица измерения |
| recorded_at | TIMESTAMPTZ | Время снятия показания |

### Связи

| Связь | Тип | Описание |
|---|---|---|
| User → House | 1 : N | Один пользователь владеет несколькими домами |
| House → sensors | 1 : N | В одном доме несколько датчиков |
| House → devices | 1 : N | В одном доме несколько IoT-устройств |
| sensors → readings | 1 : N | Один датчик генерирует множество показаний |

### Диаграмма

[schemas/er_diagram.puml](schemas/er_diagram.puml)
=======
<aside>

Чтобы составить документ с описанием текущей архитектуры приложения, можно часть информации взять из описания компании и условия задания. Это нормально.

</aside

### 1. Описание функциональности монолитного приложения

**Управление отоплением:**

- Пользователи могут…
- Система поддерживает…
- …

**Мониторинг температуры:**

- Пользователи могут…
- Система поддерживает…
- …

### 2. Анализ архитектуры монолитного приложения

Перечислите здесь основные особенности текущего приложения: какой язык программирования используется, какая база данных, как организовано взаимодействие между компонентами и так далее.

### 3. Определение доменов и границы контекстов

Опишите здесь домены, которые вы выделили.

### **4. Проблемы монолитного решения**

- …
- …
- …

Если вы считаете, что текущее решение не вызывает проблем, аргументируйте свою позицию.

### 5. Визуализация контекста системы — диаграмма С4

Добавьте сюда диаграмму контекста в модели C4.

Чтобы добавить ссылку в файл Readme.md, нужно использовать синтаксис Markdown. Это делают так:

```markdown
[Текст ссылки](URL)
```

Замените `Текст ссылки` текстом, который хотите использовать для ссылки. Вместо `URL` вставьте адрес, на который должна вести ссылка. Например:

```markdown
[Посетите Яндекс](https://ya.ru/)
```

# Задание 2. Проектирование микросервисной архитектуры

В этом задании вам нужно предоставить только диаграммы в модели C4. Мы не просим вас отдельно описывать получившиеся микросервисы и то, как вы определили взаимодействия между компонентами To-Be системы. Если вы правильно подготовите диаграммы C4, они и так это покажут.

**Диаграмма контейнеров (Containers)**

Добавьте диаграмму.

**Диаграмма компонентов (Components)**

Добавьте диаграмму для каждого из выделенных микросервисов.

**Диаграмма кода (Code)**

Добавьте одну диаграмму или несколько.

# Задание 3. Разработка ER-диаграммы

Добавьте сюда ER-диаграмму. Она должна отражать ключевые сущности системы, их атрибуты и тип связей между ними.
>>>>>>> origin/main

# Задание 4. Создание и документирование API

### 1. Тип API

<<<<<<< HEAD
Используется два типа взаимодействия:

**REST API (синхронный)** — для операций, где клиент ожидает немедленного ответа:
- управление устройствами (CRUD через Device Service)
- получение показаний и истории телеметрии (Telemetry Service)
- управление датчиками (Smart Home Monolith)

REST выбран потому что операции имеют чёткий запрос/ответ, клиент должен знать результат сразу (например, при регистрации устройства), и все сервисы предоставляют HTTP API для внешних вызовов.

**AsyncAPI / AMQP (асинхронный)** — для событийного взаимодействия между сервисами:
- Device Service публикует события об устройствах в RabbitMQ
- Telemetry Service подписывается на показания датчиков из очереди

Асинхронность выбрана для развязки сервисов: публикатор не ждёт обработки и не зависит от доступности потребителя. Это позволяет масштабировать обработку телеметрии независимо.

### 2. Документация API

#### Device Service — OpenAPI 3.0

Файл спецификации: [schemas/openapi_device_service.yaml](schemas/openapi_device_service.yaml)

| Метод | Эндпоинт | Описание |
|---|---|---|
| `GET` | `/api/v1/devices` | Список устройств с фильтрацией по homeId, roomId, deviceType, status |
| `POST` | `/api/v1/devices` | Зарегистрировать новое устройство |
| `GET` | `/api/v1/devices/{id}` | Получить устройство по ID |
| `PUT` | `/api/v1/devices/{id}` | Обновить параметры устройства |
| `DELETE` | `/api/v1/devices/{id}` | Удалить устройство |
| `GET` | `/api/v1/devices/health` | Health check |

Коды ответов: `200`, `201`, `204`, `400`, `404`, `409`, `500`

#### Telemetry Service — OpenAPI 3.0

Файл спецификации: [schemas/openapi_telemetry_service.yaml](schemas/openapi_telemetry_service.yaml)

| Метод | Эндпоинт | Описание |
|---|---|---|
| `POST` | `/api/v1/telemetry` | Принять показание датчика, вернуть 201 + Location |
| `GET` | `/api/v1/telemetry/{sensorId}/latest` | Последнее показание датчика |
| `GET` | `/api/v1/telemetry/{sensorId}/history` | История с фильтрами from/to/limit и статистикой avg/min/max |
| `GET` | `/health` | Health check |

Коды ответов: `200`, `201`, `400`, `404`, `500`

#### Асинхронные события — AsyncAPI 2.6

Файл спецификации: [schemas/asyncapi_events.yaml](schemas/asyncapi_events.yaml)

| Routing key | Направление | Описание |
|---|---|---|
| `device.registered` | Device Service → RabbitMQ | Устройство зарегистрировано в системе |
| `device.status_changed` | Device Service → RabbitMQ | Статус устройства изменился |
| `telemetry.reading` | RabbitMQ → Telemetry Service | Новое показание датчика для сохранения |

Exchange: `warmhouse.events` (topic, durable). Очередь Telemetry Service: `telemetry.readings.queue`.

# Задание 5. Работа с docker и docker-compose

### 1. Temperature API

Реализован сервис `temperature-api` на Python 3.12 / Flask.

**Файл:** [apps/temperature-api/main.py](apps/temperature-api/main.py)

Эндпоинты:
- `GET /temperature?location=Kitchen` — вернуть случайную температуру по названию локации
- `GET /temperature/<sensor_id>` — вернуть случайную температуру по ID датчика

Маппинг location ↔ sensor_id:

| sensor_id | location |
|---|---|
| 1 | Living Room |
| 2 | Bedroom |
| 3 | Kitchen |

Логика разрешения:
- Если передан только `sensor_id` — location определяется из таблицы выше
- Если передана только `location` — sensor_id определяется из таблицы выше
- При каждом запросе возвращается новое случайное значение температуры (15.0–35.0 °C)

Пример ответа:
```json
{
  "value": 22.47,
  "unit": "celsius",
  "timestamp": "2026-03-25T10:00:00Z",
  "location": "Living Room",
  "status": "active",
  "sensor_id": "1",
  "sensor_type": "temperature",
  "description": "Temperature sensor at Living Room"
}
```

### 2. Dockerfile для temperature-api

**Файл:** [apps/temperature-api/Dockerfile](apps/temperature-api/Dockerfile)

- Базовый образ: `python:3.12-alpine`
- Порт: `8081`
- Запуск: `python main.py`

### 3. Docker Compose

**Файл:** [apps/docker-compose.yml](apps/docker-compose.yml)

Запускает все сервисы одной командой из папки `apps/`:

```bash
docker-compose up -d
```

Состав:

| Сервис | Образ / сборка | Порт | Описание |
|---|---|---|---|
| `postgres` | postgres:16-alpine | — | PostgreSQL с тремя init-скриптами |
| `rabbitmq` | rabbitmq:3.12-management-alpine | 5672, 15672 | Брокер сообщений |
| `temperature-api` | ./temperature-api | 8081 | Mock-сервис температуры |
| `app` | ./smart_home | 8080 | Go-монолит |
| `device-service` | ./device-service | 8082 | Микросервис устройств |
| `telemetry-service` | ./telemetry-service | 8083 | Микросервис телеметрии |

PostgreSQL инициализируется тремя скриптами по порядку:
1. `./smart_home/init.sql` — схема монолита (таблица `sensors`)
2. `./device-service/init.sql` — схема `device_service`
3. `./telemetry-service/init.sql` — схема `telemetry_service`

### 4. Проверка

Для проверки использовать Postman-коллекцию [apps/smarthome-api.postman_collection.json](apps/smarthome-api.postman_collection.json):
1. Вызвать **Create Sensor** — создаётся датчик типа `temperature`
2. Вызвать **Get All Sensors** — в ответе у датчика поле `value` содержит случайное значение температуры
3. Повторить **Get All Sensors** — значение `value` должно отличаться при каждом вызове


# Задание 6. Разработка MVP

### Реализованные микросервисы

#### Device Service

**Файлы:** [apps/device-service/](apps/device-service/)

- **Язык / стек:** Java 21, Spring Boot 3.2, Spring Data JPA, Spring AMQP
- **База данных:** PostgreSQL, схема `device_service`, таблица `devices`
- **Порт:** 8082
- **Функциональность:** CRUD IoT-устройств — регистрация, обновление, удаление, фильтрация по homeId / roomId / deviceType / status
- **Интеграция:** при регистрации устройства и смене статуса публикует события в RabbitMQ (routing key `device.registered`, `device.status_changed`)
- **Dockerfile:** [apps/device-service/Dockerfile](apps/device-service/Dockerfile) — многоэтапная сборка Maven → JRE Alpine

#### Telemetry Service

**Файлы:** [apps/telemetry-service/](apps/telemetry-service/)

- **Язык / стек:** Java 21, Spring Boot 3.2, Spring Data JPA, Spring AMQP
- **База данных:** PostgreSQL, схема `telemetry_service`, таблица `readings`
- **Порт:** 8083
- **Функциональность:** приём показаний датчиков, хранение в БД, выдача последнего значения и истории со статистикой avg / min / max
- **Интеграция:** подписывается на очередь `telemetry.readings.queue` (routing key `telemetry.reading`) и автоматически сохраняет входящие показания
- **Dockerfile:** [apps/telemetry-service/Dockerfile](apps/telemetry-service/Dockerfile) — многоэтапная сборка Maven → JRE Alpine

### Интеграция с монолитом

Взаимодействие реализовано через RabbitMQ (exchange `warmhouse.events`, тип `topic`):

```
Smart Home Monolith
  └─ PATCH /api/v1/sensors/{id}/value
       └─ PublishTelemetryReading()
            └─ RabbitMQ routing key: telemetry.reading
                 └─ Telemetry Service (TelemetryEventConsumer)
                      └─ сохраняет показание в telemetry_service.readings
```

Монолит публикует событие `telemetry.reading` каждый раз при обновлении значения датчика через PATCH-эндпоинт. Это позволяет постепенно переносить хранение телеметрии из монолита в отдельный сервис — без изменения клиентского кода.

### Запуск всего стека

```bash
cd apps
docker-compose up -d
```

Порядок запуска (задаётся через `depends_on`):

1. **postgres** + **rabbitmq** — инфраструктура, ждём healthcheck
2. **temperature-api** — mock-сервис температуры
3. **app** (монолит) — ждёт postgres, rabbitmq, temperature-api
4. **device-service** — ждёт postgres, rabbitmq
5. **telemetry-service** — ждёт postgres, rabbitmq

| Сервис | URL |
|---|---|
| Smart Home Monolith | http://localhost:8080 |
| Temperature API | http://localhost:8081 |
| Device Service | http://localhost:8082 |
| Telemetry Service | http://localhost:8083 |
| RabbitMQ Management UI | http://localhost:15672 (guest/guest) |
=======
Укажите, какой тип API вы будете использовать для взаимодействия микросервисов. Объясните своё решение.

### 2. Документация API

Здесь приложите ссылки на документацию API для микросервисов, которые вы спроектировали в первой части проектной работы. Для документирования используйте Swagger/OpenAPI или AsyncAPI.

# Задание 5. Работа с docker и docker-compose

Перейдите в apps.

Там находится приложение-монолит для работы с датчиками температуры. В README.md описано как запустить решение.

Вам нужно:

1) сделать простое приложение temperature-api на любом удобном для вас языке программирования, которое при запросе /temperature?location= будет отдавать рандомное значение температуры.

Locations - название комнаты, sensorId - идентификатор названия комнаты

```
	// If no location is provided, use a default based on sensor ID
	if location == "" {
		switch sensorID {
		case "1":
			location = "Living Room"
		case "2":
			location = "Bedroom"
		case "3":
			location = "Kitchen"
		default:
			location = "Unknown"
		}
	}

	// If no sensor ID is provided, generate one based on location
	if sensorID == "" {
		switch location {
		case "Living Room":
			sensorID = "1"
		case "Bedroom":
			sensorID = "2"
		case "Kitchen":
			sensorID = "3"
		default:
			sensorID = "0"
		}
	}
```

2) Приложение следует упаковать в Docker и добавить в docker-compose. Порт по умолчанию должен быть 8081

3) Кроме того для smart_home приложения требуется база данных - добавьте в docker-compose файл настройки для запуска postgres с указанием скрипта инициализации ./smart_home/init.sql

Для проверки можно использовать Postman коллекцию smarthome-api.postman_collection.json и вызвать:

- Create Sensor
- Get All Sensors

Должно при каждом вызове отображаться разное значение температуры

Ревьюер будет проверять точно так же.


# **Задание 6. Разработка MVP**

Необходимо создать новые микросервисы и обеспечить их интеграции с существующим монолитом для плавного перехода к микросервисной архитектуре. 

### **Что нужно сделать**

1. Создайте новые микросервисы для управления телеметрией и устройствами (с простейшей логикой), которые будут интегрированы с существующим монолитным приложением. Каждый микросервис на своем ООП языке.
2. Обеспечьте взаимодействие между микросервисами и монолитом (при желании с помощью брокера сообщений), чтобы постепенно перенести функциональность из монолита в микросервисы. 

В результате у вас должны быть созданы Dockerfiles и docker-compose для запуска микросервисов. 
>>>>>>> origin/main
