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
import { useQuery } from "@tanstack/react-query";
import type { ApiError } from "../../shared/api/errors";
import { ApiError as SafeApiError } from "../../shared/api/errors";
import { useApiServices } from "../../shared/api/ApiServicesProvider";
import { useAuth } from "../auth/useAuth";
import { useAccountProfile } from "./AccountProfileProvider";
import { validateProfilePhotoFile } from "./profile-photo-validation";

interface StagedPhoto {
  file: File;
  userId: string;
}

export interface ProfilePhotoContextValue {
  photoUrl: string | null;
  previewUrl: string | null;
  photoPresent: boolean;
  loading: boolean;
  readError: ApiError | null;
  writeError: ApiError | null;
  validationError: string | null;
  notice: string | null;
  dirty: boolean;
  saving: boolean;
  reconciliationRequired: boolean;
  selectedFileName: string | null;
  selectedFileSize: number | null;
  canSelect: boolean;
  canUpload: boolean;
  canRemove: boolean;
  selectFile(file: File): void;
  discardSelection(): void;
  upload(): Promise<boolean>;
  remove(): Promise<boolean>;
  retryDownload(): void;
  reconcile(): Promise<boolean>;
}

const ProfilePhotoContext = createContext<ProfilePhotoContextValue | null>(
  null,
);

const validationMessages = {
  empty: "La imagen está vacía. Elige otra foto.",
  "too-large": "La imagen no puede superar los 5 MiB.",
  "unsupported-type": "El formato debe ser JPG, PNG o WebP.",
} as const;

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

function useObjectUrl(blob: Blob | null): string | null {
  const [objectUrl, setObjectUrl] = useState<{
    blob: Blob;
    url: string;
  } | null>(null);

  useEffect(() => {
    if (!blob || typeof URL.createObjectURL !== "function") {
      setObjectUrl(null);
      return;
    }

    let url: string;
    try {
      url = URL.createObjectURL(blob);
    } catch {
      setObjectUrl(null);
      return;
    }
    setObjectUrl({ blob, url });
    return () => URL.revokeObjectURL(url);
  }, [blob]);

  return objectUrl?.blob === blob ? objectUrl.url : null;
}

export function ProfilePhotoProvider({ children }: PropsWithChildren) {
  const auth = useAuth();
  const authRef = useRef(auth);
  authRef.current = auth;
  const account = useAccountProfile();
  const { profileApi } = useApiServices();
  const [stagedPhoto, setStagedPhoto] = useState<StagedPhoto | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [writeError, setWriteError] = useState<ApiError | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [photoGeneration, setPhotoGeneration] = useState(0);
  const [reconciliationRequired, setReconciliationRequired] = useState(false);
  const lastActorId = useRef<string | null>(null);
  const lastRevalidationKey = useRef<string | null>(null);

  const authenticatedUserId =
    auth.state.kind === "authenticated" ? auth.state.user.id : null;
  const canUsePrivatePhoto = Boolean(
    authenticatedUserId &&
    !auth.privacyCovered &&
    auth.pendingAction === null &&
    account.avatarProfile?.userId === authenticatedUserId,
  );
  const userId = canUsePrivatePhoto ? authenticatedUserId : null;
  const photoPresent = Boolean(
    canUsePrivatePhoto && account.avatarProfile?.photoPresent,
  );
  const photoQuery = useQuery({
    queryKey: [
      "private",
      "profile-photo",
      userId ?? "unavailable",
      photoPresent,
      photoGeneration,
    ],
    queryFn: async ({ signal }) => {
      if (!userId) throw new SafeApiError("invalid-request");
      const blob = await profileApi.downloadPhoto({ signal });
      const state = authRef.current.state;
      if (state.kind !== "authenticated" || state.user.id !== userId) {
        throw new SafeApiError("cancelled");
      }
      return blob;
    },
    enabled: Boolean(userId && photoPresent),
    staleTime: Infinity,
    gcTime: 0,
    retry: false,
    refetchOnMount: false,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });

  const remoteBlob =
    userId && photoPresent && photoQuery.data ? photoQuery.data : null;
  const remotePhotoUrl = useObjectUrl(remoteBlob);
  const selectedFile =
    canUsePrivatePhoto && stagedPhoto?.userId === userId
      ? stagedPhoto.file
      : null;
  const previewUrl = useObjectUrl(selectedFile);
  const readError =
    photoPresent && photoQuery.error instanceof SafeApiError
      ? photoQuery.error
      : null;

  const currentActorIs = useCallback((expectedUserId: string) => {
    const state = authRef.current.state;
    return state.kind === "authenticated" && state.user.id === expectedUserId;
  }, []);

  useEffect(() => {
    if (auth.pendingAction !== "password") return;
    setStagedPhoto(null);
    setValidationError(null);
    setWriteError(null);
    setNotice(null);
    setReconciliationRequired(false);
    setPhotoGeneration((current) => current + 1);
  }, [auth.pendingAction]);

  useEffect(() => {
    if (auth.state.kind === "checking") return;
    if (auth.state.kind === "authenticated") {
      const currentId = auth.state.user.id;
      if (lastActorId.current && lastActorId.current !== currentId) {
        setStagedPhoto(null);
        setValidationError(null);
        setWriteError(null);
        setNotice(null);
        setReconciliationRequired(false);
        setPhotoGeneration((current) => current + 1);
      }
      lastActorId.current = currentId;
      return;
    }

    lastActorId.current = null;
    setStagedPhoto(null);
    setValidationError(null);
    setWriteError(null);
    setNotice(null);
    setReconciliationRequired(false);
    setPhotoGeneration((current) => current + 1);
  }, [auth.state]);

  useEffect(() => {
    const error = photoQuery.error;
    if (!userId || !(error instanceof SafeApiError)) return;
    if (!mayNeedSessionRevalidation(error)) return;
    const key = `${userId}:${photoQuery.errorUpdatedAt}`;
    if (lastRevalidationKey.current === key) return;
    lastRevalidationKey.current = key;
    void authRef.current.revalidate();
  }, [photoQuery.error, photoQuery.errorUpdatedAt, userId]);

  const selectFile = useCallback(
    (file: File) => {
      if (
        !userId ||
        !account.profile ||
        account.dirty ||
        account.saving ||
        reconciliationRequired
      )
        return;

      const failure = validateProfilePhotoFile(file);
      if (failure) {
        setValidationError(validationMessages[failure]);
        return;
      }

      setStagedPhoto({ file, userId });
      setValidationError(null);
      setWriteError(null);
      setNotice(null);
    },
    [
      account.dirty,
      account.profile,
      account.saving,
      reconciliationRequired,
      userId,
    ],
  );

  const discardSelection = useCallback(() => {
    if (account.saving) return;
    setStagedPhoto(null);
    setValidationError(null);
    setWriteError(null);
    setNotice(null);
  }, [account.saving]);

  const reconcileCanonicalProfile = useCallback(
    async (expectedUserId: string): Promise<boolean> => {
      try {
        const current = await profileApi.getProfile();
        if (
          current.userId !== expectedUserId ||
          !currentActorIs(expectedUserId) ||
          !account.acceptCanonicalProfile(current)
        )
          return false;
        setPhotoGeneration((generation) => generation + 1);
        setReconciliationRequired(false);
        return true;
      } catch (caught) {
        if (!currentActorIs(expectedUserId)) return false;
        const error = toApiError(caught);
        setWriteError(error);
        setReconciliationRequired(true);
        if (mayNeedSessionRevalidation(error)) {
          void authRef.current.revalidate();
        }
        return false;
      }
    },
    [account.acceptCanonicalProfile, currentActorIs, profileApi],
  );

  const reconcile = useCallback(async (): Promise<boolean> => {
    if (!userId || !reconciliationRequired || !canUsePrivatePhoto) return false;
    setWriteError(null);
    setNotice(null);
    const reconciled = await reconcileCanonicalProfile(userId);
    if (reconciled && currentActorIs(userId))
      setNotice("El perfil y la foto están actualizados.");
    return reconciled;
  }, [
    canUsePrivatePhoto,
    currentActorIs,
    reconcileCanonicalProfile,
    reconciliationRequired,
    userId,
  ]);

  const canSelect = Boolean(
    userId &&
    account.profile &&
    !account.saving &&
    !account.dirty &&
    !reconciliationRequired,
  );
  const canUpload = Boolean(
    canSelect && selectedFile && stagedPhoto?.userId === userId,
  );
  const canRemove = Boolean(
    canSelect && photoPresent && !selectedFile && !account.dirty,
  );

  const upload = useCallback(async (): Promise<boolean> => {
    const profile = account.profile;
    const file = selectedFile;
    if (!canUpload || !profile || !file || !userId || profile.userId !== userId)
      return false;

    const expectedUserId = userId;
    const version = profile.version;
    setWriteError(null);
    setNotice(null);

    const outcome = await account.runSerializedWrite(async () => {
      try {
        await profileApi.uploadPhoto(file, version);
        if (!currentActorIs(expectedUserId)) return false;
        setStagedPhoto(null);
        setValidationError(null);
        const reconciled = await reconcileCanonicalProfile(expectedUserId);
        if (!currentActorIs(expectedUserId)) return false;
        if (!reconciled) {
          setReconciliationRequired(true);
          return false;
        }
        setWriteError(null);
        setNotice("La foto de perfil se actualizó.");
        return true;
      } catch (caught) {
        if (!currentActorIs(expectedUserId)) return false;
        const error = toApiError(caught);
        setWriteError(error);
        if (error.status === 409 || isUncertainWrite(error)) {
          setReconciliationRequired(true);
          if (currentActorIs(expectedUserId)) {
            await reconcileCanonicalProfile(expectedUserId);
            if (currentActorIs(expectedUserId)) setWriteError(error);
          }
        }
        if (mayNeedSessionRevalidation(error)) {
          void authRef.current.revalidate();
        }
        return false;
      }
    });
    return outcome.acquired && outcome.value === true;
  }, [
    account.profile,
    account.runSerializedWrite,
    canUpload,
    currentActorIs,
    profileApi,
    reconcileCanonicalProfile,
    selectedFile,
    userId,
  ]);

  const remove = useCallback(async (): Promise<boolean> => {
    const profile = account.profile;
    if (!canRemove || !profile || !userId || profile.userId !== userId)
      return false;

    const expectedUserId = userId;
    const version = profile.version;
    setWriteError(null);
    setNotice(null);

    const outcome = await account.runSerializedWrite(async () => {
      try {
        const updated = await profileApi.removePhoto(version);
        if (
          updated.userId !== expectedUserId ||
          !currentActorIs(expectedUserId) ||
          !account.acceptCanonicalProfile(updated)
        )
          return false;
        setStagedPhoto(null);
        setValidationError(null);
        setPhotoGeneration((generation) => generation + 1);
        setWriteError(null);
        setNotice("La foto de perfil se eliminó.");
        return true;
      } catch (caught) {
        if (!currentActorIs(expectedUserId)) return false;
        const error = toApiError(caught);
        setWriteError(error);
        if (error.status === 409 || isUncertainWrite(error)) {
          setReconciliationRequired(true);
          if (currentActorIs(expectedUserId)) {
            await reconcileCanonicalProfile(expectedUserId);
            if (currentActorIs(expectedUserId)) setWriteError(error);
          }
        }
        if (mayNeedSessionRevalidation(error)) {
          void authRef.current.revalidate();
        }
        return false;
      }
    });
    return outcome.acquired && outcome.value === true;
  }, [
    account.acceptCanonicalProfile,
    account.profile,
    account.runSerializedWrite,
    canRemove,
    currentActorIs,
    profileApi,
    reconcileCanonicalProfile,
    userId,
  ]);

  const retryDownload = useCallback(() => {
    if (!photoPresent) return;
    setPhotoGeneration((generation) => generation + 1);
    setWriteError(null);
    setNotice(null);
  }, [photoPresent]);

  const value = useMemo<ProfilePhotoContextValue>(
    () => ({
      photoUrl: remotePhotoUrl,
      previewUrl,
      photoPresent,
      loading: Boolean(photoPresent && photoQuery.isPending),
      readError,
      writeError,
      validationError,
      notice,
      dirty: Boolean(selectedFile),
      saving: account.saving,
      reconciliationRequired,
      selectedFileName: selectedFile?.name ?? null,
      selectedFileSize: selectedFile?.size ?? null,
      canSelect,
      canUpload,
      canRemove,
      selectFile,
      discardSelection,
      upload,
      remove,
      retryDownload,
      reconcile,
    }),
    [
      account.saving,
      canRemove,
      canSelect,
      canUpload,
      discardSelection,
      notice,
      photoPresent,
      photoQuery.isPending,
      readError,
      reconcile,
      reconciliationRequired,
      remotePhotoUrl,
      remove,
      retryDownload,
      selectFile,
      selectedFile,
      upload,
      validationError,
      previewUrl,
      writeError,
    ],
  );

  return (
    <ProfilePhotoContext.Provider value={value}>
      {children}
    </ProfilePhotoContext.Provider>
  );
}

export function useProfilePhoto(): ProfilePhotoContextValue {
  const value = useContext(ProfilePhotoContext);
  if (!value) throw new Error("Profile photo requires ProfilePhotoProvider.");
  return value;
}
