import { readFile, writeFile } from 'node:fs/promises';

const endpoint = 'https://json.tarkov.dev/regular/';
const readDataset = async name => {
  if (process.argv[2]) {
    return JSON.parse(await readFile(`${process.argv[2]}/tarkovdev-${name}.json`, 'utf8')).data;
  }
  const response = await fetch(endpoint + name);
  if (!response.ok) throw new Error(`${response.url}: ${response.status}`);
  return (await response.json()).data;
};
const [dataset, english, traders] = await Promise.all(['items', 'items_en', 'traders'].map(readDataset));
const readProjectJson = async name => JSON.parse(await readFile(new URL('../' + name, import.meta.url), 'utf8'));
const catalog = await readProjectJson('src/main/resources/data/tarkovcraft/catalog/items_catalog.json');
const localEnglish = await readProjectJson('src/main/resources/assets/tarkovcraft/lang/en_us.json');
const all = Object.values(dataset.items);
const names = new Map(all.map(item => [item.normalizedName, item]));
const traderIds = Object.fromEntries(Object.values(traders).map(t => [t.normalizedName, t.id]));
const result = {};

const categoryTypes = {
  medical: ['meds'], stimulator: ['injectors'], food: ['provisions'],
  helmet: ['helmet'], body_armor: ['armor'], rig: ['rig'], backpack: ['backpack'],
  headset: ['headphones'], grenade: ['grenade'], case: ['container'],
};
const sellerFor = {
  medical: 'therapist', stimulator: 'therapist', food: 'therapist', case: 'therapist',
  helmet: 'ragman', body_armor: 'ragman', rig: 'ragman', backpack: 'ragman',
  headset: 'ragman', face_mask: 'ragman', repair_kit: 'ragman', grenade: 'prapor',
};

function record(key, item, trader) {
  if (!item) return;
  const cash = (item.buyFromTrader ?? []).filter(offer => offer.priceRUB > 0 && offer.trader !== traderIds.fence);
  const preferred = cash.filter(offer => offer.trader === traderIds[trader]);
  const ownPrice = preferred.length ? Math.min(...preferred.map(offer => offer.priceRUB)) : 0;
  const otherPrice = cash.length ? Math.min(...cash.map(offer => offer.priceRUB)) : 0;
  const fleaPrice = item.avg24hPrice > 0 ? item.avg24hPrice : item.lastLowPrice || 0;
  const price = ownPrice || otherPrice || fleaPrice || item.basePrice;
  if (!(price > 0)) return;
  const fence = item.sellToTrader?.find(offer => offer.trader === traderIds.fence)?.priceRUB ?? 0;
  result[key] = [item.id, price, ownPrice ? 'trader' : otherPrice ? 'other_trader' : fleaPrice ? 'flea' : 'handbook', fence];
}

const tokens = name => new Set(name.toLowerCase().match(/[a-z0-9]+/g) ?? []);
function matchCatalog(entry) {
  const prefix = entry.id.match(/_([a-f0-9]{6})$/)?.[1];
  if (!prefix) return undefined;
  const matches = all.filter(item => item.id.startsWith(prefix));
  if (matches.length === 1) return matches[0];
  const expected = categoryTypes[entry.category];
  const candidates = expected ? matches.filter(item => item.types.some(type => expected.includes(type))) : matches;
  if (candidates.length === 1) return candidates[0];
  const local = tokens(localEnglish['item.tarkovcraft.' + entry.id] ?? entry.id);
  const scored = candidates.map(item => {
    const remote = tokens(english[item.name] ?? item.normalizedName);
    const score = [...local].filter(token => token.length >= 2 && remote.has(token)).length;
    return { item, score };
  }).sort((a, b) => b.score - a.score);
  return scored[0]?.score > (scored[1]?.score ?? 0) ? scored[0].item : undefined;
}

for (const entry of catalog.items) {
  record('tarkovcraft:' + entry.id, matchCatalog(entry), sellerFor[entry.category]);
}

const localAliases = {
  paca_armor: 'paca-soft-armor', salewa_medkit: 'salewa-first-aid-kit',
  bandage: 'army-bandage', cat_tourniquet: 'cat-hemostatic-tourniquet',
  ai2_medkit: 'ai-2-medkit', cms_kit: 'cms-surgical-kit', surv12_kit: 'surv12-field-surgical-kit',
  ssh68_helmet: 'ssh-68-steel-helmet', ratnik_6b47_helmet: '6b47-ratnik-bsh-helmet-olive-drab',
  altyn_helmet: 'altyn-bulletproof-helmet', scav_vest: 'scav-vest',
  berkut_backpack: 'wartech-berkut-vv-102-backpack', trizip_backpack: 'mystery-ranch-tri-zip-backpack',
  gunpowder_hawk: 'gunpowder-hawk', gunpowder_eagle: 'gunpowder-eagle', gunpowder_kite: 'gunpowder-kite',
};
for (const [item, slug] of Object.entries(localAliases)) {
  const trader = /medkit|bandage|tourniquet|_kit/.test(item) ? 'therapist' : 'ragman';
  record('tarkovcraft:' + item, names.get(slug), trader);
}

const gunNames = {
  glock_17: 'glock-17', m1911: 'm1911a1', m9a4: 'm9a3', uzi: 'uzi', ump45: 'ump-45',
  hk_mp5a5: 'mp5', hk_mp7a1: 'mp7a1', m870: 'm870', m1014: 'm1014',
  sks_tactical: 'sks', ak47: 'akm', ak74m: 'ak-74m', ak12: 'ak-12',
  m16a1: 'm16a1', m16a4: 'm16a4', g36k: 'g36', m4a1: 'm4a1',
  aug: 'aug-a3', fn_fal: 'sa58', p90: 'p90', scar_l: 'scar-l', scar_h: 'scar-h',
  m700: 'm700', svd: 'svds', m1014: 'm1014', spas_12: 'spas-12', vector45: 'vector-45acp',
  hk416d: 'hk-416a5', deagle: 'desert-eagle-l5-50-ae', hk_mk23: 'mk23', p320: 'p320',
};
const guns = all.filter(item => item.types.includes('gun') && !item.types.includes('preset'));
function findByModel(items, phrase) {
  return items.find(item => ('-' + item.normalizedName + '-').includes('-' + phrase + '-'));
}
for (const [gun, model] of Object.entries(gunNames)) {
  record('gun/tacz:' + gun, findByModel(guns, model), 'prapor');
}

const attachmentNames = {
  sight_552: 'eotech-552', sight_coyote: 'coyote', sight_acro_pistol: 'acro-p-2',
  scope_acog_ta31: 'ta31', scope_pso_1: 'pso-1', scope_elcan_4x: 'elcan-specterdr',
  grip_rk0: 'zenit-rk-0', grip_rk1_b25u: 'rk-1', grip_rk6: 'zenit-rk-6', grip_magpul_afg: 'magpul-afg',
  laser_compact: 'tactical-blue-laser', laser_peq15: 'peq-15', laser_ngal: 'ngal',
  scope_vudu: 'vudu', scope_reap_ir: 'reap-ir', scope_hamr: 'hamr', scope_xl50: 'march-tactical',
};
const attachments = all.filter(item => item.types.includes('mods'));
for (const [attachment, model] of Object.entries(attachmentNames)) {
  record('attachment/tacz:' + attachment, findByModel(attachments, model), 'prapor');
}

const calibers = {
  '9mm': '9x19PARA', '45acp': '1143x23ACP', '762x25': '762x25TT', '57x28': '57x28', '46x30': '46x30',
  '357mag': '9x33R', '12g': '12g', '545x39': '545x39', '556x45': '556x45NATO', '58x42': '58x42',
  '300blk': '762x35', '762x39': '762x39', '68x51fury': '68x51', '308': '762x51',
  '762x54': '762x54R', '338': '86x70', '50bmg': '127x99',
};
const ammunition = all.filter(item => item.types.includes('ammo') && Number.isFinite(item.properties?.penetrationPower));
for (const [caliber, spec] of Object.entries(calibers)) {
  const choices = ammunition.filter(item => item.properties.caliber === 'Caliber' + spec);
  if (!choices.length) continue;
  const ordered = choices.sort((a, b) => a.properties.penetrationPower - b.properties.penetrationPower);
  const low = ordered[0].properties.penetrationPower;
  const high = ordered.at(-1).properties.penetrationPower;
  const used = new Set();
  for (const [tier, fraction] of Object.entries({ hp: 0, fmj: 0.40, ap: 0.75, ign: 1 })) {
    const target = low + (high - low) * fraction;
    const matched = ordered.filter(item => !used.has(item.id)).sort((a, b) => {
      const distance = item => Math.abs(item.properties.penetrationPower - target);
      return distance(a) - distance(b);
    })[0];
    if (!matched) continue;
    used.add(matched.id);
    record('tacz:' + caliber + '/' + tier, matched, 'prapor');
  }
}

const sorted = Object.entries(result).sort(([a], [b]) => a.localeCompare(b));
const snapshot = '{\n  "source": "' + endpoint + 'items",\n  "date": "' + new Date().toISOString().slice(0, 10)
  + '",\n  "prices": {\n' + sorted.map(([key, entry]) => '    ' + JSON.stringify(key) + ': ' + JSON.stringify(entry)).join(',\n')
  + '\n  }\n}\n';
await writeFile(new URL('../src/main/resources/data/tarkovcraft/catalog/trader_prices.json', import.meta.url), snapshot);
console.log(`${sorted.length} prices`);
