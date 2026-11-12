import React, { useState, useMemo } from 'react';
import { Check, Phone, Star } from 'lucide-react';
import {
  FareQuote,
  GeoPoint,
  TripDetails,
  TripStatus,
  isTripActive,
  passengerMayCancel,
} from '../core/models';
import { Format, PlaceIndex } from '../core/rules';
import { Lang, t } from '../i18n/strings';
import {
  KeyValueRow,
  MessageCard,
  StatusBadge,
  YabahPrimaryButton,
} from '../components/UiPrimitives';
import { DemoMapCanvas, MapMarker } from '../components/DemoMapCanvas';

interface TripScreenProps {
  lang: Lang;
  darkTheme: boolean;
  places: PlaceIndex | null;
  pickup: GeoPoint;
  pickupAddress: string;
  dropoff: GeoPoint;
  dropoffAddress: string;
  quote: FareQuote;
  onDone: () => void;
}

const TIMELINE_STEPS: { status: TripStatus; labelKey: string }[] = [
  { status: TripStatus.Requested, labelKey: 'tl_requested' },
  { status: TripStatus.Accepted, labelKey: 'tl_accepted' },
  { status: TripStatus.Arrived, labelKey: 'tl_arrived' },
  { status: TripStatus.InProgress, labelKey: 'tl_in_progress' },
  { status: TripStatus.Completed, labelKey: 'tl_completed' },
];

export const TripScreen: React.FC<TripScreenProps> = ({
  lang,
  darkTheme,
  places,
  pickup,
  pickupAddress,
  dropoff,
  dropoffAddress,
  quote,
  onDone,
}) => {
  const arabic = lang === 'ar';
  const [status, setStatus] = useState<TripStatus>(TripStatus.Requested);
  const [stars, setStars] = useState<number>(0);
  const [ratingSubmitted, setRatingSubmitted] = useState<boolean>(false);
  const [showCancelDialog, setShowCancelDialog] = useState<boolean>(false);
  const [staleLocation, setStaleLocation] = useState<boolean>(false);

  const serverNowMs = useMemo(() => Date.now(), [status, staleLocation]);

  const details: TripDetails = useMemo(
    () => ({
      id: '3d1f8a52-7c41-4b8e-9a0b-1c2d3e4f5a6b',
      status,
      fareQuoteId: quote.id,
      pickupAddress,
      pickup,
      dropoffAddress,
      dropoff,
      estimatedFareIqd: quote.estimatedFareIqd,
      finalFareIqd: status === TripStatus.Completed ? quote.estimatedFareIqd : null,
      passengerPaysIqd: status === TripStatus.Completed ? quote.estimatedFareIqd : null,
      paymentMethod: 'cash',
      cancellationReason: null,
      vehicleClassNameAr: 'تكسي',
      captain:
        status === TripStatus.Requested
          ? null
          : {
              fullName: 'كابتن الاختبار',
              ratingAvg: 4.8,
              ratingCount: 120,
              phoneE164: '+9647700000000',
            },
      vehicle:
        status === TripStatus.Requested
          ? null
          : {
              make: 'Toyota',
              model: 'Corolla',
              color: 'أبيض',
              plateNumber: 'بغداد 12345',
              manufactureYear: 2019,
            },
      captainLocation:
        status === TripStatus.Requested
          ? null
          : {
              point: {
                lat: (pickup.lat + dropoff.lat) / 2 + 0.002,
                lng: (pickup.lng + dropoff.lng) / 2,
              },
              updatedAtMs: serverNowMs - (staleLocation ? 4 * 60_000 : 20_000),
            },
      events: [],
      myRatingSubmitted: ratingSubmitted,
      serverNowMs,
    }),
    [
      status,
      quote,
      pickupAddress,
      pickup,
      dropoffAddress,
      dropoff,
      ratingSubmitted,
      serverNowMs,
      staleLocation,
    ]
  );

  const mapCenter: GeoPoint = useMemo(
    () => ({
      lat: (pickup.lat + dropoff.lat) / 2,
      lng: (pickup.lng + dropoff.lng) / 2,
    }),
    [pickup, dropoff]
  );

  const markers: MapMarker[] = useMemo(() => {
    const list: MapMarker[] = [
      { point: details.pickup, kind: 'Pickup' },
      { point: details.dropoff, kind: 'Dropoff' },
    ];
    if (details.captainLocation) {
      list.push({ point: details.captainLocation.point, kind: 'Captain' });
    }
    return list;
  }, [details]);

  const statusHeader = useMemo(() => {
    switch (details.status) {
      case TripStatus.Requested:
        return { title: 'trip_searching', note: 'trip_searching_note' };
      case TripStatus.Accepted:
        return { title: 'trip_accepted', note: null };
      case TripStatus.Arrived:
        return { title: 'trip_arrived', note: null };
      case TripStatus.InProgress:
        return { title: 'trip_in_progress', note: null };
      case TripStatus.Completed:
        return { title: 'trip_completed', note: null };
      default:
        return { title: 'trip_cancelled', note: null };
    }
  }, [details.status]);

  const reachedIndex = TIMELINE_STEPS.findIndex((s) => s.status === details.status);
  const ageMin = details.captainLocation?.updatedAtMs
    ? Math.max(0, Math.floor((details.serverNowMs - details.captainLocation.updatedAtMs) / 60_000))
    : null;
  const isStale = (ageMin ?? 0) >= 2;

  const checkedTimeFormatted = useMemo(() => {
    return new Date(serverNowMs).toLocaleTimeString(arabic ? 'ar-IQ' : 'en-US', {
      hour: 'numeric',
      minute: '2-digit',
      second: '2-digit',
    });
  }, [serverNowMs, arabic]);

  return (
    <main
      data-testid="trip_screen"
      className="w-full h-full flex flex-col bg-[#F8F7F3] dark:bg-[#101B2C] text-[#101B2C] dark:text-[#F8F7F3] overflow-hidden"
    >
      {/* Top 260px map */}
      <div className="relative w-full h-[260px] shrink-0">
        <DemoMapCanvas
          center={mapCenter}
          zoom={12.8}
          markers={markers}
          route={null}
          overlay={null}
          places={places}
          arabic={arabic}
          darkTheme={darkTheme}
          topPadding={48}
          bottomPadding={12}
          attributionText={t(lang, 'demo_map_attribution')}
          mapDescription={t(lang, 'map_desc')}
          onCameraMove={() => {}}
          onTap={() => {}}
        />
        <div className="absolute top-3 start-3 z-10 rounded-full bg-white dark:bg-[#18263B] shadow-md px-3.5 py-1.5 text-[14px] font-semibold">
          {t(lang, 'app_name')}
        </div>
      </div>

      {/* Scrollable trip details */}
      <div className="flex-1 overflow-y-auto px-4 py-4 flex flex-col gap-3.5">
        {/* Status Header */}
        <div className="flex flex-col gap-1">
          <h1
            data-testid="trip_status_title"
            className="text-[22px] leading-[32px] font-semibold"
          >
            {t(lang, statusHeader.title)}
          </h1>
          {statusHeader.note && (
            <p className="text-[14px] leading-[22px] text-[#627186] dark:text-[#A9B4C4]">
              {t(lang, statusHeader.note)}
            </p>
          )}
          {details.status === TripStatus.Requested && (
            <div className="w-full h-1.5 rounded-full bg-[#FBEFD7] dark:bg-[#3A2F17] overflow-hidden mt-1">
              <div className="w-1/2 h-full bg-[#0DAA9B] rounded-full animate-pulse" />
            </div>
          )}
        </div>

        {/* 5-step Timeline */}
        <div className="w-full flex items-start justify-between py-1">
          {TIMELINE_STEPS.map((step, i) => {
            const done =
              details.status !== TripStatus.Cancelled && i <= reachedIndex;
            return (
              <div
                key={step.status}
                className="flex-1 flex flex-col items-center text-center gap-1"
              >
                <div
                  className={`w-[22px] h-[22px] rounded-full flex items-center justify-center ${
                    done
                      ? 'bg-[#0DAA9B] text-white'
                      : 'bg-[#F0EEE8] dark:bg-[#213149] text-transparent'
                  }`}
                >
                  {done && <Check className="w-3.5 h-3.5 stroke-[2.5]" />}
                </div>
                <span
                  className={`text-[11px] leading-[16px] font-medium ${
                    done
                      ? 'text-[#101B2C] dark:text-[#F8F7F3]'
                      : 'text-[#627186] dark:text-[#A9B4C4]'
                  }`}
                >
                  {t(lang, step.labelKey)}
                </span>
              </div>
            );
          })}
        </div>

        {/* Captain Card */}
        {details.captain &&
          (details.status === TripStatus.Accepted ||
            details.status === TripStatus.Arrived ||
            details.status === TripStatus.InProgress) && (
            <div
              data-testid="captain_card"
              className="w-full rounded-[24px] bg-white dark:bg-[#18263B] shadow-xs p-4 flex flex-col gap-2.5 border border-[#E2E0D9] dark:border-[#2A3B55]"
            >
              <div className="flex items-center gap-3">
                <div className="w-12 h-12 rounded-full bg-[#D3F1ED] dark:bg-[#0A4A44] text-[#00413B] dark:text-[#B9EDE6] flex items-center justify-center text-[20px] font-semibold shrink-0">
                  {(details.captain.fullName ?? '؟').slice(0, 1)}
                </div>
                <div className="flex-1 min-w-0">
                  <div className="text-[16px] leading-[24px] font-semibold">
                    {details.captain.fullName}
                  </div>
                  {details.captain.ratingAvg !== null && (
                    <div className="flex items-center gap-1 text-[12px] text-[#627186] dark:text-[#A9B4C4]">
                      <Star className="w-4 h-4 fill-[#E8B65A] text-[#E8B65A]" />
                      <span>
                        {Format.toArabicDigits(details.captain.ratingAvg.toFixed(1))}{' '}
                        ({details.captain.ratingCount})
                      </span>
                    </div>
                  )}
                </div>
                {details.captain.phoneE164 && (
                  <a
                    href={`tel:${details.captain.phoneE164}`}
                    aria-label={t(lang, 'call_captain')}
                    className="w-10 h-10 rounded-full flex items-center justify-center text-[#0DAA9B] hover:bg-[#0DAA9B]/10"
                  >
                    <Phone className="w-5 h-5" />
                  </a>
                )}
              </div>

              {details.vehicle && (
                <div className="flex flex-col items-start gap-1.5">
                  <div className="text-[14px] leading-[22px]">
                    {[
                      details.vehicle.color,
                      details.vehicle.make,
                      details.vehicle.model,
                      details.vehicle.manufactureYear,
                    ]
                      .filter(Boolean)
                      .join(' ')}
                  </div>
                  {details.vehicle.plateNumber && (
                    <div className="rounded-[8px] bg-[#FBEFD7] dark:bg-[#3A2F17] text-[#101B2C] dark:text-[#F3D9A4] px-3 py-1 text-[18px] font-semibold">
                      {details.vehicle.plateNumber}
                    </div>
                  )}
                </div>
              )}

              <div>
                {ageMin === null ? (
                  <StatusBadge
                    text={t(lang, 'captain_location_unknown')}
                    tone="Warn"
                  />
                ) : isStale ? (
                  <StatusBadge
                    text={t(
                      lang,
                      'captain_location_stale',
                      Format.int(ageMin, arabic)
                    )}
                    tone="Warn"
                    testId="stale_location"
                  />
                ) : (
                  <StatusBadge
                    text={t(lang, 'captain_location_fresh')}
                    tone="Success"
                  />
                )}
              </div>
            </div>
          )}

        {/* Fare Block */}
        <div className="flex flex-col gap-1">
          {details.estimatedFareIqd !== null && (
            <KeyValueRow
              label={t(lang, 'estimated_fare')}
              value={t(
                lang,
                'currency_iqd',
                Format.iqd(details.estimatedFareIqd, arabic)
              )}
            />
          )}
          {details.status === TripStatus.Completed &&
            details.finalFareIqd !== null && (
              <KeyValueRow
                label={t(lang, 'final_fare')}
                value={t(
                  lang,
                  'currency_iqd',
                  Format.iqd(details.finalFareIqd, arabic)
                )}
                emphasize
              />
            )}
          <div className="pt-1">
            <StatusBadge text={t(lang, 'payment_cash')} tone="Info" />
          </div>
        </div>

        {/* Cancel button */}
        {passengerMayCancel(details.status) && (
          <button
            type="button"
            onClick={() => setShowCancelDialog(true)}
            data-testid="cancel_trip"
            className="w-full h-[52px] rounded-full border border-[#B9C0CB] dark:border-[#51617A] text-[#C2453D] dark:text-[#FF8A80] font-semibold text-[15px] hover:bg-[#C2453D]/5 transition-colors cursor-pointer"
          >
            {t(lang, 'cancel_trip')}
          </button>
        )}

        {/* Rating Block when Completed */}
        {details.status === TripStatus.Completed && (
          <div className="flex flex-col gap-2.5 pt-1">
            {details.myRatingSubmitted ? (
              <MessageCard
                message={t(lang, 'rate_thanks')}
                tone="Success"
                testId="rate_thanks"
              />
            ) : (
              <>
                <h2 className="text-[16px] leading-[24px] font-semibold">
                  {t(lang, 'rate_trip')}
                </h2>
                <div className="flex items-center justify-center gap-2 py-1">
                  {[1, 2, 3, 4, 5].map((n) => (
                    <button
                      key={n}
                      type="button"
                      onClick={() => setStars(n)}
                      aria-label={t(lang, 'rate_star_desc', n)}
                      data-testid={`star_${n}`}
                      className="p-1.5 cursor-pointer"
                    >
                      <Star
                        className={`w-8 h-8 transition-colors ${
                          n <= stars
                            ? 'fill-[#E8B65A] text-[#E8B65A]'
                            : 'text-[#B9C0CB] dark:text-[#51617A]'
                        }`}
                      />
                    </button>
                  ))}
                </div>
                <YabahPrimaryButton
                  text={t(lang, 'rate_submit')}
                  onClick={() => {
                    if (stars >= 1) setRatingSubmitted(true);
                  }}
                  enabled={stars >= 1 && stars <= 5}
                  testId="submit_rating"
                />
              </>
            )}
          </div>
        )}

        {/* Back home button when trip is finished/cancelled */}
        {!isTripActive(details.status) && (
          <YabahPrimaryButton
            text={t(lang, 'back_home')}
            onClick={onDone}
            testId="back_home"
          />
        )}

        {/* Server check timestamp */}
        <div className="text-[11px] leading-[16px] text-[#627186] dark:text-[#A9B4C4]">
          {t(lang, 'trip_last_checked', checkedTimeFormatted)}
        </div>

        {/* Interactive simulation controls to step through server states */}
        <div className="mt-2 pt-3 border-t border-[#E2E0D9] dark:border-[#2A3B55] flex flex-wrap items-center gap-1.5">
          {TIMELINE_STEPS.map((s) => (
            <button
              key={s.status}
              type="button"
              onClick={() => setStatus(s.status)}
              data-testid={`sim_status_${s.status}`}
              className={`px-2.5 py-1 rounded-lg text-[12px] font-medium transition-colors cursor-pointer ${
                status === s.status
                  ? 'bg-[#101B2C] text-white dark:bg-[#F8F7F3] dark:text-[#101B2C]'
                  : 'bg-[#F0EEE8] dark:bg-[#213149] text-[#627186] dark:text-[#A9B4C4]'
              }`}
            >
              {t(lang, s.labelKey)}
            </button>
          ))}
          {details.captain && (
            <button
              type="button"
              onClick={() => setStaleLocation((v) => !v)}
              data-testid="sim_toggle_stale"
              className="px-2.5 py-1 rounded-lg text-[12px] font-medium bg-[#FBEFD7] text-[#6B4A0E] cursor-pointer"
            >
              {staleLocation ? 'موقع محدّث' : 'اختبار تأخر الموقع'}
            </button>
          )}
        </div>
      </div>

      {/* Cancel confirmation dialog */}
      {showCancelDialog && (
        <div
          role="dialog"
          aria-modal="true"
          className="fixed inset-0 z-50 bg-black/40 flex items-center justify-center p-4"
        >
          <div className="w-full max-w-sm rounded-[24px] bg-white dark:bg-[#18263B] p-6 shadow-xl flex flex-col gap-4">
            <h2 className="text-[18px] font-semibold">
              {t(lang, 'cancel_confirm_title')}
            </h2>
            <div className="flex items-center justify-end gap-3">
              <button
                type="button"
                onClick={() => setShowCancelDialog(false)}
                className="px-4 py-2 rounded-lg text-[14px] font-medium text-[#627186] hover:bg-black/5 cursor-pointer"
              >
                {t(lang, 'cancel_confirm_no')}
              </button>
              <button
                type="button"
                onClick={() => {
                  setShowCancelDialog(false);
                  setStatus(TripStatus.Cancelled);
                }}
                data-testid="confirm_cancel_yes"
                className="px-4 py-2 rounded-lg text-[14px] font-semibold text-[#C2453D] hover:bg-[#C2453D]/10 cursor-pointer"
              >
                {t(lang, 'cancel_confirm_yes')}
              </button>
            </div>
          </div>
        </div>
      )}
    </main>
  );
};
