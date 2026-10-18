/** Synthetic values only. No real users, cookies or provider credentials. */
export const csrfFixture = {
  token: "synthetic-csrf-first",
  headerName: "X-XSRF-TOKEN",
  parameterName: "_csrf",
};

export const branchFixture = {
  id: "00000000-0000-4000-8000-000000000002",
  organizationId: "00000000-0000-4000-8000-000000000001",
  code: "TEST",
  name: "Sucursal de prueba",
  timezone: "America/El_Salvador",
  initialBranch: true,
};

export const identityFixture = {
  id: "00000000-0000-4000-8000-000000000003",
  username: "operador-prueba",
  fullName: "Operador de prueba",
  roles: ["ADMIN"],
  organizationScope: "ORGANIZATION",
  activeBranch: null,
  availableBranches: [branchFixture],
  passwordChangeRequired: false,
};
