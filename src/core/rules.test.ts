import test from 'node:test';
import assert from 'node:assert/strict';
import {
  Geo,
  ServiceAreaRule,
  PlaceIndex,
  PhoneNumbers,
  Format,
  SheetValue,
  resolveSheetTarget,
} from './rules';
import { DEMO_BAGHDAD, TripStatus, isTripActive, passengerMayCancel } from './models';

test('PhoneNumbers normalizes Iraqi mobile numbers and Arabic/Persian digits', () => {
  assert.equal(PhoneNumbers.toE164('07701234567'), '+9647701234567');
  assert.equal(PhoneNumbers.toE164('٠٧٨٠ ١٢٣ ٤٥٦٧'), '+9647801234567');
  assert.equal(PhoneNumbers.toE164('+9647901234567'), '+9647901234567');
  assert.equal(PhoneNumbers.toE164('009647701234567'), '+9647701234567');
  assert.equal(PhoneNumbers.toE164('077012345'), null);
  assert.equal(PhoneNumbers.toE164('05501234567'), null);
  assert.equal(PhoneNumbers.display('+9647701234567'), '0770 123 4567');
  assert.equal(PhoneNumbers.normalizeOtp('١٢٣-٤٥٦'), '123456');
  assert.equal(PhoneNumbers.normalizeOtp('12345'), null);
});

test('Geo haversine and Web-Mercator round-trip accurately', () => {
  const mansour = { lat: 33.32297, lng: 44.35253 };
  const karrada = { lat: 33.29376, lng: 44.41288 };
  const dist = Geo.metersBetween(mansour, karrada);
  assert.ok(dist > 6000 && dist < 7000, `Expected ~6.5km, got ${dist}`);

  const ws = 256 * Math.pow(2, 13);
  const x = Geo.worldX(mansour.lng, ws);
  const y = Geo.worldY(mansour.lat, ws);
  assert.ok(Math.abs(Geo.lngAt(x, ws) - mansour.lng) < 1e-6);
  assert.ok(Math.abs(Geo.latAt(y, ws) - mansour.lat) < 1e-6);
});

test('ServiceAreaRule checks governorate polygon and radius', () => {
  const box: [number, number][] = [
    [44.0, 33.0],
    [44.8, 33.0],
    [44.8, 33.6],
    [44.0, 33.6],
    [44.0, 33.0],
  ];
  const rule = new ServiceAreaRule({ baghdad: box });
  assert.equal(rule.inServiceArea({ lat: 33.3152, lng: 44.3661 }, DEMO_BAGHDAD), true);
  // Outside 30km radius
  assert.equal(rule.inServiceArea({ lat: 33.59, lng: 44.75 }, DEMO_BAGHDAD), false);
  // Outside polygon
  assert.equal(rule.inServiceArea({ lat: 32.5, lng: 44.36 }, DEMO_BAGHDAD), false);
});

test('PlaceIndex normalizes Arabic variants and ranks matches', () => {
  const index = PlaceIndex.parse([
    ['المنصور', 'Al Mansur', 33.32297, 44.35253],
    ['الكرادة الشرقية', 'Hayy al Karradah', 33.29376, 44.41288],
    ['الأعظمية', "Al A'zamiyah", 33.3667, 44.36316],
  ]);

  const r1 = index.search('منصور');
  assert.equal(r1.length, 1);
  assert.equal(r1[0].nameAr, 'المنصور');

  const r2 = index.search('اعظميه');
  assert.equal(r2.length, 1);
  assert.equal(r2[0].nameAr, 'الأعظمية');

  const near = index.nearest({ lat: 33.323, lng: 44.352 });
  assert.equal(near?.nameAr, 'المنصور');
});

test('Format outputs Arabic-Indic and Latin numbers properly', () => {
  assert.equal(Format.iqd(7000, true), '٧٬٠٠٠');
  assert.equal(Format.iqd(7000, false), '7,000');
  assert.equal(Format.km(7200, true), '٧٫٢');
  assert.equal(Format.km(7200, false), '7.2');
  assert.equal(Format.minutes(960), 16);
  assert.equal(Format.mmss(179, true), '٢:٥٩');
});

test('TripStatus and Sheet target anchor rules match Android implementation', () => {
  assert.equal(isTripActive(TripStatus.Requested), true);
  assert.equal(isTripActive(TripStatus.InProgress), true);
  assert.equal(isTripActive(TripStatus.Completed), false);
  assert.equal(passengerMayCancel(TripStatus.Arrived), true);
  assert.equal(passengerMayCancel(TripStatus.InProgress), false);

  const anchors = {
    [SheetValue.Expanded]: 72,
    [SheetValue.Half]: 400,
    [SheetValue.Collapsed]: 650,
  };
  assert.equal(resolveSheetTarget(anchors, 380, 0), SheetValue.Half);
  assert.equal(resolveSheetTarget(anchors, 380, -1200), SheetValue.Expanded);
  assert.equal(resolveSheetTarget(anchors, 420, 1200), SheetValue.Collapsed);
});
