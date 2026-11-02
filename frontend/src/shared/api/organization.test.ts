import { beforeEach, describe, expect, it, vi } from "vitest";
import { http, HttpResponse } from "msw";
import { server } from "../../test/server";
import { csrfFixture } from "../../test/identity-fixtures";
import { createApiClient } from "./client";
import type { ApiClient } from "./client";
import { createOrganizationApi } from "./organization";

const origin = globalThis.location.origin;
const path = (route: string) => `${origin}/api/v1/${route}`;
const organizationId = "00000000-0000-4000-8000-000000000001";
const branchId = "00000000-0000-4000-8000-000000000002";
const updatedAt = "2026-10-31T12:00:00Z";
const branchResponse = {
  id: branchId,
  code: "PRINCIPAL",
  name: "Coach Gym Principal",
  addressLine1: null,
  addressLine2: null,
  city: "San Salvador",
  stateOrDepartment: null,
  postalCode: null,
  countryCode: "SV",
  phone: null,
  email: null,
  timezone: "America/El_Salvador",
  status: "ACTIVE",
  initialBranch: true,
  version: 3,
  updatedAt,
};
const branchPage = {
  items: [branchResponse],
  page: 0,
  size: 25,
  totalElements: 1,
  totalPages: 1,
};

let client: ApiClient;
let api: ReturnType<typeof createOrganizationApi>;

beforeEach(() => {
  client = createApiClient();
  api = createOrganizationApi(client);
  server.use(http.get(path("auth/csrf"), () => HttpResponse.json(csrfFixture)));
});

describe("F03 organization API", () => {
  it("reads the safe organization projection without requiring admin-only fields", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch");
    server.use(
      http.get(path("organization"), ({ request }) => {
        expect(request.headers.has(csrfFixture.headerName)).toBe(false);
        return HttpResponse.json({
          id: organizationId,
          code: "COACH-GYM",
          brandName: "Coach Gym",
          defaultCurrency: "USD",
          status: "ACTIVE",
        });
      }),
    );
    await expect(api.organization()).resolves.toMatchObject({
      id: organizationId,
      code: "COACH-GYM",
      brandName: "Coach Gym",
    });
    expect(fetchSpy).toHaveBeenCalledWith(
      new URL(path("organization")),
      expect.objectContaining({ credentials: "same-origin" }),
    );
  });

  it("serializes only the eight allowlisted catalog filters", async () => {
    server.use(
      http.get(path("branches"), ({ request }) => {
        const url = new URL(request.url);
        expect([...url.searchParams.keys()]).toEqual([
          "status",
          "search",
          "city",
          "countryCode",
          "page",
          "size",
          "sort",
          "direction",
        ]);
        expect(url.searchParams.get("search")).toBe("Gym & Health");
        expect(request.headers.has(csrfFixture.headerName)).toBe(false);
        return HttpResponse.json(branchPage);
      }),
    );
    await expect(
      api.listBranches({
        status: "ACTIVE",
        search: "Gym & Health",
        city: "San Salvador",
        countryCode: "sv",
        page: 0,
        size: 25,
        sort: "CODE",
        direction: "ASC",
      }),
    ).resolves.toMatchObject({ items: [{ id: branchId }], totalElements: 1 });
  });

  it("reads a detail only through a canonical UUID route", async () => {
    server.use(
      http.get(path(`branches/${branchId}`), () =>
        HttpResponse.json(branchResponse),
      ),
    );
    await expect(api.branch(branchId)).resolves.toMatchObject({
      id: branchId,
      version: 3,
    });
    const fetchSpy = vi.spyOn(globalThis, "fetch");
    await expect(
      Promise.resolve().then(() => api.branch(`${branchId}/policies`)),
    ).rejects.toMatchObject({ kind: "invalid-request" });
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it("creates with CSRF and omits server-owned or extra caller fields", async () => {
    server.use(
      http.post(path("branches"), async ({ request }) => {
        expect(request.headers.get(csrfFixture.headerName)).toBe(
          csrfFixture.token,
        );
        expect(await request.json()).toEqual({
          code: "NORTH",
          name: "North",
          city: "San Salvador",
          timezone: "America/El_Salvador",
        });
        return HttpResponse.json(branchResponse, { status: 201 });
      }),
    );
    await expect(
      api.createBranch({
        code: "NORTH",
        name: "North",
        city: "San Salvador",
        timezone: "America/El_Salvador",
        ...({ organizationId, status: "INACTIVE" } as object),
      } as never),
    ).resolves.toMatchObject({ id: branchId });
  });

  it("updates only editable fields and includes the expected version", async () => {
    server.use(
      http.put(path(`branches/${branchId}`), async ({ request }) => {
        expect(request.headers.get(csrfFixture.headerName)).toBe(
          csrfFixture.token,
        );
        expect(await request.json()).toEqual({
          name: "Updated name",
          city: "Santa Tecla",
          timezone: "America/El_Salvador",
          version: 3,
        });
        return HttpResponse.json({ ...branchResponse, version: 4 });
      }),
    );
    await expect(
      api.updateBranch(branchId, {
        name: "Updated name",
        city: "Santa Tecla",
        timezone: "America/El_Salvador",
        version: 3,
        ...({ code: "IGNORED", status: "INACTIVE" } as object),
      } as never),
    ).resolves.toMatchObject({ version: 4 });
  });

  it("posts lifecycle reasons and versions to their fixed transition paths", async () => {
    const actions: string[] = [];
    for (const action of ["activate", "deactivate"] as const) {
      server.use(
        http.post(
          path(`branches/${branchId}/${action}`),
          async ({ request }) => {
            actions.push(action);
            expect(request.headers.get(csrfFixture.headerName)).toBe(
              csrfFixture.token,
            );
            expect(await request.json()).toEqual({
              reason: "Reviewed",
              version: 3,
            });
            return HttpResponse.json(branchResponse);
          },
        ),
      );
    }
    await api.activateBranch(branchId, { reason: "Reviewed", version: 3 });
    await api.deactivateBranch(branchId, { reason: "Reviewed", version: 3 });
    expect(actions).toEqual(["activate", "deactivate"]);
  });
});
