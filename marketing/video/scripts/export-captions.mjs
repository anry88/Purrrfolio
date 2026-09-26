import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const videoDirectory = resolve(scriptDirectory, "..");
const runDirectory = resolve(
  videoDirectory,
  process.argv[2] ?? "../runs/youtube-short-001",
);
const sourceDirectory = resolve(runDirectory, "captions");
const outputDirectory = resolve(sourceDirectory, "exported");
const languages = ["en", "ru", "tr", "id"];
const manifest = JSON.parse(
  await readFile(resolve(runDirectory, "metadata.json"), "utf8"),
);
const durationMs = (manifest.creative?.duration_seconds ?? 15) * 1000;
let masterTimings;

const timestamp = (milliseconds, separator) => {
  const hours = Math.floor(milliseconds / 3_600_000);
  const minutes = Math.floor((milliseconds % 3_600_000) / 60_000);
  const seconds = Math.floor((milliseconds % 60_000) / 1000);
  const remainder = milliseconds % 1000;
  return `${String(hours).padStart(2, "0")}:${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}${separator}${String(remainder).padStart(3, "0")}`;
};

const validateCaptions = (captions, language) => {
  if (!Array.isArray(captions) || captions.length === 0) {
    throw new Error(`${language}: captions must be a non-empty array`);
  }
  let previousEnd = 0;
  for (const [index, caption] of captions.entries()) {
    if (
      typeof caption.text !== "string" ||
      !caption.text.trim() ||
      !Number.isInteger(caption.startMs) ||
      !Number.isInteger(caption.endMs) ||
      caption.startMs < previousEnd ||
      caption.endMs <= caption.startMs ||
      caption.endMs > durationMs ||
      caption.timestampMs !== null ||
      caption.confidence !== null
    ) {
      throw new Error(`${language}: invalid Caption at index ${index}`);
    }
    previousEnd = caption.endMs;
  }
};

await mkdir(outputDirectory, { recursive: true });

for (const language of languages) {
  const captions = JSON.parse(
    await readFile(resolve(sourceDirectory, `${language}.json`), "utf8"),
  );
  validateCaptions(captions, language);
  const timings = captions.map(({ startMs, endMs }) => [startMs, endMs]);
  if (language === "en") masterTimings = timings;
  else if (JSON.stringify(timings) !== JSON.stringify(masterTimings)) {
    throw new Error(`${language}: timing boundaries differ from English`);
  }

  const srt = captions
    .map(
      (caption, index) =>
        `${index + 1}\n${timestamp(caption.startMs, ",")} --> ${timestamp(caption.endMs, ",")}\n${caption.text}\n`,
    )
    .join("\n");
  const vtt = `WEBVTT\n\n${captions
    .map(
      (caption) =>
        `${timestamp(caption.startMs, ".")} --> ${timestamp(caption.endMs, ".")}\n${caption.text}\n`,
    )
    .join("\n")}`;

  await writeFile(resolve(outputDirectory, `${language}.srt`), srt, "utf8");
  await writeFile(resolve(outputDirectory, `${language}.vtt`), vtt, "utf8");
}

console.log(`Exported SRT and WebVTT tracks for ${languages.join(", ")}.`);
