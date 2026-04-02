import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from main import resolve


def test_resolve_by_sensor_id_1():
    location, sensor_id = resolve("", "1")
    assert location == "Living Room"
    assert sensor_id == "1"


def test_resolve_by_sensor_id_2():
    location, sensor_id = resolve("", "2")
    assert location == "Bedroom"
    assert sensor_id == "2"


def test_resolve_by_sensor_id_3():
    location, sensor_id = resolve("", "3")
    assert location == "Kitchen"
    assert sensor_id == "3"


def test_resolve_by_location_living_room():
    location, sensor_id = resolve("Living Room", "")
    assert location == "Living Room"
    assert sensor_id == "1"


def test_resolve_by_location_bedroom():
    location, sensor_id = resolve("Bedroom", "")
    assert location == "Bedroom"
    assert sensor_id == "2"


def test_resolve_unknown_sensor_id():
    location, sensor_id = resolve("", "99")
    assert location == "Unknown"
    assert sensor_id == "99"


def test_resolve_unknown_location():
    location, sensor_id = resolve("Garage", "")
    assert location == "Garage"
    assert sensor_id == "0"


def test_resolve_both_provided_preserves_values():
    location, sensor_id = resolve("Living Room", "1")
    assert location == "Living Room"
    assert sensor_id == "1"
