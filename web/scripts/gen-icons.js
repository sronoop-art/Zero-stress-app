// Generates web/icons/*.png from an inline SVG of the NEXUS logo mark.
// Run: node web/scripts/gen-icons.js  (or: bun web/scripts/gen-icons.js)
// sharp is only a devDependency - the PWA itself has zero dependencies.
const sharp = require("sharp");
const fs = require("fs");

const SIZES = [512, 192, 180, 152, 167];
const OUT = __dirname + "/../icons";

// NEXUS mark: deep-space rounded square + cyan->violet ring + angular "ZS".
// Letterforms are drawn as STROKED PATHS (no <text>) so rendering never
// depends on system fonts being installed.
const svg = (size) => `
<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 512 512">
  <defs>
    <linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#20E7FF"/>
      <stop offset="1" stop-color="#8B5CFF"/>
    </linearGradient>
  </defs>
  <rect width="512" height="512" rx="112" fill="#050814"/>
  <circle cx="256" cy="256" r="176" fill="none" stroke="url(#g)" stroke-width="14" opacity="0.6"/>
  <g transform="translate(256 256)" stroke="url(#g)" stroke-width="17" fill="none"
     stroke-linecap="square" stroke-linejoin="miter">
    <!-- Z: top bar, diagonal, bottom bar -->
    <path d="M -96 -62 L -16 -62 L -96 62 L -16 62"/>
    <!-- S: squared double-arc -->
    <path d="M 96 -62 L 44 -62 A 31 31 0 0 0 44 0 L 76 0 A 31 31 0 0 1 76 62 L 24 62"/>
  </g>
</svg>`;

async function main() {
  fs.mkdirSync(OUT, { recursive: true });
  for (const s of SIZES) {
    await sharp(Buffer.from(svg(s))).resize(s, s).png().toFile(`${OUT}/icon-${s}.png`);
  }
  console.log("icons written to", OUT);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
