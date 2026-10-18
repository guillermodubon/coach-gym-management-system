import process from "node:process";
import { log } from "node:console";
import { readFile, writeFile, mkdir } from "node:fs/promises";
import openapiTS, { astToString } from "openapi-typescript";
import { validateSnapshot } from "./scope.mjs";

const source = new URL("../../contracts/f01.openapi.json", import.meta.url);
const directory = new URL("../../src/shared/api/generated/", import.meta.url);
const destination = new URL("schema.d.ts", directory);
const document = JSON.parse(await readFile(source, "utf8"));
validateSnapshot(document);
const generate = async () =>
  astToString(await openapiTS(document)).replaceAll("\r\n", "\n");
const expected = await generate();
if (process.argv.includes("--check")) {
  if ((await generate()) !== expected)
    throw new Error("La generación no es reproducible.");
  const existing = await readFile(destination, "utf8").catch((error) => {
    if (error.code === "ENOENT") return undefined;
    throw error;
  });
  if (existing !== undefined && existing !== expected) {
    throw new Error(
      "Los tipos derivados no coinciden. Ejecuta npm run types:generate.",
    );
  }
}
await mkdir(directory, { recursive: true });
await writeFile(destination, expected, "utf8");
log(
  process.argv.includes("--check")
    ? "Contrato y tipos reproducibles comprobados."
    : "Tipos de F01 generados sin acceso a red.",
);
