import type {
  CreateBranchInput,
  UpdateBranchInput,
} from "../../shared/api/organization-contracts";
import type { BranchRecord } from "../../shared/api/organization-contracts";

export type BranchEditorMode = "create" | "update";

export interface BranchEditorValues {
  code: string;
  name: string;
  addressLine1: string;
  addressLine2: string;
  city: string;
  stateOrDepartment: string;
  postalCode: string;
  countryCode: string;
  phone: string;
  email: string;
  timezone: string;
}

export type BranchEditorField = keyof BranchEditorValues;
export type BranchEditorErrors = Partial<Record<BranchEditorField, string>>;

export interface BranchEditorValidation {
  values: BranchEditorValues;
  errors: BranchEditorErrors;
  valid: boolean;
}

export const emptyBranchEditorValues: BranchEditorValues = {
  code: "",
  name: "",
  addressLine1: "",
  addressLine2: "",
  city: "",
  stateOrDepartment: "",
  postalCode: "",
  countryCode: "",
  phone: "",
  email: "",
  timezone: "",
};

const optionalLimits: Partial<Record<BranchEditorField, number>> = {
  addressLine1: 200,
  addressLine2: 200,
  city: 120,
  stateOrDepartment: 120,
  postalCode: 32,
  countryCode: 2,
  phone: 32,
  email: 254,
  timezone: 64,
};

function collapseWhitespace(value: string): string {
  return value.replace(/\s+/gu, " ").trim();
}

function hasControlCharacters(value: string): boolean {
  for (let index = 0; index < value.length; index += 1) {
    const code = value.charCodeAt(index);
    if (code <= 0x1f || (code >= 0x7f && code <= 0x9f)) return true;
  }
  return false;
}

export function normalizeBranchEditorValues(
  values: BranchEditorValues,
): BranchEditorValues {
  return {
    code: collapseWhitespace(values.code).toUpperCase(),
    name: collapseWhitespace(values.name),
    addressLine1: collapseWhitespace(values.addressLine1),
    addressLine2: collapseWhitespace(values.addressLine2),
    city: collapseWhitespace(values.city),
    stateOrDepartment: collapseWhitespace(values.stateOrDepartment),
    postalCode: collapseWhitespace(values.postalCode),
    countryCode: collapseWhitespace(values.countryCode).toUpperCase(),
    phone: collapseWhitespace(values.phone),
    email: collapseWhitespace(values.email).toLowerCase(),
    timezone: collapseWhitespace(values.timezone),
  };
}

export function branchEditorValuesFromRecord(
  branch: BranchRecord,
): BranchEditorValues {
  return {
    code: branch.code,
    name: branch.name,
    addressLine1: branch.addressLine1 ?? "",
    addressLine2: branch.addressLine2 ?? "",
    city: branch.city ?? "",
    stateOrDepartment: branch.stateOrDepartment ?? "",
    postalCode: branch.postalCode ?? "",
    countryCode: branch.countryCode ?? "",
    phone: branch.phone ?? "",
    email: branch.email ?? "",
    timezone: branch.timezone,
  };
}

export function validateBranchEditorValues(
  mode: BranchEditorMode,
  rawValues: BranchEditorValues,
): BranchEditorValidation {
  const values = normalizeBranchEditorValues(rawValues);
  const errors: BranchEditorErrors = {};

  for (const field of Object.keys(rawValues) as BranchEditorField[]) {
    if (hasControlCharacters(rawValues[field])) {
      errors[field] = "Este campo contiene caracteres no permitidos.";
    }
  }

  if (mode === "create") {
    const normalizedCode = collapseWhitespace(rawValues.code);
    if (!values.code) {
      errors.code = "Escribe el código de la sucursal.";
    } else if (normalizedCode.length > 32) {
      errors.code = "Usa como máximo 32 caracteres.";
    } else if (!/^[A-Z0-9]+(?:[-_][A-Z0-9]+)*$/.test(values.code)) {
      errors.code = "Usa letras, números, guiones o guiones bajos.";
    }
  }

  if (!values.name) {
    errors.name = "Escribe el nombre de la sucursal.";
  } else if (values.name.length > 160) {
    errors.name = "Usa como máximo 160 caracteres.";
  }

  for (const [field, limit] of Object.entries(optionalLimits) as [
    BranchEditorField,
    number,
  ][]) {
    const value = values[field];
    if (value.length > limit) {
      errors[field] = `Usa como máximo ${limit} caracteres.`;
    }
  }

  if (values.countryCode && !/^[A-Z]{2}$/.test(values.countryCode)) {
    errors.countryCode = "Usa un código de país de dos letras.";
  }
  if (values.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(values.email)) {
    errors.email = "Escribe un correo electrónico válido.";
  }
  if (mode === "update" && !values.timezone) {
    errors.timezone = "La zona horaria es obligatoria al editar.";
  }

  return { values, errors, valid: Object.keys(errors).length === 0 };
}

function optionalValue(value: string): string | undefined {
  return value || undefined;
}

export function toCreateBranchInput(
  values: BranchEditorValues,
): CreateBranchInput {
  const normalized = normalizeBranchEditorValues(values);
  return {
    code: normalized.code,
    name: normalized.name,
    ...(optionalValue(normalized.addressLine1)
      ? { addressLine1: normalized.addressLine1 }
      : {}),
    ...(optionalValue(normalized.addressLine2)
      ? { addressLine2: normalized.addressLine2 }
      : {}),
    ...(optionalValue(normalized.city) ? { city: normalized.city } : {}),
    ...(optionalValue(normalized.stateOrDepartment)
      ? { stateOrDepartment: normalized.stateOrDepartment }
      : {}),
    ...(optionalValue(normalized.postalCode)
      ? { postalCode: normalized.postalCode }
      : {}),
    ...(optionalValue(normalized.countryCode)
      ? { countryCode: normalized.countryCode }
      : {}),
    ...(optionalValue(normalized.phone) ? { phone: normalized.phone } : {}),
    ...(optionalValue(normalized.email) ? { email: normalized.email } : {}),
    ...(optionalValue(normalized.timezone)
      ? { timezone: normalized.timezone }
      : {}),
  };
}

export function toUpdateBranchInput(
  values: BranchEditorValues,
  version: number,
): UpdateBranchInput {
  const normalized = normalizeBranchEditorValues(values);
  return {
    name: normalized.name,
    ...(optionalValue(normalized.addressLine1)
      ? { addressLine1: normalized.addressLine1 }
      : {}),
    ...(optionalValue(normalized.addressLine2)
      ? { addressLine2: normalized.addressLine2 }
      : {}),
    ...(optionalValue(normalized.city) ? { city: normalized.city } : {}),
    ...(optionalValue(normalized.stateOrDepartment)
      ? { stateOrDepartment: normalized.stateOrDepartment }
      : {}),
    ...(optionalValue(normalized.postalCode)
      ? { postalCode: normalized.postalCode }
      : {}),
    ...(optionalValue(normalized.countryCode)
      ? { countryCode: normalized.countryCode }
      : {}),
    ...(optionalValue(normalized.phone) ? { phone: normalized.phone } : {}),
    ...(optionalValue(normalized.email) ? { email: normalized.email } : {}),
    timezone: normalized.timezone,
    version,
  };
}
