import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import type { PropsWithChildren } from "react";
import { useQueryClient } from "@tanstack/react-query";
import type { BranchRecord } from "../../shared/api/organization-contracts";
import { useApiServices } from "../../shared/api/ApiServicesProvider";
import { ApiError } from "../../shared/api/errors";
import type { AuthSnapshot } from "../auth/auth-model";
import { useAuth } from "../auth/useAuth";
import { useContextChangeGuard } from "../branch-context/ContextChangeGuardProvider";
import { branchDetailQueryKey } from "./organization-query";
import {
  branchEditorValuesFromRecord,
  emptyBranchEditorValues,
  normalizeBranchEditorValues,
  toCreateBranchInput,
  toUpdateBranchInput,
  validateBranchEditorValues,
} from "./branch-editor-validation";
import type {
  BranchEditorField,
  BranchEditorMode,
  BranchEditorValues,
} from "./branch-editor-validation";
import { getBranchManagementUser } from "./branch-editor-policy";

export interface BranchEditorDraft {
  mode: BranchEditorMode;
  actorId: string;
  branchId: string | null;
  baseVersion: number | null;
  baseValues: BranchEditorValues;
  values: BranchEditorValues;
}

interface BranchEditorState {
  draft: BranchEditorDraft | null;
  saving: boolean;
  writeError: ApiError | null;
  outcomeUncertain: boolean;
  versionConflict: boolean;
  latestBranch: BranchRecord | null;
  latestReadError: ApiError | null;
  loadingLatest: boolean;
  blockedWriteReason: string | null;
}

export interface BranchEditorContextValue extends BranchEditorState {
  authorized: boolean;
  dirty: boolean;
  matchesTarget(mode: BranchEditorMode, branchId?: string): boolean;
  beginCreate(): void;
  beginUpdate(branch: BranchRecord): void;
  setField(field: BranchEditorField, value: string): void;
  discard(): void;
  save(): Promise<BranchRecord | null>;
  loadLatest(): Promise<void>;
  replaceWithLatest(): void;
}

const BranchEditorContext = createContext<BranchEditorContextValue | null>(
  null,
);

const emptyState: BranchEditorState = {
  draft: null,
  saving: false,
  writeError: null,
  outcomeUncertain: false,
  versionConflict: false,
  latestBranch: null,
  latestReadError: null,
  loadingLatest: false,
  blockedWriteReason: null,
};

function toSafeApiError(error: unknown): ApiError {
  return error instanceof ApiError ? error : new ApiError("invalid-response");
}

function isUncertainWrite(error: ApiError): boolean {
  return (
    error.kind === "network" ||
    error.kind === "timeout" ||
    error.kind === "invalid-response" ||
    (error.kind === "http" && (error.status ?? 0) >= 500)
  );
}

function isDirtyDraft(draft: BranchEditorDraft | null): boolean {
  if (!draft) return false;
  const values = normalizeBranchEditorValues(draft.values);
  const base = normalizeBranchEditorValues(draft.baseValues);
  const editableFields: readonly BranchEditorField[] =
    draft.mode === "create"
      ? [
          "code",
          "name",
          "addressLine1",
          "addressLine2",
          "city",
          "stateOrDepartment",
          "postalCode",
          "countryCode",
          "phone",
          "email",
          "timezone",
        ]
      : [
          "name",
          "addressLine1",
          "addressLine2",
          "city",
          "stateOrDepartment",
          "postalCode",
          "countryCode",
          "phone",
          "email",
          "timezone",
        ];
  return editableFields.some((field) => values[field] !== base[field]);
}

function draftFromBranch(
  actorId: string,
  branch: BranchRecord,
): BranchEditorDraft {
  const values = branchEditorValuesFromRecord(branch);
  return {
    mode: "update",
    actorId,
    branchId: branch.id,
    baseVersion: branch.version,
    baseValues: values,
    values: { ...values },
  };
}

function isSameDraftTarget(
  draft: BranchEditorDraft | null,
  mode: BranchEditorMode,
  actorId: string,
  branchId?: string,
): boolean {
  return Boolean(
    draft &&
    draft.mode === mode &&
    draft.actorId === actorId &&
    (mode === "create" ? draft.branchId === null : draft.branchId === branchId),
  );
}

function isRevalidatingAuth(error: ApiError): boolean {
  return (
    error.status === 401 ||
    (error.status === 403 && error.code !== "CSRF_TOKEN_INVALID")
  );
}

function queryAuthority(auth: AuthSnapshot): string | null {
  const user = getBranchManagementUser(auth);
  return user ? `${user.roles[0]}:${user.organizationScope}` : null;
}

export function BranchEditorProvider({ children }: PropsWithChildren) {
  const auth = useAuth();
  const authRef = useRef(auth);
  authRef.current = auth;
  const contextChangeGuard = useContextChangeGuard();
  const queryClient = useQueryClient();
  const { organizationApi } = useApiServices();
  const [state, setState] = useState<BranchEditorState>(emptyState);
  const stateRef = useRef(state);
  stateRef.current = state;
  const lastIdentity = useRef<{ actorId: string; authority: string } | null>(
    null,
  );
  const operationSequence = useRef(0);
  const writeLock = useRef(false);

  const commit = useCallback((next: BranchEditorState) => {
    stateRef.current = next;
    setState(next);
  }, []);

  const currentActorIs = useCallback((actorId: string) => {
    const current = authRef.current;
    const user = getBranchManagementUser(current);
    return user?.id === actorId;
  }, []);

  useEffect(
    () =>
      contextChangeGuard.registerBlocker(
        () => writeLock.current || isDirtyDraft(stateRef.current.draft),
      ),
    [contextChangeGuard],
  );

  useEffect(() => {
    if (auth.state.kind === "checking" || auth.privacyCovered) return;
    const user = getBranchManagementUser(auth);
    if (!user) {
      lastIdentity.current = null;
      operationSequence.current += 1;
      commit(emptyState);
      return;
    }

    const authority = `${user.roles[0]}:${user.organizationScope}`;
    const previous = lastIdentity.current;
    if (
      previous &&
      (previous.actorId !== user.id || previous.authority !== authority)
    ) {
      operationSequence.current += 1;
      commit(emptyState);
    }
    lastIdentity.current = { actorId: user.id, authority };
  }, [auth, commit]);

  const beginCreate = useCallback(() => {
    const user = getBranchManagementUser(authRef.current);
    if (!user) return;
    const current = stateRef.current;
    if (isSameDraftTarget(current.draft, "create", user.id)) return;
    if (isDirtyDraft(current.draft) || current.saving) return;
    commit({
      ...emptyState,
      draft: {
        mode: "create",
        actorId: user.id,
        branchId: null,
        baseVersion: null,
        baseValues: { ...emptyBranchEditorValues },
        values: { ...emptyBranchEditorValues },
      },
    });
  }, [commit]);

  const beginUpdate = useCallback(
    (branch: BranchRecord) => {
      const user = getBranchManagementUser(authRef.current);
      if (!user) return;
      const current = stateRef.current;
      if (isSameDraftTarget(current.draft, "update", user.id, branch.id)) {
        if (current.outcomeUncertain) return;
        if (isDirtyDraft(current.draft)) {
          if (current.draft?.baseVersion !== branch.version) {
            commit({
              ...current,
              versionConflict: true,
              latestBranch: branch,
              latestReadError: null,
            });
          }
          return;
        }
      } else if (isDirtyDraft(current.draft) || current.saving) {
        return;
      }
      commit({ ...emptyState, draft: draftFromBranch(user.id, branch) });
    },
    [commit],
  );

  const setField = useCallback(
    (field: BranchEditorField, value: string) => {
      const current = stateRef.current;
      const draft = current.draft;
      if (!draft || current.saving || current.outcomeUncertain) return;
      if (field === "code" && draft.mode === "update") return;
      const nextDraft = {
        ...draft,
        values: { ...draft.values, [field]: value },
      };
      const next = {
        ...current,
        draft: nextDraft,
        writeError: null,
        blockedWriteReason: null,
      };
      commit(next);
    },
    [commit],
  );

  const discard = useCallback(() => {
    if (stateRef.current.saving) return;
    operationSequence.current += 1;
    commit(emptyState);
  }, [commit]);

  const save = useCallback(async (): Promise<BranchRecord | null> => {
    const current = stateRef.current;
    const draft = current.draft;
    const user = getBranchManagementUser(authRef.current);
    if (
      !draft ||
      !user ||
      draft.actorId !== user.id ||
      current.saving ||
      current.outcomeUncertain ||
      current.versionConflict ||
      writeLock.current
    ) {
      return null;
    }

    const validation = validateBranchEditorValues(draft.mode, draft.values);
    if (!validation.valid) return null;
    if (draft.mode === "update" && draft.baseVersion === null) return null;

    const writeLease = contextChangeGuard.tryAcquireWrite();
    if (!writeLease) {
      const reason = contextChangeGuard.getBlockReason();
      commit({
        ...stateRef.current,
        blockedWriteReason:
          reason === "context-change-in-progress"
            ? "Espera a que termine el cambio de sucursal antes de guardar."
            : "Hay otra operación en curso. Espera a que termine antes de guardar.",
      });
      return null;
    }

    const expectedActorId = draft.actorId;
    const expectedBranchId = draft.branchId;
    const startingAuth = authRef.current;
    const startingEpoch = startingAuth.contextEpoch;
    const startingSequence = operationSequence.current;
    const authority = queryAuthority(startingAuth);
    if (!authority) {
      writeLease.release();
      return null;
    }

    writeLock.current = true;
    commit({
      ...stateRef.current,
      saving: true,
      writeError: null,
      blockedWriteReason: null,
    });

    let result: BranchRecord | null = null;
    let requiresRevalidation = false;
    try {
      result =
        draft.mode === "create"
          ? await organizationApi.createBranch(
              toCreateBranchInput(validation.values),
            )
          : await organizationApi.updateBranch(
              expectedBranchId!,
              toUpdateBranchInput(validation.values, draft.baseVersion!),
            );

      if (draft.mode === "update" && result.id !== expectedBranchId) {
        throw new ApiError("invalid-response");
      }
      if (
        !currentActorIs(expectedActorId) ||
        operationSequence.current !== startingSequence
      ) {
        result = null;
        if (authRef.current.state.kind === "checking") {
          commit({
            ...stateRef.current,
            outcomeUncertain: true,
            writeError: null,
          });
        }
      }
    } catch (caught) {
      if (
        !currentActorIs(expectedActorId) ||
        operationSequence.current !== startingSequence
      ) {
        result = null;
      } else {
        const error = toSafeApiError(caught);
        requiresRevalidation = isRevalidatingAuth(error);
        commit({
          ...stateRef.current,
          writeError: error,
          outcomeUncertain: isUncertainWrite(error),
          versionConflict: error.code === "GYM_BRANCH_VERSION_CONFLICT",
          latestBranch: null,
          latestReadError: null,
        });
      }
    } finally {
      writeLock.current = false;
      writeLease.release();
      commit({ ...stateRef.current, saving: false });
    }

    if (requiresRevalidation) {
      void authRef.current.revalidate();
    }
    if (!result) return null;

    const latestAuth = authRef.current;
    const currentUser = getBranchManagementUser(latestAuth);
    if (
      !currentUser ||
      currentUser.id !== expectedActorId ||
      latestAuth.contextEpoch !== startingEpoch ||
      operationSequence.current !== startingSequence
    ) {
      commit({
        ...stateRef.current,
        outcomeUncertain: true,
        writeError: null,
      });
      return null;
    }

    queryClient.setQueryData(
      branchDetailQueryKey(
        {
          actorId: expectedActorId,
          authority,
          contextEpoch: startingEpoch,
        },
        result.id,
      ),
      result,
    );
    void queryClient.invalidateQueries({
      queryKey: ["private", "branch-catalog", expectedActorId, authority],
    });
    commit(emptyState);
    void authRef.current.reconcileBranchContext();
    return result;
  }, [
    commit,
    contextChangeGuard,
    currentActorIs,
    organizationApi,
    queryClient,
  ]);

  const loadLatest = useCallback(async () => {
    const current = stateRef.current;
    const draft = current.draft;
    if (
      !draft ||
      draft.mode !== "update" ||
      !draft.branchId ||
      !current.versionConflict ||
      current.loadingLatest
    ) {
      return;
    }
    const actorId = draft.actorId;
    const branchId = draft.branchId;
    const operation = ++operationSequence.current;
    commit({
      ...current,
      loadingLatest: true,
      latestReadError: null,
    });
    try {
      const branch = await organizationApi.branch(branchId);
      if (
        !currentActorIs(actorId) ||
        operationSequence.current !== operation ||
        branch.id !== branchId
      ) {
        return;
      }
      commit({
        ...stateRef.current,
        latestBranch: branch,
        latestReadError: null,
      });
    } catch (caught) {
      if (!currentActorIs(actorId) || operationSequence.current !== operation) {
        return;
      }
      const error = toSafeApiError(caught);
      commit({ ...stateRef.current, latestReadError: error });
      if (isRevalidatingAuth(error)) void authRef.current.revalidate();
    } finally {
      if (operationSequence.current === operation) {
        commit({ ...stateRef.current, loadingLatest: false });
      }
    }
  }, [commit, currentActorIs, organizationApi]);

  const replaceWithLatest = useCallback(() => {
    const current = stateRef.current;
    const branch = current.latestBranch;
    const draft = current.draft;
    if (
      !branch ||
      !draft ||
      draft.mode !== "update" ||
      draft.branchId !== branch.id ||
      !currentActorIs(draft.actorId)
    ) {
      return;
    }
    commit({ ...emptyState, draft: draftFromBranch(draft.actorId, branch) });
  }, [commit, currentActorIs]);

  const authorized = getBranchManagementUser(auth) !== null;
  const visibleDraft =
    authorized && state.draft?.actorId === getBranchManagementUser(auth)?.id
      ? state.draft
      : null;
  const dirty = isDirtyDraft(state.draft);

  const value = useMemo<BranchEditorContextValue>(
    () => ({
      ...state,
      draft: visibleDraft,
      authorized,
      dirty,
      matchesTarget: (mode, branchId) =>
        Boolean(
          visibleDraft &&
          isSameDraftTarget(visibleDraft, mode, visibleDraft.actorId, branchId),
        ),
      beginCreate,
      beginUpdate,
      setField,
      discard,
      save,
      loadLatest,
      replaceWithLatest,
    }),
    [
      authorized,
      beginCreate,
      beginUpdate,
      dirty,
      discard,
      loadLatest,
      replaceWithLatest,
      save,
      setField,
      state,
      visibleDraft,
    ],
  );

  return (
    <BranchEditorContext.Provider value={value}>
      {children}
    </BranchEditorContext.Provider>
  );
}

export function useBranchEditor(): BranchEditorContextValue {
  const editor = useContext(BranchEditorContext);
  if (!editor) {
    throw new Error("Branch editing requires BranchEditorProvider.");
  }
  return editor;
}
