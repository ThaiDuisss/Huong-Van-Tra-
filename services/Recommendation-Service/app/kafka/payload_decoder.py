import json
from typing import Any


class KafkaPayloadDecodeError(ValueError):
    """Raised when a Kafka message cannot be decoded into a JSON value."""


class KafkaPayloadShapeError(KafkaPayloadDecodeError):
    """Raised when decoded Kafka transport data does not contain an object payload."""


def _decode_json(value: bytes | bytearray | str, context: str) -> Any:
    if isinstance(value, (bytes, bytearray)):
        try:
            value = bytes(value).decode("utf-8")
        except UnicodeDecodeError as exc:
            raise KafkaPayloadDecodeError(
                f"{context} is not valid UTF-8"
            ) from exc

    try:
        return json.loads(value)
    except json.JSONDecodeError as exc:
        raise KafkaPayloadDecodeError(
            f"{context} is not valid JSON"
        ) from exc


def decode_kafka_payload(raw_value: Any) -> dict[str, Any]:
    """
    Decode a Kafka value into its business payload.

    Supports both a plain JSON object and Kafka Connect's ``{schema, payload}``
    envelope. Kafka Connect may encode ``payload`` as either an object or a JSON
    string. Transport failures use decoder-specific exceptions so they remain
    distinct from business contract validation performed by event adapters.
    """
    if raw_value is None:
        raise KafkaPayloadDecodeError("Kafka message value is None")

    if isinstance(raw_value, dict):
        decoded: Any = raw_value
    elif isinstance(raw_value, (bytes, bytearray, str)):
        decoded = _decode_json(raw_value, "Kafka message value")
    else:
        raise KafkaPayloadDecodeError(
            f"Unsupported Kafka message value type: {type(raw_value).__name__}"
        )

    if not isinstance(decoded, dict):
        raise KafkaPayloadShapeError(
            "Decoded Kafka message value must be a JSON object"
        )

    if "schema" not in decoded:
        return decoded

    if "payload" not in decoded:
        raise KafkaPayloadShapeError(
            "Kafka Connect envelope is missing required field 'payload'"
        )

    payload = decoded["payload"]
    if isinstance(payload, dict):
        business_payload: Any = payload
    elif isinstance(payload, (bytes, bytearray, str)):
        business_payload = _decode_json(payload, "Kafka Connect envelope payload")
    else:
        raise KafkaPayloadShapeError(
            "Kafka Connect envelope payload must be a JSON object or JSON string"
        )

    if not isinstance(business_payload, dict):
        raise KafkaPayloadShapeError(
            "Decoded Kafka Connect envelope payload must be a JSON object"
        )

    return business_payload
