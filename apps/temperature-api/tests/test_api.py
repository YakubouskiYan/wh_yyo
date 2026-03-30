import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import pytest
from main import app


@pytest.fixture
def client():
    app.config["TESTING"] = True
    with app.test_client() as client:
        yield client


def test_get_temperature_by_sensor_id_param(client):
    response = client.get("/temperature?sensor_id=1")
    assert response.status_code == 200
    data = response.get_json()
    assert data["sensor_id"] == "1"
    assert data["location"] == "Living Room"
    assert data["unit"] == "celsius"
    assert data["sensor_type"] == "temperature"
    assert 15.0 <= data["value"] <= 35.0


def test_get_temperature_by_location_param(client):
    response = client.get("/temperature?location=Bedroom")
    assert response.status_code == 200
    data = response.get_json()
    assert data["location"] == "Bedroom"
    assert data["sensor_id"] == "2"


def test_get_temperature_by_path_sensor_id(client):
    response = client.get("/temperature/3")
    assert response.status_code == 200
    data = response.get_json()
    assert data["sensor_id"] == "3"
    assert data["location"] == "Kitchen"


def test_get_temperature_unknown_sensor_id(client):
    response = client.get("/temperature?sensor_id=99")
    assert response.status_code == 200
    data = response.get_json()
    assert data["sensor_id"] == "99"
    assert data["location"] == "Unknown"


def test_get_temperature_no_params_returns_unknown(client):
    response = client.get("/temperature")
    assert response.status_code == 200
    data = response.get_json()
    assert data["location"] == "Unknown"
    assert data["sensor_id"] == "0"


def test_response_has_required_fields(client):
    response = client.get("/temperature/1")
    assert response.status_code == 200
    data = response.get_json()
    for field in ["value", "unit", "timestamp", "location", "status", "sensor_id", "sensor_type", "description"]:
        assert field in data, f"Missing field: {field}"


def test_response_status_is_active(client):
    response = client.get("/temperature/2")
    assert response.status_code == 200
    data = response.get_json()
    assert data["status"] == "active"
