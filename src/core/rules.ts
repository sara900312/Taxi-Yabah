import { City, GeoPoint, Place, placeDisplayName } from './models';

export const Geo = {
  /** Same haversine as the server's regions/geometry.ts (`metersBetween`). */
  metersBetween(a: GeoPoint, b: GeoPoint): number {
    const rad = Math.PI / 180;
    const s1 = Math.sin(((b.lat - a.lat) * rad) / 2);
    const s2 = Math.sin(((b.lng - a.lng) * rad) / 2);
    const h =
      s1 * s1 +
      Math.cos(a.lat * rad) * Math.cos(b.lat * rad) * s2 * s2;
    return 12_742_000.0 * Math.asin(Math.min(1.0, Math.sqrt(h)));
  },

  worldX(lng: number, worldSize: number): number {
    return ((lng + 180.0) / 360.0) * worldSize;
  },

  worldY(lat: number, worldSize: number): number {
    const clamped = Math.max(-85.05112878, Math.min(85.05112878, lat));
    const r = (clamped * Math.PI) / 180;
    return ((1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2) * worldSize;
  },

  lngAt(x: number, worldSize: number): number {
    return (x / worldSize) * 360.0 - 180.0;
  },

  latAt(y: number, worldSize: number): number {
    const n = Math.PI - (2 * Math.PI * y) / worldSize;
    return (180 / Math.PI) * Math.atan(0.5 * (Math.exp(n) - Math.exp(-n)));
  },

  /** Point at [meters] from [c] on bearing [bearingDeg] (spherical earth). */
  destination(c: GeoPoint, meters: number, bearingDeg: number): GeoPoint {
    const r = 6_371_000.0;
    const d = meters / r;
    const b = (bearingDeg * Math.PI) / 180;
    const la1 = (c.lat * Math.PI) / 180;
    const lo1 = (c.lng * Math.PI) / 180;
    const la2 = Math.asin(
      Math.sin(la1) * Math.cos(d) + Math.cos(la1) * Math.sin(d) * Math.cos(b)
    );
    const lo2 =
      lo1 +
      Math.atan2(
        Math.sin(b) * Math.sin(d) * Math.cos(la1),
        Math.cos(d) - Math.sin(la1) * Math.sin(la2)
      );
    return { lat: (la2 * 180) / Math.PI, lng: (lo2 * 180) / Math.PI };
  },
};

export type Ring = [number, number][];

export class ServiceAreaRule {
  constructor(private outlines: Record<string, Ring>) {}

  outline(code: string): Ring | undefined {
    return this.outlines[code];
  }

  inGovernorate(p: GeoPoint, ring: Ring): boolean {
    let inside = false;
    for (let i = 1; i < ring.length; i++) {
      const x = ring[i - 1][0];
      const y = ring[i - 1][1];
      const xx = ring[i][0];
      const yy = ring[i][1];
      if (
        y > p.lat !== yy > p.lat &&
        p.lng < ((xx - x) * (p.lat - y)) / (yy - y) + x
      ) {
        inside = !inside;
      }
    }
    return inside;
  }

  inServiceArea(p: GeoPoint, city: City): boolean {
    const ring = this.outlines[city.code];
    if (!ring) return false;
    return (
      this.inGovernorate(p, ring) &&
      Geo.metersBetween(p, city.center) <= city.serviceRadiusM
    );
  }
}

export class PlaceIndex {
  private keysAr: string[];
  private keysEn: string[];

  constructor(public readonly places: Place[]) {
    this.keysAr = places.map((p) => PlaceIndex.normalize(p.nameAr ?? ''));
    this.keysEn = places.map((p) => PlaceIndex.normalize(p.nameEn));
  }

  search(query: string, limit = 20, near?: GeoPoint | null): Place[] {
    const q = PlaceIndex.normalize(query);
    if (q.length < 2) return [];
    interface Hit {
      place: Place;
      score: number;
      dist: number;
    }
    const hits: Hit[] = [];
    const qBare = PlaceIndex.stripArticle(q);

    for (let i = 0; i < this.places.length; i++) {
      const ar = this.keysAr[i];
      const en = this.keysEn[i];
      const arWords = ar.split(' ').map(PlaceIndex.stripArticle);
      const enWords = en.split(' ').map(PlaceIndex.stripArticle);

      let score = -1;
      if (
        ar === q ||
        en === q ||
        PlaceIndex.stripArticle(ar) === qBare ||
        PlaceIndex.stripArticle(en) === qBare
      ) {
        score = 0;
      } else if (
        ar.startsWith(q) ||
        en.startsWith(q) ||
        (arWords[0] && arWords[0].startsWith(qBare)) ||
        (enWords[0] && enWords[0].startsWith(qBare))
      ) {
        score = 1;
      } else if (
        arWords.some((w) => w.startsWith(qBare)) ||
        enWords.some((w) => w.startsWith(qBare))
      ) {
        score = 2;
      } else if (ar.includes(qBare) || en.includes(qBare)) {
        score = 3;
      } else {
        continue;
      }

      hits.push({
        place: this.places[i],
        score,
        dist: near ? Geo.metersBetween(near, this.places[i].point) : 0,
      });
    }

    hits.sort((a, b) => {
      if (a.score !== b.score) return a.score - b.score;
      if (a.dist !== b.dist) return a.dist - b.dist;
      return a.place.nameEn.length - b.place.nameEn.length;
    });

    const out: Place[] = [];
    for (const h of hits) {
      const dup = out.some(
        (o) =>
          (o.nameAr ?? o.nameEn) === (h.place.nameAr ?? h.place.nameEn) &&
          Geo.metersBetween(o.point, h.place.point) < 3_000
      );
      if (!dup) out.push(h.place);
      if (out.length === limit) break;
    }
    return out;
  }

  nearest(p: GeoPoint, maxMeters = 1500.0): Place | null {
    let best: Place | null = null;
    let bestD = maxMeters;
    for (const pl of this.places) {
      if (
        Math.abs(pl.point.lat - p.lat) > 0.02 ||
        Math.abs(pl.point.lng - p.lng) > 0.025
      ) {
        continue;
      }
      const d = Geo.metersBetween(p, pl.point);
      if (d < bestD) {
        bestD = d;
        best = pl;
      }
    }
    return best;
  }

  static parse(rawRows: unknown[]): PlaceIndex {
    const list: Place[] = [];
    for (const row of rawRows) {
      if (Array.isArray(row) && row.length >= 4) {
        const nameAr = typeof row[0] === 'string' && row[0].trim() ? row[0] : null;
        const nameEn = String(row[1] ?? '');
        const lat = Number(row[2]);
        const lng = Number(row[3]);
        if (Number.isFinite(lat) && Number.isFinite(lng)) {
          list.push({ nameAr, nameEn, point: { lat, lng } });
        }
      }
    }
    return new PlaceIndex(list);
  }

  static stripArticle(word: string): string {
    if (word.length > 3 && word.startsWith('ال')) return word.slice(2);
    if (word.startsWith('al ')) return word.slice(3);
    if (word.startsWith('hayy al ')) return word.slice(8);
    return word;
  }

  static normalize(s: string): string {
    let out = '';
    for (const ch of s.toLowerCase()) {
      if (ch === 'أ' || ch === 'إ' || ch === 'آ' || ch === 'ٱ') out += 'ا';
      else if (ch === 'ة') out += 'ه';
      else if (ch === 'ى') out += 'ي';
      else if (ch === 'ؤ') out += 'و';
      else if (ch === 'ئ') out += 'ي';
      else if (ch === 'گ') out += 'ك';
      else if (ch === 'ڤ') out += 'ف';
      else if (ch === '-') out += ' ';
      else if (ch === 'ـ' || ch === "'" || ch === '’' || ch === '`') {
        // strip
      } else if ((ch >= '\u064B' && ch <= '\u065F') || ch === '\u0670') {
        // strip Arabic diacritics
      } else {
        out += ch;
      }
    }
    return out
      .normalize('NFD')
      .replace(/\p{Mn}+/gu, '')
      .replace(/\s+/g, ' ')
      .trim();
  }
}

export const PhoneNumbers = {
  iraqiMobile: /^(07\d{9}|\+9647\d{9})$/,

  clean(input: string): string {
    let res = '';
    for (const ch of input.trim()) {
      const code = ch.charCodeAt(0);
      if (code >= 0x0660 && code <= 0x0669) {
        res += String(code - 0x0660);
      } else if (code >= 0x06f0 && code <= 0x06f9) {
        res += String(code - 0x06f0);
      } else if ((ch >= '0' && ch <= '9') || ch === '+') {
        res += ch;
      }
    }
    return res;
  },

  toE164(input: string): string | null {
    let c = PhoneNumbers.clean(input);
    if (c.startsWith('00964')) c = '+' + c.slice(2);
    if (c.startsWith('964')) c = '+' + c;
    if (c.length === 10 && c.startsWith('7')) c = '0' + c;
    if (!PhoneNumbers.iraqiMobile.test(c)) return null;
    return c.startsWith('07') ? '+964' + c.slice(1) : c;
  },

  display(e164: string): string {
    const local = e164.startsWith('+964') ? '0' + e164.slice(4) : e164;
    if (local.length === 11) {
      return `${local.slice(0, 4)} ${local.slice(4, 7)} ${local.slice(7)}`;
    }
    return local;
  },

  normalizeOtp(input: string): string | null {
    const digits = PhoneNumbers.clean(input).replace(/\D/g, '');
    return digits.length === 6 ? digits : null;
  },
};

export const Format = {
  toArabicDigits(str: string): string {
    const arabicDigits = ['٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩'];
    return str
      .replace(/\d/g, (d) => arabicDigits[Number(d)])
      .replace(/,/g, '٬')
      .replace(/\./g, '٫');
  },

  iqd(amount: number, arabic: boolean): string {
    const formatted = Math.round(amount).toLocaleString('en-US');
    return arabic ? Format.toArabicDigits(formatted) : formatted;
  },

  km(meters: number, arabic: boolean): string {
    const val = (meters / 1000.0).toFixed(1);
    return arabic ? Format.toArabicDigits(val) : val;
  },

  minutes(seconds: number): number {
    return Math.max(1, Math.floor((seconds + 59) / 60));
  },

  int(n: number, arabic: boolean): string {
    const s = String(Math.round(n));
    return arabic ? Format.toArabicDigits(s) : s;
  },

  mmss(totalSeconds: number, arabic: boolean): string {
    const s = Math.max(0, Math.floor(totalSeconds));
    const mins = Math.floor(s / 60);
    const secs = s % 60;
    const raw = `${mins}:${secs < 10 ? '0' + secs : secs}`;
    return arabic ? Format.toArabicDigits(raw) : raw;
  },
};

export enum SheetValue {
  Collapsed = 'Collapsed',
  Half = 'Half',
  Expanded = 'Expanded',
}

export function resolveSheetTarget(
  anchors: Record<SheetValue, number>,
  position: number,
  velocity: number,
  velocityThreshold = 900
): SheetValue {
  const entries = (Object.entries(anchors) as [SheetValue, number][]).sort(
    (a, b) => a[1] - b[1]
  );
  if (entries.length === 0) return SheetValue.Half;
  if (velocity < -velocityThreshold) {
    const above = entries.filter(([, offset]) => offset < position - 1);
    return (above[above.length - 1] ?? entries[0])[0];
  }
  if (velocity > velocityThreshold) {
    const below = entries.find(([, offset]) => offset > position + 1);
    return (below ?? entries[entries.length - 1])[0];
  }
  let closest = entries[0];
  let bestDiff = Math.abs(entries[0][1] - position);
  for (const entry of entries) {
    const diff = Math.abs(entry[1] - position);
    if (diff < bestDiff) {
      bestDiff = diff;
      closest = entry;
    }
  }
  return closest[0];
}

export { placeDisplayName };
