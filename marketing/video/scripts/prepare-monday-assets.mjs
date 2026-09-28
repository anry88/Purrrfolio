import {copyFile, mkdir} from "node:fs/promises";
import {dirname, resolve} from "node:path";
import {fileURLToPath} from "node:url";

const videoDirectory = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const repositoryRoot = resolve(videoDirectory, "../..");
const destination = resolve(videoDirectory, "public/cards/yarn-keeper.png");

await mkdir(dirname(destination), {recursive: true});
await copyFile(resolve(repositoryRoot, "assets/cards/yarn-keeper.png"), destination);
console.log("Prepared the Yarn Keeper card for the Monday composition.");
