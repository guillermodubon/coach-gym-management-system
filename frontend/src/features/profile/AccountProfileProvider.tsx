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
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useLocation } from "react-router";
import type { ApiError } from "../../shared/api/errors";
import { ApiError as SafeApiError } from "../../shared/api/errors";
import type { StaffSelfProfile } from "../../shared/api/profile-contracts";
import { useApiServices } from "../../shared/api/ApiServicesProvider";
import { useAuth } from "../auth/useAuth";
import { accountAvatarQueryKey, accountProfileQueryKey } from "./profile-query";
import {
  normalizeProfileName,
  validateProfileNames,
} from "./profile-validation";

export interface AccountProfileDraft {
  userId: string;
  baseVersion: number;
  baseFirstName: string;
  baseLastName: string;
  firstName: string;
  lastName: string;
}

export interface ProfileWriteOutcome<T> {
  acquired: boolean;
  value?: T;
}

export interface AccountProfileContextValue {
  profile: StaffSelfProfile | null;
  avatarProfile: Pick<
    StaffSelfProfile,
    "userId" | "displayName" | "photoPresent"
  > | null;
  draft: AccountProfileDraft | null;
  loading: boolean;
  readError: ApiError | null;
  dirty: boolean;
  conflict: boolean;
  saving: boolean;
  writeError: ApiError | null;
  setName(field: "firstName" | "lastName", value: string): void;
  save(): Promise<void>;
  discard(): void;
  reload(): Promise<StaffSelfProfile | null>;
  acceptCanonicalProfile(profile: StaffSelfProfile): boolean;
  runSerializedWrite<T>(
    operation: () => Promise<T>,
  ): Promise<ProfileWriteOutcome<T>>;
}

const AccountProfileContext = createContext<AccountProfileContextValue | null>(
  null,
);

function draftFromProfile(profile: StaffSelfProfile): AccountProfileDraft {
  return {
    userId: profile.userId,
    baseVersion: profile.version,
    baseFirstName: profile.firstName,
    baseLastName: profile.lastName,
    firstName: profile.firstName,
    lastName: profile.lastName,
  };
}

function isDirtyDraft(draft: AccountProfileDraft | null): boolean {
  return Boolean(
    draft &&
    (normalizeProfileName(draft.firstName) !== draft.baseFirstName ||
      normalizeProfileName(draft.lastName) !== draft.baseLastName),
  );
}

function toApiError(error: unknown): ApiError {
  return error instanceof SafeApiError
    ? error
    : new SafeApiError("invalid-response");
}

function isUncertainWrite(error: ApiError): boolean {
  return (
    error.kind === "network" ||
    error.kind === "timeout" ||
    error.kind === "invalid-response" ||
    (error.kind === "http" && (error.status ?? 0) >= 500)
  );
}

function mayNeedSessionRevalidation(error: ApiError): boolean {
  return (
    (error.status === 401 || error.status === 403) &&
    error.code !== "CSRF_TOKEN_INVALID"
  );
}

export function AccountProfileProvider({ children }: PropsWithChildren) {
  const auth = useAuth();
  const location = useLocation();
  const authRef = useRef(auth);
  authRef.current = auth;
  const queryClient = useQueryClient();
  const { profileApi } = useApiServices();
  const [draft, setDraft] = useState<AccountProfileDraft | null>(null);
  const [writeError, setWriteError] = useState<ApiError | null>(null);
  const [saving, setSaving] = useState(false);
  const writeLock = useRef(false);
  const lastActorId = useRef<string | null>(null);
  const lastRevalidationKey = useRef<string | null>(null);

  const authenticatedUser =
    auth.state.kind === "authenticated" ? auth.state.user : null;
  const canRead = Boolean(
    authenticatedUser && !auth.privacyCovered && auth.pendingAction === null,
  );
  const onProfileRoute = location.pathname === "/mi-perfil";
  const userId = authenticatedUser?.id ?? null;
  const avatarQuery = useQuery({
    queryKey: accountAvatarQueryKey(userId ?? "unavailable"),
    queryFn: async ({ signal }) => {
      if (!userId) throw new SafeApiError("invalid-request");
      const profile = await profileApi.getProfile({ signal });
      if (profile.userId !== userId) throw new SafeApiError("invalid-response");
      return {
        userId: profile.userId,
        displayName: profile.displayName,
        photoPresent: profile.photoPresent,
      };
    },
    enabled: canRead && !onProfileRoute,
    staleTime: 0,
    gcTime: 0,
    retry: false,
    refetchOnMount: true,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });
  const queryKey = accountProfileQueryKey(userId ?? "unavailable");
  const profileQuery = useQuery({
    queryKey,
    queryFn: async ({ signal }) => {
      if (!userId) throw new SafeApiError("invalid-request");
      const profile = await profileApi.getProfile({ signal });
      if (profile.userId !== userId) throw new SafeApiError("invalid-response");
      return profile;
    },
    enabled: canRead && onProfileRoute,
    staleTime: 0,
    gcTime: 0,
    retry: false,
    refetchOnMount: true,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });

  const currentProfile =
    canRead && profileQuery.data?.userId === userId ? profileQuery.data : null;
  const profile = onProfileRoute ? currentProfile : null;
  const avatarProfile = currentProfile
    ? {
        userId: currentProfile.userId,
        displayName: currentProfile.displayName,
        photoPresent: currentProfile.photoPresent,
      }
    : avatarQuery.data?.userId === userId
      ? avatarQuery.data
      : null;

  useEffect(() => {
    if (auth.pendingAction === "password") {
      setDraft(null);
      setWriteError(null);
    }
  }, [auth.pendingAction]);

  useEffect(() => {
    if (auth.state.kind === "checking") return;
    if (auth.state.kind === "authenticated") {
      const currentId = auth.state.user.id;
      if (lastActorId.current && lastActorId.current !== currentId) {
        setDraft(null);
        setWriteError(null);
      }
      lastActorId.current = currentId;
      return;
    }

    lastActorId.current = null;
    setDraft(null);
    setWriteError(null);
  }, [auth.state]);

  useEffect(() => {
    if (!profile) return;
    setDraft((current) => {
      if (
        !current ||
        current.userId !== profile.userId ||
        !isDirtyDraft(current)
      ) {
        return draftFromProfile(profile);
      }
      return current;
    });
  }, [
    profile?.userId,
    profile?.version,
    profile?.firstName,
    profile?.lastName,
  ]);

  useEffect(() => {
    const error = onProfileRoute ? profileQuery.error : avatarQuery.error;
    const errorUpdatedAt = onProfileRoute
      ? profileQuery.errorUpdatedAt
      : avatarQuery.errorUpdatedAt;
    if (!canRead || !(error instanceof SafeApiError) || !userId) return;
    if (!mayNeedSessionRevalidation(error)) return;
    const key = `${userId}:${errorUpdatedAt}`;
    if (lastRevalidationKey.current === key) return;
    lastRevalidationKey.current = key;
    void authRef.current.revalidate();
  }, [
    avatarQuery.error,
    avatarQuery.errorUpdatedAt,
    canRead,
    onProfileRoute,
    profileQuery.error,
    profileQuery.errorUpdatedAt,
    userId,
  ]);

  const currentActorIs = useCallback((expectedUserId: string) => {
    const state = authRef.current.state;
    if (state.kind === "authenticated") return state.user.id === expectedUserId;
    return state.kind === "checking" && lastActorId.current === expectedUserId;
  }, []);

  const runSerializedWrite = useCallback(
    async <T,>(
      operation: () => Promise<T>,
    ): Promise<ProfileWriteOutcome<T>> => {
      if (writeLock.current) return { acquired: false };
      writeLock.current = true;
      setSaving(true);
      try {
        return { acquired: true, value: await operation() };
      } finally {
        writeLock.current = false;
        setSaving(false);
      }
    },
    [],
  );

  const setName = useCallback(
    (field: "firstName" | "lastName", value: string) => {
      if (!profile || saving) return;
      setDraft((current) => {
        if (!current || current.userId !== profile.userId) {
          current = draftFromProfile(profile);
        }
        return { ...current, [field]: value };
      });
      setWriteError(null);
    },
    [profile, saving],
  );

  const discard = useCallback(() => {
    if (!profile) return;
    setDraft(draftFromProfile(profile));
    setWriteError(null);
  }, [profile]);

  const acceptCanonicalProfile = useCallback(
    (updated: StaffSelfProfile): boolean => {
      if (!userId || updated.userId !== userId || !currentActorIs(userId))
        return false;
      queryClient.setQueryData(accountProfileQueryKey(userId), updated);
      setDraft((current) =>
        current && isDirtyDraft(current) ? current : draftFromProfile(updated),
      );
      setWriteError(null);
      return true;
    },
    [currentActorIs, queryClient, userId],
  );

  const reload = useCallback(async (): Promise<StaffSelfProfile | null> => {
    if (!canRead || !userId) return null;
    const result = await profileQuery.refetch();
    if (
      result.isSuccess &&
      result.data?.userId === userId &&
      currentActorIs(userId)
    ) {
      setDraft(draftFromProfile(result.data));
      setWriteError(null);
      return result.data;
    }
    return null;
  }, [canRead, currentActorIs, profileQuery, userId]);

  const save = useCallback(async () => {
    if (!profile || !draft || !isDirtyDraft(draft) || saving) return;
    const expectedUserId = profile.userId;
    if (
      draft.userId !== expectedUserId ||
      profile.version !== draft.baseVersion
    ) {
      return;
    }

    const validated = validateProfileNames(draft);
    if (!validated.valid) return;
    setWriteError(null);

    await runSerializedWrite(async () => {
      try {
        const updated = await profileApi.updateProfile({
          firstName: validated.values.firstName,
          lastName: validated.values.lastName,
          version: draft.baseVersion,
        });
        if (
          !currentActorIs(expectedUserId) ||
          updated.userId !== expectedUserId
        )
          return;
        queryClient.setQueryData(
          accountProfileQueryKey(expectedUserId),
          updated,
        );
        setDraft(draftFromProfile(updated));
        setWriteError(null);
        void authRef.current.revalidate();
      } catch (caught) {
        if (!currentActorIs(expectedUserId)) return;
        const error = toApiError(caught);
        setWriteError(error);

        if (error.status === 409 || isUncertainWrite(error)) {
          try {
            const current = await profileApi.getProfile();
            if (
              current.userId !== expectedUserId ||
              !currentActorIs(expectedUserId)
            )
              return;
            queryClient.setQueryData(
              accountProfileQueryKey(expectedUserId),
              current,
            );
            if (
              isUncertainWrite(error) &&
              current.firstName === validated.values.firstName &&
              current.lastName === validated.values.lastName
            ) {
              setDraft(draftFromProfile(current));
              setWriteError(null);
              void authRef.current.revalidate();
            }
          } catch {
            // Keep the draft and safe write error; never replay an uncertain PUT.
          }
        }

        if (mayNeedSessionRevalidation(error)) {
          void authRef.current.revalidate();
        }
      }
    });
  }, [
    currentActorIs,
    draft,
    profile,
    profileApi,
    queryClient,
    runSerializedWrite,
    saving,
  ]);

  const visibleDraft =
    onProfileRoute && canRead && draft?.userId === userId ? draft : null;
  const value = useMemo<AccountProfileContextValue>(
    () => ({
      profile,
      avatarProfile,
      draft: visibleDraft,
      loading: onProfileRoute && canRead && profileQuery.isPending,
      readError:
        onProfileRoute && profileQuery.error instanceof SafeApiError
          ? profileQuery.error
          : null,
      dirty: Boolean(visibleDraft && isDirtyDraft(visibleDraft)),
      conflict: Boolean(
        visibleDraft && profile && profile.version !== visibleDraft.baseVersion,
      ),
      saving,
      writeError,
      setName,
      save,
      discard,
      reload,
      acceptCanonicalProfile,
      runSerializedWrite,
    }),
    [
      canRead,
      onProfileRoute,
      acceptCanonicalProfile,
      avatarProfile,
      discard,
      profile,
      profileQuery.error,
      profileQuery.isPending,
      reload,
      runSerializedWrite,
      save,
      saving,
      setName,
      visibleDraft,
      writeError,
    ],
  );

  return (
    <AccountProfileContext.Provider value={value}>
      {children}
    </AccountProfileContext.Provider>
  );
}

export function useAccountProfile(): AccountProfileContextValue {
  const value = useContext(AccountProfileContext);
  if (!value)
    throw new Error("Account profile requires AccountProfileProvider.");
  return value;
}
