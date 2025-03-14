import React, { useEffect, useState, useCallback, useRef } from 'react';
import {
  Alert,
  Button,
  Col,
  Container,
  Navbar,
  Row,
  Spinner,
  Toast,
  ToastContainer,
} from 'react-bootstrap';

import { useTranslation } from 'react-i18next';
import LanguageSelector from './components/LanguageSelector';

import { formatTimestamp, extractVideoId, availableLanguages } from './utils/helpers';
import { usePolling, useImageRefresh } from './hooks/useCustomHooks';

import ScraperControlsCard from './components/ScraperControlsCard';
import ScraperDataCard from './components/ScraperDataCard';
import MessageGraphCard from './components/MessageGraphCard';
import HistoryModal from './components/HistoryModal';
import SettingsModal from './components/SettingsModal';
import Footer from './components/Footer';

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

  // Add state for toast notifications
  const [showToast, setShowToast] = useState(false);
  const [toastDonation, setToastDonation] = useState(null);
  const previousDonationsRef = useRef([]);
  // Add state for collapsible donations section
  const [showDonations, setShowDonations] = useState(true);

  // Add state for settings
  const [showSettings, setShowSettings] = useState(false);
  const [notificationSoundEnabled, setNotificationSoundEnabled] = useState(() => {
    const savedSetting = localStorage.getItem('notificationSoundEnabled');
    return savedSetting === null ? true : savedSetting === 'true';
  });

  // Use the image refresh hook for the graph
  const { i18n } = useTranslation();
  const graphUrl = videoId ? 
    `http://localhost:8080/scrapers/messageGraph?videoId=${encodeURIComponent(videoId)}&lang=${i18n.language}` : 
    null;
  const refreshedGraphUrl = useImageRefresh(graphUrl, 60000); // Refresh every 60 seconds

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

  // Load settings from localStorage on mount
  useEffect(() => {
    const soundEnabled = localStorage.getItem('notificationSoundEnabled');
    if (soundEnabled !== null) {
      setNotificationSoundEnabled(soundEnabled === 'true');
    }

    // Load previously seen donations from localStorage
    const previousDonations = localStorage.getItem('previousDonations');
    if (previousDonations) {
      previousDonationsRef.current = JSON.parse(previousDonations);
    }
  }, []);

  // Save settings to localStorage when changed
  useEffect(() => {
    localStorage.setItem('notificationSoundEnabled', notificationSoundEnabled.toString());
  }, [notificationSoundEnabled]);

  // Initialize previousDonationsRef on component mount
  useEffect(() => {
    // Load previously seen donations from localStorage
    const previousDonations = localStorage.getItem('previousDonations');
    if (previousDonations) {
      previousDonationsRef.current = JSON.parse(previousDonations);
    } else {
      previousDonationsRef.current = [];
    }
  }, []);

  // Check for new donations and show toast
  useEffect(() => {
    if (scraperData && scraperData.recentDonations && scraperData.recentDonations.length > 0) {
      // Skip toast notification on initial load/refresh if we already have donations in localStorage
      const isInitialLoad = previousDonationsRef.current.length > 0 &&
        !previousDonationsRef.current.some(d => d._processed);

      // Create a unique identifier for each donation
      const createDonationId = (donation) =>
        `${donation.username}|${donation.amount}|${donation.message || ''}`;

      // Find new donations (those in current but not in previous)
      const newDonations = scraperData.recentDonations.filter(donation => {
        const donationId = createDonationId(donation);
        const isNew = !previousDonationsRef.current.some(
          prevDonation => createDonationId(prevDonation) === donationId
        );
        return isNew && !isInitialLoad;
      });

      // If we found new donations, show toast for the most recent one
      if (newDonations.length > 0) {
        setToastDonation(newDonations[0]);
        setShowToast(true);
        // Auto-expand the donations section
        setShowDonations(true);

        // Play notification sound only if enabled
        if (notificationSoundEnabled) {
          const notificationSound = document.getElementById('donationSound');
          if (notificationSound) {
            notificationSound.play().catch(e => console.log("Error playing sound:", e));
          }
        }
      }

      // Always update the previous donations reference with a deep copy
      // Add a _processed flag to indicate these donations have been processed
      const processedDonations = JSON.parse(JSON.stringify(scraperData.recentDonations));
      processedDonations.forEach(d => d._processed = true);
      previousDonationsRef.current = processedDonations;

      // Save to localStorage for persistence across refreshes
      localStorage.setItem('previousDonations', JSON.stringify(previousDonationsRef.current));
    }
  }, [scraperData, notificationSoundEnabled]);

  // Load stored videoId and history on mount
  useEffect(() => {
    const storedId = localStorage.getItem('videoId');
    if (storedId) {
      setVideoId(storedId);
      setInputValue(storedId);
      setPolling(true);
      // If we're polling, assume the scraper is running
      setIsScraperRunning(true);
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

      // Filter out duplicates and keep only the most recent entries
      const filteredHistory = prevHistory.filter(item => item.id !== id);
      const newHistory = [newEntry, ...filteredHistory].slice(0, 5);

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
    }, 300); // debounce
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
          // Update isScraperRunning based on the status
          setIsScraperRunning(data.status === 'RUNNING');
          failureCountRef.current = 0; // Reset failure count on success
        } else if (data.scrapers && data.scrapers[videoId]) {
          // Fallback to previous format if needed
          setScraperData(data.scrapers[videoId]);
          // Update isScraperRunning based on the status
          setIsScraperRunning(data.scrapers[videoId].status === 'RUNNING');
          failureCountRef.current = 0;
        } else {
          setScraperData(null);
          setIsScraperRunning(false);
        }
      })
      .catch((err) => {
        failureCountRef.current++;
        console.error(`Error fetching stats (${failureCountRef.current}/5):`, err);

        if (failureCountRef.current >= 5) {
          console.error("Stopping polling due to repeated failures.");
          setPolling(false);
          setIsScraperRunning(false);
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

  // Add a new state to track if the scraper is running
  const [isScraperRunning, setIsScraperRunning] = useState(false);

  // Memoized start scraper function
  const handleStartScraper = useCallback(() => {
    if (!inputValue) return;
    const actualId = extractVideoId(inputValue);
    localStorage.setItem('videoId', actualId);
    setVideoId(actualId);
    setInputValue(actualId);
    setLoading(true);
    // Set scraper as running
    setIsScraperRunning(true);

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
        // Set scraper as not running
        setIsScraperRunning(false);
        // Fetch stats immediately after stopping
        fetchScraperStats();
      })
      .catch((err) => {
        setLoading(false);
        console.error(err);
        setMessage('Error stopping scraper');
        // Also set scraper as not running in case of error
        setIsScraperRunning(false);
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
              <div
                className="text-light"
                style={{ fontSize: '0.70rem', marginTop: '2px' }}
              >
                {t('app.title')}
              </div>
            </div>

            <div className="d-flex gap-2">
              {/* <LanguageSelector /> */}
              <Button
                variant="outline-light"
                size="sm"
                onClick={() => setShowHistory(true)}
              >
                <i className="bi bi-clock-history"></i>
              </Button>
              <Button
                variant="outline-light"
                size="sm"
                onClick={() => setShowSettings(true)}
              >
                <i className="bi bi-gear"></i>
              </Button>
            </div>
          </div>
        </Container>
      </Navbar>

      {/* MAIN CONTAINER */}
      <Container>
        {/* SCRAPER CONTROLS CARD */}
        <Row className="mb-3">
          <Col md={10} className="mx-auto">
            <ScraperControlsCard
              t={t}
              videoId={videoId}
              inputValue={inputValue}
              handleInputChange={handleInputChange}
              isScraperRunning={isScraperRunning}
              loading={loading}
              handleStartScraper={handleStartScraper}
              handleStopScraper={handleStopScraper}
              selectedLangs={selectedLangs}
              handleLanguageSelect={handleLanguageSelect}
              availableLanguages={availableLanguages}
            />
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

        {/* Global loading spinner */}
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
              <ScraperDataCard
                t={t}
                scraperData={scraperData}
                isScraperRunning={isScraperRunning}
                showDonations={showDonations}
                setShowDonations={setShowDonations}
                formatTimestamp={formatTimestamp}
              />
            </Col>
          </Row>
        ) : (
          <Row>
            <Col md={8} className="mx-auto text-center text-secondary">
              <p>{t('messages.noData')}</p>
            </Col>
          </Row>
        )}

        {/* Message Graph Section */}
        {videoId && scraperData && (
          <Row className="mt-4">
            <Col md={10} className="mx-auto">
              <MessageGraphCard
                t={t}
                refreshedGraphUrl={refreshedGraphUrl}
              />
            </Col>
          </Row>
        )}
      </Container>

      <Footer />

      {/* Toast Container for Donation Notifications */}
      <ToastContainer
        position="bottom-end"
        className="p-3"
        style={{
          zIndex: 1050,
          position: 'fixed',
          bottom: 0,
          right: 0,
          maxWidth: '350px',
        }}
      >
        <Toast
          show={showToast}
          onClose={() => setShowToast(false)}
          delay={15000}
          autohide
          bg="success"
          className="text-white"
        >
          <Toast.Header closeButton>
            <strong className="me-auto">
              {t('donations.newDonation') || 'New Donation!'}
            </strong>
            <small>{formatTimestamp(new Date().toISOString())}</small>
          </Toast.Header>
          <Toast.Body>
            {toastDonation && (
              <div>
                <div className="fw-bold mb-1">
                  {toastDonation.username}: {toastDonation.amount}
                </div>
                {toastDonation.message && (
                  <div className="small">{toastDonation.message}</div>
                )}
              </div>
            )}
          </Toast.Body>
        </Toast>
      </ToastContainer>

      {/* History Modal */}
      <HistoryModal
        t={t}
        showHistory={showHistory}
        setShowHistory={setShowHistory}
        videoHistory={videoHistory}
        loadFromHistory={loadFromHistory}
      />

      {/* Settings Modal */}
      <SettingsModal
        t={t}
        showSettings={showSettings}
        setShowSettings={setShowSettings}
        notificationSoundEnabled={notificationSoundEnabled}
        setNotificationSoundEnabled={setNotificationSoundEnabled}
        LanguageSelector={LanguageSelector}
      />

      {/* Audio element for donation notification */}
      <audio id="donationSound" preload="auto">
        <source src="/sound/donation.mp3" type="audio/mpeg" />
        <source src="/sound/donation.ogg" type="audio/ogg" />
      </audio>
    </>
  );
}

export default App;