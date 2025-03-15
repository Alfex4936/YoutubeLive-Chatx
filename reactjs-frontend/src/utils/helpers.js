import i18n from '../i18n/i18n';

export function formatTimestamp(isoString) {
    if (!isoString) return 'N/A';

    try {
        const date = new Date(isoString);

        return new Intl.DateTimeFormat(i18n.language, {
            year: 'numeric',
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit',
            timeZoneName: 'short',
        }).format(date);
    } catch (error) {
        console.error('Invalid date format:', isoString);
        return 'Invalid Date';
    }
}

// Utility to parse videoId from a full YouTube URL
export function extractVideoId(linkOrId) {
    try {
        const url = new URL(linkOrId);

        // Handle both watch and live links
        if (url.hostname.includes("youtube.com")) {
            if (url.pathname.startsWith("/watch")) {
                return url.searchParams.get("v"); // Extract video ID from "v" parameter
            } else if (url.pathname.startsWith("/live/")) {
                return url.pathname.split("/live/")[1]; // Extract video ID from "/live/VIDEO_ID"
            }
        }
    } catch (err) {
        // If parsing fails, assume the input is already a video ID
        return linkOrId;
    }
    return linkOrId;
}

export function getTranslatedCurrencyInfo(amount, t) {
    const codeMatch = amount.match(/^([A-Z]{3})\s/);
    if (codeMatch && currencyMap[codeMatch[1]]) {
        const currencyCode = codeMatch[1];
        return t(`currency.${currencyCode}`, { defaultValue: currencyMap[currencyCode] });
    }

    // Check for currency symbols
    for (const [symbol, info] of Object.entries(currencyMap)) {
        if (amount.startsWith(symbol)) {
            return t(`currency.${symbol}`, { defaultValue: info });
        }
    }

    return t('currency.unknown', { defaultValue: "Unknown Currency" });
}

export const availableLanguages = [
    'ENGLISH', 'SPANISH', 'FRENCH', 'GERMAN', 'KOREAN',
    'ARABIC', 'NYNORSK', 'DANISH', 'MAORI', 'DUTCH',
    'PERSIAN', 'ESTONIAN', 'POLISH', 'TAGALOG', 'SWEDISH',
    'SOMALI', 'ALBANIAN', 'AZERBAIJANI', 'URDU', "RUSSIAN", "CHINESE"
];

export const currencyMap = {
    '$': 'US Dollar (USD) - United States',
    '€': 'Euro (EUR) - European Union',
    '£': 'British Pound (GBP) - United Kingdom',
    '¥': 'Japanese Yen (JPY) - Japan',
    '₩': 'Korean Won (KRW) - South Korea',
    '₹': 'Indian Rupee (INR) - India',
    '₽': 'Russian Ruble (RUB) - Russia',
    '₿': 'Bitcoin (BTC) - Cryptocurrency',
    'A$': 'Australian Dollar (AUD) - Australia',
    'C$': 'Canadian Dollar (CAD) - Canada',
    'CA$': 'Canadian Dollar (CAD) - Canada',
    'HK$': 'Hong Kong Dollar (HKD) - Hong Kong',
    'R$': 'Brazilian Real (BRL) - Brazil',
    '₺': 'Turkish Lira (TRY) - Turkey',
    '฿': 'Thai Baht (THB) - Thailand',
    'CHF': 'Swiss Franc (CHF) - Switzerland',
    'SEK': 'Swedish Krona (SEK) - Sweden',
    'NOK': 'Norwegian Krone (NOK) - Norway',
    'DKK': 'Danish Krone (DKK) - Denmark',
    'PLN': 'Polish Złoty (PLN) - Poland',
    'CZK': 'Czech Koruna (CZK) - Czech Republic',
    'MXN': 'Mexican Peso (MXN) - Mexico',
    'THB': 'Thai Baht (THB) - Thailand',
    'SGD': 'Singapore Dollar (SGD) - Singapore',
    'NZD': 'New Zealand Dollar (NZD) - New Zealand',
    'ZAR': 'South African Rand (ZAR) - South Africa',
    'AED': 'United Arab Emirates Dirham (AED) - UAE',
    'CNY': 'Chinese Yuan (CNY) - China',
    '₴': 'Ukrainian Hryvnia (UAH) - Ukraine',
    '₱': 'Philippine Peso (PHP) - Philippines',
    'RON': 'Romanian Leu (RON) - Romania',
    'HUF': 'Hungarian Forint (HUF) - Hungary',
    'MYR': 'Malaysian Ringgit (MYR) - Malaysia',
    'IDR': 'Indonesian Rupiah (IDR) - Indonesia',
    'VND': 'Vietnamese Dong (VND) - Vietnam',
    'ILS': 'Israeli New Shekel (ILS) - Israel',
    'CLP': 'Chilean Peso (CLP) - Chile',
    'ARS': 'Argentine Peso (ARS) - Argentina',
    'COP': 'Colombian Peso (COP) - Colombia',
    'PEN': 'Peruvian Sol (PEN) - Peru',
    'BGN': 'Bulgarian Lev (BGN) - Bulgaria',
    'SAR': 'Saudi Riyal (SAR) - Saudi Arabia',
    'QAR': 'Qatari Rial (QAR) - Qatar',
    'PKR': 'Pakistani Rupee (PKR) - Pakistan',
    'EGP': 'Egyptian Pound (EGP) - Egypt',
    'TWD': 'New Taiwan Dollar (TWD) - Taiwan',
    'BHD': 'Bahraini Dinar (BHD) - Bahrain',
    'KWD': 'Kuwaiti Dinar (KWD) - Kuwait',
    'OMR': 'Omani Rial (OMR) - Oman',
    'LKR': 'Sri Lankan Rupee (LKR) - Sri Lanka',
    'JOD': 'Jordanian Dinar (JOD) - Jordan',
    'RSD': 'Serbian Dinar (RSD) - Serbia',
    'MAD': 'Moroccan Dirham (MAD) - Morocco',
    'DZD': 'Algerian Dinar (DZD) - Algeria',
    'UGX': 'Ugandan Shilling (UGX) - Uganda',
    'TZS': 'Tanzanian Shilling (TZS) - Tanzania',
    'GHS': 'Ghanaian Cedi (GHS) - Ghana',
    'XAF': 'Central African CFA Franc (XAF) - Multiple nations',
    'XOF': 'West African CFA Franc (XOF) - Multiple nations',
    'BYN': 'Belarusian Ruble (BYN) - Belarus',
    'TMT': 'Turkmenistan Manat (TMT) - Turkmenistan',
    'AZN': 'Azerbaijani Manat (AZN) - Azerbaijan',
    'NGN': 'Nigerian Naira (NGN) - Nigeria',
    '₦': 'Nigerian Naira (NGN) - Nigeria',  // symbol form
};
