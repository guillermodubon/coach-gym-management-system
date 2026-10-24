import process from "node:process";
import { log } from "node:console";
import { writeFile, mkdir } from "node:fs/promises";
import prettier from "prettier";
import { projectF01, projectF02 } from "./scope.mjs";

const argumentsList = process.argv.slice(2);
let urlArgument;
let scope = "f01";
let scopeSeen = false;
for (const argument of argumentsList) {
  if (argument.startsWith("--scope=")) {
    if (scopeSeen || !["--scope=f01", "--scope=f02"].includes(argument))
      throw new Error("Elige un único scope: f01 o f02.");
    scopeSeen = true;
    scope = argument.slice("--scope=".length);
  } else if (!urlArgument && !argument.startsWith("--")) {
    urlArgument = argument;
  } else {
    throw new Error("Argumento de captura desconocido.");
  }
}

const source = new URL(urlArgument ?? "http://127.0.0.1:8080/v3/api-docs");
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
const project = scope === "f01" ? projectF01 : projectF02;
const snapshot = project(await response.json());
const directory = new URL("../../contracts/", import.meta.url);
await mkdir(directory, { recursive: true });
await writeFile(
  new URL(`${scope}.openapi.json`, directory),
  await prettier.format(JSON.stringify(snapshot), { parser: "json" }),
  "utf8",
);
log(
  `Contrato de ${scope.toUpperCase()} capturado. Revisa el diff antes de aceptar una actualización.`,
);
