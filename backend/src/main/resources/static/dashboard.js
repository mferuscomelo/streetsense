// Cell size mirrors backend/.../domain/GridCell.java and
// app/.../location/GridCell.java — three independent implementations of the
// same constant now, same discipline as the BLE UUIDs in docs/ble-protocol.md.
// A drift here is cosmetic (misdrawn rectangles), not a privacy break like a
// drift on the other two would be, but it's still one constant, kept equal.
const CELL_SIZE_DEGREES = 0.001;
const PM_SCALE_MAX = 100; // µg/m³ at which the sequential ramp saturates
const FALLBACK_CENTER = [49.0069, 8.4037]; // used when geolocation is denied/unavailable/disabled
const THEME_KEY = 'streetsense-theme';

const TILES = {
  light: 'https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png',
  dark: 'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png',
};

const STATUS_COLOR = {
  NORMAL: 'var(--status-good)',
  TRAFFIC_PLUME: 'var(--status-warning)',
  SOLVENT: 'var(--status-serious)',
  SMOKE_OR_EXHAUST: 'var(--status-critical)',
  LOUD_BUT_CLEAN: 'var(--muted-foreground)',
};

// Every pollutant channel a reading carries. pm2_5 is the hero by default;
// clicking any other metric card promotes it into the hero slot.
const METRICS = {
  pm2_5: { label: 'PM2.5', unit: 'µg/m³', decimals: 1 },
  pm1: { label: 'PM1.0', unit: 'µg/m³', decimals: 1 },
  pm4: { label: 'PM4.0', unit: 'µg/m³', decimals: 1 },
  pm10: { label: 'PM10', unit: 'µg/m³', decimals: 1 },
  vocIndex: { label: 'VOC', unit: 'idx', decimals: 0 },
  noiseDb: { label: 'Noise', unit: 'dB(A)', decimals: 1 },
  tempC: { label: 'Temp', unit: '°C', decimals: 1 },
  humidity: { label: 'Humidity', unit: '%', decimals: 0 },
};
const SECONDARY_METRIC_KEYS = ['pm1', 'pm4', 'pm10', 'vocIndex', 'noiseDb', 'tempC', 'humidity'];

// Module state. Cells/markers are kept at this scope, not local to any one
// function, so a zoomend resize can update marker icons in place instead of
// tearing everything down — and so an SSE-driven cell refresh can find the
// right marker to update without redrawing the whole layer.
let map = null;
let activeTileLayer = null;
let cellsData = [];
let cellMarkers = [];
let openCellKey = null; // { latBucket, lonBucket } of whatever cell panel is open, or null
let heroMetricKey = 'pm2_5';
let seqRampRgb = null; // cached [ [r,g,b] low, [r,g,b] high ], invalidated on theme change

// --- Theme ---------------------------------------------------------------

function currentTheme() {
  return document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light';
}

function setTheme(theme) {
  document.documentElement.dataset.theme = theme;
  localStorage.setItem(THEME_KEY, theme);
  seqRampRgb = null;
  if (map) {
    swapTileLayer(theme);
    if (cellsData.length) drawCellIcons({ forceRebuild: true });
  }
}

function initThemeToggle() {
  document.getElementById('themeToggle').addEventListener('click', () => {
    setTheme(currentTheme() === 'dark' ? 'light' : 'dark');
  });
}

function swapTileLayer(theme) {
  if (activeTileLayer) map.removeLayer(activeTileLayer);
  activeTileLayer = L.tileLayer(TILES[theme], {
    attribution: '&copy; OpenStreetMap contributors &copy; CARTO',
    subdomains: 'abcd',
    maxZoom: 20,
  }).addTo(map);
}

// --- Color -----------------------------------------------------------------

function hexToRgb(hex) {
  const h = hex.trim().replace('#', '');
  return [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16)];
}

function seqRamp() {
  if (!seqRampRgb) {
    const style = getComputedStyle(document.documentElement);
    seqRampRgb = [
      hexToRgb(style.getPropertyValue('--seq-100')),
      hexToRgb(style.getPropertyValue('--seq-700')),
    ];
  }
  return seqRampRgb;
}

function pmColor(meanPm25) {
  const [low, high] = seqRamp();
  const t = Math.max(0, Math.min(1, meanPm25 / PM_SCALE_MAX));
  const r = Math.round(low[0] + (high[0] - low[0]) * t);
  const g = Math.round(low[1] + (high[1] - low[1]) * t);
  const b = Math.round(low[2] + (high[2] - low[2]) * t);
  return `rgb(${r}, ${g}, ${b})`;
}

// --- Cell geometry -----------------------------------------------------

function cellBounds(cell) {
  const south = cell.latBucket * CELL_SIZE_DEGREES;
  const north = south + CELL_SIZE_DEGREES;
  const west = cell.lonBucket * CELL_SIZE_DEGREES;
  const east = west + CELL_SIZE_DEGREES;
  return [[south, west], [north, east]];
}

// Web Mercator's y-axis scales by sec(latitude) relative to x, so a cell's
// true geographic bounds render taller than wide the further from the
// equator you are — widening the longitude span to compensate would make
// neighboring cells overlap. Instead, cells are drawn as fixed-pixel
// squares: this measures the true north-south pixel span (latitude-correct,
// longitude-independent) and reuses it as both the width and height.
function cellPixelSize(cell) {
  const bounds = cellBounds(cell);
  const pSouth = map.latLngToContainerPoint(bounds[0]);
  const pNorth = map.latLngToContainerPoint(bounds[1]);
  return Math.max(2, Math.round(Math.abs(pNorth.y - pSouth.y)));
}

function cellCenter(cell) {
  const bounds = cellBounds(cell);
  return [(bounds[0][0] + bounds[1][0]) / 2, (bounds[0][1] + bounds[1][1]) / 2];
}

function formatHour(h) {
  return h == null || h < 0 ? '—' : String(h).padStart(2, '0') + ':00';
}

function formatValue(v, decimals) {
  return v.toFixed(decimals);
}

function el(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text != null) node.textContent = text;
  return node;
}

// --- Map / cell markers --------------------------------------------------

function renderCells(cells) {
  cellsData = cells;
  drawCellIcons({ forceRebuild: true });
  updateStats(cellsData);
}

function updateStats(cells) {
  const stat = (id, value) => { document.getElementById(id).textContent = value; };
  stat('statCells', cells.length);
  stat('statCorroborated', cells.filter(c => c.confidence === 'CORROBORATED').length);
  stat('statSingle', cells.filter(c => c.confidence === 'SINGLE_CONTRIBUTOR').length);
  stat('statSeeded', cells.some(c => c.hasSeededData) ? 'yes' : 'no');
}

// A reading's own cell summary (sample count, confidence, means) isn't in
// the SSE payload — only the decoded reading is. So a new/changed cell is
// pulled from its dedicated endpoint and merged in, rather than trying to
// derive a CellSummary client-side from a stream of individual readings.
function upsertCell(cell) {
  const idx = cellsData.findIndex(c => c.latBucket === cell.latBucket && c.lonBucket === cell.lonBucket);
  if (idx === -1) {
    cellsData.push(cell);
  } else {
    cellsData[idx] = cell;
  }
  drawCellIcons();
  updateStats(cellsData);
  refreshOpenPanelIfMatches(cell.latBucket, cell.lonBucket);
}

function refreshCell(latBucket, lonBucket) {
  fetch(`/api/v1/cells/${latBucket}/${lonBucket}`)
    .then((res) => res.json())
    .then((cell) => upsertCell(cell))
    .catch(() => {
      // Live feed row already showed the reading; a missed grid refresh
      // isn't worth surfacing as an error — the next reading retries it.
    });
}

function drawCellIcons({ forceRebuild = false } = {}) {
  const canUpdateInPlace = !forceRebuild && cellMarkers.length === cellsData.length;

  if (!canUpdateInPlace) {
    for (const marker of cellMarkers) map.removeLayer(marker);
    cellMarkers = [];
  }

  cellsData.forEach((cell, i) => {
    const size = cellPixelSize(cell);
    const borderColor = cell.confidence === 'CORROBORATED' ? 'var(--foreground)' : 'var(--muted-foreground)';
    const borderWidth = cell.confidence === 'CORROBORATED' ? 2 : 1;
    const borderStyle = cell.confidence === 'SINGLE_CONTRIBUTOR' ? 'dashed' : 'solid';
    const fillOpacity = cell.confidence === 'NO_DATA' ? 0.15 : cell.confidence === 'SINGLE_CONTRIBUTOR' ? 0.45 : 0.75;

    const icon = L.divIcon({
      className: 'cell-icon',
      iconSize: [size, size],
      iconAnchor: [size / 2, size / 2],
      html: `<div style="width:${size}px;height:${size}px;box-sizing:border-box;`
          + `background:${pmColor(cell.meanPm2_5)};opacity:${fillOpacity};`
          + `border:${borderWidth}px ${borderStyle} ${borderColor};"></div>`,
    });

    if (canUpdateInPlace) {
      cellMarkers[i].setIcon(icon);
    } else {
      const marker = L.marker(cellCenter(cell), { icon }).addTo(map);
      marker.on('click', () => openCellPanel(cell.latBucket, cell.lonBucket));
      cellMarkers.push(marker);
    }
  });
}

function initMap(center) {
  map = L.map('map', { scrollWheelZoom: true, zoomControl: true }).setView(center, 15);
  swapTileLayer(currentTheme());
  map.on('zoomend', () => drawCellIcons());
}

// --- Geolocation-driven centering + seeding -------------------------------

// Checking /api/v1/seed/status first means a real (non-demo) deployment
// never prompts a visitor for their location when there's nothing to seed.
async function resolveCenter() {
  let status = { enabled: false };
  try {
    status = await fetch('/api/v1/seed/status').then((res) => res.json());
  } catch {
    // Backend unreachable — the /api/v1/cells fetch in boot() will surface
    // that error to the live-feed label; fall through to the default center.
  }

  if (!status.enabled || !('geolocation' in navigator)) {
    return FALLBACK_CENTER;
  }

  try {
    const pos = await new Promise((resolve, reject) =>
        navigator.geolocation.getCurrentPosition(resolve, reject, { timeout: 5000 }));
    const center = [pos.coords.latitude, pos.coords.longitude];
    // Fire-and-forget: the map should center and start loading /api/v1/cells
    // immediately, with the seeded cells simply appearing once the backend
    // finishes writing them (or on the next SSE tick).
    fetch(`/api/v1/seed?lat=${center[0]}&lon=${center[1]}`, { method: 'POST' }).catch(() => {});
    return center;
  } catch {
    return FALLBACK_CENTER;
  }
}

// --- Cell detail side panel ------------------------------------------------

function openCellPanel(latBucket, lonBucket) {
  openCellKey = { latBucket, lonBucket };
  heroMetricKey = 'pm2_5';
  const panel = document.getElementById('cellPanel');
  panel.hidden = false;
  requestAnimationFrame(() => panel.classList.add('open'));
  // The panel is wider than the live feed's offset from the right edge, so
  // without this the panel would silently bury the feed behind it.
  document.getElementById('feedOverlay').classList.add('panel-open');
  renderPanelMessage(`Cell ${latBucket}, ${lonBucket}`, 'Loading…');
  fetchAndRenderPanel(latBucket, lonBucket);
}

function closeCellPanel() {
  const panel = document.getElementById('cellPanel');
  panel.classList.remove('open');
  document.getElementById('feedOverlay').classList.remove('panel-open');
  openCellKey = null;
  setTimeout(() => {
    if (!panel.classList.contains('open')) panel.hidden = true;
  }, 220);
}

function fetchAndRenderPanel(latBucket, lonBucket) {
  fetch(`/api/v1/cells/${latBucket}/${lonBucket}/detail`)
    .then((res) => res.json())
    .then((detail) => renderDetailPanel(detail))
    .catch(() => renderPanelMessage(`Cell ${latBucket}, ${lonBucket}`, "Couldn't load this cell — try again."));
}

function refreshOpenPanelIfMatches(latBucket, lonBucket) {
  if (openCellKey && openCellKey.latBucket === latBucket && openCellKey.lonBucket === lonBucket) {
    fetchAndRenderPanel(latBucket, lonBucket);
  }
}

function panelCloseButton() {
  const btn = el('button', 'icon-button panel-close', '✕');
  btn.type = 'button';
  btn.setAttribute('aria-label', 'Close');
  btn.addEventListener('click', closeCellPanel);
  return btn;
}

function renderPanelMessage(title, message) {
  const panel = document.getElementById('cellPanel');
  panel.replaceChildren();

  const header = el('div', 'panel-header');
  const headerText = el('div', 'panel-header-text');
  headerText.appendChild(el('h2', 'panel-title', title));
  header.appendChild(headerText);
  header.appendChild(panelCloseButton());
  panel.appendChild(header);

  const body = el('div', 'panel-body');
  body.appendChild(el('p', 'panel-empty', message));
  panel.appendChild(body);
}

function renderDetailPanel(detail) {
  const panel = document.getElementById('cellPanel');
  panel.replaceChildren();

  const summary = detail.summary;

  const header = el('div', 'panel-header');
  const headerText = el('div', 'panel-header-text');
  headerText.appendChild(el('h2', 'panel-title', `Cell ${detail.latBucket}, ${detail.lonBucket}`));

  const pillClass = summary.confidence === 'CORROBORATED' ? 'corroborated' : 'single';
  const pill = el('span', `confidence-pill ${pillClass}`);
  pill.appendChild(el('span', 'dot'));
  pill.appendChild(document.createTextNode(
      summary.confidence === 'CORROBORATED' ? 'Corroborated'
      : summary.confidence === 'SINGLE_CONTRIBUTOR' ? 'Single contributor'
      : 'Not enough data'));
  headerText.appendChild(pill);
  header.appendChild(headerText);
  header.appendChild(panelCloseButton());
  panel.appendChild(header);

  if (summary.hasSeededData) {
    panel.appendChild(el('div', 'seeded-callout',
        `Includes ${summary.seededContributorCount} seeded (synthetic) contributor`
        + `${summary.seededContributorCount === 1 ? '' : 's'}`));
  }

  const body = el('div', 'panel-body');
  panel.appendChild(body);

  if (summary.sampleCount === 0) {
    body.appendChild(el('p', 'panel-empty', 'No readings for this block yet.'));
    return;
  }

  const statRow = el('div', 'panel-stat-row');
  statRow.appendChild(buildPanelStat(
      String(summary.sampleCount),
      `reading${summary.sampleCount === 1 ? '' : 's'} · ${summary.contributorCount} contributor${summary.contributorCount === 1 ? '' : 's'}`));
  statRow.appendChild(buildPanelStat(
      `${formatHour(summary.cleanestHour)} / ${formatHour(summary.quietestHour)}`,
      'cleanest / quietest hour'));
  body.appendChild(statRow);

  body.appendChild(buildHeroSection(detail));
  body.appendChild(buildMetricGrid(detail));
  body.appendChild(buildSessionsSection(detail));
}

function buildPanelStat(value, label) {
  const div = el('div', 'panel-stat');
  div.appendChild(el('div', 'value', value));
  div.appendChild(el('div', 'label', label));
  return div;
}

function hourlyPoints(hourly, key) {
  return hourly
      .filter((h) => h.sampleCount > 0)
      .map((h) => ({
        hour: h.hourOfDay,
        value: h.means[key],
        sampleCount: h.sampleCount,
        contributorCount: h.contributorCount,
      }));
}

// --- Hero chart (one pollutant, 24h, sequential ramp, hover + table twin) --

function buildHeroSection(detail) {
  const meta = METRICS[heroMetricKey];
  const points = hourlyPoints(detail.hourly, heroMetricKey);
  const currentMean = detail.means[heroMetricKey];

  const section = el('div');
  const titleRow = el('div', 'panel-section-title');
  titleRow.appendChild(document.createTextNode(`${meta.label} over the day`));

  const card = el('div', 'hero-chart-card');
  const valueRow = el('div');
  valueRow.appendChild(el('span', 'hero-chart-value', formatValue(currentMean, meta.decimals)));
  valueRow.appendChild(document.createTextNode(' '));
  valueRow.appendChild(el('span', 'hero-chart-unit', meta.unit));
  card.appendChild(valueRow);

  const chartWrap = el('div', 'hero-chart-wrap');
  const tableWrap = el('div', 'hidden');
  card.appendChild(chartWrap);
  card.appendChild(tableWrap);

  if (points.length === 0) {
    chartWrap.appendChild(el('p', 'panel-empty', 'Not enough readings yet to chart.'));
  } else {
    const { svg, tooltip } = buildHeroChart(points, meta);
    chartWrap.appendChild(svg);
    chartWrap.appendChild(tooltip);
    tableWrap.appendChild(buildChartTable(points, meta));

    const tableToggle = el('button', 'chart-table-toggle', 'View as table');
    tableToggle.type = 'button';
    tableToggle.addEventListener('click', () => {
      const showingTable = tableWrap.classList.contains('hidden');
      tableWrap.classList.toggle('hidden', !showingTable);
      chartWrap.classList.toggle('hidden', showingTable);
      tableToggle.textContent = showingTable ? 'View as chart' : 'View as table';
    });
    titleRow.appendChild(tableToggle);
  }

  section.appendChild(titleRow);
  section.appendChild(card);
  return section;
}

function buildHeroChart(points, meta) {
  const width = 300, height = 96, padX = 6, padY = 12;
  const values = points.map((p) => p.value);
  let min = Math.min(...values), max = Math.max(...values);
  if (min === max) { min -= 1; max += 1; }

  const scaleX = (hour) => padX + (hour / 23) * (width - 2 * padX);
  const scaleY = (v) => height - padY - ((v - min) / (max - min)) * (height - 2 * padY);

  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('viewBox', `0 0 ${width} ${height}`);
  svg.classList.add('hero-chart-svg');
  const ns = svg.namespaceURI;

  [0.25, 0.75].forEach((f) => {
    const y = padY + f * (height - 2 * padY);
    const line = document.createElementNS(ns, 'line');
    line.setAttribute('x1', padX); line.setAttribute('x2', width - padX);
    line.setAttribute('y1', y); line.setAttribute('y2', y);
    line.setAttribute('stroke', 'var(--border)'); line.setAttribute('stroke-width', '1');
    svg.appendChild(line);
  });

  [0, 6, 12, 18, 23].forEach((hour) => {
    const text = document.createElementNS(ns, 'text');
    text.setAttribute('x', scaleX(hour));
    text.setAttribute('y', height - 1);
    text.setAttribute('font-size', '8');
    text.setAttribute('fill', 'var(--muted-foreground)');
    text.setAttribute('text-anchor', hour === 0 ? 'start' : hour === 23 ? 'end' : 'middle');
    text.textContent = formatHour(hour);
    svg.appendChild(text);
  });

  if (points.length === 1) {
    const p = points[0];
    const dot = document.createElementNS(ns, 'circle');
    dot.setAttribute('cx', scaleX(p.hour)); dot.setAttribute('cy', scaleY(p.value));
    dot.setAttribute('r', 4); dot.setAttribute('fill', 'var(--seq-700)');
    svg.appendChild(dot);
  } else {
    const linePath = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${scaleX(p.hour).toFixed(1)},${scaleY(p.value).toFixed(1)}`).join(' ');
    const areaPath = `${linePath} L${scaleX(points[points.length - 1].hour).toFixed(1)},${(height - padY).toFixed(1)}`
        + ` L${scaleX(points[0].hour).toFixed(1)},${(height - padY).toFixed(1)} Z`;

    const area = document.createElementNS(ns, 'path');
    area.setAttribute('d', areaPath);
    area.setAttribute('fill', 'var(--seq-100)');
    area.setAttribute('opacity', '0.35');
    svg.appendChild(area);

    const line = document.createElementNS(ns, 'path');
    line.setAttribute('d', linePath);
    line.setAttribute('fill', 'none');
    line.setAttribute('stroke', 'var(--seq-700)');
    line.setAttribute('stroke-width', '2');
    line.setAttribute('stroke-linecap', 'round');
    line.setAttribute('stroke-linejoin', 'round');
    svg.appendChild(line);

    const last = points[points.length - 1];
    const endCap = document.createElementNS(ns, 'circle');
    endCap.setAttribute('cx', scaleX(last.hour)); endCap.setAttribute('cy', scaleY(last.value));
    endCap.setAttribute('r', 4);
    endCap.setAttribute('fill', 'var(--seq-700)');
    svg.appendChild(endCap);
  }

  const crosshair = document.createElementNS(ns, 'line');
  crosshair.setAttribute('y1', padY); crosshair.setAttribute('y2', height - padY);
  crosshair.setAttribute('stroke', 'var(--muted-foreground)');
  crosshair.setAttribute('stroke-width', '1');
  crosshair.style.opacity = '0';
  svg.appendChild(crosshair);

  const hoverDot = document.createElementNS(ns, 'circle');
  hoverDot.setAttribute('r', 4);
  hoverDot.setAttribute('fill', 'var(--seq-700)');
  hoverDot.style.opacity = '0';
  svg.appendChild(hoverDot);

  const tooltip = el('div', 'hero-tooltip');

  const hitRect = document.createElementNS(ns, 'rect');
  hitRect.setAttribute('x', '0'); hitRect.setAttribute('y', '0');
  hitRect.setAttribute('width', String(width)); hitRect.setAttribute('height', String(height));
  hitRect.setAttribute('fill', 'transparent');
  hitRect.addEventListener('pointermove', (e) => {
    const rect = svg.getBoundingClientRect();
    const xFrac = (e.clientX - rect.left) / rect.width;
    const hour = Math.max(0, Math.min(23, xFrac * 23));
    let nearest = points[0];
    for (const p of points) {
      if (Math.abs(p.hour - hour) < Math.abs(nearest.hour - hour)) nearest = p;
    }
    const cx = scaleX(nearest.hour), cy = scaleY(nearest.value);
    crosshair.setAttribute('x1', String(cx)); crosshair.setAttribute('x2', String(cx));
    crosshair.style.opacity = '1';
    hoverDot.setAttribute('cx', String(cx)); hoverDot.setAttribute('cy', String(cy));
    hoverDot.style.opacity = '1';

    tooltip.textContent = `${formatHour(nearest.hour)} · ${formatValue(nearest.value, meta.decimals)} ${meta.unit} · `
        + `${nearest.sampleCount} reading${nearest.sampleCount === 1 ? '' : 's'}, `
        + `${nearest.contributorCount} contributor${nearest.contributorCount === 1 ? '' : 's'}`;

    // Positioned in px, clamped to the chart's own rendered width, rather
    // than a %-of-container + translate(-50%) — the tooltip's width varies
    // with its text, and near either edge of the 24h chart that centered
    // placement pushed it past the panel's edge, where it got truncated.
    const idealLeftPx = (cx / width) * rect.width;
    const halfTooltipWidth = tooltip.offsetWidth / 2;
    const clampedLeftPx = Math.min(Math.max(idealLeftPx, halfTooltipWidth), rect.width - halfTooltipWidth);
    tooltip.style.left = `${clampedLeftPx}px`;
    tooltip.style.top = `${(cy / height) * 100}%`;
    tooltip.style.opacity = '1';
  });
  hitRect.addEventListener('pointerleave', () => {
    crosshair.style.opacity = '0';
    hoverDot.style.opacity = '0';
    tooltip.style.opacity = '0';
  });
  svg.appendChild(hitRect);

  return { svg, tooltip };
}

function buildChartTable(points, meta) {
  const table = el('table', 'chart-table');
  const thead = el('thead');
  const headRow = el('tr');
  [`Hour`, `${meta.label} (${meta.unit})`, 'Readings', 'Contributors'].forEach((h) => headRow.appendChild(el('th', null, h)));
  thead.appendChild(headRow);
  table.appendChild(thead);

  const tbody = el('tbody');
  points.forEach((p) => {
    const row = el('tr');
    row.appendChild(el('td', null, formatHour(p.hour)));
    row.appendChild(el('td', null, formatValue(p.value, meta.decimals)));
    row.appendChild(el('td', null, String(p.sampleCount)));
    row.appendChild(el('td', null, String(p.contributorCount)));
    tbody.appendChild(row);
  });
  table.appendChild(tbody);
  return table;
}

// --- Secondary pollutant grid (compact sparklines, shape not magnitude) ----

function buildMetricGrid(detail) {
  const section = el('div');
  section.appendChild(el('div', 'panel-section-title', 'Other readings'));
  const grid = el('div', 'metric-grid');

  SECONDARY_METRIC_KEYS.forEach((key) => {
    const meta = METRICS[key];
    const points = hourlyPoints(detail.hourly, key);
    const value = detail.means[key];

    const card = el('button', 'metric-card');
    card.type = 'button';
    card.appendChild(el('div', 'label', meta.label));
    const valueDiv = el('div', 'value');
    valueDiv.appendChild(document.createTextNode(formatValue(value, meta.decimals)));
    valueDiv.appendChild(el('span', 'unit', meta.unit));
    card.appendChild(valueDiv);

    if (points.length >= 2) {
      card.appendChild(buildSparkline(points));
    }

    card.addEventListener('click', () => {
      heroMetricKey = key;
      if (openCellKey) fetchAndRenderPanel(openCellKey.latBucket, openCellKey.lonBucket);
    });
    grid.appendChild(card);
  });

  section.appendChild(grid);
  return section;
}

function buildSparkline(points) {
  const width = 140, height = 18;
  const values = points.map((p) => p.value);
  let min = Math.min(...values), max = Math.max(...values);
  if (min === max) { min -= 1; max += 1; }
  const scaleX = (hour) => (hour / 23) * width;
  const scaleY = (v) => height - ((v - min) / (max - min)) * height;

  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('viewBox', `0 0 ${width} ${height}`);
  const path = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${scaleX(p.hour).toFixed(1)},${scaleY(p.value).toFixed(1)}`).join(' ');
  const line = document.createElementNS(svg.namespaceURI, 'path');
  line.setAttribute('d', path);
  line.setAttribute('fill', 'none');
  line.setAttribute('stroke', 'var(--muted-foreground)');
  line.setAttribute('stroke-width', '2');
  line.setAttribute('stroke-linecap', 'round');
  line.setAttribute('stroke-linejoin', 'round');
  svg.appendChild(line);
  return svg;
}

// --- Related sessions --------------------------------------------------

function buildSessionsSection(detail) {
  const sessions = detail.sessions;
  const flagged = sessions.filter((s) => s.eventCount > 0).length;

  const section = el('div');
  section.appendChild(el('div', 'panel-section-title', 'Sessions through this block'));
  section.appendChild(el('p', 'session-summary-line',
      sessions.length === 0
        ? 'No sessions have passed through this block yet.'
        : `${sessions.length} session${sessions.length === 1 ? '' : 's'} passed through, `
          + `${flagged} flagged event${flagged === 1 ? '' : 's'}.`));

  if (sessions.length > 0) {
    const list = el('div', 'session-list');
    sessions.forEach((s) => list.appendChild(buildSessionRow(s)));
    section.appendChild(list);
  }
  return section;
}

function buildSessionRow(s) {
  const row = el('div', 'session-row');

  const swatch = el('span', 'band-swatch');
  swatch.style.background = s.eventCount > 0 ? 'var(--band-poor)' : 'var(--band-good)';
  row.appendChild(swatch);

  const main = el('div', 'session-main');
  const activityLine = el('div', 'session-activity', s.activity.toLowerCase());
  if (s.mock) {
    activityLine.appendChild(el('span', 'badge-mock', 'MOCK'));
  }
  main.appendChild(activityLine);

  const start = new Date(s.startedAt), end = new Date(s.endedAt);
  main.appendChild(el('div', 'session-time',
      `${start.toLocaleTimeString()} – ${end.toLocaleTimeString()} · `
      + `${s.readingCountInCell} reading${s.readingCountInCell === 1 ? '' : 's'} here`));
  row.appendChild(main);

  if (s.eventCount > 0) {
    row.appendChild(el('span', 'event-badge', `${s.eventCount} event${s.eventCount === 1 ? '' : 's'}`));
  }
  return row;
}

// --- Live feed --------------------------------------------------------

const MAX_FEED_ROWS = 50;

function feedRow(reading) {
  const li = el('li', 'feed-row');

  const swatch = el('div', 'feed-swatch');
  swatch.style.background = STATUS_COLOR[reading.verdict.type] || 'var(--muted-foreground)';
  li.appendChild(swatch);

  const body = el('div', 'feed-body');

  const headline = el('div', 'feed-headline', reading.verdict.headline);
  if (reading.mock) {
    headline.appendChild(el('span', 'badge-mock', 'MOCK'));
  }
  body.appendChild(headline);
  body.appendChild(el('div', 'feed-explanation', reading.verdict.explanation));

  const time = new Date(reading.capturedAt).toLocaleTimeString();
  body.appendChild(el('div', 'feed-meta', `${time} · ${reading.activity} · cell ${reading.cell.latBucket},${reading.cell.lonBucket}`));

  li.appendChild(body);
  return li;
}

function feedTableRow(reading) {
  const tr = el('tr');
  const time = new Date(reading.capturedAt).toLocaleTimeString();
  for (const value of [time, reading.activity, reading.verdict.headline, reading.verdict.explanation]) {
    tr.appendChild(el('td', null, value));
  }
  return tr;
}

function onReading(reading) {
  refreshCell(reading.cell.latBucket, reading.cell.lonBucket);

  document.getElementById('feedEmpty').classList.add('hidden');

  const list = document.getElementById('feedList');
  list.insertBefore(feedRow(reading), list.firstChild);
  while (list.children.length > MAX_FEED_ROWS) {
    list.removeChild(list.lastChild);
  }

  const tbody = document.getElementById('feedTableBody');
  tbody.insertBefore(feedTableRow(reading), tbody.firstChild);
  while (tbody.children.length > MAX_FEED_ROWS) {
    tbody.removeChild(tbody.lastChild);
  }
}

function connectLiveFeed() {
  const dot = document.getElementById('liveDot');
  const label = document.getElementById('liveLabel');
  const source = new EventSource('/api/v1/stream');

  source.onopen = () => {
    dot.classList.add('connected');
    label.textContent = 'live';
  };
  source.onerror = () => {
    dot.classList.remove('connected');
    label.textContent = 'reconnecting…';
  };
  source.addEventListener('reading', (event) => {
    onReading(JSON.parse(event.data));
  });
}

function initFeedToggle() {
  const button = document.getElementById('feedToggle');
  const list = document.getElementById('feedList');
  const table = document.getElementById('feedTable');
  let tableView = false;

  button.addEventListener('click', () => {
    tableView = !tableView;
    list.classList.toggle('hidden', tableView);
    table.classList.toggle('hidden', !tableView);
    button.textContent = tableView ? 'List view' : 'Table view';
  });
}

function initFeedCollapse() {
  const button = document.getElementById('feedCollapse');
  const overlay = document.getElementById('feedOverlay');

  button.addEventListener('click', () => {
    const collapsed = overlay.classList.toggle('collapsed');
    button.textContent = collapsed ? 'Show' : 'Hide';
    button.setAttribute('aria-expanded', String(!collapsed));
  });
}

// --- Boot ---------------------------------------------------------------

(async function boot() {
  const center = await resolveCenter();
  initMap(center);
  initThemeToggle();
  initFeedToggle();
  initFeedCollapse();
  connectLiveFeed();

  fetch('/api/v1/cells')
    .then((res) => res.json())
    .then((cells) => renderCells(cells))
    .catch(() => {
      document.getElementById('liveLabel').textContent = 'backend unreachable';
    });
})();
