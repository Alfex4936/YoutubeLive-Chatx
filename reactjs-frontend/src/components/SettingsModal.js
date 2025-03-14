// SettingsModal.jsx
import React from 'react';
import { Modal, Button, Form, Row, Col } from 'react-bootstrap';

function SettingsModal({
    t,
    showSettings,
    setShowSettings,
    notificationSoundEnabled,
    setNotificationSoundEnabled,
    LanguageSelector,
}) {
    return (
        <Modal show={showSettings} onHide={() => setShowSettings(false)}>
            <Modal.Header closeButton>
                <Modal.Title>{t('settings.title') || 'Settings'}</Modal.Title>
            </Modal.Header>
            <Modal.Body>
                <Form>
                    <Form.Group className="mb-3">
                        <Form.Label>{t('settings.notifications') || 'Notifications'}</Form.Label>
                        <div className="d-flex align-items-center">
                            <Form.Check
                                type="switch"
                                id="notification-sound-switch"
                                checked={notificationSoundEnabled}
                                onChange={(e) => setNotificationSoundEnabled(e.target.checked)}
                                label={t('settings.enableDonationSound')}
                            />
                        </div>
                    </Form.Group>

                    <Form.Group className="mb-3">
                        <Form.Label>{t('settings.appLanguage') || 'Application Language'}</Form.Label>
                        <div className="mt-2">
                            <div className="language-selector-container" style={{ background: '#fff', padding: '10px', borderRadius: '5px' }}>
                                <LanguageSelector darkMode={false} />
                            </div>
                        </div>
                    </Form.Group>
                </Form>
            </Modal.Body>
            <Modal.Footer>
                <Button variant="primary" onClick={() => setShowSettings(false)}>
                    {t('settings.close') || 'Close'}
                </Button>
            </Modal.Footer>
        </Modal>
    );
}

export default SettingsModal;
