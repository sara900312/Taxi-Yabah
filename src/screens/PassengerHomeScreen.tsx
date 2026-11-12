import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { Settings, Search, X, MapPin } from 'lucide-react';
import {
  City,
  DEMO_BAGHDAD,
  DEMO_CLASSES,
  FareQuote,
  GeoPoint,
  Place,
  RoutePreview,
  displayFareIqd,
  isValidGeoPoint,
  placeDisplayName,
} from '../core/models';
import {
  Format,
  Geo,
  PlaceIndex,
  ServiceAreaRule,
  SheetValue,
} from '../core/rules';
import { Lang, t } from '../i18n/strings';
import {
  CarIcon,
  CenterPin,
  DemoChip,
  LocationRow,
  MessageCard,
  MyLocationIcon,
  StatusBadge,
  YabahMark,
  YabahPrimaryButton,
  YabahSecondaryButton,
} from '../components/UiPrimitives';
import {
  DemoMapCanvas,
  MapMarker,
  MapRoute,
  ServiceOverlay,
} from '../components/DemoMapCanvas';
import { YabahBottomSheetLayout } from '../components/YabahBottomSheet';

type Stage = 'Pickup' | 'Dropoff' | 'Review';

interface PointSelection {
  point: GeoPoint;
  near: Place | null;
}

type RouteUi =
  | { kind: 'Idle' }
  | { kind: 'Loading' }
  | { kind: 'Ready'; route: RoutePreview }
  | { kind: 'DemoOnly'; straightMeters: number };

type QuoteUi =
  | { kind: 'Idle' }
  | { kind: 'Loading' }
  | { kind: 'Ready'; quote: FareQuote }
  | { kind: 'Expired'; quote: FareQuote };

interface PassengerHomeScreenProps {
  lang: Lang;
  darkTheme: boolean;
  isDemo: boolean;
  places: PlaceIndex | null;
  serviceAreas: ServiceAreaRule | null;
  onOpenTrip: (pickup: PointSelection, dropoff: PointSelection, quote: FareQuote) => void;
  onOpenSettings: () => void;
}

export const PassengerHomeScreen: React.FC<PassengerHomeScreenProps> = ({
  lang,
  darkTheme,
  isDemo,
  places,
  serviceAreas,
  onOpenTrip,
  onOpenSettings,
}) => {
  const arabic = lang === 'ar';
  const city: City = DEMO_BAGHDAD;

  const [cameraCenter, setCameraCenter] = useState<GeoPoint>(city.center);
  const [cameraZoom, setCameraZoom] = useState<number>(12.8);
  const [isGesturing, setIsGesturing] = useState<boolean>(false);
  const [sheetValue, setSheetValue] = useState<SheetValue>(SheetValue.Half);

  const [stage, setStage] = useState<Stage>('Pickup');
  const [pickup, setPickup] = useState<PointSelection | null>(null);
  const [dropoff, setDropoff] = useState<PointSelection | null>(null);
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [routeUi, setRouteUi] = useState<RouteUi>({ kind: 'Idle' });
  const [quoteUi, setQuoteUi] = useState<QuoteUi>({ kind: 'Idle' });
  const [bookingSubmitting, setBookingSubmitting] = useState<boolean>(false);
  const [locateState, setLocateState] = useState<
    'Idle' | 'Locating' | 'Denied' | 'Unavailable'
  >('Idle');
  const [online, setOnline] = useState<boolean>(navigator.onLine);
  const [nowMs, setNowMs] = useState<number>(() => Date.now());

  useEffect(() => {
    const onOnline = () => setOnline(true);
    const onOffline = () => setOnline(false);
    window.addEventListener('online', onOnline);
    window.addEventListener('offline', onOffline);
    return () => {
      window.removeEventListener('online', onOnline);
      window.removeEventListener('offline', onOffline);
    };
  }, []);

  // Derive live pin selection under camera center
  const pinSelection: PointSelection | null = useMemo(() => {
    if (!isValidGeoPoint(cameraCenter)) return null;
    const nearest = places ? places.nearest(cameraCenter) : null;
    return { point: cameraCenter, near: nearest };
  }, [cameraCenter, places]);

  const pinOutsideArea = useMemo(() => {
    if (!serviceAreas || stage === 'Review') return false;
    return !serviceAreas.inServiceArea(cameraCenter, city);
  }, [cameraCenter, city, serviceAreas, stage]);

  const searchResults = useMemo(() => {
    if (!places || searchQuery.trim().length < 2) return [];
    return places.search(searchQuery, 20, city.center);
  }, [places, searchQuery, city.center]);

  // Quote countdown timer
  useEffect(() => {
    if (quoteUi.kind !== 'Ready') return;
    const interval = setInterval(() => {
      const current = Date.now();
      setNowMs(current);
      if (current >= quoteUi.quote.expiresAtLocalMs) {
        setQuoteUi({ kind: 'Expired', quote: quoteUi.quote });
      }
    }, 1000);
    return () => clearInterval(interval);
  }, [quoteUi]);

  const fitBoundsToPoints = useCallback((points: GeoPoint[]) => {
    if (points.length === 0) return;
    if (points.length === 1) {
      setCameraCenter(points[0]);
      setCameraZoom(15);
      return;
    }
    const lats = points.map((p) => p.lat);
    const lngs = points.map((p) => p.lng);
    const minLat = Math.min(...lats);
    const maxLat = Math.max(...lats);
    const minLng = Math.min(...lngs);
    const maxLng = Math.max(...lngs);
    const mid: GeoPoint = {
      lat: (minLat + maxLat) / 2,
      lng: (minLng + maxLng) / 2,
    };
    const span = Math.max(maxLat - minLat, maxLng - minLng, 0.005);
    const z = Math.max(11, Math.min(15.2, Math.log2(0.35 / span) + 11.5));
    setCameraCenter(mid);
    setCameraZoom(z);
  }, []);

  const enterReview = useCallback(
    (p: PointSelection, d: PointSelection) => {
      setQuoteUi({ kind: 'Idle' });
      fitBoundsToPoints([p.point, d.point]);
      const straightMeters = Math.round(Geo.metersBetween(p.point, d.point));
      if (isDemo) {
        setRouteUi({ kind: 'DemoOnly', straightMeters });
      } else {
        setRouteUi({ kind: 'Loading' });
        setTimeout(() => {
          const mid1: GeoPoint = {
            lat: p.point.lat + (d.point.lat - p.point.lat) * 0.35 + 0.0035,
            lng: p.point.lng + (d.point.lng - p.point.lng) * 0.35 - 0.002,
          };
          const mid2: GeoPoint = {
            lat: p.point.lat + (d.point.lat - p.point.lat) * 0.7 + 0.0018,
            lng: p.point.lng + (d.point.lng - p.point.lng) * 0.7 + 0.0015,
          };
          const roadDist = Math.max(1200, Math.round(straightMeters * 1.28));
          const durationS = Math.max(240, Math.round(roadDist / 7.5));
          setRouteUi({
            kind: 'Ready',
            route: {
              points: [p.point, mid1, mid2, d.point],
              distanceM: roadDist,
              durationS,
            },
          });
        }, 250);
      }
    },
    [fitBoundsToPoints, isDemo]
  );

  // Re-evaluate review mode if user toggles isDemo while in Review
  useEffect(() => {
    if (stage === 'Review' && pickup && dropoff) {
      enterReview(pickup, dropoff);
    }
  }, [isDemo, stage, pickup, dropoff, enterReview]);

  const handleConfirmPin = () => {
    if (!pinSelection) return;
    if (stage === 'Pickup') {
      setPickup(pinSelection);
      setSearchQuery('');
      if (dropoff) {
        setStage('Review');
        enterReview(pinSelection, dropoff);
      } else {
        setStage('Dropoff');
        // Shift camera slightly toward Karrada so dropoff isn't identical to pickup by default
        const offsetPoint: GeoPoint = {
          lat: pinSelection.point.lat - 0.015,
          lng: pinSelection.point.lng + 0.02,
        };
        setCameraCenter(offsetPoint);
      }
    } else if (stage === 'Dropoff') {
      setDropoff(pinSelection);
      setSearchQuery('');
      setStage('Review');
      if (pickup) {
        enterReview(pickup, pinSelection);
      }
    }
  };

  const handleEditPickup = () => {
    setRouteUi({ kind: 'Idle' });
    setQuoteUi({ kind: 'Idle' });
    setStage('Pickup');
    if (pickup) {
      setCameraCenter(pickup.point);
      setCameraZoom(15);
    }
  };

  const handleEditDropoff = () => {
    setRouteUi({ kind: 'Idle' });
    setQuoteUi({ kind: 'Idle' });
    setStage('Dropoff');
    const focus = dropoff?.point ?? pickup?.point;
    if (focus) {
      setCameraCenter(focus);
      setCameraZoom(15);
    }
  };

  const handlePlaceChosen = (place: Place) => {
    setSearchQuery('');
    setCameraCenter(place.point);
    setCameraZoom(15.5);
    setSheetValue(SheetValue.Half);
  };

  const handleRequestQuote = () => {
    if (isDemo || routeUi.kind !== 'Ready' || quoteUi.kind === 'Loading') return;
    setQuoteUi({ kind: 'Loading' });
    setTimeout(() => {
      const dist = routeUi.route.distanceM;
      const rawFare = Math.max(3000, Math.round((2500 + (dist / 1000) * 650) / 500) * 500);
      const now = Date.now();
      setNowMs(now);
      const quote: FareQuote = {
        id: '6f1c2b9e-1d2a-4c3b-9a8d-0e1f2a3b4c5d',
        estimatedFareIqd: rawFare,
        currency: 'IQD',
        distanceM: dist,
        durationS: routeUi.route.durationS,
        expiresAtLocalMs: now + 180_000,
        cityId: city.id,
        option: {
          vehicleClass: 'taxi',
          vehicleClassId: 2,
          nameAr: 'تكسي',
          maxPassengers: 4,
          fareIqd: rawFare,
          availableCaptains: 3,
        },
      };
      setQuoteUi({ kind: 'Ready', quote });
    }, 300);
  };

  const handleRequestTrip = () => {
    if (isDemo || quoteUi.kind !== 'Ready' || !pickup || !dropoff || bookingSubmitting) {
      return;
    }
    setBookingSubmitting(true);
    setTimeout(() => {
      setBookingSubmitting(false);
      onOpenTrip(pickup, dropoff, quoteUi.quote);
    }, 350);
  };

  const handleLocate = () => {
    if (locateState === 'Locating') return;
    if (!navigator.geolocation) {
      setLocateState('Unavailable');
      return;
    }
    setLocateState('Locating');
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const pt: GeoPoint = {
          lat: pos.coords.latitude,
          lng: pos.coords.longitude,
        };
        if (stage === 'Review') handleEditPickup();
        // If user's browser location is outside Iraq, center on Mansour in Baghdad so they stay on the map
        const inBaghdad = Geo.metersBetween(pt, city.center) < 60_000;
        const target = inBaghdad ? pt : { lat: 33.32297, lng: 44.35253 };
        setCameraCenter(target);
        setCameraZoom(15.5);
        setLocateState('Idle');
      },
      () => {
        // Move to Mansour on fallback or show permission message
        setLocateState('Denied');
      },
      { timeout: 5000 }
    );
  };

  const formatPointLabel = (sel: PointSelection | null): string | null => {
    if (!sel) return null;
    if (sel.near) {
      return t(lang, 'near_place', placeDisplayName(sel.near, arabic));
    }
    return t(lang, 'map_point');
  };

  const pickupValue = stage === 'Pickup' ? pinSelection : pickup;
  const dropoffValue = stage === 'Dropoff' ? pinSelection : dropoff;

  const markers: MapMarker[] = useMemo(() => {
    const list: MapMarker[] = [];
    if (stage !== 'Pickup' && pickup) {
      list.push({ point: pickup.point, kind: 'Pickup' });
    }
    if (stage !== 'Dropoff' && dropoff) {
      list.push({ point: dropoff.point, kind: 'Dropoff' });
    }
    return list;
  }, [stage, pickup, dropoff]);

  const mapRoute: MapRoute | null = useMemo(() => {
    if (routeUi.kind === 'Ready') {
      return { points: routeUi.route.points, style: 'ServerRoad' };
    }
    if (routeUi.kind === 'DemoOnly' && pickup && dropoff) {
      return {
        points: [pickup.point, dropoff.point],
        style: 'DemoStraight',
      };
    }
    return null;
  }, [routeUi, pickup, dropoff]);

  const overlay: ServiceOverlay = useMemo(
    () => ({
      outline: serviceAreas?.outline(city.code),
      center: city.center,
      radiusM: city.serviceRadiusM,
    }),
    [serviceAreas, city]
  );

  const headerHeight = 104;

  return (
    <YabahBottomSheetLayout
      value={sheetValue}
      onChange={setSheetValue}
      peekHeight={196}
      expandedTopInset={64}
      handleDesc={t(lang, 'sheet_handle_desc')}
      expandLabel={t(lang, 'sheet_expand')}
      collapseLabel={t(lang, 'sheet_collapse')}
      background={(visibleSheetHeight) => {
        const mapBottom = Math.min(visibleSheetHeight, 440);
        return (
          <div className="relative w-full h-full">
            <DemoMapCanvas
              center={cameraCenter}
              zoom={cameraZoom}
              markers={markers}
              route={mapRoute}
              overlay={overlay}
              places={places}
              arabic={arabic}
              darkTheme={darkTheme}
              topPadding={headerHeight}
              bottomPadding={mapBottom}
              attributionText={t(lang, 'demo_map_attribution')}
              mapDescription={t(lang, 'map_desc')}
              onCameraMove={(c, z, gest) => {
                setCameraCenter(c);
                setCameraZoom(z);
                setIsGesturing(gest);
              }}
              onTap={(pt) => {
                if (stage !== 'Review') {
                  setCameraCenter(pt);
                }
              }}
            />

            {stage !== 'Review' && (
              <CenterPin
                isPickup={stage === 'Pickup'}
                lifted={isGesturing}
                description={t(
                  lang,
                  stage === 'Pickup' ? 'pin_pickup_desc' : 'pin_dropoff_desc'
                )}
                topPadding={headerHeight}
                bottomPadding={mapBottom}
              />
            )}

            {/* Top Header */}
            <header className="absolute top-0 inset-x-0 z-10 px-4 pt-3 pb-2 flex flex-col gap-2 pointer-events-none">
              <div className="flex items-center justify-between">
                <div className="pointer-events-auto inline-flex items-center rounded-full bg-white dark:bg-[#18263B] shadow-md ps-1.5 pe-3.5 py-1">
                  <YabahMark size={36} />
                  <span className="text-[16px] leading-[24px] font-semibold text-[#101B2C] dark:text-[#F8F7F3]">
                    {t(lang, 'app_name')}
                  </span>
                  <span className="ms-2 text-[14px] leading-[22px] text-[#627186] dark:text-[#A9B4C4]">
                    · {arabic ? city.nameAr : city.nameEn}
                  </span>
                </div>

                <button
                  type="button"
                  onClick={onOpenSettings}
                  data-testid="settings_button"
                  aria-label={t(lang, 'settings_desc')}
                  className="pointer-events-auto w-11 h-11 rounded-full bg-white dark:bg-[#18263B] shadow-md flex items-center justify-center text-[#101B2C] dark:text-[#F8F7F3] hover:bg-black/5 dark:hover:bg-white/5 cursor-pointer"
                >
                  <Settings className="w-5 h-5" />
                </button>
              </div>

              <div className="flex items-center flex-wrap gap-2">
                {isDemo && (
                  <DemoChip label={t(lang, 'demo_badge')} testId="demo_chip" />
                )}
                {!online && (
                  <StatusBadge
                    text={t(lang, 'offline_badge')}
                    tone="Danger"
                    testId="offline_badge"
                  />
                )}
                <StatusBadge
                  text={t(lang, 'demo_map_notice')}
                  tone="Info"
                  testId="simplified_map_badge"
                />
              </div>
            </header>

            {/* My Location FAB */}
            {sheetValue !== SheetValue.Expanded && (
              <button
                type="button"
                onClick={handleLocate}
                data-testid="locate_button"
                aria-label={t(lang, 'my_location')}
                style={{ bottom: `${mapBottom + 12}px` }}
                className="absolute end-4 z-10 w-12 h-12 rounded-full bg-white dark:bg-[#18263B] text-[#0DAA9B] shadow-md flex items-center justify-center hover:scale-105 active:scale-95 transition-transform cursor-pointer"
              >
                {locateState === 'Locating' ? (
                  <span className="w-5 h-5 border-2 border-[#0DAA9B] border-t-transparent rounded-full animate-spin" />
                ) : (
                  <MyLocationIcon size={22} />
                )}
              </button>
            )}
          </div>
        );
      }}
    >
      {/* Greeting when picking pickup */}
      {stage === 'Pickup' && pickup === null && (
        <div className="pb-1">
          <h2 className="text-[20px] leading-[30px] font-semibold text-[#101B2C] dark:text-[#F8F7F3]">
            {t(lang, 'home_greeting')}
          </h2>
          <p className="text-[14px] leading-[22px] text-[#627186] dark:text-[#A9B4C4]">
            {t(lang, 'home_question')}
          </p>
        </div>
      )}

      {/* Pickup & Dropoff Rows */}
      <LocationRow
        label={t(lang, 'pickup_label')}
        value={formatPointLabel(pickupValue)}
        placeholder={t(lang, 'pickup_hint')}
        dotColor="#0DAA9B"
        active={stage === 'Pickup'}
        onClick={stage !== 'Pickup' ? handleEditPickup : undefined}
        showEdit={stage !== 'Pickup' && pickup !== null}
        editLabel={t(lang, 'edit')}
        testId="pickup_row"
      />

      <LocationRow
        label={t(lang, 'dropoff_label')}
        value={formatPointLabel(dropoffValue)}
        placeholder={t(lang, 'dropoff_hint')}
        dotColor="#E8B65A"
        active={stage === 'Dropoff'}
        onClick={
          stage === 'Review' || (stage === 'Pickup' && dropoff !== null)
            ? handleEditDropoff
            : undefined
        }
        showEdit={stage === 'Review'}
        editLabel={t(lang, 'edit')}
        testId="dropoff_row"
      />

      {/* Picking section vs Review section */}
      {(stage === 'Pickup' || stage === 'Dropoff') && (
        <div className="flex flex-col gap-2.5 pt-1">
          <p className="text-[12px] leading-[18px] text-[#627186] dark:text-[#A9B4C4]">
            {t(lang, 'move_map_hint')}
          </p>

          {/* Search box */}
          <div className="relative w-full">
            <Search className="w-5 h-5 text-[#627186] dark:text-[#A9B4C4] absolute start-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
            <input
              type="search"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              onFocus={() => setSheetValue(SheetValue.Expanded)}
              placeholder={t(lang, 'search_places')}
              aria-label={t(lang, 'search_places')}
              data-testid="search_field"
              className="w-full h-13 ps-11 pe-10 rounded-[16px] border border-[#B9C0CB] dark:border-[#51617A] bg-white dark:bg-[#18263B] text-[15px] text-[#101B2C] dark:text-[#F8F7F3] placeholder:text-[#627186] dark:placeholder:text-[#A9B4C4] focus:outline-none focus:border-[#0DAA9B]"
            />
            {searchQuery.length > 0 && (
              <button
                type="button"
                onClick={() => setSearchQuery('')}
                aria-label={t(lang, 'close')}
                className="absolute end-2.5 top-1/2 -translate-y-1/2 w-8 h-8 rounded-full flex items-center justify-center text-[#627186] hover:bg-black/5 cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            )}
          </div>

          {/* Search results */}
          {searchQuery.trim().length >= 2 && (
            <div className="flex flex-col gap-1.5">
              {searchResults.length === 0 ? (
                <p className="text-[14px] text-[#627186] dark:text-[#A9B4C4] py-1">
                  {t(lang, 'search_no_results')}
                </p>
              ) : (
                <div className="w-full rounded-[16px] border border-[#E2E0D9] dark:border-[#2A3B55] divide-y divide-[#E2E0D9] dark:divide-[#2A3B55] overflow-hidden bg-white dark:bg-[#18263B]">
                  {searchResults.map((pl, idx) => {
                    const primary = placeDisplayName(pl, arabic);
                    const other = (arabic ? pl.nameEn : pl.nameAr)?.trim();
                    const distMeters = Math.round(
                      Geo.metersBetween(city.center, pl.point)
                    );
                    const fromCentre = t(
                      lang,
                      'km_from_centre',
                      Format.km(distMeters, arabic)
                    );
                    const secondary = [
                      other && other !== primary ? other : null,
                      fromCentre,
                    ]
                      .filter(Boolean)
                      .join(' · ');

                    return (
                      <button
                        key={`${pl.nameEn}-${idx}`}
                        type="button"
                        onClick={() => handlePlaceChosen(pl)}
                        data-testid="search_result"
                        className="w-full min-h-[52px] px-3 py-2 flex items-center gap-2.5 text-start hover:bg-black/[0.03] dark:hover:bg-white/[0.04] transition-colors cursor-pointer"
                      >
                        <MapPin className="w-[18px] h-[18px] text-[#627186] dark:text-[#A9B4C4] shrink-0" />
                        <div className="flex-1 min-w-0">
                          <div className="text-[16px] leading-[24px] text-[#101B2C] dark:text-[#F8F7F3] truncate">
                            {primary}
                          </div>
                          {secondary && (
                            <div className="text-[12px] leading-[18px] text-[#627186] dark:text-[#A9B4C4] truncate">
                              {secondary}
                            </div>
                          )}
                        </div>
                      </button>
                    );
                  })}
                </div>
              )}
              <span className="text-[11px] leading-[16px] text-[#627186] dark:text-[#A9B4C4]">
                {t(lang, 'search_source')}
              </span>
            </div>
          )}

          {locateState === 'Denied' && (
            <MessageCard
              message={t(lang, 'location_permission_denied')}
              tone="Warn"
              actionLabel={t(lang, 'close')}
              onAction={() => setLocateState('Idle')}
            />
          )}
          {locateState === 'Unavailable' && (
            <MessageCard
              message={t(lang, 'location_unavailable')}
              tone="Warn"
              actionLabel={t(lang, 'close')}
              onAction={() => setLocateState('Idle')}
            />
          )}

          {pinOutsideArea && (
            <MessageCard
              message={t(lang, 'outside_area_local')}
              tone="Warn"
              testId="outside_area"
            />
          )}

          <YabahPrimaryButton
            text={t(
              lang,
              stage === 'Pickup' ? 'confirm_pickup' : 'confirm_dropoff'
            )}
            onClick={handleConfirmPin}
            enabled={pinSelection !== null}
            testId="confirm_button"
          />
        </div>
      )}

      {stage === 'Review' && (
        <div className="flex flex-col gap-2.5 pt-1">
          {routeUi.kind === 'Loading' && (
            <div
              data-testid="route_loading"
              className="flex items-center gap-2 py-1 text-[14px] text-[#101B2C] dark:text-[#F8F7F3]"
            >
              <span className="w-4 h-4 border-2 border-[#0DAA9B] border-t-transparent rounded-full animate-spin" />
              <span>{t(lang, 'route_loading')}</span>
            </div>
          )}

          {routeUi.kind === 'Ready' && (
            <div
              data-testid="route_ready"
              className="flex items-center justify-between gap-2 py-0.5"
            >
              <StatusBadge
                text={t(lang, 'route_from_server')}
                tone="Success"
              />
              <span className="text-[14px] leading-[20px] font-semibold text-[#101B2C] dark:text-[#F8F7F3]">
                {t(lang, 'distance_km', Format.km(routeUi.route.distanceM, arabic))} ·{' '}
                {t(
                  lang,
                  'duration_min',
                  Format.int(Format.minutes(routeUi.route.durationS), arabic)
                )}
              </span>
            </div>
          )}

          {routeUi.kind === 'DemoOnly' && (
            <>
              <MessageCard
                message={t(lang, 'demo_route_unavailable')}
                tone="Demo"
                testId="demo_route_notice"
              />
              <p className="text-[12px] leading-[18px] text-[#627186] dark:text-[#A9B4C4]">
                {t(
                  lang,
                  'straight_distance',
                  t(lang, 'distance_km', Format.km(routeUi.straightMeters, arabic))
                )}{' '}
                · {t(lang, 'demo_straight_line')}
              </p>
            </>
          )}

          {isDemo ? (
            <>
              <span className="text-[13px] leading-[18px] font-medium text-[#627186] dark:text-[#A9B4C4]">
                {t(lang, 'ride_classes_demo_note')}
              </span>
              <div className="flex flex-col gap-2">
                {DEMO_CLASSES.map((c) => (
                  <RideOptionCard
                    key={c.code}
                    name={arabic ? c.nameAr : c.nameEn}
                    seats={c.maxPassengers}
                    price={null}
                    selected={c.code === 'taxi'}
                    priceNote={t(lang, 'demo_price_placeholder')}
                    lang={lang}
                    arabic={arabic}
                  />
                ))}
              </div>
              <YabahPrimaryButton
                text={t(lang, 'demo_cannot_book')}
                onClick={() => {}}
                enabled={false}
                testId="request_button"
              />
            </>
          ) : (
            <>
              <RideOptionCard
                name={
                  (quoteUi.kind === 'Ready'
                    ? quoteUi.quote.option?.nameAr
                    : null) ?? t(lang, 'ride_taxi_fallback')
                }
                seats={
                  quoteUi.kind === 'Ready'
                    ? (quoteUi.quote.option?.maxPassengers ?? 4)
                    : 4
                }
                price={
                  quoteUi.kind === 'Ready'
                    ? displayFareIqd(quoteUi.quote)
                    : null
                }
                selected={true}
                priceNote={
                  quoteUi.kind === 'Ready'
                    ? t(lang, 'server_price_note')
                    : null
                }
                lang={lang}
                arabic={arabic}
              />

              {quoteUi.kind === 'Idle' && (
                <YabahSecondaryButton
                  text={t(lang, 'show_price')}
                  onClick={handleRequestQuote}
                  enabled={routeUi.kind === 'Ready'}
                  testId="quote_button"
                />
              )}

              {quoteUi.kind === 'Loading' && (
                <div className="flex items-center gap-2 py-1 text-[14px]">
                  <span className="w-4 h-4 border-2 border-[#0DAA9B] border-t-transparent rounded-full animate-spin" />
                  <span>{t(lang, 'price_loading')}</span>
                </div>
              )}

              {quoteUi.kind === 'Ready' && (
                <div
                  data-testid="quote_countdown"
                  className="text-[12px] leading-[18px] text-[#627186] dark:text-[#A9B4C4]"
                >
                  {t(
                    lang,
                    'price_expires_in',
                    Format.mmss(
                      Math.max(0, (quoteUi.quote.expiresAtLocalMs - nowMs) / 1000),
                      arabic
                    )
                  )}
                </div>
              )}

              {quoteUi.kind === 'Expired' && (
                <MessageCard
                  message={t(lang, 'price_expired')}
                  tone="Warn"
                  actionLabel={t(lang, 'refresh_price')}
                  onAction={handleRequestQuote}
                  testId="quote_expired"
                />
              )}

              <div>
                <StatusBadge text={t(lang, 'payment_cash')} tone="Info" />
              </div>

              <YabahPrimaryButton
                text={t(
                  lang,
                  bookingSubmitting ? 'requesting' : 'request_ride'
                )}
                onClick={handleRequestTrip}
                enabled={
                  quoteUi.kind === 'Ready' &&
                  routeUi.kind === 'Ready' &&
                  !bookingSubmitting
                }
                loading={bookingSubmitting}
                testId="request_button"
              />
            </>
          )}
        </div>
      )}

      {isDemo && (
        <MessageCard
          message={t(lang, 'demo_banner')}
          tone="Demo"
          testId="demo_notice"
        />
      )}
    </YabahBottomSheetLayout>
  );
};

const RideOptionCard: React.FC<{
  name: string;
  seats: number | null;
  price: number | null;
  selected: boolean;
  priceNote: string | null;
  lang: Lang;
  arabic: boolean;
}> = ({ name, seats, price, selected, priceNote, lang, arabic }) => (
  <div
    className={`w-full rounded-[16px] p-3 flex items-center gap-3 bg-white dark:bg-[#18263B] ${
      selected
        ? 'border-2 border-[#0DAA9B]'
        : 'border border-[#E2E0D9] dark:border-[#2A3B55]'
    }`}
  >
    <div className="w-11 h-11 rounded-full bg-[#D3F1ED] dark:bg-[#0A4A44] text-[#00413B] dark:text-[#B9EDE6] flex items-center justify-center shrink-0">
      <CarIcon size={24} />
    </div>
    <div className="flex-1 min-w-0">
      <div className="text-[16px] leading-[24px] font-semibold text-[#101B2C] dark:text-[#F8F7F3]">
        {name}
      </div>
      {seats !== null && (
        <div className="text-[12px] leading-[18px] text-[#627186] dark:text-[#A9B4C4]">
          {t(lang, 'ride_class_seats', Format.int(seats, arabic))}
        </div>
      )}
    </div>
    <div className="flex flex-col items-end text-end">
      {price !== null && (
        <div
          data-testid="fare_amount"
          className="text-[20px] leading-[30px] font-semibold text-[#101B2C] dark:text-[#F8F7F3]"
        >
          {t(lang, 'currency_iqd', Format.iqd(price, arabic))}
        </div>
      )}
      {priceNote && (
        <div className="text-[11px] leading-[16px] text-[#627186] dark:text-[#A9B4C4]">
          {priceNote}
        </div>
      )}
    </div>
  </div>
);
