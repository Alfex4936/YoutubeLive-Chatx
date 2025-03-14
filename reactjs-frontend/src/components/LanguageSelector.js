import React from 'react';
import { useTranslation } from 'react-i18next';
import { Dropdown } from 'react-bootstrap';

const LanguageSelector = ({ darkMode }) => {
    const { i18n } = useTranslation();

    const changeLanguage = (lng) => {
        i18n.changeLanguage(lng);
    };

    // Use a different variant based on where the component is used
    const variant = darkMode ? "outline-light" : "outline-dark";

    return (
        <Dropdown>
            <Dropdown.Toggle
                variant={variant}
                size="sm"
                id="dropdown-language"
                style={{ width: '100%', textAlign: 'center' }}
            >
                {i18n.language === 'ko' ? '🇰🇷 한국어' :
                    i18n.language === 'fr' ? '🇫🇷 Français' :
                        i18n.language === 'ja' ? '🇯🇵 日本語' :
                            '🇺🇸 English'}
            </Dropdown.Toggle>

            <Dropdown.Menu style={{ width: '100%' }}>
                <Dropdown.Item onClick={() => changeLanguage('en')} active={i18n.language === 'en'}>
                    🇺🇸 English
                </Dropdown.Item>
                <Dropdown.Item onClick={() => changeLanguage('ko')} active={i18n.language === 'ko'}>
                    🇰🇷 한국어
                </Dropdown.Item>
                <Dropdown.Item onClick={() => changeLanguage('fr')} active={i18n.language === 'fr'}>
                    🇫🇷 Français
                </Dropdown.Item>
                <Dropdown.Item onClick={() => changeLanguage('ja')} active={i18n.language === 'ja'}>
                    🇯🇵 日本語
                </Dropdown.Item>
            </Dropdown.Menu>
        </Dropdown>
    );
};

export default LanguageSelector;
