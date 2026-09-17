/** Canonical money formatter — prefer importing this or {@link money} from this module. */
export function formatMoney(value: unknown, currencyCode?: string): string {
  const number = Number(value ?? 0);
  if (Number.isNaN(number)) {
    return String(value ?? '');
  }
  if (currencyCode) {
    try {
      return new Intl.NumberFormat(undefined, {
        style: 'currency',
        currency: currencyCode,
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
      }).format(number);
    } catch {
      // fall through to plain decimal when currency code is invalid
    }
  }
  return new Intl.NumberFormat(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  }).format(number);
}

/** Alias for {@link formatMoney} — single shared entry point for currency display. */
export function money(value: unknown, currencyCode?: string): string {
  return formatMoney(value, currencyCode);
}
