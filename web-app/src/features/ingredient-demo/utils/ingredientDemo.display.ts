export const shortResearchId = (value: string) => value.length > 16 ? `${value.slice(0, 8)}…${value.slice(-4)}` : value;

export function formatDemoInstant(value: string): string {
  const iso = new Date(value).toISOString();
  return `${iso.slice(0, 10).split("-").reverse().join("/")} lúc ${iso.slice(11, 16)} UTC`;
}
