import type { FormValues } from "../components/CatalogResourceManagement";

export function getString(values: FormValues, key: string) {
    const value = values[key];
    return typeof value === "string" ? value.trim() : "";
}

export function getNumber(values: FormValues, key: string) {
    return Number(getString(values, key));
}

export function getNullableNumber(
    values: FormValues,
    key: string
) {
    const value = getString(values, key);
    return value ? Number(value) : null;
}

export function toApiDateTime(value: string) {
    return value ? `${value}T00:00:00` : null;
}

export function toDateInput(value: string | null) {
    return value ? value.slice(0, 10) : "";
}
