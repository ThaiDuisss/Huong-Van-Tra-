from dataclasses import dataclass

from app.recommendation.taste_rules import VariantMatch


@dataclass(frozen=True)
class AttributeDefinition:
    label: str
    maximum: int
    value_labels: dict[str, str]


def explain_variant(match: VariantMatch, definitions: dict[str, AttributeDefinition]) -> str:
    def describe(attribute: str, values: list[str]) -> str:
        definition = definitions[attribute]
        value_labels = ", ".join(definition.value_labels[value] for value in values)
        return f"{definition.label} ({value_labels})"

    sentences = []
    if match.matched_hard:
        hard = ", ".join(describe(item.attribute, item.matchedValues) for item in match.matched_hard)
        sentences.append(f"Hồ sơ đã duyệt của biến thể đáp ứng tiêu chí bắt buộc về {hard}.")
    if match.matched_soft:
        matched = ", ".join(describe(item.attribute, [item.value]) for item in match.matched_soft)
        sentences.append(f"Hồ sơ đã duyệt khớp ưu tiên về {matched}.")
    missed = [
        describe(item.attribute, [item.value])
        for item in match.unmatched_soft if item.status == "NOT_MATCHED"
    ]
    if missed:
        sentences.append(f"Hồ sơ đã duyệt chưa phù hợp với ưu tiên về {', '.join(missed)}.")
    if match.unknown_attributes:
        labels = ", ".join(definitions[code].label for code in match.unknown_attributes)
        sentences.append(f"Chưa có dữ liệu đã duyệt về {labels}.")
    return " ".join(sentences)
