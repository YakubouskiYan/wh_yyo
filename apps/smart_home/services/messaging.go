package services

import (
	"encoding/json"
	"log"
	"time"

	amqp "github.com/rabbitmq/amqp091-go"
)

// MessagingService publishes events to RabbitMQ.
type MessagingService struct {
	conn     *amqp.Connection
	channel  *amqp.Channel
	exchange string
	enabled  bool
}

// TelemetryReadingEvent is the event payload for telemetry.reading.
type TelemetryReadingEvent struct {
	EventType string    `json:"eventType"`
	SensorID  int       `json:"sensorId"`
	Value     float64   `json:"value"`
	Unit      string    `json:"unit"`
	Timestamp time.Time `json:"timestamp"`
}

// NewMessagingService connects to RabbitMQ. If the connection fails, the service
// is created in a disabled state so the monolith continues to function without it.
func NewMessagingService(amqpURL, exchange string) *MessagingService {
	svc := &MessagingService{exchange: exchange}

	conn, err := amqp.Dial(amqpURL)
	if err != nil {
		log.Printf("WARNING: cannot connect to RabbitMQ (%s): %v — messaging disabled", amqpURL, err)
		return svc
	}

	ch, err := conn.Channel()
	if err != nil {
		log.Printf("WARNING: cannot open RabbitMQ channel: %v — messaging disabled", err)
		conn.Close()
		return svc
	}

	// Declare a durable topic exchange (no-op if it already exists with the same params).
	if err = ch.ExchangeDeclare(exchange, "topic", true, false, false, false, nil); err != nil {
		log.Printf("WARNING: cannot declare exchange %q: %v — messaging disabled", exchange, err)
		ch.Close()
		conn.Close()
		return svc
	}

	svc.conn = conn
	svc.channel = ch
	svc.enabled = true
	log.Printf("Connected to RabbitMQ, exchange=%q", exchange)
	return svc
}

// PublishTelemetryReading publishes a telemetry.reading event.
// The call is fire-and-forget: errors are only logged.
func (m *MessagingService) PublishTelemetryReading(sensorID int, value float64, unit string) {
	if !m.enabled {
		return
	}

	event := TelemetryReadingEvent{
		EventType: "telemetry.reading",
		SensorID:  sensorID,
		Value:     value,
		Unit:      unit,
		Timestamp: time.Now().UTC(),
	}

	body, err := json.Marshal(event)
	if err != nil {
		log.Printf("Failed to marshal telemetry event: %v", err)
		return
	}

	err = m.channel.Publish(
		m.exchange,        // exchange
		"telemetry.reading", // routing key
		false,             // mandatory
		false,             // immediate
		amqp.Publishing{
			ContentType:  "application/json",
			DeliveryMode: amqp.Persistent,
			Body:         body,
		},
	)
	if err != nil {
		log.Printf("Failed to publish telemetry.reading event: %v", err)
	}
}

// Close releases the RabbitMQ connection.
func (m *MessagingService) Close() {
	if m.channel != nil {
		m.channel.Close()
	}
	if m.conn != nil {
		m.conn.Close()
	}
}
