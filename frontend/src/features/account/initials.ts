import type { CurrentUser } from "../../shared/api/contracts";
import { getAccountDisplayName } from "./account-labels";

function firstLetter(word: string | undefined): string | null {
  if (!word) return null;
  return Array.from(word).find((character) => /\p{L}/u.test(character)) ?? null;
}

export function getAccountInitials(user: CurrentUser): string {
  return getInitialsForName(getAccountDisplayName(user));
}

export function getInitialsForName(displayName: string): string {
  const words = displayName.trim().split(/\s+/u).filter(Boolean);
  const letteredWords = words
    .map((word) => firstLetter(word))
    .filter((letter): letter is string => letter !== null);
  const first = letteredWords[0];
  if (!first) return "?";

  const last = letteredWords.length > 1 ? letteredWords.at(-1) : null;
  const initials = [first, ...(last ? [last] : [])]
    .join("")
    .toLocaleUpperCase("es");
  return Array.from(initials).slice(0, 2).join("") || "?";
}
