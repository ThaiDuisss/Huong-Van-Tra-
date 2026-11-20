from app.kafka.purchase_consumer import OrderKafkaConsumer


class ReturnKafkaConsumer(OrderKafkaConsumer):
    event_type = "RETURN_COMPLETED"
    topic_setting = "kafka_return_completed_topic"
