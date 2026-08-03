// Cell size mirrors backend/.../domain/GridCell.java and
// app/.../location/GridCell.java — three independent implementations of the
// same constant now, same discipline as the BLE UUIDs in docs/ble-protocol.md.
// A drift here is cosmetic (misdrawn rectangles), not a privacy break like a
// drift on the other two would be, but it's still one constant, kept equal.
const CELL_SIZE_DEGREES = 0.001;

const SEQ_LOW = [205, 226, 251];   // --seq-100 as RGB
const SEQ_HIGH = [13, 54, 107];    // --seq-700 as RGB
const PM_SCALE_MAX = 100;          // µg/m³ at which the ramp saturates

const STATUS_COLOR = {
  NORMAL: 'var(--status-good)',
  TRAFFIC_PLUME: 'var(--status-warning)',
  SOLVENT: 'var(--status-serious)',
  SMOKE_OR_EXHAUST: 'var(--status-critical)',
  LOUD_BUT_CLEAN: 'var(--ink-muted)',
};

function pmColor(meanPm25) {
  const t = Math.max(0, Math.min(1, meanPm25 / PM_SCALE_MAX));
  const r = Math.round(SEQ_LOW[0] + (SEQ_HIGH[0] - SEQ_LOW[0]) * t);
  const g = Math.round(SEQ_LOW[1] + (SEQ_HIGH[1] - SEQ_LOW[1]) * t);
  const b = Math.round(SEQ_LOW[2] + (SEQ_HIGH[2] - SEQ_LOW[2]) * t);
  return `rgb(${r}, ${g}, ${b})`;
}

function cellBounds(cell) {
  const south = cell.latBucket * CELL_SIZE_DEGREES;
  const north = south + CELL_SIZE_DEGREES;
  const west = cell.lonBucket * CELL_SIZE_DEGREES;
  const east = west + CELL_SIZE_DEGREES;
  return [[south, west], [north, east]];
}

function formatHour(h) {
  return h == null || h < 0 ? '—' : String(h).padStart(2, '0') + ':00';
}

// Builds the cell popup from DOM nodes with textContent, never innerHTML —
// every field here came from an API response, and contributorId in
// particular is influenced by whatever a client (or the seeder) sent.
function buildPopup(cell) {
  const root = document.createElement('div');

  const title = document.createElement('strong');
  title.textContent = `Cell ${cell.latBucket}, ${cell.lonBucket}`;
  root.appendChild(title);

  const lines = [
    `${cell.sampleCount} readings from ${cell.contributorCount} contributor${cell.contributorCount === 1 ? '' : 's'}`,
    `Mean PM2.5 ${cell.meanPm2_5.toFixed(1)} µg/m³, ${cell.meanNoiseDb.toFixed(1)} dB(A)`,
    `Cleanest around ${formatHour(cell.cleanestHour)}, quietest around ${formatHour(cell.quietestHour)}`,
    cell.confidence === 'SINGLE_CONTRIBUTOR' ? 'Single contributor — not yet corroborated' : 'Corroborated by more than one contributor',
  ];
  for (const line of lines) {
    const p = document.createElement('div');
    p.textContent = line;
    root.appendChild(p);
  }

  if (cell.hasSeededData) {
    const seeded = document.createElement('div');
    seeded.style.color = 'var(--status-critical)';
    seeded.style.fontWeight = '600';
    seeded.textContent = `Includes ${cell.seededContributorCount} seeded (synthetic) contributor${cell.seededContributorCount === 1 ? '' : 's'}`;
    root.appendChild(seeded);
  }

  return root;
}

function renderCells(map, cells) {
  for (const cell of cells) {
    const rect = L.rectangle(cellBounds(cell), {
      color: cell.confidence === 'CORROBORATED' ? 'var(--ink-primary)' : 'var(--ink-secondary)',
      weight: cell.confidence === 'CORROBORATED' ? 2 : 1,
      dashArray: cell.confidence === 'SINGLE_CONTRIBUTOR' ? '4 3' : null,
      fillColor: pmColor(cell.meanPm2_5),
      fillOpacity: cell.confidence === 'NO_DATA' ? 0.15 : cell.confidence === 'SINGLE_CONTRIBUTOR' ? 0.45 : 0.75,
    }).addTo(map);
    rect.bindPopup(buildPopup(cell));
  }

  const stat = (id, value) => { document.getElementById(id).textContent = value; };
  stat('statCells', cells.length);
  stat('statCorroborated', cells.filter(c => c.confidence === 'CORROBORATED').length);
  stat('statSingle', cells.filter(c => c.confidence === 'SINGLE_CONTRIBUTOR').length);
  stat('statSeeded', cells.some(c => c.hasSeededData) ? 'yes' : 'no');
}

function initMap() {
  const map = L.map('map', { scrollWheelZoom: false }).setView([49.0069, 8.4037], 15);
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '&copy; OpenStreetMap contributors',
    maxZoom: 19,
  }).addTo(map);
  return map;
}

// --- Live feed --------------------------------------------------------

const MAX_FEED_ROWS = 50;
const feedEvents = [];

function feedRow(reading) {
  const li = document.createElement('li');
  li.className = 'feed-row';

  const swatch = document.createElement('div');
  swatch.className = 'feed-swatch';
  swatch.style.background = STATUS_COLOR[reading.verdict.type] || 'var(--ink-muted)';
  li.appendChild(swatch);

  const body = document.createElement('div');
  body.className = 'feed-body';

  const headline = document.createElement('div');
  headline.className = 'feed-headline';
  headline.textContent = reading.verdict.headline;
  if (reading.mock) {
    const badge = document.createElement('span');
    badge.className = 'badge-mock';
    badge.textContent = 'MOCK';
    headline.appendChild(badge);
  }
  body.appendChild(headline);

  const explanation = document.createElement('div');
  explanation.className = 'feed-explanation';
  explanation.textContent = reading.verdict.explanation;
  body.appendChild(explanation);

  const meta = document.createElement('div');
  meta.className = 'feed-meta';
  const time = new Date(reading.capturedAt).toLocaleTimeString();
  meta.textContent = `${time} · ${reading.activity} · cell ${reading.cell.latBucket},${reading.cell.lonBucket}`;
  body.appendChild(meta);

  li.appendChild(body);
  return li;
}

function feedTableRow(reading) {
  const tr = document.createElement('tr');
  const time = new Date(reading.capturedAt).toLocaleTimeString();
  for (const value of [time, reading.activity, reading.verdict.headline, reading.verdict.explanation]) {
    const td = document.createElement('td');
    td.textContent = value;
    tr.appendChild(td);
  }
  return tr;
}

function onReading(reading) {
  feedEvents.unshift(reading);
  feedEvents.length = Math.min(feedEvents.length, MAX_FEED_ROWS);

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

// --- Boot ---------------------------------------------------------------

const map = initMap();
initFeedToggle();
connectLiveFeed();

fetch('/api/v1/cells')
  .then((res) => res.json())
  .then((cells) => renderCells(map, cells))
  .catch(() => {
    document.getElementById('liveLabel').textContent = 'backend unreachable';
  });
