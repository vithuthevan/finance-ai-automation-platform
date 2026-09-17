import { AbstractControl, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';

export const PASSWORD_MIN_LENGTH = 8;

export const passwordValidators = [
  Validators.required,
  Validators.minLength(PASSWORD_MIN_LENGTH)
];

export function matchingFieldValidator(otherControlName: string): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const parent = control.parent;
    if (!parent) {
      return null;
    }
    const other = parent.get(otherControlName);
    if (!other || !control.value) {
      return null;
    }
    return control.value === other.value ? null : { passwordMismatch: true };
  };
}

export const amountValidators = [
  Validators.required,
  Validators.min(0.01)
];

export const currencyCodeValidators = [
  Validators.required,
  Validators.pattern(/^[A-Za-z]{3}$/)
];

export function timezoneValidator(control: AbstractControl): ValidationErrors | null {
  const value = typeof control.value === 'string' ? control.value.trim() : '';
  if (!value) {
    return null;
  }
  try {
    Intl.DateTimeFormat(undefined, { timeZone: value });
    return null;
  } catch {
    return { invalidTimezone: true };
  }
}

export function notFutureDateValidator(control: AbstractControl): ValidationErrors | null {
  const value = control.value;
  if (!value) {
    return null;
  }
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) {
    return null;
  }
  const endOfToday = new Date();
  endOfToday.setHours(23, 59, 59, 999);
  return date.getTime() > endOfToday.getTime() ? { futureDate: true } : null;
}
