import React, { useState } from 'react';
import { Lang, t } from '../i18n/strings';
import { YabahMark, YabahPrimaryButton } from '../components/UiPrimitives';

interface OnboardingScreenProps {
  lang: Lang;
  onFinished: () => void;
}

const PAGES = [
  { title: 'onb1_title', body: 'onb1_body' },
  { title: 'onb2_title', body: 'onb2_body' },
  { title: 'onb3_title', body: 'onb3_body' },
] as const;

export const OnboardingScreen: React.FC<OnboardingScreenProps> = ({
  lang,
  onFinished,
}) => {
  const [page, setPage] = useState(0);
  const isLast = page === PAGES.length - 1;

  return (
    <main
      data-testid="onboarding"
      className="w-full h-full bg-[#101B2C] text-[#F8F7F3] flex flex-col items-center justify-between p-6 select-none"
    >
      {/* Top row with Skip */}
      <div className="w-full flex items-center justify-end">
        <button
          type="button"
          onClick={onFinished}
          data-testid="onboarding_skip"
          className="px-3 py-2 rounded-lg text-[15px] font-semibold text-[#F8F7F3] hover:bg-white/10 transition-colors cursor-pointer"
        >
          {t(lang, 'onb_skip')}
        </button>
      </div>

      {/* Center slide content */}
      <div className="flex-1 flex flex-col items-center justify-center text-center max-w-md px-4">
        <YabahMark size={140} />
        <h1 className="mt-6 text-[26px] leading-[36px] font-bold text-[#F8F7F3]">
          {t(lang, PAGES[page].title)}
        </h1>
        <p className="mt-3 text-[16px] leading-[26px] text-[#F8F7F3]/80">
          {t(lang, PAGES[page].body)}
        </p>
      </div>

      {/* Bottom dots + Next / Start button */}
      <div className="w-full max-w-md flex flex-col items-center">
        <div className="flex items-center justify-center gap-2 py-4" role="tablist">
          {PAGES.map((_, idx) => (
            <button
              key={idx}
              type="button"
              role="tab"
              aria-selected={idx === page}
              aria-label={`Slide ${idx + 1}`}
              onClick={() => setPage(idx)}
              className={`h-2 rounded-full transition-all duration-200 cursor-pointer ${
                idx === page ? 'w-[22px] bg-[#E8B65A]' : 'w-2 bg-[#F8F7F3]/30'
              }`}
            />
          ))}
        </div>

        <YabahPrimaryButton
          text={t(lang, isLast ? 'onb_start' : 'onb_next')}
          onClick={() => {
            if (isLast) onFinished();
            else setPage((p) => p + 1);
          }}
          testId="onboarding_next"
        />
      </div>
    </main>
  );
};
