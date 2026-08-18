import Decimal from 'decimal.js';

export type MoneyInput = Decimal.Value | null | undefined;

export const MoneyDecimal = Decimal.clone({
  precision: 28,
  rounding: Decimal.ROUND_HALF_UP,
});

export function formatBrl(value: MoneyInput): string {
  if (value === null || value === undefined || value === '') {
    return '—';
  }

  try {
    const fixedValue = new MoneyDecimal(value)
      .toDecimalPlaces(2)
      .toFixed(2);

    const isNegative = fixedValue.startsWith('-');
    const absoluteValue = isNegative ? fixedValue.slice(1) : fixedValue;
    const [integerPart, decimalPart] = absoluteValue.split('.');

    const groupedInteger = integerPart.replace(
      /\B(?=(\d{3})+(?!\d))/g,
      '.',
    );

    return `${isNegative ? '-' : ''}R$ ${groupedInteger},${decimalPart}`;
  } catch {
    return '—';
  }
}