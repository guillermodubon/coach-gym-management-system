import process from "node:process";
import { log } from "node:console";
import { readFile, writeFile, mkdir } from "node:fs/promises";
import openapiTS, { astToString } from "openapi-typescript";
import { validateSnapshot } from "./scope.mjs";

const contracts = [
  { scope: "f01", snapshot: "f01.openapi.json", output: "schema.d.ts" },
  {
    scope: "f02",
    snapshot: "f02.openapi.json",
    output: "f02-schema.d.ts",
  },
];
const checkOnly = process.argv.slice(2).includes("--check");
if (process.argv.slice(2).some((argument) => argument !== "--check")) {
  throw new Error("El único argumento permitido es --check.");
}
const directory = new URL("../../src/shared/api/generated/", import.meta.url);

for (const contract of contracts) {
  const snapshotUrl = new URL(
    `../../contracts/${contract.snapshot}`,
    import.meta.url,
  );
  const destination = new URL(contract.output, directory);
  const document = JSON.parse(await readFile(snapshotUrl, "utf8"));
  validateSnapshot(document, contract.scope);
  const generate = async () =>
    astToString(await openapiTS(document)).replaceAll("\r\n", "\n");
  const expected = await generate();

  if (checkOnly) {
    if ((await generate()) !== expected) {
      throw new Error(
        `La generación de ${contract.scope.toUpperCase()} no es reproducible.`,
      );
    }
    const existing = await readFile(destination, "utf8").catch((error) => {
      if (error.code === "ENOENT") return undefined;
      throw error;
    });
    if (existing !== undefined && existing !== expected) {
      throw new Error(
        `${contract.output} no coincide. Ejecuta npm run types:generate.`,
      );
    }
  } else {
    await mkdir(directory, { recursive: true });
    await writeFile(destination, expected, "utf8");
  }
}

log(
  checkOnly
    ? "Contratos F01/F02 y tipos reproducibles comprobados sin modificarlos."
    : "Tipos F01/F02 generados sin acceso a red.",
);
