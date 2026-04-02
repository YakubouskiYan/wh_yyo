import random
import datetime
from flask import Flask, request, jsonify

app = Flask(__name__)

SENSOR_ID_TO_LOCATION = {
    "1": "Living Room",
    "2": "Bedroom",
    "3": "Kitchen",
}

LOCATION_TO_SENSOR_ID = {
    "Living Room": "1",
    "Bedroom": "2",
    "Kitchen": "3",
}


def resolve(location, sensor_id):
    if not location:
        location = SENSOR_ID_TO_LOCATION.get(sensor_id, "Unknown")
    if not sensor_id:
        sensor_id = LOCATION_TO_SENSOR_ID.get(location, "0")
    return location, sensor_id


def make_temperature_response(location, sensor_id):
    value = round(random.uniform(15.0, 35.0), 2)
    return {
        "value": value,
        "unit": "celsius",
        "timestamp": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "location": location,
        "status": "active",
        "sensor_id": sensor_id,
        "sensor_type": "temperature",
        "description": f"Temperature sensor at {location}",
    }


@app.route("/temperature")
def get_temperature():
    location = request.args.get("location", "")
    sensor_id = request.args.get("sensor_id", "")
    location, sensor_id = resolve(location, sensor_id)
    return jsonify(make_temperature_response(location, sensor_id))


@app.route("/temperature/<sensor_id>")
def get_temperature_by_id(sensor_id):
    location, sensor_id = resolve("", sensor_id)
    return jsonify(make_temperature_response(location, sensor_id))


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=8081)
