from dataclasses import dataclass
from datetime import datetime, timezone
import hashlib
import json
import re
from typing import Annotated, Literal
import unicodedata

from pydantic import BaseModel, ConfigDict, Field, JsonValue, StringConstraints, ValidationError

Digest = Annotated[str, StringConstraints(pattern=r"^[0-9a-f]{64}$")]
Identifier = Annotated[str, StringConstraints(pattern=r"^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$")]
Version = Annotated[str, StringConstraints(pattern=r"^[A-Za-z0-9][A-Za-z0-9._-]{0,39}$")]
Preparation = Annotated[str, StringConstraints(pattern=r"^[A-Z][A-Z0-9_]{0,79}$")]
SourceMethod = Annotated[str, StringConstraints(pattern=r"^[A-Z][A-Z0-9_]{0,29}$")]
Revision = Annotated[int, Field(ge=1, le=9223372036854775807)]
CODE = re.compile(r"[A-Z][A-Z0-9_]{0,39}")
UTC_TIME = re.compile(r"\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d{1,6})?Z")
REQUIRED_ATTRIBUTES = {"BITTERNESS", "SWEETNESS", "AFTERTASTE", "ASTRINGENCY", "FLAVOR", "TEA_TYPE"}
SPLIT_TASTE_VERSION = "hvtra-tea-v2"
LEVEL_ONE_TASTE_VERSION = "hvtra-tea-v3"
SPLIT_SWEETNESS_VERSIONS = frozenset({SPLIT_TASTE_VERSION, LEVEL_ONE_TASTE_VERSION})
AFTERTASTE_CHARACTER = "AFTERTASTE_CHARACTER"
ADDED_SWEETNESS = "ADDED_SWEETNESS"
NOT_APPLICABLE = "NOT_APPLICABLE"
ORDINAL_ATTRIBUTES = {"BITTERNESS", "NATURAL_SWEETNESS", ADDED_SWEETNESS, "AFTERTASTE", "ASTRINGENCY"}
ORDINAL_LEVELS = {"NONE", "LOW", "MEDIUM", "HIGH"}
ESCAPE = re.compile(r'["\\\x00-\x1f\ud800-\udfff\U00010000-\U0010ffff]')
SHORT_ESCAPES = {'"': r'\"', '\\': r'\\', '\t': r'\t', '\n': r'\n', '\b': r'\b', '\f': r'\f', '\r': r'\r'}


class SnapshotError(RuntimeError):
    def __init__(self, code: str):
        super().__init__(code)
        self.code = code


class StrictDocument(BaseModel):
    model_config = ConfigDict(strict=True, extra="forbid")


class Taxonomy(StrictDocument):
    versionCode: Version
    manifestSha256: Digest
    state: Literal["PUBLISHED"]
    manifest: dict[str, JsonValue]


class Product(StrictDocument):
    id: Identifier
    name: Annotated[str, StringConstraints(min_length=1, max_length=255)]
    status: Literal["ACTIVE"]
    isDeleted: Literal[False]


class Variant(StrictDocument):
    id: Identifier
    productId: Identifier
    name: Annotated[str, StringConstraints(min_length=1, max_length=255)]
    sku: Annotated[str, StringConstraints(min_length=1, max_length=100)]
    status: Literal["ACTIVE"]
    isDeleted: Literal[False]
    sellable: bool
    customBundle: Literal[False]
    compositionRevision: Annotated[int, Field(ge=0, le=9223372036854775807)]
    compositionSha256: Digest | None


class Profile(StrictDocument):
    id: Identifier
    variantId: Identifier
    profileRevision: Revision
    taxonomyVersion: Version
    taxonomySha256: Digest
    preparationCode: Preparation
    preparationProtocol: dict[str, JsonValue]
    preparationSha256: Digest
    compositionRevision: Revision
    compositionSha256: Digest
    profileSha256: Digest
    sensoryValues: dict[str, list[str]]
    sourceMethod: SourceMethod
    status: Literal["APPROVED"]
    approvedAt: str


class Counts(StrictDocument):
    products: Annotated[int, Field(ge=0)]
    variants: Annotated[int, Field(ge=0)]
    approvedProfiles: Annotated[int, Field(ge=0)]


class Payload(StrictDocument):
    schemaVersion: Literal[1]
    catalogState: Literal["PUBLISHED", "NO_TAXONOMY"]
    taxonomies: list[Taxonomy]
    supportedTaxonomyVersions: list[Version]
    products: list[Product]
    variants: list[Variant]
    profiles: list[Profile]
    preparationCodes: list[Preparation]
    counts: Counts


class Envelope(StrictDocument):
    snapshotId: Annotated[str, StringConstraints(pattern=r"^taste-v1-[0-9a-f]{64}$")]
    sourceReadAt: str
    payloadSha256: Digest
    payload: dict[str, JsonValue]


@dataclass(frozen=True)
class VerifiedSnapshot:
    snapshot_id: str
    source_read_at: datetime
    digest: str
    payload: dict


def utc_timestamp(value: str) -> datetime:
    if not UTC_TIME.fullmatch(value):
        raise SnapshotError("INVALID_SNAPSHOT")
    try:
        return datetime.fromisoformat(value).astimezone(timezone.utc)
    except ValueError:
        raise SnapshotError("INVALID_SNAPSHOT") from None


def _escaped(match: re.Match) -> str:
    char = match.group()
    if char in SHORT_ESCAPES:
        return SHORT_ESCAPES[char]
    codepoint = ord(char)
    if 0xD800 <= codepoint <= 0xDFFF:
        raise SnapshotError("INVALID_SNAPSHOT")
    if codepoint > 0xFFFF:
        offset = codepoint - 0x10000
        return rf"\u{0xD800 + (offset >> 10):04X}\u{0xDC00 + (offset & 1023):04X}"
    return rf"\u{codepoint:04X}"


def _quote(value: str) -> str:
    return '"' + ESCAPE.sub(_escaped, value) + '"'


def _canonical(value) -> str:
    if isinstance(value, dict):
        # Java String.compareTo uses UTF-16 code units.
        keys = sorted(value, key=lambda key: key.encode("utf-16-be"))
        return "{" + ",".join(_quote(key) + ":" + _canonical(value[key]) for key in keys) + "}"
    if isinstance(value, list):
        return "[" + ",".join(map(_canonical, value)) + "]"
    if isinstance(value, str):
        return _quote(unicodedata.normalize("NFC", value))
    if value is None or type(value) in (int, bool):
        return json.dumps(value)
    raise SnapshotError("INVALID_SNAPSHOT")


def canonical_bytes(value) -> bytes:
    try:
        return _canonical(value).encode("utf-8")
    except (UnicodeError, RecursionError, ValueError):
        raise SnapshotError("INVALID_SNAPSHOT") from None


def digest(value) -> str:
    return hashlib.sha256(canonical_bytes(value)).hexdigest()


def parse_json(raw: bytes) -> dict:
    def pairs(entries):
        result = dict(entries)
        if len(result) != len(entries):
            raise SnapshotError("INVALID_SNAPSHOT")
        return result

    def invalid_number(_):
        raise SnapshotError("INVALID_SNAPSHOT")

    try:
        return json.loads(raw.decode("utf-8"), object_pairs_hook=pairs,
                          parse_float=invalid_number, parse_constant=invalid_number)
    except (UnicodeError, RecursionError, ValueError):
        raise SnapshotError("INVALID_SNAPSHOT") from None


def _require(condition: bool) -> None:
    if not condition:
        raise SnapshotError("INVALID_SNAPSHOT")


def _code(value) -> bool:
    return isinstance(value, str) and bool(CODE.fullmatch(value)) and value != "UNKNOWN"


def _label_and_order(node: dict) -> bool:
    label = node.get("label")
    order = node.get("displayOrder")
    return isinstance(label, str) and bool(label.strip()) and len(label) <= 200 and type(order) is int and order >= 0


def _manifest(taxonomy: Taxonomy) -> dict[str, tuple[int, set[str]]]:
    manifest = taxonomy.manifest
    attributes = manifest.get("attributes")
    encoded = canonical_bytes(manifest)
    _require(len(encoded) <= 256 * 1024 and hashlib.sha256(encoded).hexdigest() == taxonomy.manifestSha256
             and manifest.get("versionCode") == taxonomy.versionCode and type(manifest.get("schemaVersion")) is int
             and manifest["schemaVersion"] == 1 and isinstance(attributes, list) and 1 <= len(attributes) <= 64)
    definitions = {}
    level_one = taxonomy.versionCode == LEVEL_ONE_TASTE_VERSION
    split_sweetness = taxonomy.versionCode in SPLIT_SWEETNESS_VERSIONS
    for attribute in attributes:
        _require(isinstance(attribute, dict) and _label_and_order(attribute))
        code, limit = attribute.get("attributeCode"), attribute.get("maxSelections")
        kind, values = attribute.get("selectionKind"), attribute.get("values")
        _require(_code(code) and code not in definitions and attribute.get("matchingSemantics") == "EXACT"
                 and isinstance(values, list) and 1 <= len(values) <= 128 and type(limit) is int
                 and 1 <= limit <= len(values)
                 and (kind == "ENUM_MULTI" or kind in ("ENUM_SINGLE", "ORDINAL_SINGLE") and limit == 1))
        codes = []
        for value in values:
            _require(isinstance(value, dict) and _label_and_order(value) and _code(value.get("valueCode")))
            codes.append(value["valueCode"])
        _require(codes == sorted(set(codes)))
        profile_codes = set(codes)
        if split_sweetness:
            _require(not profile_codes.intersection({NOT_APPLICABLE, "UNANSWERED"}))
            if code in ORDINAL_ATTRIBUTES:
                _require(kind == "ORDINAL_SINGLE" and profile_codes == ORDINAL_LEVELS)
            elif code == "FLAVOR":
                _require(kind == "ENUM_MULTI" and limit == 3)
                if level_one:
                    _require({"FRUITY", "PEACH"} <= profile_codes)
            elif level_one and code == AFTERTASTE_CHARACTER:
                _require(kind == "ENUM_SINGLE" and profile_codes == {"CLEAN"})
            else:
                _require(code == "TEA_TYPE" and kind == "ENUM_SINGLE")
            if code == ADDED_SWEETNESS:
                _require(attribute.get("allowsNotApplicable") is True)
                profile_codes.add(NOT_APPLICABLE)
            else:
                _require("allowsNotApplicable" not in attribute)
        definitions[code] = (limit, profile_codes)
    split_attributes = ORDINAL_ATTRIBUTES | {"FLAVOR", "TEA_TYPE"}
    if level_one:
        split_attributes.add(AFTERTASTE_CHARACTER)
    required = (definitions.keys() == split_attributes
                if split_sweetness else REQUIRED_ATTRIBUTES <= definitions.keys())
    _require(required and list(definitions) == sorted(definitions))
    return definitions


def _profile(row: Profile, taxonomy: Taxonomy, definitions: dict, variant: Variant) -> None:
    _require(variant.sellable and row.compositionRevision == variant.compositionRevision
             and row.compositionSha256 == variant.compositionSha256 and row.taxonomySha256 == taxonomy.manifestSha256)
    protocol = row.preparationProtocol
    encoded = canonical_bytes(protocol)
    _require(len(encoded) <= 16 * 1024 and hashlib.sha256(encoded).hexdigest() == row.preparationSha256
             and all(isinstance(protocol.get(key), str) and bool(protocol[key].strip())
                     for key in ("instructions", "servingBasis")))
    values = row.sensoryValues
    _require(bool(values) and len(canonical_bytes(values)) <= 16 * 1024)
    for code, choices in values.items():
        allowed = definitions.get(code)
        _require(allowed is not None and 1 <= len(choices) <= allowed[0] and choices == sorted(set(choices))
                 and set(choices) <= allowed[1] and ("NONE" not in choices or len(choices) == 1))
    utc_timestamp(row.approvedAt)
    content = {key: getattr(row, key) for key in (
        "variantId", "profileRevision", "compositionRevision", "compositionSha256", "taxonomySha256",
        "preparationCode", "preparationSha256", "sourceMethod", "sensoryValues",
    )}
    _require(digest(content) == row.profileSha256)


def verify_payload(raw: dict, expected_digest: str, max_bytes: int, max_variants: int) -> Payload:
    encoded = canonical_bytes(raw)
    if len(encoded) > max_bytes or isinstance(raw, dict) and isinstance(raw.get("variants"), list) \
            and len(raw["variants"]) > max_variants:
        raise SnapshotError("CAPACITY_ERROR")
    _require(hashlib.sha256(encoded).hexdigest() == expected_digest)
    try:
        payload = Payload.model_validate(raw)
    except (ValidationError, RecursionError):
        raise SnapshotError("INVALID_SNAPSHOT") from None
    _require(type(raw.get("schemaVersion")) is int and all(
        type(item.get(flag)) is bool
        for name, flags in (("products", ("isDeleted",)), ("variants", ("isDeleted", "customBundle", "sellable")))
        for item in raw[name] for flag in flags
    ))
    taxonomies = {row.versionCode: (row, _manifest(row)) for row in payload.taxonomies}
    products = {product.id for product in payload.products}
    variants = {variant.id: variant for variant in payload.variants}
    _require(payload.supportedTaxonomyVersions == sorted(taxonomies) and len(taxonomies) == len(payload.taxonomies)
             and (payload.catalogState == "PUBLISHED") == bool(taxonomies)
             and products == {row.productId for row in payload.variants}
             and len(products) == len(payload.products) == payload.counts.products
             and len(variants) == len(payload.variants) == payload.counts.variants
             and len(payload.profiles) == payload.counts.approvedProfiles)
    _require(all((row.compositionRevision == 0) == (row.compositionSha256 is None) for row in variants.values()))
    slots, identities, preparations, revisions = set(), set(), set(), set()
    for row in payload.profiles:
        variant = variants.get(row.variantId)
        release = taxonomies.get(row.taxonomyVersion)
        slot = (row.variantId, row.taxonomyVersion, row.preparationCode)
        revision = (row.variantId, row.profileRevision)
        _require(variant is not None and release is not None and row.id not in identities
                 and slot not in slots and revision not in revisions)
        _profile(row, *release, variant)
        slots.add(slot)
        identities.add(row.id)
        preparations.add(row.preparationCode)
        revisions.add(revision)
    _require(payload.preparationCodes == sorted(preparations))
    return payload
