import { describe, expect, it } from "vitest";
import {
  emptyBranchEditorValues,
  normalizeBranchEditorValues,
  toCreateBranchInput,
  toUpdateBranchInput,
  validateBranchEditorValues,
} from "./branch-editor-validation";

describe("branch editor validation and payload mapping", () => {
  it("normalizes create identity and optional fields without inventing defaults", () => {
    const values = {
      ...emptyBranchEditorValues,
      code: "  central   north ",
      name: "  Gimnasio   Norte  ",
      countryCode: " sv ",
      email: "  SUPPORT@EXAMPLE.TEST ",
      timezone: " ",
    };

    expect(normalizeBranchEditorValues(values)).toMatchObject({
      code: "CENTRAL NORTH",
      name: "Gimnasio Norte",
      countryCode: "SV",
      email: "support@example.test",
    });
    expect(validateBranchEditorValues("create", values).errors.code).toBe(
      "Usa letras, números, guiones o guiones bajos.",
    );

    const valid = {
      ...values,
      code: "  central_north ",
    };
    expect(validateBranchEditorValues("create", valid).valid).toBe(true);
    expect(toCreateBranchInput(valid)).toEqual({
      code: "CENTRAL_NORTH",
      name: "Gimnasio Norte",
      countryCode: "SV",
      email: "support@example.test",
    });
  });

  it("preserves Unicode, validates bounds and rejects control characters", () => {
    const result = validateBranchEditorValues("create", {
      ...emptyBranchEditorValues,
      code: "CENTRAL",
      name: "Gimnasio Ñandutí",
      city: `San\nSalvador`,
      phone: "x".repeat(33),
      email: "no-es-correo",
    });

    expect(result.values.name).toBe("Gimnasio Ñandutí");
    expect(result.errors.city).toBe(
      "Este campo contiene caracteres no permitidos.",
    );
    expect(result.errors.phone).toBe("Usa como máximo 32 caracteres.");
    expect(result.errors.email).toBe("Escribe un correo electrónico válido.");
  });

  it("requires timezone on update and sends the server version without code", () => {
    const values = {
      ...emptyBranchEditorValues,
      code: "NORTH",
      name: "Sucursal Norte",
    };
    expect(validateBranchEditorValues("update", values).errors.timezone).toBe(
      "La zona horaria es obligatoria al editar.",
    );

    const valid = { ...values, timezone: "America/El_Salvador" };
    expect(validateBranchEditorValues("update", valid).valid).toBe(true);
    expect(toUpdateBranchInput(valid, 7)).toEqual({
      name: "Sucursal Norte",
      timezone: "America/El_Salvador",
      version: 7,
    });
  });

  it("uses the allowlisted editable values and excludes server-owned fields", () => {
    const input = toUpdateBranchInput(
      {
        ...emptyBranchEditorValues,
        code: "UNCHANGED",
        name: "Nueva sucursal",
        addressLine1: " Avenida 1 ",
        phone: " 2222 3333 ",
        timezone: "America/El_Salvador",
      },
      4,
    );

    expect(input).toEqual({
      name: "Nueva sucursal",
      addressLine1: "Avenida 1",
      phone: "2222 3333",
      timezone: "America/El_Salvador",
      version: 4,
    });
    expect(input).not.toHaveProperty("code");
    expect(input).not.toHaveProperty("status");
    expect(input).not.toHaveProperty("initialBranch");
    expect(input).not.toHaveProperty("organizationId");
  });
});
