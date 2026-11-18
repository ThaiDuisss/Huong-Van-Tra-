import json
from decimal import Decimal, InvalidOperation
from typing import Any


class KafkaPayloadDecodeError(ValueError):
    """Raised when a Kafka message cannot be decoded into a JSON value."""


class KafkaPayloadShapeError(KafkaPayloadDecodeError):
    """Raised when decoded Kafka transport data does not contain an object payload."""


class JsonDecimal(Decimal):
    """Exact JSON decimal, including the original exponent/scale spelling."""

    def __new__(cls, token: str):
        value = super().__new__(cls, token)
        if not value.is_finite():
            raise KafkaPayloadDecodeError("Nonfinite JSON number")
        value.json_token = token
        return value


class _JsonInteger(int):
    def __new__(cls, token: str):
        value = super().__new__(cls, token)
        value.json_token = token
        return value


class DecodedPayload(dict):
    """Business object with out-of-band token evidence; no wire fields added."""

    numeric_tokens: dict[str, dict[str, str]]
    transport_kind: str


def _unique_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result = {}
    for key, value in pairs:
        if key in result:
            # Keys and values can contain PII; never include them in diagnostics.
            raise KafkaPayloadDecodeError("Duplicate JSON object key")
        result[key] = value
    return result


def _reject_constant(_token: str) -> None:
    raise KafkaPayloadDecodeError("Non-standard JSON numeric token")


def _decode_json(value: bytes | bytearray | str, context: str) -> Any:
    if isinstance(value, (bytes, bytearray)):
        try:
            value = bytes(value).decode("utf-8")
        except UnicodeDecodeError:
            raise KafkaPayloadDecodeError(
                f"{context} is not valid UTF-8"
            ) from None

    try:
        return json.loads(value, parse_float=JsonDecimal, parse_int=_JsonInteger,
                          parse_constant=_reject_constant, object_pairs_hook=_unique_object)
    except KafkaPayloadDecodeError:
        raise
    except (ValueError, InvalidOperation, OverflowError, RecursionError):
        raise KafkaPayloadDecodeError(
            f"{context} is not valid strict JSON"
        ) from None


def _business_object(value: dict, transport_kind: str) -> DecodedPayload:
    tokens = {}

    def visit(node, path):
        if isinstance(node, (_JsonInteger, JsonDecimal)):
            tokens[path] = {"token": node.json_token,
                            "kind": "integer" if isinstance(node, _JsonInteger) else "decimal"}
            # eventVersion deliberately remains an exact built-in int.
            return int(node) if isinstance(node, _JsonInteger) else node
        if isinstance(node, dict):
            return {key: visit(child, path + "/" + key.replace("~", "~0").replace("/", "~1"))
                    for key, child in node.items()}
        if isinstance(node, list):
            return [visit(child, path + "/" + str(i)) for i, child in enumerate(node)]
        return node

    try:
        result = DecodedPayload(visit(value, ""))
    except RecursionError:
        raise KafkaPayloadDecodeError("JSON nesting exceeds decoder capacity") from None
    result.numeric_tokens = tokens
    result.transport_kind = transport_kind
    return result


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

    if isinstance(raw_value, DecodedPayload):
        return raw_value
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
        return _business_object(decoded, "plain")

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

    return _business_object(business_payload, "envelope-object" if isinstance(payload, dict)
                            else "envelope-string")
