/** Exact fractions for the handful of economic operations. No floating currency. */
export interface Fraction {
  readonly numerator: bigint;
  readonly denominator: bigint;
}

export class CalculationInputError extends Error {
  readonly field: string;

  constructor(field: string, message: string) {
    super(`${field}: ${message}`);
    this.name = "CalculationInputError";
    this.field = field;
  }
}

export const ZERO: Fraction = { numerator: 0n, denominator: 1n };

export function decimal(value: string, field: string): Fraction {
  // Bounded plain decimals keep parsing predictable. Provider adapters normalize
  // numbers into these strings; scientific notation and locale formatting fail.
  if (typeof value !== "string" || !/^-?\d{1,12}(?:\.\d{1,9})?$/.test(value)) {
    throw new CalculationInputError(field, "use a plain decimal string (up to 9 decimal places)");
  }
  const [whole = "", fraction = ""] = value.split(".");
  const denominator = 10n ** BigInt(fraction.length);
  const sign = whole.startsWith("-") ? -1n : 1n;
  return {
    numerator: BigInt(whole) * denominator + sign * BigInt(fraction || "0"),
    denominator,
  };
}

export function nonNegative(value: string, field: string): Fraction {
  const parsed = decimal(value, field);
  if (parsed.numerator < 0n) throw new CalculationInputError(field, "must be non-negative");
  return parsed;
}

export function positive(value: string, field: string): Fraction {
  const parsed = decimal(value, field);
  if (parsed.numerator <= 0n) throw new CalculationInputError(field, "must be positive");
  return parsed;
}

export const add = (a: Fraction, b: Fraction): Fraction => ({
  numerator: a.numerator * b.denominator + b.numerator * a.denominator,
  denominator: a.denominator * b.denominator,
});

export const subtract = (a: Fraction, b: Fraction): Fraction => ({
  numerator: a.numerator * b.denominator - b.numerator * a.denominator,
  denominator: a.denominator * b.denominator,
});

export const multiply = (a: Fraction, b: Fraction): Fraction => ({
  numerator: a.numerator * b.numerator,
  denominator: a.denominator * b.denominator,
});

export function divide(a: Fraction, b: Fraction): Fraction {
  if (b.numerator <= 0n) throw new CalculationInputError("divisor", "must be positive");
  return { numerator: a.numerator * b.denominator, denominator: a.denominator * b.numerator };
}

export function compare(a: Fraction, b: Fraction): number {
  const difference = a.numerator * b.denominator - b.numerator * a.denominator;
  return difference < 0n ? -1 : difference > 0n ? 1 : 0;
}

function roundedInteger(value: Fraction): bigint {
  const sign = value.numerator < 0n ? -1n : 1n;
  const magnitude = value.numerator * sign;
  const whole = magnitude / value.denominator;
  const remainder = magnitude % value.denominator;
  // Half away from zero, including negative saving values.
  return sign * (whole + (2n * remainder >= value.denominator ? 1n : 0n));
}

export function pence(value: Fraction): number {
  const rounded = roundedInteger(value);
  const limit = BigInt(Number.MAX_SAFE_INTEGER);
  if (rounded > limit || rounded < -limit) {
    throw new CalculationInputError("money", "result exceeds safe integer pence");
  }
  return Number(rounded);
}

/** Nine places for non-money quantities; never used as an intermediate value. */
export function decimalText(value: Fraction): string {
  const scale = 1_000_000_000n;
  const rounded = roundedInteger(multiply(value, { numerator: scale, denominator: 1n }));
  const magnitude = rounded < 0n ? -rounded : rounded;
  const fraction = (magnitude % scale).toString().padStart(9, "0").replace(/0+$/, "");
  return `${rounded < 0n ? "-" : ""}${magnitude / scale}${fraction ? `.${fraction}` : ""}`;
}
