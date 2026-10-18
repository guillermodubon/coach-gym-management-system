import {
  branchFixture,
  identityFixture,
} from "../../src/test/identity-fixtures";
import type { CurrentUser } from "../../src/shared/api/contracts";

/** Synthetic identity with long labels to exercise reflow, never real staff data. */
const branch = {
  ...branchFixture,
  code: "CENTRO_METROPOLITANO_01",
  name: "Sucursal de Atención Integral del Área Metropolitana de San Salvador",
};

export const responsiveIdentity = {
  ...identityFixture,
  organizationScope: "ORGANIZATION",
  fullName:
    "Administradora de Operaciones Regionales ApellidoExtensoSinSeparadoresParaVerificarElAjuste",
  activeBranch: branch,
  availableBranches: [branch],
} satisfies CurrentUser;
