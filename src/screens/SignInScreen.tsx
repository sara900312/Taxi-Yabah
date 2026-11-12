import React, { useState, useEffect } from 'react';
import { Lang, t } from '../i18n/strings';
import { PhoneNumbers } from '../core/rules';
import {
  YabahMark,
  YabahPrimaryButton,
  YabahSecondaryButton,
  MessageCard,
} from '../components/UiPrimitives';

type SignInStep = 'Phone' | 'Code' | 'Name';

interface SignInScreenProps {
  lang: Lang;
  otpEnabled: boolean;
  onSignedIn: (phoneE164: string, fullName: string) => void;
  onTryDemo: () => void;
  onTryLiveSim: () => void;
}

export const SignInScreen: React.FC<SignInScreenProps> = ({
  lang,
  otpEnabled,
  onSignedIn,
  onTryDemo,
  onTryLiveSim,
}) => {
  const [step, setStep] = useState<SignInStep>('Phone');
  const [phoneInput, setPhoneInput] = useState('');
  const [phoneInvalid, setPhoneInvalid] = useState(false);
  const [e164, setE164] = useState<string>('');
  const [codeInput, setCodeInput] = useState('');
  const [nameInput, setNameInput] = useState('');
  const [nameInvalid, setNameInvalid] = useState(false);
  const [resendInSec, setResendInSec] = useState(0);
  const [errorKey, setErrorKey] = useState<string | null>(null);

  useEffect(() => {
    if (resendInSec <= 0) return;
    const timer = setInterval(() => {
      setResendInSec((s) => Math.max(0, s - 1));
    }, 1000);
    return () => clearInterval(timer);
  }, [resendInSec]);

  const handleSendCode = () => {
    if (!otpEnabled) return;
    const parsed = PhoneNumbers.toE164(phoneInput);
    if (!parsed) {
      setPhoneInvalid(true);
      return;
    }
    setE164(parsed);
    setCodeInput('');
    setErrorKey(null);
    setStep('Code');
    setResendInSec(60);
  };

  const handleVerify = () => {
    const code = PhoneNumbers.normalizeOtp(codeInput);
    if (!code) {
      setErrorKey('err_otp_invalid');
      return;
    }
    setErrorKey(null);
    setStep('Name');
  };

  const handleSaveName = () => {
    const trimmed = nameInput.trim();
    if (trimmed.length < 2) {
      setNameInvalid(true);
      return;
    }
    onSignedIn(e164, trimmed);
  };

  return (
    <main
      data-testid="signin_screen"
      className="w-full h-full overflow-y-auto bg-[#F8F7F3] dark:bg-[#101B2C] text-[#101B2C] dark:text-[#F8F7F3] flex flex-col items-center px-6 py-8"
    >
      <div className="w-full max-w-md flex flex-col items-center gap-3 mt-6">
        <YabahMark size={96} />
        <h1 className="text-[26px] leading-[36px] font-bold">
          {t(lang, 'app_name')}
        </h1>
        <p className="text-[16px] leading-[26px] text-[#627186] dark:text-[#A9B4C4]">
          {t(lang, 'slogan')}
        </p>

        <div className="w-full h-4" />

        {step === 'Phone' && (
          <div className="w-full flex flex-col gap-3">
            <h2 className="text-[20px] leading-[30px] font-semibold text-center">
              {t(lang, 'signin_title')}
            </h2>

            <div className="w-full flex flex-col gap-1">
              <label
                htmlFor="phone_field"
                className="text-[12px] font-medium text-[#627186] dark:text-[#A9B4C4] px-1"
              >
                {t(lang, 'phone_label')}
              </label>
              <input
                id="phone_field"
                type="tel"
                dir="ltr"
                disabled={!otpEnabled}
                value={phoneInput}
                onChange={(e) => {
                  setPhoneInput(e.target.value.slice(0, 20));
                  setPhoneInvalid(false);
                  setErrorKey(null);
                }}
                placeholder={t(lang, 'phone_hint')}
                data-testid="phone_field"
                className={`w-full h-14 px-4 rounded-[12px] border text-[16px] bg-white dark:bg-[#18263B] focus:outline-none transition-colors ${
                  phoneInvalid
                    ? 'border-[#C2453D] text-[#C2453D]'
                    : 'border-[#B9C0CB] dark:border-[#51617A]'
                } ${!otpEnabled ? 'opacity-60 cursor-not-allowed bg-[#F0EEE8] dark:bg-[#142135]' : ''}`}
              />
              {phoneInvalid && (
                <span className="text-[12px] text-[#C2453D] px-1">
                  {t(lang, 'phone_invalid')}
                </span>
              )}
            </div>

            {!otpEnabled && (
              <MessageCard
                message={t(lang, 'otp_coming_soon')}
                tone="Info"
                testId="otp_coming_soon"
              />
            )}

            <YabahPrimaryButton
              text={t(lang, 'send_code')}
              onClick={handleSendCode}
              enabled={otpEnabled}
              testId="send_code"
            />
          </div>
        )}

        {step === 'Code' && (
          <div className="w-full flex flex-col gap-3 items-center">
            <p className="text-[16px] leading-[26px] text-center">
              {t(lang, 'code_sent_to', PhoneNumbers.display(e164))}
            </p>
            <div className="w-full">
              <label
                htmlFor="code_field"
                className="text-[12px] font-medium text-[#627186] dark:text-[#A9B4C4] px-1"
              >
                {t(lang, 'code_label')}
              </label>
              <input
                id="code_field"
                type="text"
                inputMode="numeric"
                dir="ltr"
                value={codeInput}
                onChange={(e) => {
                  const cleaned = PhoneNumbers.clean(e.target.value)
                    .replace(/\D/g, '')
                    .slice(0, 6);
                  setCodeInput(cleaned);
                  setErrorKey(null);
                }}
                placeholder="123456"
                data-testid="code_field"
                className="w-full h-14 px-4 rounded-[12px] border border-[#B9C0CB] dark:border-[#51617A] text-center tracking-widest text-[18px] font-semibold bg-white dark:bg-[#18263B]"
              />
            </div>

            <YabahPrimaryButton
              text={t(lang, 'verify')}
              onClick={handleVerify}
              enabled={codeInput.length === 6}
              testId="verify_code"
            />

            {resendInSec > 0 ? (
              <span className="text-[12px] text-[#627186] dark:text-[#A9B4C4]">
                {t(lang, 'resend_in', resendInSec)}
              </span>
            ) : (
              <button
                type="button"
                onClick={handleSendCode}
                className="text-[14px] font-semibold text-[#0DAA9B] cursor-pointer"
              >
                {t(lang, 'resend')}
              </button>
            )}

            <button
              type="button"
              onClick={() => {
                setStep('Phone');
                setResendInSec(0);
                setErrorKey(null);
              }}
              className="text-[14px] font-medium text-[#627186] dark:text-[#A9B4C4] cursor-pointer"
            >
              {t(lang, 'change_number')}
            </button>
          </div>
        )}

        {step === 'Name' && (
          <div className="w-full flex flex-col gap-3">
            <h2 className="text-[20px] leading-[30px] font-semibold text-center">
              {t(lang, 'name_title')}
            </h2>
            <div className="w-full flex flex-col gap-1">
              <label
                htmlFor="name_field"
                className="text-[12px] font-medium text-[#627186] dark:text-[#A9B4C4] px-1"
              >
                {t(lang, 'name_label')}
              </label>
              <input
                id="name_field"
                type="text"
                value={nameInput}
                onChange={(e) => {
                  setNameInput(e.target.value.slice(0, 60));
                  setNameInvalid(false);
                }}
                data-testid="name_field"
                className="w-full h-14 px-4 rounded-[12px] border border-[#B9C0CB] dark:border-[#51617A] text-[16px] bg-white dark:bg-[#18263B]"
              />
              {nameInvalid && (
                <span className="text-[12px] text-[#C2453D] px-1">
                  {t(lang, 'name_invalid')}
                </span>
              )}
            </div>
            <YabahPrimaryButton
              text={t(lang, 'continue_btn')}
              onClick={handleSaveName}
              testId="continue_name"
            />
          </div>
        )}

        {errorKey && <MessageCard message={t(lang, errorKey)} tone="Danger" />}

        {step === 'Phone' && (
          <div className="w-full flex flex-col gap-2.5 mt-4">
            <YabahSecondaryButton
              text={t(lang, 'try_demo')}
              onClick={onTryDemo}
              testId="try_demo"
            />
            <button
              type="button"
              onClick={onTryLiveSim}
              data-testid="try_live_sim"
              className="w-full min-h-[44px] rounded-[12px] px-4 py-2 text-[13px] font-medium text-[#0DAA9B] hover:bg-[#0DAA9B]/10 transition-colors cursor-pointer"
            >
              {t(lang, 'try_live_sim')}
            </button>
          </div>
        )}
      </div>
    </main>
  );
};
