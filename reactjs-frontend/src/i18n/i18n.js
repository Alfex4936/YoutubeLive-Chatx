import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';

import enTranslation from '../locales/en/translation.json';
import koTranslation from '../locales/ko/translation.json';
import frTranslation from '../locales/fr/translation.json';
import jaTranslation from '../locales/ja/translation.json';

if (!i18n.isInitialized) {
    i18n
        // detect user language
        .use(LanguageDetector)
        // pass the i18n instance to react-i18next
        .use(initReactI18next)
        // init i18next
        .init({
            resources: {
                en: {
                    translation: enTranslation
                },
                ko: {
                    translation: koTranslation
                },
                fr: {
                    translation: frTranslation
                },
                ja: {
                    translation: jaTranslation
                }
            },
            fallbackLng: 'en',
            debug: process.env.NODE_ENV === 'development',

            interpolation: {
                escapeValue: false, // not needed for react as it escapes by default
            },

            // detection options
            detection: {
                order: ['localStorage', 'navigator'],
                caches: ['localStorage'],
            }
        });
}

export default i18n;