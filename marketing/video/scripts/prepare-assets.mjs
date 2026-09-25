import { copyFile, mkdir } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const videoDirectory = resolve(scriptDirectory, "..");
const repositoryRoot = resolve(videoDirectory, "../..");
const sourceDirectory = resolve(repositoryRoot, "assets/cards");
const targetDirectory = resolve(videoDirectory, "public/cards");
const selectedCards = [
  "sleepy.png", "tiny-gardener.png", "boxie.png",
  "sock-thief.png", "fireplace-friend.png", "pillow-fort.png",
];

await mkdir(targetDirectory, { recursive: true });

for (const filename of selectedCards) {
  await copyFile(
    resolve(sourceDirectory, filename),
    resolve(targetDirectory, filename),
  );
}

console.log(`Prepared ${selectedCards.length} card assets.`);
