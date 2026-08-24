import { DateTime } from 'luxon';

export const APP_LOCALE = 'pt-BR';
export const APP_TIME_ZONE = 'America/Sao_Paulo';

export type DateInput = string | Date | DateTime | null | undefined;

export function parseAppDateTime(value: DateInput): DateTime | null {
  if (value === null || value === undefined) {
    return null;
  }

  let dateTime: DateTime;

  if (DateTime.isDateTime(value)) {
    dateTime = value;
  } else if (value instanceof Date) {
    dateTime = DateTime.fromJSDate(value, {
      zone: APP_TIME_ZONE,
    });
  } else {
    const isoValue = value.trim();

    if (isoValue === '') {
      return null;
    }

    dateTime = DateTime.fromISO(isoValue, {
      zone: APP_TIME_ZONE,
    });
  }

  if (!dateTime.isValid) {
    return null;
  }

  return dateTime.setZone(APP_TIME_ZONE).setLocale(APP_LOCALE);
}

export function formatAppDate(value: DateInput): string {
  return parseAppDateTime(value)?.toFormat('dd/MM/yyyy') ?? '—';
}

export function formatAppDateTime(value: DateInput): string {
  return parseAppDateTime(value)?.toFormat('dd/MM/yyyy HH:mm') ?? '—';
}
