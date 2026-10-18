import process from "node:process";
import { log } from "node:console";
import { writeFile, mkdir } from "node:fs/promises";
import { projectF01 } from "./scope.mjs";

const source = new URL(process.argv[2] ?? "http://127.0.0.1:8080/v3/api-docs");
if (
  source.protocol !== "http:" ||
  !["127.0.0.1", "localhost", "[::1]"].includes(source.hostname) ||
  source.pathname !== "/v3/api-docs" ||
  source.username ||
  source.password ||
  source.search ||
  source.hash
)
  throw new Error(
    "Usa /v3/api-docs de un backend local aislado, sin credenciales en la URL.",
  );

const response = await fetch(source, {
  redirect: "error",
  signal: AbortSignal.timeout(15_000),
});
if (!response.ok)
  throw new Error("No se pudo obtener OpenAPI del backend aislado.");
const snapshot = projectF01(await response.json());
const directory = new URL("../../contracts/", import.meta.url);
await mkdir(directory, { recursive: true });
await writeFile(
  new URL("f01.openapi.json", directory),
  JSON.stringify(snapshot, null, 2) + "\n",
  "utf8",
);
log(
  "Contrato de F01 capturado. Revisa el diff antes de aceptar una actualización.",
);
