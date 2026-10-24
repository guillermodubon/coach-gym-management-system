export const accountProfileQueryKey = (userId: string) =>
  ["private", "profile", userId] as const;

export const accountAvatarQueryKey = (userId: string) =>
  ["private", "profile-avatar", userId] as const;
