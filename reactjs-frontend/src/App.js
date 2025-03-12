import React, { useEffect, useState, useCallback, useRef } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Container,
  Form,
  ListGroup,
  Modal,
  Navbar,
  ProgressBar,
  Row,
  Spinner,
} from 'react-bootstrap';

import { useTranslation } from 'react-i18next';
import LanguageSelector from './components/LanguageSelector';
import './i18n/i18n'; // Import the i18n configuration

function formatTimestamp(isoString) {
  if (!isoString) return 'N/A';

  try {
    const date = new Date(isoString);

    return new Intl.DateTimeFormat(navigator.language, {
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
function extractVideoId(linkOrId) {
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

// Custom hook for polling
function usePolling(callback, delay, dependencies = []) {
  const savedCallback = useRef();

  // Remember the latest callback
  useEffect(() => {
    savedCallback.current = callback;
  }, [callback]);

  // Set up the interval
  useEffect(() => {
    if (delay === null) return;

    const tick = () => {
      savedCallback.current();
    };

    const id = setInterval(tick, delay);
    return () => clearInterval(id);
  }, [delay]);
}

function App() {
  const { t } = useTranslation();

  const [videoId, setVideoId] = useState('');
  const [inputValue, setInputValue] = useState('');
  const [scraperData, setScraperData] = useState(null);
  const [message, setMessage] = useState('');
  const [polling, setPolling] = useState(false);
  const [loading, setLoading] = useState(false);
  const failureCountRef = useRef(0);
  // Add state for selected languages
  const [selectedLangs, setSelectedLangs] = useState([]);
  // Add state for history dialog
  const [showHistory, setShowHistory] = useState(false);
  const [videoHistory, setVideoHistory] = useState([]);

  // Available languages list
  const availableLanguages = [
    'ENGLISH', 'SPANISH', 'FRENCH', 'GERMAN', 'KOREAN',
    'ARABIC', 'NYNORSK', 'DANISH', 'MAORI', 'DUTCH',
    'PERSIAN', 'ESTONIAN', 'POLISH', 'TAGALOG', 'SWEDISH',
    'SOMALI', 'ALBANIAN', 'AZERBAIJANI', 'URDU', "RUSSIAN", "CHINESE"
  ];

  // Function to handle language selection
  const handleLanguageSelect = (lang) => {
    if (selectedLangs.includes(lang)) {
      // If already selected, remove it
      setSelectedLangs(selectedLangs.filter(item => item !== lang));
    } else if (selectedLangs.length < 5) {
      // If not selected and less than 5 languages are selected, add it
      setSelectedLangs([...selectedLangs, lang]);
    }
  };

  // Load stored videoId and history on mount
  useEffect(() => {
    const storedId = localStorage.getItem('videoId');
    if (storedId) {
      setVideoId(storedId);
      setInputValue(storedId);
      setPolling(true);
    }

    // Load video history from localStorage
    const history = JSON.parse(localStorage.getItem('videoHistory') || '[]');
    setVideoHistory(history);
  }, []);

  // Function to add current video to history
  const addToHistory = useCallback((id, title) => {
    if (!id) return;

    setVideoHistory(prevHistory => {
      // Create new history entry
      const newEntry = {
        id,
        title: title || id,
        timestamp: new Date().toISOString(),
        url: `https://www.youtube.com/watch?v=${id}`
      };

      // Filter out duplicates and keep only the most recent 10 entries
      const filteredHistory = prevHistory.filter(item => item.id !== id);
      const newHistory = [newEntry, ...filteredHistory].slice(0, 10);

      // Save to localStorage
      localStorage.setItem('videoHistory', JSON.stringify(newHistory));
      return newHistory;
    });
  }, []);

  // Effect to clear message after a few seconds
  useEffect(() => {
    if (message) {
      const timer = setTimeout(() => {
        setMessage('');
      }, 5000); // Message will disappear after 5 seconds

      return () => clearTimeout(timer); // Cleanup the timer on component unmount or when message changes
    }
  }, [message]);

  // Update history when scraper data changes and has a title
  useEffect(() => {
    if (scraperData && scraperData.videoTitle && videoId) {
      addToHistory(videoId, scraperData.videoTitle);
    }
  }, [scraperData, videoId, addToHistory]);

  // Debounce input changes
  const debounceTimerRef = useRef(null);
  const handleInputChange = (e) => {
    const value = e.target.value;
    setInputValue(value);

    if (debounceTimerRef.current) {
      clearTimeout(debounceTimerRef.current);
    }

    debounceTimerRef.current = setTimeout(() => {
      setVideoId(value);
    }, 500); // 500ms debounce
  };

  // Memoized fetch function
  const fetchScraperStats = useCallback(() => {
    if (!videoId) return;

    fetch(`http://localhost:8080/scrapers/statistics?videoId=${encodeURIComponent(videoId)}`, {
      method: 'GET',
      // Add cache control headers to prevent browser caching
      headers: {
        'Cache-Control': 'no-cache, no-store, must-revalidate',
        'Pragma': 'no-cache',
        'Expires': '0'
      }
    })
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP error! Status: ${res.status}`);
        return res.json();
      })
      .then((data) => {
        // Direct response handling - the data is the scraper object itself, not nested in scrapers[videoId]
        if (data && data.videoTitle) {
          setScraperData(data);
          failureCountRef.current = 0; // Reset failure count on success
        } else if (data.scrapers && data.scrapers[videoId]) {
          // Fallback to previous format if needed
          setScraperData(data.scrapers[videoId]);
          failureCountRef.current = 0;
        } else {
          setScraperData(null);
        }
      })
      .catch((err) => {
        failureCountRef.current++;
        console.error(`Error fetching stats (${failureCountRef.current}/5):`, err);

        if (failureCountRef.current >= 5) {
          console.error("Stopping polling due to repeated failures.");
          setPolling(false);
        }
      });
  }, [videoId]);

  // Function to load video from history
  const loadFromHistory = useCallback((historyItem) => {
    setVideoId(historyItem.id);
    setInputValue(historyItem.id);
    setShowHistory(false);
    fetchScraperStats();
  }, [fetchScraperStats]);

  // Use the custom polling hook
  usePolling(fetchScraperStats, polling ? 4000 : null, [fetchScraperStats, polling]);

  // Memoized start scraper function
  const handleStartScraper = useCallback(() => {
    if (!inputValue) return;
    const actualId = extractVideoId(inputValue);
    localStorage.setItem('videoId', actualId);
    setVideoId(actualId);
    setInputValue(actualId);
    setLoading(true);

    addToHistory(actualId, `Scraping: ${actualId}`);

    // Build the URL with selected languages
    let url = `http://localhost:8080/scrapers/start?videoId=${encodeURIComponent(actualId)}`;
    if (selectedLangs.length > 0) {
      url += selectedLangs.map(lang => `&langs=${encodeURIComponent(lang)}`).join('');
    }

    fetch(url, {
      method: 'GET',
    })
      .then((res) => res.json())
      .then((data) => {
        setLoading(false);
        setMessage(data.message || 'Scraper started.');
        setPolling(true);
        // Fetch stats immediately after starting
        fetchScraperStats();
      })
      .catch((err) => {
        setLoading(false);
        console.error(err);
        setMessage('Error starting scraper');
      });
  }, [inputValue, fetchScraperStats, selectedLangs, addToHistory]);

  // Memoized stop scraper function
  const handleStopScraper = useCallback(() => {
    if (!videoId) return;
    setLoading(true);

    fetch(`http://localhost:8080/scrapers/stop?videoId=${encodeURIComponent(videoId)}`, {
      method: 'GET',
      redirect: 'manual',
    })
      .then((res) => res.json())
      .then((data) => {
        setLoading(false);
        setMessage(data.message || 'Scraper stopped.');
        setPolling(false);
        // Fetch stats immediately after stopping
        fetchScraperStats();
      })
      .catch((err) => {
        setLoading(false);
        console.error(err);
        setMessage('Error stopping scraper');
      });
  }, [videoId, fetchScraperStats]);

  return (
    <>
      {/* NAVBAR */}
      <Navbar bg="dark" variant="dark" className="mb-4">
        <Container>
          <div className="d-flex justify-content-between align-items-center w-100">
            <div className="invisible">
              <LanguageSelector />
              <Button variant="outline-light" size="sm" className="ms-2">
                {t('app.history')}
              </Button>
            </div>

            <div className="text-center">
              <Navbar.Brand className="mb-0">ytChatX</Navbar.Brand>
              <div className="text-light" style={{ fontSize: '0.70rem', marginTop: '2px' }}>
                {t('app.title')}
              </div>
            </div>

            <div className="d-flex gap-2">
              <LanguageSelector />
              <Button
                variant="outline-light"
                size="sm"
                onClick={() => setShowHistory(true)}
              >
                {t('app.history')}
              </Button>
            </div>
          </div>
        </Container>
      </Navbar>

      {/* MAIN CONTAINER */}
      <Container>
        <Row className="mb-3">
          <Col md={10} className="mx-auto">
            {/* SCRAPER CONTROLS CARD */}
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
                  />
                </Form.Group>

                {/* Language Selector */}
                <Form.Group className="mb-3">
                  <Form.Label>{t('controls.languageFilter')}</Form.Label>
                  <div className="d-flex flex-wrap gap-2 mb-2">
                    {selectedLangs.map(lang => (
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
                  <div className="language-selector border rounded p-2" style={{ maxHeight: '150px', overflowY: 'auto' }}>
                    <div className="d-flex flex-wrap gap-2">
                      {availableLanguages.map(lang => (
                        <Button
                          key={lang}
                          variant={selectedLangs.includes(lang) ? "primary" : "outline-secondary"}
                          size="sm"
                          onClick={() => handleLanguageSelect(lang)}
                          disabled={!selectedLangs.includes(lang) && selectedLangs.length >= 5}
                        >
                          {t(`languages.${lang}`)}
                        </Button>
                      ))}
                    </div>
                  </div>
                  <small className="text-muted mt-1 d-block">
                    {t('controls.selectionsRemaining', { count: 5 - selectedLangs.length })}
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
          </Col>
        </Row>

        {/* DISPLAY MESSAGES */}
        {message && (
          <Row className="mb-3">
            <Col md={10} className="mx-auto">
              <Alert variant="info">{message}</Alert>
            </Col>
          </Row>
        )}

        {/* If loading globally, show a spinner */}
        {loading && (
          <Row className="mb-3">
            <Col className="text-center">
              <Spinner animation="border" variant="primary" />
            </Col>
          </Row>
        )}

        {/* SCRAPER DATA CARD */}
        {scraperData ? (
          <Row>
            <Col md={10} className="mx-auto">
              <Card className="shadow-sm">
                <Card.Body>
                  <Card.Title className="border-bottom pb-2 mb-3">{t('stats.title')}</Card.Title>

                  {/* Video and Channel Info */}
                  <div className="mb-4">
                    <div className="d-flex align-items-center mb-3">
                      <h5 className="text-danger mb-0 me-2" style={{ color: '#FF0000' }}>{t('stats.videoInfo')}</h5>
                      <span className={`badge ${scraperData.status === 'RUNNING' ? 'bg-success' : 'bg-secondary'}`}>
                        {scraperData.status}
                      </span>
                    </div>
                    <Row className="mb-2">
                      <Col xs={12}>
                        <div className="d-flex align-items-center">
                          <span className="fw-bold me-2">{t('stats.vidTitle')}</span>
                          <a
                            rel="noreferrer"
                            href={scraperData.videoUrl}
                            target='_blank'
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
                          <span className="fw-bold me-2">Channel:</span>
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
                              <div className="text-muted small">{t('stats.runningTime')}</div>
                              <div className="fs-4 fw-bold">{scraperData.runningTimeMinutes} {t('stats.minutes')}</div>
                            </div>
                          </Card.Body>
                        </Card>
                      </Col>
                      <Col xs={12} md={6} className="mb-2">
                        <Card className="bg-light h-100">
                          <Card.Body className="py-2">
                            <div className="text-center">
                              <div className="text-muted small">{t('stats.totalMessages')}</div>
                              <div className="fs-4 fw-bold">{scraperData.totalMessages.toLocaleString()}</div>
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
                              <div className="text-muted small">{t('stats.throughput.current')}</div>
                              <div className="fs-5 fw-bold">{scraperData.lastThroughput} {t('stats.throughput.unit')}</div>
                              <div className="text-muted" style={{ fontSize: '0.7rem' }}>{t('stats.throughput.lastInterval')}</div>
                            </div>
                          </Card.Body>
                        </Card>
                      </Col>
                      <Col xs={12} md={4} className="mb-2">
                        <Card className="bg-light h-100">
                          <Card.Body className="py-2">
                            <div className="text-center">
                              <div className="text-muted small">{t('stats.throughput.average')}</div>
                              <div className="fs-5 fw-bold">{scraperData.averageThroughput.toFixed(2)} {t('stats.throughput.unit')}</div>
                              <div className="text-muted" style={{ fontSize: '0.7rem' }}>{t('stats.throughput.allIntervals')}</div>
                            </div>
                          </Card.Body>
                        </Card>
                      </Col>
                      <Col xs={12} md={4} className="mb-2">
                        <Card className="bg-light h-100">
                          <Card.Body className="py-2">
                            <div className="text-center">
                              <div className="text-muted small">{t('stats.throughput.maximum')}</div>
                              <div className="fs-5 fw-bold">{scraperData.maxThroughput} {t('stats.throughput.unit')}</div>
                              <div className="text-muted" style={{ fontSize: '0.7rem' }}>{t('stats.throughput.peakInterval')}</div>
                            </div>
                          </Card.Body>
                        </Card>
                      </Col>
                    </Row>

                    <div className="mt-2 text-muted small text-end">
                      {t('stats.started')} {formatTimestamp(scraperData.createdAt)}
                    </div>
                  </div>

                  {/* Keywords and Languages Section */}
                  <Row>
                    {/* Top Keywords */}
                    {scraperData.topKeywords && scraperData.topKeywords.length > 0 && (
                      <Col xs={12} md={6} className="mb-3">
                        <h5 className="text-primary mb-3">{t('stats.topKeywords')}</h5>
                        <div className="keyword-cloud">
                          {scraperData.topKeywords.map((kwPair, i) => {
                            // Calculate size based on score - higher score = larger badge
                            const maxScore = Math.max(...scraperData.topKeywords.map(kw => kw.score));
                            const minSize = 0.9;
                            const maxSize = 1.4;
                            const sizeScale = minSize + ((kwPair.score / maxScore) * (maxSize - minSize));

                            // Calculate color intensity based on score
                            const baseColor = kwPair.score > (maxScore * 0.7) ? "info" :
                              kwPair.score > (maxScore * 0.4) ? "primary" : "secondary";

                            // Truncate very long keywords
                            const displayKeyword = kwPair.keyword.length > 20
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
                                  textOverflow: 'ellipsis'
                                }}
                                title={kwPair.keyword} // Show full keyword on hover
                              >
                                {displayKeyword} <span className="badge bg-light text-dark ms-1">{kwPair.score}</span>
                              </span>
                            );
                          })}
                        </div>
                      </Col>
                    )}

                    {/* Filtered Languages */}
                    {scraperData.skipLangs && scraperData.skipLangs.length > 0 && (
                      <Col xs={12} md={6} className="mb-3">
                        <h5 className="text-primary mb-3">{t('stats.filteredLanguages')}</h5>
                        <div className="d-flex flex-wrap gap-2">
                          {scraperData.skipLangs.map(lang => (
                            <span key={lang} className="badge bg-secondary p-2">
                              {t(`languages.${lang}`)}
                            </span>
                          ))}
                        </div>
                      </Col>
                    )}
                  </Row>

                  {/* Top Languages with better progress bars */}
                  {scraperData.topLanguages && Object.keys(scraperData.topLanguages).length > 0 && (
                    <div className="mt-2">
                      <h5 className="text-primary mb-3">{t('stats.languageDistribution')}</h5>
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
                              variant={percent > 70 ? "primary" : percent > 30 ? "info" : "secondary"}
                              style={{ height: '0.8rem' }}
                            />
                          </div>
                        ))}
                    </div>
                  )}
                </Card.Body>
              </Card>
            </Col>
          </Row>
        ) : (
          <Row>
            <Col md={8} className="mx-auto text-center text-secondary">
              <p>{t('messages.noData')}</p>
            </Col>
          </Row>
        )}
      </Container>
      {/* History Modal */}
      <Modal show={showHistory} onHide={() => setShowHistory(false)}>
        <Modal.Header closeButton>
          <Modal.Title>{t('history.title')}</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {videoHistory.length === 0 ? (
            <p className="text-center text-muted">{t('history.noHistory')}</p>
          ) : (
            <ListGroup>
              {videoHistory.map((item, index) => (
                <ListGroup.Item
                  key={index}
                  action
                  onClick={() => loadFromHistory(item)}
                  className="d-flex flex-column"
                  title={item.title}
                >
                  <div className="d-flex justify-content-between align-items-start">
                    <div className="fw-bold text-truncate" style={{ maxWidth: '80%' }}>
                      {item.title}
                    </div>
                    <small className="text-muted">
                      {new Date(item.timestamp).toLocaleDateString()} {new Date(item.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                    </small>
                  </div>
                  <div className="d-flex justify-content-between mt-1">
                    <small className="text-muted">{t('history.id')} {item.id}</small>
                    <a
                      href={`https://www.youtube.com/watch?v=${item.id}`}
                      target="_blank"
                      rel="noreferrer"
                      onClick={(e) => e.stopPropagation()}
                      className="btn btn-sm btn-outline-primary"
                    >
                      {t('history.youtube')}
                    </a>
                  </div>
                </ListGroup.Item>
              ))}
            </ListGroup>
          )}
        </Modal.Body>
        <Modal.Footer>
          <Button
            variant="secondary"
            size="sm"
            onClick={() => {
              localStorage.removeItem('videoHistory');
              setVideoHistory([]);
            }}
          >
            {t('history.clearHistory')}
          </Button>
          <Button variant="primary" onClick={() => setShowHistory(false)}>
            {t('history.close')}
          </Button>
        </Modal.Footer>
      </Modal>
    </>
  );
}

export default App;
