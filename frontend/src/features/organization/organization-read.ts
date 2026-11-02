import { useQuery } from "@tanstack/react-query";
import { ApiError } from "../../shared/api/errors";
import { useApiServices } from "../../shared/api/ApiServicesProvider";
import type { BranchCatalogQuery } from "../../shared/api/organization-contracts";
import { useAuth } from "../auth/useAuth";
import {
  branchCatalogQueryKey,
  branchDetailQueryKey,
  organizationQueryKey,
} from "./organization-query";

export function useOrganizationReadScope() {
  const auth = useAuth();
  const user = auth.state.kind === "authenticated" ? auth.state.user : null;
  const role = user?.roles[0] ?? null;
  const scope = user?.organizationScope ?? null;
  const authority = role && scope ? `${role}:${scope}` : "unavailable";
  // These are global read projections. The actor, authority and context epoch
  // in each query key isolate results across session and context transitions.
  const enabled = Boolean(user && !auth.privacyCovered);
  return {
    actorId: user?.id ?? "unavailable",
    authority,
    contextEpoch: auth.contextEpoch,
    enabled,
  };
}

export function useOrganizationQuery() {
  const scope = useOrganizationReadScope();
  const { organizationApi } = useApiServices();
  return useQuery({
    queryKey: organizationQueryKey(scope),
    queryFn: ({ signal }) => organizationApi.organization({ signal }),
    enabled: scope.enabled,
    staleTime: 0,
    gcTime: 0,
    retry: false,
    refetchOnMount: true,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });
}

export function useBranchCatalogQuery(
  query: BranchCatalogQuery,
  queryEnabled = true,
) {
  const scope = useOrganizationReadScope();
  const { organizationApi } = useApiServices();
  return useQuery({
    queryKey: branchCatalogQueryKey(scope, query),
    queryFn: ({ signal }) => organizationApi.listBranches(query, { signal }),
    enabled: scope.enabled && queryEnabled,
    staleTime: 0,
    gcTime: 0,
    retry: false,
    refetchOnMount: true,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });
}

export function useBranchDetailQuery(branchId: string, queryEnabled = true) {
  const scope = useOrganizationReadScope();
  const { organizationApi } = useApiServices();
  return useQuery({
    queryKey: branchDetailQueryKey(scope, branchId),
    queryFn: ({ signal }) => organizationApi.branch(branchId, { signal }),
    enabled: scope.enabled && queryEnabled && branchId.length > 0,
    staleTime: 0,
    gcTime: 0,
    retry: false,
    refetchOnMount: true,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });
}

export function organizationReadError(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return "No se pudo cargar la información. Comprueba la sesión e inténtalo de nuevo.";
  }
  if (error.status === 403) {
    return "Tu cuenta no tiene autorización para consultar esta información.";
  }
  if (error.status === 404) {
    return "La información solicitada no está disponible.";
  }
  if (error.status === 400) {
    return "Los filtros enviados no son válidos. Revisa la URL y vuelve a intentarlo.";
  }
  return error.message;
}

export function isBranchId(value: string | undefined): value is string {
  return Boolean(
    value && /^[\da-f]{8}-(?:[\da-f]{4}-){3}[\da-f]{12}$/i.test(value),
  );
}
