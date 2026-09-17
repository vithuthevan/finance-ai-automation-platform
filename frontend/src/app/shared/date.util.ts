import { format, startOfMonth } from 'date-fns';

export function monthStart(): string {
  return format(startOfMonth(new Date()), 'yyyy-MM-dd');
}

export function today(): string {
  return format(new Date(), 'yyyy-MM-dd');
}

export function parseIsoDate(value: string | null | undefined): Date | null {
  if (!value) {
    return null;
  }
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!match) {
    const parsed = new Date(value);
    return Number.isNaN(parsed.getTime()) ? null : parsed;
  }
  const year = Number(match[1]);
  const month = Number(match[2]) - 1;
  const day = Number(match[3]);
  return new Date(year, month, day);
}

export function formatIsoDate(value: Date | null | undefined): string {
  if (!value || Number.isNaN(value.getTime())) {
    return '';
  }
  return format(value, 'yyyy-MM-dd');
}

export const DATE_RANGE_ERROR = 'From date must be on or before To date.';

export const COMPARE_DATE_RANGE_ERROR = 'Compare from date must be on or before compare to date.';

export function isInvalidDateRange(
  from: string | null | undefined,
  to: string | null | undefined
): boolean {
  return !!(from && to && from > to);
}
