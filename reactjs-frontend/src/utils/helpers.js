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

export const availableLanguages = [
    'ENGLISH', 'SPANISH', 'FRENCH', 'GERMAN', 'KOREAN',
    'ARABIC', 'NYNORSK', 'DANISH', 'MAORI', 'DUTCH',
    'PERSIAN', 'ESTONIAN', 'POLISH', 'TAGALOG', 'SWEDISH',
    'SOMALI', 'ALBANIAN', 'AZERBAIJANI', 'URDU', "RUSSIAN", "CHINESE"
];
