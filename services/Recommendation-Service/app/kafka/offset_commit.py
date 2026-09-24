from typing import Any


class KafkaOffsetCommitError(RuntimeError):
    """Raised when a synchronous Kafka offset commit is not fully successful."""


def _message_field(message: Any, name: str) -> Any:
    value = getattr(message, name, None)
    return value() if callable(value) else value


def _partition_error(topic_partition: Any) -> Any:
    error = getattr(topic_partition, "error", None)
    error = error() if callable(error) else error
    if error is not None:
        return error

    legacy_error = getattr(topic_partition, "err", None)
    return legacy_error() if callable(legacy_error) else legacy_error


def commit_message_offset(consumer: Any, message: Any) -> None:
    """
    Commit exactly ``message.offset + 1`` and validate the synchronous result.

    ``confluent-kafka`` may return partition-level errors without raising a
    top-level exception, so every returned partition result is inspected.
    """
    topic = _message_field(message, "topic")
    partition = _message_field(message, "partition")
    offset = _message_field(message, "offset")

    if topic is None or partition is None or offset is None:
        raise KafkaOffsetCommitError(
            "Cannot commit Kafka message without topic, partition, and offset metadata"
        )

    committed = consumer.commit(message=message, asynchronous=False)
    if not committed:
        raise KafkaOffsetCommitError(
            f"Kafka returned no synchronous commit result for {topic}[{partition}]"
        )

    expected_offset = offset + 1
    expected_result = None

    for result in committed:
        result_topic = getattr(result, "topic", None)
        result_partition = getattr(result, "partition", None)
        error = _partition_error(result)
        if error is not None:
            raise KafkaOffsetCommitError(
                f"Kafka offset commit failed for {result_topic}[{result_partition}]: {error}"
            )
        if result_topic == topic and result_partition == partition:
            expected_result = result

    if expected_result is None:
        raise KafkaOffsetCommitError(
            f"Kafka commit result did not include {topic}[{partition}]"
        )

    committed_offset = getattr(expected_result, "offset", None)
    if committed_offset != expected_offset:
        raise KafkaOffsetCommitError(
            f"Kafka committed unexpected offset for {topic}[{partition}]: "
            f"expected {expected_offset}, got {committed_offset}"
        )
