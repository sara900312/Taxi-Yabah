export interface GeoPoint {
  lat: number;
  lng: number;
}

export function isValidGeoPoint(p: GeoPoint): boolean {
  return (
    Number.isFinite(p.lat) &&
    Number.isFinite(p.lng) &&
    Math.abs(p.lat) <= 90 &&
    Math.abs(p.lng) <= 180
  );
}

export interface City {
  id: number;
  code: string;
  nameAr: string;
  nameEn: string;
  center: GeoPoint;
  serviceRadiusM: number;
}

export interface LabeledPoint {
  point: GeoPoint;
  label: string;
}

export interface RoutePreview {
  points: GeoPoint[];
  distanceM: number;
  durationS: number;
}

export interface RideOption {
  vehicleClass: string;
  vehicleClassId: number | null;
  nameAr: string | null;
  maxPassengers: number | null;
  fareIqd: number;
  availableCaptains: number | null;
}

export interface FareQuote {
  id: string;
  estimatedFareIqd: number;
  currency: string;
  distanceM: number;
  durationS: number;
  expiresAtLocalMs: number;
  cityId: number | null;
  option: RideOption | null;
}

export function displayFareIqd(quote: FareQuote): number {
  return quote.option?.fareIqd ?? quote.estimatedFareIqd;
}

export enum TripStatus {
  Requested = 'requested',
  Accepted = 'accepted',
  Arrived = 'arrived',
  InProgress = 'in_progress',
  Completed = 'completed',
  Cancelled = 'cancelled',
  Unknown = '',
}

export function isTripActive(status: TripStatus): boolean {
  return (
    status === TripStatus.Requested ||
    status === TripStatus.Accepted ||
    status === TripStatus.Arrived ||
    status === TripStatus.InProgress
  );
}

export function passengerMayCancel(status: TripStatus): boolean {
  return (
    status === TripStatus.Requested ||
    status === TripStatus.Accepted ||
    status === TripStatus.Arrived
  );
}

export function tripStatusFromWire(value: string | null | undefined): TripStatus {
  switch (value) {
    case 'requested':
      return TripStatus.Requested;
    case 'accepted':
      return TripStatus.Accepted;
    case 'arrived':
      return TripStatus.Arrived;
    case 'in_progress':
      return TripStatus.InProgress;
    case 'completed':
      return TripStatus.Completed;
    case 'cancelled':
      return TripStatus.Cancelled;
    default:
      return TripStatus.Unknown;
  }
}

export interface CaptainInfo {
  fullName: string | null;
  ratingAvg: number | null;
  ratingCount: number | null;
  phoneE164: string | null;
}

export interface VehicleInfo {
  make: string | null;
  model: string | null;
  color: string | null;
  plateNumber: string | null;
  manufactureYear: number | null;
}

export interface CaptainLocation {
  point: GeoPoint;
  updatedAtMs: number | null;
}

export interface TripEvent {
  type: string;
  atMs: number | null;
}

export interface TripDetails {
  id: string;
  status: TripStatus;
  fareQuoteId: string | null;
  pickupAddress: string;
  pickup: GeoPoint;
  dropoffAddress: string;
  dropoff: GeoPoint;
  estimatedFareIqd: number | null;
  finalFareIqd: number | null;
  passengerPaysIqd: number | null;
  paymentMethod: string | null;
  cancellationReason: string | null;
  vehicleClassNameAr: string | null;
  captain: CaptainInfo | null;
  vehicle: VehicleInfo | null;
  captainLocation: CaptainLocation | null;
  events: TripEvent[];
  myRatingSubmitted: boolean;
  serverNowMs: number;
}

export interface Profile {
  id: string;
  fullName: string | null;
  phoneE164: string | null;
  phoneVerified: boolean;
  accountStatus: string | null;
}

export interface Place {
  nameAr: string | null;
  nameEn: string;
  point: GeoPoint;
}

export function placeDisplayName(place: Place, arabic: boolean): string {
  return arabic && place.nameAr && place.nameAr.trim().length > 0
    ? place.nameAr
    : place.nameEn;
}

export interface DemoClass {
  code: string;
  nameAr: string;
  nameEn: string;
  maxPassengers: number;
}

export const DEMO_BAGHDAD: City = {
  id: 1,
  code: 'baghdad',
  nameAr: 'بغداد',
  nameEn: 'Baghdad',
  center: { lat: 33.3152, lng: 44.3661 },
  serviceRadiusM: 30_000,
};

export const DEMO_CLASSES: DemoClass[] = [
  { code: 'taxi', nameAr: 'تكسي', nameEn: 'Taxi', maxPassengers: 4 },
  { code: 'economy', nameAr: 'أوفر', nameEn: 'Economy', maxPassengers: 4 },
  { code: 'comfort', nameAr: 'مريح', nameEn: 'Comfort', maxPassengers: 4 },
  { code: 'family', nameAr: 'عائلي', nameEn: 'Family', maxPassengers: 7 },
  { code: 'tuktuk', nameAr: 'تكتك', nameEn: 'Tuk-tuk', maxPassengers: 2 },
];
