export function safeReturnPath(candidate: unknown): "/" {
  if (typeof candidate !== "string" || candidate !== "/") return "/";
  return candidate;
}
