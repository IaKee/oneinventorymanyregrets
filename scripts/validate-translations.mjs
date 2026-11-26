import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const root = path.resolve(import.meta.dirname, "..");
const langDir = path.join(
  root,
  "src",
  "main",
  "resources",
  "assets",
  "oneinventorymanyregrets",
  "lang",
);
const supportedFiles = new Set(["en_us.json", "pt_br.json"]);
const strictAll = process.argv.includes("--strict-all");
const placeholderPattern = /%%|%(?:(\d+)\$)?([a-zA-Z])/g;

function placeholders(value) {
  const result = [];
  let implicitIndex = 1;
  for (const match of value.matchAll(placeholderPattern)) {
    if (match[0] === "%%") continue;
    const index = match[1] === undefined ? implicitIndex++ : Number(match[1]);
    result.push(`${index}:${match[2].toLowerCase()}`);
  }
  return result.sort();
}

function sameList(left, right) {
  return left.length === right.length && left.every((value, index) => value === right[index]);
}

const errors = [];
const warnings = [];
const incompleteDrafts = [];
const documents = new Map();
const files = fs.readdirSync(langDir).filter((file) => file.endsWith(".json")).sort();

for (const file of files) {
  const filePath = path.join(langDir, file);
  try {
    const parsed = JSON.parse(fs.readFileSync(filePath, "utf8"));
    if (parsed === null || Array.isArray(parsed) || typeof parsed !== "object") {
      errors.push(`${file}: the JSON root must be an object`);
      continue;
    }
    for (const [key, value] of Object.entries(parsed)) {
      if (typeof value !== "string") errors.push(`${file}: ${key} must map to a string`);
    }
    documents.set(file, parsed);
  } catch (error) {
    errors.push(`${file}: invalid JSON (${error.message})`);
  }
}

for (const file of supportedFiles) {
  if (!documents.has(file)) errors.push(`${file}: required supported language is missing or invalid`);
}

const reference = documents.get("en_us.json") ?? {};
const referenceKeys = Object.keys(reference).sort();

for (const [file, document] of documents) {
  const keys = Object.keys(document);
  const missing = referenceKeys.filter((key) => !(key in document));
  const extra = keys.filter((key) => !(key in reference)).sort();
  const mustBeComplete = supportedFiles.has(file) || strictAll;

  if (missing.length > 0) {
    const message = `${file}: missing ${missing.length} key(s): ${missing.join(", ")}`;
    if (mustBeComplete) errors.push(message);
    else incompleteDrafts.push({ file, missing, extra });
  }
  if (extra.length > 0) {
    const message = `${file}: has ${extra.length} key(s) absent from en_us.json: ${extra.join(", ")}`;
    if (mustBeComplete) errors.push(message);
    else if (missing.length === 0) incompleteDrafts.push({ file, missing, extra });
  }

  for (const key of referenceKeys) {
    if (!(key in document) || typeof document[key] !== "string" || typeof reference[key] !== "string") continue;
    const expected = placeholders(reference[key]);
    const actual = placeholders(document[key]);
    if (!sameList(expected, actual)) {
      errors.push(
        `${file}: placeholder mismatch for ${key}; expected [${expected.join(", ")}], got [${actual.join(", ")}]`,
      );
    }
  }
}

if (warnings.length > 0) {
  console.warn(`Experimental translation warnings (${warnings.length}):`);
  for (const warning of warnings) console.warn(`- ${warning}`);
}

if (incompleteDrafts.length > 0) {
  const groups = new Map();
  for (const draft of incompleteDrafts) {
    const signature = JSON.stringify({ missing: draft.missing, extra: draft.extra });
    const group = groups.get(signature) ?? { ...draft, files: [] };
    group.files.push(draft.file);
    groups.set(signature, group);
  }
  console.warn(`Experimental coverage warnings (${incompleteDrafts.length} file(s), ${groups.size} pattern(s)):`);
  for (const group of groups.values()) {
    const sample = group.files.slice(0, 8).join(", ");
    const remainder = group.files.length > 8 ? `, and ${group.files.length - 8} more` : "";
    console.warn(
      `- ${group.files.length} file(s) [${sample}${remainder}]: ` +
        `${group.missing.length} missing key(s), ${group.extra.length} extra key(s)`,
    );
  }
}

if (errors.length > 0) {
  console.error(`Translation validation failed (${errors.length} error(s)):`);
  for (const error of errors) console.error(`- ${error}`);
  process.exitCode = 1;
} else {
  console.log(
    `Translation validation passed: ${files.length} valid JSON file(s), ${referenceKeys.length} reference key(s), ` +
      `${supportedFiles.size} complete supported language(s).`,
  );
  if (!strictAll) {
    console.log("Experimental drafts are syntax/placeholder checked but may be incomplete; use --strict-all to require full coverage.");
  }
}
