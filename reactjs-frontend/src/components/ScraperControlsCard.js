// ScraperControlsCard.jsx
import React from 'react';
import { Card, Button, Form, Spinner } from 'react-bootstrap';

function ScraperControlsCard({
    t,
    videoId,
    inputValue,
    handleInputChange,
    isScraperRunning,
    loading,
    handleStartScraper,
    handleStopScraper,
    selectedLangs,
    handleLanguageSelect,
    availableLanguages,
}) {
    return (
        <Card>
            <Card.Body>
                <Card.Title>{t('controls.title')}</Card.Title>
                <Form.Group className="mb-3" controlId="videoIdInput">
                    <Form.Label>{t('controls.videoInput')}</Form.Label>
                    <Form.Control
                        type="text"
                        placeholder={t('controls.videoPlaceholder')}
                        value={videoId}
                        onChange={handleInputChange}
                        disabled={isScraperRunning}
                    />
                </Form.Group>

                {/* Language Selector */}
                <Form.Group className="mb-3">
                    <Form.Label>{t('controls.languageFilter')}</Form.Label>
                    <div className="d-flex flex-wrap gap-2 mb-2">
                        {selectedLangs.map((lang) => (
                            <Button
                                key={lang}
                                variant="primary"
                                size="sm"
                                className="d-flex align-items-center"
                                onClick={() => handleLanguageSelect(lang)}
                            >
                                {t(`languages.${lang}`)}
                                <span className="ms-1">&times;</span>
                            </Button>
                        ))}
                        {selectedLangs.length === 0 && (
                            <span className="text-muted">{t('controls.noLanguagesSelected')}</span>
                        )}
                    </div>
                    <div
                        className="language-selector border rounded p-2"
                        style={{ maxHeight: '150px', overflowY: 'auto' }}
                    >
                        <div className="d-flex flex-wrap gap-2">
                            {availableLanguages.map((lang) => (
                                <Button
                                    key={lang}
                                    variant={selectedLangs.includes(lang) ? 'primary' : 'outline-secondary'}
                                    size="sm"
                                    onClick={() => handleLanguageSelect(lang)}
                                    disabled={
                                        !selectedLangs.includes(lang) &&
                                        selectedLangs.length >= 5
                                    }
                                >
                                    {t(`languages.${lang}`)}
                                </Button>
                            ))}
                        </div>
                    </div>
                    <small className="text-muted mt-1 d-block">
                        {t('controls.selectionsRemaining', {
                            count: 5 - selectedLangs.length,
                        })}
                    </small>
                </Form.Group>

                <div className="d-flex gap-3 justify-content-center mt-4">
                    <Button
                        variant="primary"
                        size="lg"
                        className="px-4 d-flex align-items-center shadow-sm"
                        onClick={handleStartScraper}
                        disabled={loading}
                    >
                        {loading ? (
                            <>
                                <Spinner animation="border" size="sm" className="me-2" />
                                {t('controls.starting')}
                            </>
                        ) : (
                            <>
                                <i className="bi bi-play-fill me-2"></i>
                                {t('controls.startAnalyzer')}
                            </>
                        )}
                    </Button>
                    <Button
                        variant="outline-danger"
                        size="lg"
                        className="px-4 d-flex align-items-center shadow-sm"
                        onClick={handleStopScraper}
                        disabled={loading}
                    >
                        {loading ? (
                            <>
                                <Spinner animation="border" size="sm" className="me-2" />
                                {t('controls.stopping')}
                            </>
                        ) : (
                            <>
                                <i className="bi bi-stop-fill me-2"></i>
                                {t('controls.stopAnalyzer')}
                            </>
                        )}
                    </Button>
                </div>
            </Card.Body>
        </Card>
    );
}

export default ScraperControlsCard;
