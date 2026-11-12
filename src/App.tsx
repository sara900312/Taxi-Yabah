import React, { useState, useEffect } from 'react';
import { FareQuote, GeoPoint, placeDisplayName } from './core/models';
import { PlaceIndex, Ring, ServiceAreaRule } from './core/rules';
import { Lang } from './i18n/strings';
import { OnboardingScreen } from './screens/OnboardingScreen';
import { SignInScreen } from './screens/SignInScreen';
import { PassengerHomeScreen } from './screens/PassengerHomeScreen';
import { TripScreen } from './screens/TripScreen';
import { SettingsScreen, CreditsScreen } from './screens/SettingsScreen';

type RouteName = 'onboarding' | 'signin' | 'home' | 'trip' | 'settings' | 'credits';

interface ActiveTripContext {
  pickup: GeoPoint;
  pickupAddress: string;
  dropoff: GeoPoint;
  dropoffAddress: string;
  quote: FareQuote;
}

export const App: React.FC = () => {
  const autostart =
    typeof window !== 'undefined' &&
    new URLSearchParams(window.location.search).get('autostart') === '1';

  const [langPref, setLangPref] = useState<'system' | 'ar' | 'en'>('ar');
  const [darkTheme, setDarkTheme] = useState<boolean>(false);
  const [isDemo, setIsDemo] = useState<boolean>(true);
  const [phoneE164, setPhoneE164] = useState<string | null>(null);
  const [route, setRoute] = useState<RouteName>(autostart ? 'home' : 'onboarding');
  const [prevRoute, setPrevRoute] = useState<RouteName>('home');
  const [activeTrip, setActiveTrip] = useState<ActiveTripContext | null>(null);

  const [places, setPlaces] = useState<PlaceIndex | null>(null);
  const [serviceAreas, setServiceAreas] = useState<ServiceAreaRule | null>(null);

  const lang: Lang = langPref === 'en' ? 'en' : 'ar';

  useEffect(() => {
    document.documentElement.lang = lang;
    document.documentElement.dir = lang === 'ar' ? 'rtl' : 'ltr';
  }, [lang]);

  useEffect(() => {
    if (darkTheme) {
      document.documentElement.classList.add('dark');
    } else {
      document.documentElement.classList.remove('dark');
    }
  }, [darkTheme]);

  // Load bundled GeoNames places and OCHA service outlines
  useEffect(() => {
    fetch('/places/baghdad.json')
      .then((r) => (r.ok ? r.json() : []))
      .then((raw) => {
        if (Array.isArray(raw)) {
          setPlaces(PlaceIndex.parse(raw));
        }
      })
      .catch(() => {});

    fetch('/geo/service_outlines.json')
      .then((r) => (r.ok ? r.json() : {}))
      .then((raw: Record<string, Ring>) => {
        if (raw && typeof raw === 'object') {
          setServiceAreas(new ServiceAreaRule(raw));
        }
      })
      .catch(() => {});
  }, []);

  return (
    <div
      dir={lang === 'ar' ? 'rtl' : 'ltr'}
      className={`w-screen h-screen overflow-hidden ${darkTheme ? 'dark bg-[#101B2C] text-[#F8F7F3]' : 'bg-[#F8F7F3] text-[#101B2C]'}`}
    >
      {route === 'onboarding' && (
        <OnboardingScreen
          lang={lang}
          onFinished={() => setRoute('signin')}
        />
      )}

      {route === 'signin' && (
        <SignInScreen
          lang={lang}
          otpEnabled={false}
          onSignedIn={(phone) => {
            setPhoneE164(phone);
            setIsDemo(false);
            setRoute('home');
          }}
          onTryDemo={() => {
            setIsDemo(true);
            setRoute('home');
          }}
          onTryLiveSim={() => {
            setPhoneE164('+9647701234567');
            setIsDemo(false);
            setRoute('home');
          }}
        />
      )}

      {route === 'home' && (
        <PassengerHomeScreen
          lang={lang}
          darkTheme={darkTheme}
          isDemo={isDemo}
          places={places}
          serviceAreas={serviceAreas}
          onOpenSettings={() => {
            setPrevRoute('home');
            setRoute('settings');
          }}
          onOpenTrip={(pickupSel, dropoffSel, quote) => {
            const arabic = lang === 'ar';
            setActiveTrip({
              pickup: pickupSel.point,
              pickupAddress: pickupSel.near
                ? `قرب ${placeDisplayName(pickupSel.near, arabic)}`
                : 'نقطة على الخريطة',
              dropoff: dropoffSel.point,
              dropoffAddress: dropoffSel.near
                ? `قرب ${placeDisplayName(dropoffSel.near, arabic)}`
                : 'نقطة على الخريطة',
              quote,
            });
            setRoute('trip');
          }}
        />
      )}

      {route === 'trip' && activeTrip && (
        <TripScreen
          lang={lang}
          darkTheme={darkTheme}
          places={places}
          pickup={activeTrip.pickup}
          pickupAddress={activeTrip.pickupAddress}
          dropoff={activeTrip.dropoff}
          dropoffAddress={activeTrip.dropoffAddress}
          quote={activeTrip.quote}
          onDone={() => {
            setActiveTrip(null);
            setRoute('home');
          }}
        />
      )}

      {route === 'settings' && (
        <SettingsScreen
          lang={lang}
          langPref={langPref}
          onSetLangPref={setLangPref}
          darkTheme={darkTheme}
          onSetDarkTheme={setDarkTheme}
          isDemo={isDemo}
          onSetIsDemo={setIsDemo}
          phoneE164={phoneE164}
          onBack={() => setRoute(prevRoute)}
          onOpenCredits={() => setRoute('credits')}
          onSignOut={() => {
            setPhoneE164(null);
            setIsDemo(true);
            setRoute('signin');
          }}
        />
      )}

      {route === 'credits' && (
        <CreditsScreen
          lang={lang}
          onBack={() => setRoute('settings')}
        />
      )}
    </div>
  );
};
