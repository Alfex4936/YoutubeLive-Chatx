// ScraperDataCard.jsx
import React from 'react';
import { Card, Row, Col, ProgressBar, Alert, Button } from 'react-bootstrap';
import { currencyMap, getTranslatedCurrencyInfo } from '../utils/helpers';

function ScraperDataCard({
    t,
    scraperData,
    isScraperRunning,
    showDonations,
    setShowDonations,
    formatTimestamp,
}) {
    // Function to extract currency information from donation amount
    const getCurrencyInfo = (amount) => {
        return getTranslatedCurrencyInfo(amount, t);
    };

    if (!scraperData) {
        return (
            <Alert variant="warning" className="mt-3">
                {t('messages.noData')}
            </Alert>
        );
    }

    return (
        <Card className="shadow-sm">
            <Card.Body>
                <Card.Title className="border-bottom pb-2 mb-3">
                    {t('stats.title')}
                </Card.Title>

                {/* Video and Channel Info */}
                <div className="mb-4">
                    <div className="d-flex align-items-center mb-3">
                        <h5
                            className="text-danger mb-0 me-2"
                            style={{ color: '#FF0000' }}
                        >
                            {t('stats.videoInfo')}
                        </h5>
                        <span
                            className={`badge ${scraperData.status === 'RUNNING'
                                    ? 'bg-success'
                                    : scraperData.status === 'COMPLETED'
                                        ? 'bg-primary'
                                        : scraperData.status === 'FAILED'
                                            ? 'bg-danger'
                                            : 'bg-secondary'
                                }`}
                        >
                            {scraperData.status}
                        </span>

                        {/* Status icon indicator */}
                        {scraperData.status && (
                            <div className="ms-2">
                                {scraperData.status === 'RUNNING' && (
                                    <span className="text-success" title={t('stats.statusRunning')}>
                                        <i className="bi bi-play-circle-fill"></i>
                                    </span>
                                )}
                                {scraperData.status === 'COMPLETED' && (
                                    <span className="text-primary" title={t('stats.statusCompleted')}>
                                        <i className="bi bi-check-circle-fill"></i>
                                    </span>
                                )}
                                {scraperData.status === 'FAILED' && (
                                    <span className="text-danger" title={t('stats.statusFailed')}>
                                        <i className="bi bi-exclamation-circle-fill"></i>
                                    </span>
                                )}
                                {scraperData.status !== 'RUNNING' &&
                                    scraperData.status !== 'COMPLETED' &&
                                    scraperData.status !== 'FAILED' && (
                                        <span className="text-secondary" title={t('stats.statusOther')}>
                                            <i className="bi bi-question-circle-fill"></i>
                                        </span>
                                    )}
                            </div>
                        )}
                    </div>
                    <Row className="mb-2">
                        <Col xs={12}>
                            <div className="d-flex align-items-center">
                                <span className="fw-bold me-2">{t('stats.vidTitle')}</span>
                                <a
                                    rel="noreferrer"
                                    href={scraperData.videoUrl}
                                    target="_blank"
                                    className="text-truncate"
                                    title={scraperData.videoTitle}
                                >
                                    {scraperData.videoTitle}
                                </a>
                            </div>
                        </Col>
                    </Row>
                    <Row>
                        <Col xs={12} className="mb-2">
                            <div className="d-flex align-items-center">
                                <span className="fw-bold me-2">{t('stats.channel')}</span>
                                <span>{scraperData.channelName}</span>
                            </div>
                        </Col>
                    </Row>
                </div>

                {/* Stats Section */}
                <div className="mb-4">
                    <h5 className="text-primary mb-3">{t('stats.performance')}</h5>
                    <Row className="mb-3">
                        <Col xs={12} md={6} className="mb-2">
                            <Card className="bg-light h-100">
                                <Card.Body className="py-2">
                                    <div className="text-center">
                                        <div className="text-muted small">
                                            {t('stats.runningTime')}
                                        </div>
                                        <div className="fs-4 fw-bold">
                                            {scraperData.runningTimeMinutes} {t('stats.minutes')}
                                        </div>
                                    </div>
                                </Card.Body>
                            </Card>
                        </Col>
                        <Col xs={12} md={6} className="mb-2">
                            <Card className="bg-light h-100">
                                <Card.Body className="py-2">
                                    <div className="text-center">
                                        <div className="text-muted small">
                                            {t('stats.totalMessages')}
                                        </div>
                                        <div className="fs-4 fw-bold">
                                            {scraperData.totalMessages.toLocaleString()}
                                        </div>
                                    </div>
                                </Card.Body>
                            </Card>
                        </Col>
                    </Row>

                    <Row>
                        <Col xs={12} md={4} className="mb-2">
                            <Card className="bg-light h-100">
                                <Card.Body className="py-2">
                                    <div className="text-center">
                                        <div className="text-muted small">
                                            {t('stats.throughput.current')}
                                        </div>
                                        <div className="fs-5 fw-bold">
                                            {scraperData.lastThroughput} {t('stats.throughput.unit')}
                                        </div>
                                        <div className="text-muted" style={{ fontSize: '0.7rem' }}>
                                            {t('stats.throughput.lastInterval')}
                                        </div>
                                    </div>
                                </Card.Body>
                            </Card>
                        </Col>
                        <Col xs={12} md={4} className="mb-2">
                            <Card className="bg-light h-100">
                                <Card.Body className="py-2">
                                    <div className="text-center">
                                        <div className="text-muted small">
                                            {t('stats.throughput.average')}
                                        </div>
                                        <div className="fs-5 fw-bold">
                                            {scraperData.averageThroughput.toFixed(2)}{' '}
                                            {t('stats.throughput.unit')}
                                        </div>
                                        <div className="text-muted" style={{ fontSize: '0.7rem' }}>
                                            {t('stats.throughput.allIntervals')}
                                        </div>
                                    </div>
                                </Card.Body>
                            </Card>
                        </Col>
                        <Col xs={12} md={4} className="mb-2">
                            <Card className="bg-light h-100">
                                <Card.Body className="py-2">
                                    <div className="text-center">
                                        <div className="text-muted small">
                                            {t('stats.throughput.maximum')}
                                        </div>
                                        <div className="fs-5 fw-bold">
                                            {scraperData.maxThroughput} {t('stats.throughput.unit')}
                                        </div>
                                        <div className="text-muted" style={{ fontSize: '0.7rem' }}>
                                            {t('stats.throughput.peakInterval')}
                                        </div>
                                    </div>
                                </Card.Body>
                            </Card>
                        </Col>
                    </Row>

                    <div className="mt-2 text-muted small text-end">
                        {t('stats.started')} {formatTimestamp(scraperData.createdAt)}
                    </div>
                </div>

                {/* Top Chatters Section */}
                {scraperData.topChatters && scraperData.topChatters.length > 0 && (
                    <div className="mb-4">
                        <h5 className="text-primary mb-3">{t('stats.topChatters')}</h5>
                        <Row>
                            {Array.from({ length: scraperData.topChatters.length }).map(
                                (_, i) => {
                                    // Get chatter from the end of the array (highest message count first)
                                    const index = scraperData.topChatters.length - 1 - i;
                                    const chatter = scraperData.topChatters[index];

                                    // Skip users with only 1 message
                                    if (chatter.messageCount <= 1) {
                                        return null;
                                    }

                                    // First chatter (i=0) is the top chatter
                                    const isTopChatter = i === 0;

                                    return (
                                        <Col key={index} xs={6} md={4} lg={2} className="mb-3">
                                            <Card
                                                className={`h-100 ${isTopChatter ? 'border border-warning' : ''
                                                    }`}
                                                style={
                                                    isTopChatter
                                                        ? {
                                                            boxShadow:
                                                                '0 0 10px rgba(255, 193, 7, 0.5)',
                                                        }
                                                        : {}
                                                }
                                            >
                                                <Card.Body className="p-2 text-center">
                                                    <div className="d-flex flex-column align-items-center">
                                                        {isTopChatter && (
                                                            <div
                                                                className="position-absolute"
                                                                style={{ top: '-10px', right: '-10px' }}
                                                            >
                                                                <span className="badge rounded-pill bg-warning text-yellow">
                                                                    <svg
                                                                        xmlns="http://www.w3.org/2000/svg"
                                                                        width="16"
                                                                        height="16"
                                                                        fill="currentColor"
                                                                        className="bi bi-trophy-fill"
                                                                        viewBox="0 0 16 16"
                                                                    >
                                                                        <path d="M2.5.5A.5.5 0 0 1 3 0h10a.5.5 0 0 1 .5.5q0 .807-.034 1.536a3 3 0 1 1-1.133 5.89c-.79 1.865-1.878 2.777-2.833 3.011v2.173l1.425.356c.194.048.377.135.537.255L13.3 15.1a.5.5 0 0 1-.3.9H3a.5.5 0 0 1-.3-.9l1.838-1.379c.16-.12.343-.207.537-.255L6.5 13.11v-2.173c-.955-.234-2.043-1.146-2.833-3.012a3 3 0 1 1-1.132-5.89A33 33 0 0 1 2.5.5m.099 2.54a2 2 0 0 0 .72 3.935c-.333-1.05-.588-2.346-.72-3.935m10.083 3.935a2 2 0 0 0 .72-3.935c-.133 1.59-.388 2.885-.72 3.935" />
                                                                    </svg>
                                                                </span>
                                                            </div>
                                                        )}
                                                        <div className="mb-2">
                                                            <div
                                                                className={`rounded-circle d-flex align-items-center justify-content-center text-white ${isTopChatter ? 'bg-warning' : 'bg-primary'
                                                                    }`}
                                                                style={{
                                                                    width: '40px',
                                                                    height: '40px',
                                                                    fontSize: '1.2rem',
                                                                }}
                                                            >
                                                                {chatter.username.charAt(0).toUpperCase()}
                                                            </div>
                                                        </div>
                                                        <div
                                                            className="text-truncate w-100"
                                                            title={chatter.username}
                                                        >
                                                            <strong>{chatter.username}</strong>
                                                        </div>
                                                        <div className="small text-muted">
                                                            {chatter.messageCount} messages
                                                        </div>
                                                    </div>
                                                </Card.Body>
                                            </Card>
                                        </Col>
                                    );
                                }
                            )}
                        </Row>
                    </div>
                )}

                {/* Recent Donations Section */}
                {scraperData.recentDonations &&
                    scraperData.recentDonations.length > 0 && (
                        <div className="mb-4">
                            <div
                                className="d-flex align-items-center justify-content-between mb-3"
                                onClick={() => setShowDonations(!showDonations)}
                                style={{ cursor: 'pointer' }}
                            >
                                <h5 className="text-primary mb-0 d-flex align-items-center">
                                    <svg
                                        xmlns="http://www.w3.org/2000/svg"
                                        width="20"
                                        height="20"
                                        fill="currentColor"
                                        className="bi bi-coin me-2"
                                        viewBox="0 0 16 16"
                                    >
                                        <path d="M5.5 9.511c.076.954.83 1.697 2.182 1.785V12h.6v-.709c1.4-.098 2.218-.846 2.218-1.932 0-.987-.626-1.496-1.745-1.76l-.473-.112V5.57c.6.068.982.396 1.074.85h1.052c-.076-.919-.864-1.638-2.126-1.716V4h-.6v.719c-1.195.117-2.01.836-2.01 1.853 0 .9.606 1.472 1.613 1.707l.397.098v2.034c-.615-.093-1.022-.43-1.114-.9zm2.177-2.166c-.59-.137-.91-.416-.91-.836 0-.47.345-.822.915-.925v1.76h-.005zm.692 1.193c.717.166 1.048.435 1.048.91 0 .542-.412.914-1.135.982V8.518z" />
                                        <path d="M8 15A7 7 0 1 1 8 1a7 7 0 0 1 0 14m0 1A8 8 0 1 0 8 0a8 8 0 0 0 0 16" />
                                        <path d="M8 13.5a5.5 5.5 0 1 1 0-11 5.5 5.5 0 0 1 0 11m0 .5A6 6 0 1 0 8 2a6 6 0 0 0 0 12" />
                                    </svg>
                                    {t('stats.recentDonations')}
                                </h5>
                                <Button
                                    variant="link"
                                    className="p-0 text-primary"
                                    aria-expanded={showDonations}
                                    aria-controls="donation-collapse"
                                >
                                    <i
                                        className={`bi ${showDonations ? 'bi-chevron-up' : 'bi-chevron-down'
                                            }`}
                                    ></i>
                                </Button>
                            </div>
                            <div
                                id="donation-collapse"
                                className={`donation-list ${showDonations ? '' : 'd-none'}`}
                            >
                                {scraperData.recentDonations.map((donation, index) => (
                                    <Card key={index} className="mb-3 donation-card">
                                        <Card.Body className="p-3">
                                            <div className="d-flex align-items-start">
                                                <div className="donation-avatar me-3">
                                                    <div
                                                        className="rounded-circle bg-success d-flex align-items-center justify-content-center text-white"
                                                        style={{
                                                            width: '48px',
                                                            height: '48px',
                                                            fontSize: '1.4rem',
                                                        }}
                                                    >
                                                        {donation.username.charAt(0).toUpperCase()}
                                                    </div>
                                                </div>
                                                <div className="donation-content flex-grow-1">
                                                    <div className="d-flex justify-content-between align-items-center mb-2">
                                                        <h6 className="mb-0 fw-bold">
                                                            {donation.username}
                                                        </h6>
                                                        <span
                                                            className="badge bg-success px-3 py-2"
                                                            title={getCurrencyInfo(donation.amount)}
                                                        >
                                                            {donation.amount}
                                                        </span>
                                                    </div>
                                                    {donation.message && (
                                                        <div className="donation-message p-2 bg-light rounded">
                                                            <p className="mb-0 small">
                                                                {donation.message}
                                                            </p>
                                                        </div>
                                                    )}
                                                </div>
                                            </div>
                                        </Card.Body>
                                    </Card>
                                ))}
                            </div>
                        </div>
                    )}

                {/* Keywords and Languages Section */}
                <Row>
                    {/* Top Keywords */}
                    {scraperData.topKeywords && scraperData.topKeywords.length > 0 && (
                        <Col xs={12} md={6} className="mb-3">
                            <h5 className="text-primary mb-3">{t('stats.topKeywords')}</h5>
                            <div className="keyword-cloud">
                                {scraperData.topKeywords.map((kwPair, i) => {
                                    const maxScore = Math.max(
                                        ...scraperData.topKeywords.map((kw) => kw.score)
                                    );
                                    const minSize = 0.9;
                                    const maxSize = 1.4;
                                    const sizeScale =
                                        minSize +
                                        (kwPair.score / maxScore) * (maxSize - minSize);

                                    // Decide a "baseColor" just for variety
                                    const baseColor =
                                        kwPair.score > maxScore * 0.7
                                            ? 'info'
                                            : kwPair.score > maxScore * 0.4
                                                ? 'primary'
                                                : 'secondary';

                                    // Truncate very long keywords
                                    const displayKeyword =
                                        kwPair.keyword.length > 20
                                            ? kwPair.keyword.substring(0, 18) + '...'
                                            : kwPair.keyword;

                                    return (
                                        <span
                                            key={i}
                                            className={`badge bg-${baseColor} text-wrap m-1`}
                                            style={{
                                                fontSize: `${sizeScale}rem`,
                                                padding: '0.5rem 0.75rem',
                                                display: 'inline-block',
                                                maxWidth: '100%',
                                                overflow: 'hidden',
                                                textOverflow: 'ellipsis',
                                            }}
                                            title={kwPair.keyword}
                                        >
                                            {displayKeyword}{' '}
                                            <span className="badge bg-light text-dark ms-1">
                                                {kwPair.score}
                                            </span>
                                        </span>
                                    );
                                })}
                            </div>
                        </Col>
                    )}

                    {/* Filtered Languages */}
                    {scraperData.skipLangs && scraperData.skipLangs.length > 0 && (
                        <Col xs={12} md={6} className="mb-3">
                            <h5 className="text-primary mb-3">
                                {t('stats.filteredLanguages')}
                            </h5>
                            <div className="d-flex flex-wrap gap-2">
                                {scraperData.skipLangs.map((lang) => (
                                    <span key={lang} className="badge bg-secondary p-2">
                                        {t(`languages.${lang}`)}
                                    </span>
                                ))}
                            </div>
                        </Col>
                    )}
                </Row>

                {/* Top Languages with better progress bars */}
                {scraperData.topLanguages &&
                    Object.keys(scraperData.topLanguages).length > 0 && (
                        <div className="mt-2">
                            <h5 className="text-primary mb-3">
                                {t('stats.languageDistribution')}
                            </h5>
                            {Object.entries(scraperData.topLanguages)
                                .sort(([, a], [, b]) => b - a)
                                .map(([lang, percent]) => (
                                    <div key={lang} className="mb-3">
                                        <div className="d-flex justify-content-between mb-1">
                                            <span className="fw-bold">{lang}</span>
                                            <span>{percent.toFixed(1)}%</span>
                                        </div>
                                        <ProgressBar
                                            now={percent}
                                            variant={
                                                percent > 70 ? 'primary' : percent > 30 ? 'info' : 'secondary'
                                            }
                                            style={{ height: '0.8rem' }}
                                        />
                                    </div>
                                ))}
                        </div>
                    )}
            </Card.Body>
        </Card>
    );
}

export default ScraperDataCard;
