import type { CurrentUser } from "../../shared/api/contracts";

export type BranchContextView =
  | {
      kind: "active-branch";
      headline: string;
      code: string;
      timezone: string;
      availableNames: string[];
    }
  | {
      kind: "organization-with-branches" | "organization-without-branches";
      headline: string;
      description: string;
      availableNames: string[];
    }
  | {
      kind: "branch-without-selection";
      headline: string;
      description: string;
      availableNames: string[];
    }
  | {
      kind: "inconsistent-context";
      headline: string;
      description: string;
      availableNames: [];
    };

export function getBranchContextView(user: CurrentUser): BranchContextView {
  const availableNames = user.availableBranches.map(({ name }) => name);

  if (user.activeBranch) {
    return {
      kind: "active-branch",
      headline: `Sucursal: ${user.activeBranch.name}`,
      code: user.activeBranch.code,
      timezone: user.activeBranch.timezone,
      availableNames,
    };
  }

  if (user.organizationScope === "ORGANIZATION") {
    if (availableNames.length > 0) {
      return {
        kind: "organization-with-branches",
        headline: "Ámbito de organización · Sin sucursal activa",
        description:
          "Esta sesión tiene ámbito de organización. F01 no contiene operaciones que requieran elegir una sucursal.",
        availableNames,
      };
    }
    return {
      kind: "organization-without-branches",
      headline:
        "Ámbito de organización · Sin sucursales operativas disponibles",
      description:
        "La sesión conserva su ámbito de organización y no tiene sucursales operativas disponibles.",
      availableNames,
    };
  }

  if (availableNames.length === 0) {
    return {
      kind: "inconsistent-context",
      headline: "Contexto de sucursal no disponible",
      description:
        "No se recibieron sucursales autorizadas. Actualiza el contexto para comprobar el estado de la sesión.",
      availableNames: [],
    };
  }

  const count = availableNames.length;
  return {
    kind: "branch-without-selection",
    headline: "Sin sucursal activa",
    description: `La sesión tiene ${count} ${count === 1 ? "sucursal" : "sucursales"} asignada${count === 1 ? "" : "s"}. La selección se incorpora en F03.`,
    availableNames,
  };
}
