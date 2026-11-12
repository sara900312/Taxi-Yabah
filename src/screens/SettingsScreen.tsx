import React, { useState, useEffect } from 'react';
import { ArrowLeft, ArrowRight } from 'lucide-react';
import { Lang, t } from '../i18n/strings';
import { PhoneNumbers } from '../core/rules';
import { KeyValueRow, YabahSecondaryButton } from '../components/UiPrimitives';

interface SettingsScreenProps {
  lang: Lang;
  langPref: 'system' | 'ar' | 'en';
  onSetLangPref: (pref: 'system' | 'ar' | 'en') => void;
  darkTheme: boolean;
  onSetDarkTheme: (dark: boolean) => void;
  isDemo: boolean;
  onSetIsDemo: (demo: boolean) => void;
  phoneE164: string | null;
  onBack: () => void;
  onOpenCredits: () => void;
  onSignOut: () => void;
}

export const SettingsScreen: React.FC<SettingsScreenProps> = ({
  lang,
  langPref,
  onSetLangPref,
  darkTheme,
  onSetDarkTheme,
  isDemo,
  onSetIsDemo,
  phoneE164,
  onBack,
  onOpenCredits,
  onSignOut,
}) => {
  const arabic = lang === 'ar';
  const BackIcon = arabic ? ArrowRight : ArrowLeft;

  const langOptions: { tag: 'system' | 'ar' | 'en'; label: string }[] = [
    { tag: 'system', label: 'System / النظام' },
    { tag: 'ar', label: t(lang, 'lang_ar') },
    { tag: 'en', label: t(lang, 'lang_en') },
  ];

  return (
    <main
      data-testid="settings_screen"
      className="w-full h-full overflow-y-auto bg-[#F8F7F3] dark:bg-[#101B2C] text-[#101B2C] dark:text-[#F8F7F3] p-4 flex flex-col gap-3"
    >
      <div className="w-full max-w-lg mx-auto flex flex-col gap-3">
        {/* Header */}
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={onBack}
            aria-label={t(lang, 'back')}
            data-testid="settings_back"
            className="w-11 h-11 rounded-full flex items-center justify-center hover:bg-black/5 dark:hover:bg-white/5 cursor-pointer"
          >
            <BackIcon className="w-5 h-5" />
          </button>
          <h1 className="text-[20px] leading-[30px] font-semibold">
            {t(lang, 'settings_title')}
          </h1>
        </div>

        {/* Language Section */}
        <h2 className="text-[16px] leading-[24px] font-semibold pt-1">
          {t(lang, 'language')}
        </h2>
        <div className="flex flex-col" role="radiogroup" aria-label={t(lang, 'language')}>
          {langOptions.map((opt) => {
            const selected = langPref === opt.tag;
            return (
              <button
                key={opt.tag}
                type="button"
                role="radio"
                aria-checked={selected}
                onClick={() => onSetLangPref(opt.tag)}
                data-testid={`lang_${opt.tag}`}
                className="w-full min-h-[48px] flex items-center gap-3 px-2 rounded-xl hover:bg-black/5 dark:hover:bg-white/5 text-start cursor-pointer"
              >
                <span
                  className={`w-5 h-5 rounded-full border-2 flex items-center justify-center ${
                    selected
                      ? 'border-[#0DAA9B]'
                      : 'border-[#627186] dark:border-[#A9B4C4]'
                  }`}
                >
                  {selected && (
                    <span className="w-2.5 h-2.5 rounded-full bg-[#0DAA9B]" />
                  )}
                </span>
                <span className="text-[16px] leading-[26px]">{opt.label}</span>
              </button>
            );
          })}
        </div>

        <hr className="border-[#E2E0D9] dark:border-[#2A3B55]" />

        {/* Theme Section */}
        <h2 className="text-[16px] leading-[24px] font-semibold">
          {t(lang, 'theme_label')}
        </h2>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => onSetDarkTheme(false)}
            data-testid="theme_light"
            className={`flex-1 min-h-[44px] rounded-[12px] border text-[14px] font-medium cursor-pointer ${
              !darkTheme
                ? 'border-2 border-[#0DAA9B] bg-white text-[#101B2C]'
                : 'border-[#51617A] text-[#A9B4C4]'
            }`}
          >
            {t(lang, 'theme_light')}
          </button>
          <button
            type="button"
            onClick={() => onSetDarkTheme(true)}
            data-testid="theme_dark"
            className={`flex-1 min-h-[44px] rounded-[12px] border text-[14px] font-medium cursor-pointer ${
              darkTheme
                ? 'border-2 border-[#0DAA9B] bg-[#18263B] text-[#F8F7F3]'
                : 'border-[#B9C0CB] text-[#627186]'
            }`}
          >
            {t(lang, 'theme_dark')}
          </button>
        </div>

        <hr className="border-[#E2E0D9] dark:border-[#2A3B55]" />

        {/* Mode & Build info */}
        <div className="flex flex-col gap-2">
          <KeyValueRow
            label={t(lang, 'mode_label')}
            value={t(lang, isDemo ? 'mode_demo' : 'mode_live')}
          />
          <div className="flex gap-2 py-1">
            <button
              type="button"
              onClick={() => onSetIsDemo(true)}
              data-testid="mode_switch_demo"
              className={`flex-1 py-1.5 px-3 rounded-lg text-[13px] font-medium cursor-pointer ${
                isDemo
                  ? 'bg-[#E8B65A] text-[#101B2C]'
                  : 'bg-[#F0EEE8] dark:bg-[#213149] text-[#627186] dark:text-[#A9B4C4]'
              }`}
            >
              {t(lang, 'mode_demo')}
            </button>
            <button
              type="button"
              onClick={() => onSetIsDemo(false)}
              data-testid="mode_switch_live"
              className={`flex-1 py-1.5 px-3 rounded-lg text-[13px] font-medium cursor-pointer ${
                !isDemo
                  ? 'bg-[#0DAA9B] text-white'
                  : 'bg-[#F0EEE8] dark:bg-[#213149] text-[#627186] dark:text-[#A9B4C4]'
              }`}
            >
              {t(lang, 'mode_live')}
            </button>
          </div>
          <KeyValueRow
            label={t(lang, 'map_engine')}
            value={t(lang, 'map_engine_demo')}
          />
          {phoneE164 && (
            <KeyValueRow
              label={t(lang, 'phone_label')}
              value={PhoneNumbers.display(phoneE164)}
            />
          )}
          <KeyValueRow label="" value={t(lang, 'build_info', '0.1.0-native')} />
        </div>

        <hr className="border-[#E2E0D9] dark:border-[#2A3B55]" />

        <button
          type="button"
          onClick={onOpenCredits}
          data-testid="data_credits_button"
          className="w-full min-h-[48px] py-2 text-start text-[16px] leading-[26px] font-medium text-[#0DAA9B] hover:underline cursor-pointer"
        >
          {t(lang, 'data_credits')}
        </button>

        <YabahSecondaryButton
          text={t(lang, isDemo ? 'exit_demo' : 'sign_out')}
          onClick={onSignOut}
          testId="signout_button"
        />
      </div>
    </main>
  );
};

export const CreditsScreen: React.FC<{
  lang: Lang;
  onBack: () => void;
}> = ({ lang, onBack }) => {
  const arabic = lang === 'ar';
  const BackIcon = arabic ? ArrowRight : ArrowLeft;
  const [creditsText, setCreditsText] = useState<string>('Loading…');

  useEffect(() => {
    Promise.all([
      fetch('/licenses/DATA_CREDITS.txt').then((r) => (r.ok ? r.text() : '')),
      fetch('/licenses/IBM_PLEX_OFL.txt').then((r) => (r.ok ? r.text() : '')),
    ])
      .then(([dataCredits, ofl]) => {
        setCreditsText(`${dataCredits}\n\n————\n\n${ofl}`);
      })
      .catch(() => {
        setCreditsText(
          'Governorate outlines: Iraq CSO / OCHA COD-AB (CC BY 3.0 IGO).\nPlace names: GeoNames (CC BY 4.0).\nFont: IBM Plex Sans Arabic (SIL OFL 1.1).'
        );
      });
  }, []);

  return (
    <main
      data-testid="credits_screen"
      className="w-full h-full overflow-y-auto bg-[#F8F7F3] dark:bg-[#101B2C] text-[#101B2C] dark:text-[#F8F7F3] p-4"
    >
      <div className="w-full max-w-2xl mx-auto flex flex-col gap-3">
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={onBack}
            aria-label={t(lang, 'back')}
            className="w-11 h-11 rounded-full flex items-center justify-center hover:bg-black/5 dark:hover:bg-white/5 cursor-pointer"
          >
            <BackIcon className="w-5 h-5" />
          </button>
          <h1 className="text-[20px] leading-[30px] font-semibold">
            {t(lang, 'data_credits')}
          </h1>
        </div>

        <pre
          dir="ltr"
          className="whitespace-pre-wrap font-sans text-[12px] leading-[18px] text-[#627186] dark:text-[#A9B4C4] bg-white dark:bg-[#18263B] p-4 rounded-[16px] border border-[#E2E0D9] dark:border-[#2A3B55]"
        >
          {creditsText}
        </pre>
      </div>
    </main>
  );
};
