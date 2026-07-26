#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";

function usage() {
  return `Usage:
  node normalize-regions.mjs --input SOURCE.geojson [--input SOURCE2.geojson] \\
    --output regions-xx.geojson \\
    --country-code XX --code-property CODE --name-property NAME \\
    --expected-count N [--mapping mapping.json] [--overseas-property PATH] \\
    [--default-overseas true|false] [--precision N] [--require-mapping] \\
    [--max-output-bytes N] [--dry-run]
`;
}

function fail(message) {
  throw new Error(message);
}

function parseArgs(argv) {
  const options = {};
  const booleanFlags = new Set(["dry-run", "help", "require-mapping"]);
  for (let index = 0; index < argv.length; index += 1) {
    const token = argv[index];
    if (!token.startsWith("--")) fail(`Argument inattendu : ${token}`);
    const key = token.slice(2);
    if (booleanFlags.has(key)) {
      options[key] = true;
      continue;
    }
    const value = argv[index + 1];
    if (value === undefined || value.startsWith("--")) {
      fail(`Valeur manquante pour --${key}`);
    }
    if (key === "input") {
      options.input ??= [];
      options.input.push(value);
    } else {
      options[key] = value;
    }
    index += 1;
  }
  return options;
}

function readJson(filePath, label) {
  let raw;
  try {
    raw = fs.readFileSync(filePath, "utf8");
  } catch (error) {
    fail(`${label} illisible (${filePath}) : ${error.message}`);
  }
  try {
    return JSON.parse(raw);
  } catch (error) {
    fail(`${label} JSON invalide (${filePath}) : ${error.message}`);
  }
}

function scalar(value) {
  if (Array.isArray(value)) return scalar(value[0]);
  return value;
}

function readProperty(feature, propertyPath) {
  const segments = propertyPath.split(".");
  let current;
  if (segments.length === 1) {
    current = feature.properties?.[propertyPath];
    if (current === undefined) current = feature[propertyPath];
  } else {
    current = feature;
    for (const segment of segments) current = current?.[segment];
  }
  return scalar(current);
}

function parseBoolean(value, label) {
  if (typeof value === "boolean") return value;
  if (value === "true" || value === "1" || value === 1) return true;
  if (value === "false" || value === "0" || value === 0) return false;
  fail(`${label} doit être un booléen, reçu : ${JSON.stringify(value)}`);
}

function roundNumber(value, precision) {
  if (precision === null) return value;
  const factor = 10 ** precision;
  return Math.round(value * factor) / factor;
}

function normalizePosition(position, precision, featureLabel) {
  if (!Array.isArray(position) || position.length < 2) {
    fail(`${featureLabel} contient une position invalide`);
  }
  const longitude = Number(position[0]);
  const latitude = Number(position[1]);
  if (!Number.isFinite(longitude) || !Number.isFinite(latitude)) {
    fail(`${featureLabel} contient une coordonnée non numérique`);
  }
  if (longitude < -180 || longitude > 180 || latitude < -90 || latitude > 90) {
    fail(
      `${featureLabel} n'est pas en WGS84 : [${longitude}, ${latitude}] est hors limites`,
    );
  }
  return [
    roundNumber(longitude, precision),
    roundNumber(latitude, precision),
  ];
}

function samePosition(first, last) {
  return first[0] === last[0] && first[1] === last[1];
}

function normalizeRing(ring, precision, featureLabel, stats) {
  if (!Array.isArray(ring) || ring.length < 4) {
    fail(`${featureLabel} contient un anneau de moins de quatre positions`);
  }
  const normalized = ring.map((position) => {
    stats.positions += 1;
    return normalizePosition(position, precision, featureLabel);
  });
  if (!samePosition(normalized[0], normalized.at(-1))) {
    fail(`${featureLabel} contient un anneau non fermé`);
  }
  return normalized;
}

function normalizePolygon(polygon, precision, featureLabel, stats) {
  if (!Array.isArray(polygon) || polygon.length === 0) {
    fail(`${featureLabel} contient un polygone vide`);
  }
  return polygon.map((ring) =>
    normalizeRing(ring, precision, featureLabel, stats),
  );
}

function normalizeGeometry(geometry, precision, featureLabel, stats) {
  if (!geometry || !["Polygon", "MultiPolygon"].includes(geometry.type)) {
    fail(`${featureLabel} doit utiliser Polygon ou MultiPolygon`);
  }
  if (geometry.type === "Polygon") {
    return {
      type: "Polygon",
      coordinates: normalizePolygon(
        geometry.coordinates,
        precision,
        featureLabel,
        stats,
      ),
    };
  }
  if (!Array.isArray(geometry.coordinates) || geometry.coordinates.length === 0) {
    fail(`${featureLabel} contient un MultiPolygon vide`);
  }
  return {
    type: "MultiPolygon",
    coordinates: geometry.coordinates.map((polygon) =>
      normalizePolygon(polygon, precision, featureLabel, stats),
    ),
  };
}

function required(options, key) {
  const value = options[key];
  if (!value) fail(`Option obligatoire manquante : --${key}`);
  return value;
}

function featuresFrom(source, label) {
  if (source.type === "FeatureCollection" && Array.isArray(source.features)) {
    return source.features;
  }
  if (source.type === "Feature") return [source];
  if (source.feature?.type === "Feature") return [source.feature];
  fail(`${label} doit contenir un Feature ou un FeatureCollection GeoJSON`);
}

function main() {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) {
    process.stdout.write(usage());
    return;
  }

  if (!Array.isArray(options.input) || options.input.length === 0) {
    fail("Option obligatoire manquante : --input");
  }
  const inputPaths = options.input.map((input) => path.resolve(input));
  const outputOption = options.output;
  if (!options["dry-run"] && !outputOption) {
    fail("Option obligatoire manquante : --output");
  }
  const countryCode = required(options, "country-code").toUpperCase();
  if (!/^[A-Z]{2}$/.test(countryCode)) {
    fail("--country-code doit être un code ISO alpha-2");
  }
  const codeProperty = required(options, "code-property");
  const nameProperty = required(options, "name-property");
  const expectedCount = Number(required(options, "expected-count"));
  if (!Number.isInteger(expectedCount) || expectedCount <= 0) {
    fail("--expected-count doit être un entier strictement positif");
  }
  let maxOutputBytes = null;
  if (options["max-output-bytes"] !== undefined) {
    maxOutputBytes = Number(options["max-output-bytes"]);
    if (!Number.isInteger(maxOutputBytes) || maxOutputBytes <= 0) {
      fail("--max-output-bytes doit être un entier strictement positif");
    }
  }

  let precision = null;
  if (options.precision !== undefined) {
    precision = Number(options.precision);
    if (!Number.isInteger(precision) || precision < 0 || precision > 12) {
      fail("--precision doit être un entier compris entre 0 et 12");
    }
  }

  const defaultOverseas = parseBoolean(
    options["default-overseas"] ?? false,
    "--default-overseas",
  );
  const mapping = options.mapping
    ? readJson(path.resolve(options.mapping), "Mapping")
    : {};
  if (mapping === null || Array.isArray(mapping) || typeof mapping !== "object") {
    fail("Le mapping doit être un objet JSON indexé par code source");
  }

  const sourceFeatures = inputPaths.flatMap((inputPath) =>
    featuresFrom(
      readJson(inputPath, "GeoJSON source"),
      `GeoJSON source (${inputPath})`,
    ),
  );
  if (sourceFeatures.length !== expectedCount) {
    fail(
      `Nombre de features inattendu : ${sourceFeatures.length}, attendu : ${expectedCount}`,
    );
  }

  const seenSourceCodes = new Set();
  const seenCodes = new Set();
  const stats = {positions: 0};
  const features = sourceFeatures.map((feature, index) => {
    const rawCodeValue = readProperty(feature, codeProperty);
    const rawNameValue = readProperty(feature, nameProperty);
    if (rawCodeValue === undefined || rawCodeValue === null) {
      fail(`Feature ${index + 1} : code absent (${codeProperty})`);
    }
    if (rawNameValue === undefined || rawNameValue === null) {
      fail(`Feature ${index + 1} : nom absent (${nameProperty})`);
    }
    const rawCode = String(rawCodeValue).trim();
    seenSourceCodes.add(rawCode);
    if (options["require-mapping"] && !(rawCode in mapping)) {
      fail(`Feature ${rawCode} : entrée absente du mapping obligatoire`);
    }
    const override = mapping[rawCode] ?? {};
    if (override === null || Array.isArray(override) || typeof override !== "object") {
      fail(`Mapping ${rawCode} : la valeur doit être un objet`);
    }
    const code = String(override.code ?? rawCode).trim();
    const name = String(override.name ?? rawNameValue).trim();
    if (!code) fail(`Feature ${index + 1} : code vide`);
    if (!name) fail(`Feature ${index + 1} : nom vide`);
    if (seenCodes.has(code)) fail(`Code de subdivision dupliqué : ${code}`);
    seenCodes.add(code);

    const overseas = options["overseas-property"]
      ? parseBoolean(
          readProperty(feature, options["overseas-property"]),
          `Feature ${code} : outreMer`,
        )
      : defaultOverseas;

    return {
      type: "Feature",
      properties: {
        countryCode,
        code,
        nom: name,
        outreMer: overseas,
      },
      geometry: normalizeGeometry(
        feature.geometry,
        precision,
        `Feature ${code}`,
        stats,
      ),
    };
  });

  features.sort((first, second) =>
    first.properties.code.localeCompare(second.properties.code, "en"),
  );
  const normalized = {type: "FeatureCollection", features};
  const serialized = `${JSON.stringify(normalized)}\n`;
  const unknownMappings = Object.keys(mapping).filter(
    (code) => !seenSourceCodes.has(code),
  );
  if (unknownMappings.length > 0) {
    fail(`Le mapping contient des codes absents de la source : ${unknownMappings.join(", ")}`);
  }
  const outputBytes = Buffer.byteLength(serialized, "utf8");
  if (maxOutputBytes !== null && outputBytes > maxOutputBytes) {
    fail(
      `Asset trop volumineux : ${outputBytes} octets, limite : ${maxOutputBytes}`,
    );
  }

  if (!options["dry-run"]) {
    const outputPath = path.resolve(outputOption);
    fs.mkdirSync(path.dirname(outputPath), {recursive: true});
    fs.writeFileSync(outputPath, serialized, "utf8");
  }

  process.stdout.write(
    [
      `Pays : ${countryCode}`,
      `Subdivisions : ${features.length}`,
      `Positions : ${stats.positions}`,
      `Taille : ${outputBytes} octets`,
      options["dry-run"] ? "Mode : validation seule" : `Sortie : ${path.resolve(outputOption)}`,
    ].join("\n") + "\n",
  );
}

try {
  main();
} catch (error) {
  process.stderr.write(`Erreur : ${error.message}\n`);
  process.exitCode = 1;
}
