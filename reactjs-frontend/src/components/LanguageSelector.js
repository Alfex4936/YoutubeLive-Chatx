import React from 'react';
import { useTranslation } from 'react-i18next';
import { Dropdown } from 'react-bootstrap';

const LanguageSelector = () => {
    const { i18n } = useTranslation();

    const changeLanguage = (lng) => {
        i18n.changeLanguage(lng);
    };

    return (
        <Dropdown>
            <Dropdown.Toggle variant="outline-light" size="sm" id="dropdown-language">
                {i18n.language === 'ko' ? '🇰🇷 한국어' : '🇺🇸 English'}
            </Dropdown.Toggle>

            <Dropdown.Menu>
                <Dropdown.Item
                    onClick={() => changeLanguage('en')}
                    active={i18n.language === 'en'}
                >
                    🇺🇸 English
                </Dropdown.Item>
                <Dropdown.Item
                    onClick={() => changeLanguage('ko')}
                    active={i18n.language === 'ko'}
                >
                    🇰🇷 한국어
                </Dropdown.Item>
            </Dropdown.Menu>
        </Dropdown>
    );
};

export default LanguageSelector;