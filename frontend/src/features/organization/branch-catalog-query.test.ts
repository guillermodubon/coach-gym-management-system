import { describe, expect, it } from "vitest";
import {
  branchCatalogSearch,
  parseBranchCatalogSearch,
} from "./branch-catalog-query";

describe("branch catalog URL query", () => {
  it("parses and canonicalizes filters, sorting, and bounded paging", () => {
    expect(
      parseBranchCatalogSearch(
        "?status=inactive&search=%20North%20%20Club%20&countryCode=sv&page=2&size=50&sort=updated_at&direction=desc",
      ),
    ).toEqual({
      valid: true,
      query: {
        status: "INACTIVE",
        search: "North Club",
        countryCode: "SV",
        page: 2,
        size: 50,
        sort: "UPDATED_AT",
        direction: "DESC",
      },
    });
  });

  it.each([
    "?page=-1",
    "?size=101",
    "?status=PAUSED",
    "?sort=UNSAFE",
    "?countryCode=SLV",
    "?page=1&page=2",
    "?search=%01",
    `?search=${"x".repeat(121)}`,
  ])("rejects an invalid URL query %s", (search) => {
    expect(parseBranchCatalogSearch(search).valid).toBe(false);
  });

  it("omits defaults and empty filters when writing a URL", () => {
    expect(
      branchCatalogSearch({
        page: 0,
        size: 25,
        sort: "CODE",
        direction: "ASC",
      }),
    ).toBe("");
    expect(
      branchCatalogSearch({
        page: 3,
        size: 50,
        sort: "NAME",
        direction: "DESC",
        status: "ACTIVE",
        city: "San Salvador",
      }),
    ).toBe(
      "?status=ACTIVE&city=San+Salvador&page=3&size=50&sort=NAME&direction=DESC",
    );
  });
});
