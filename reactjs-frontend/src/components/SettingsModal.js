// SettingsModal.jsx
import React from 'react';
import { Modal, Button, Form } from 'react-bootstrap';

// Add isAuthenticated, userProfile, handleLogin, and handleLogout props
function SettingsModal({
    t,
    showSettings,
    setShowSettings,
    notificationSoundEnabled,
    setNotificationSoundEnabled,
    LanguageSelector,
    isAuthenticated,
    userProfile,
    handleLogin,
    handleLogout
}) {
    return (
        <Modal show={showSettings} onHide={() => setShowSettings(false)}>
            <Modal.Header closeButton>
                <Modal.Title>{t('settings.title') || 'Settings'}</Modal.Title>
            </Modal.Header>
            <Modal.Body>
                <Form>
                    {/* User Account Section */}
                    <Form.Group className="mb-4">
                        <Form.Label className="fw-bold">{t('settings.account') || 'Account'}</Form.Label>
                        {isAuthenticated ? (
                            <div className="d-flex align-items-center mt-2">
                                {userProfile && (
                                    <>
                                        <img
                                            src={userProfile.profilePictureUrl}
                                            alt={userProfile.username}
                                            className="rounded-circle me-3"
                                            style={{ width: '40px', height: '40px' }}
                                        />
                                        <div className="me-auto">
                                            <div className="fw-bold">{userProfile.username}</div>
                                            <div className="text-muted small">{userProfile.email}</div>
                                        </div>
                                    </>
                                )}
                                <Button
                                    variant="outline-danger"
                                    size="sm"
                                    onClick={() => {
                                        if (window.confirm(t('auth.confirmLogout'))) {
                                            handleLogout();
                                        }
                                    }}
                                >
                                    <i className="bi bi-box-arrow-right me-1"></i>
                                    {t('auth.logout') || 'Logout'}
                                </Button>
                            </div>
                        ) : (
                            <div className="d-flex align-items-center mt-2">
                                <div className="text-muted me-auto">{t('auth.notLoggedIn')}</div>
                                <Button
                                    variant="outline-primary"
                                    size="sm"
                                    onClick={handleLogin}
                                >
                                    <i className="bi bi-google me-1"></i>
                                    {t('auth.login')}
                                </Button>
                            </div>
                        )}
                    </Form.Group>

                    {/* Existing notification settings */}
                    <Form.Group className="mb-3">
                        <Form.Label className="fw-bold">{t('settings.notifications') || 'Notifications'}</Form.Label>
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

                    {/* Language selector */}
                    <Form.Group className="mb-3">
                        <Form.Label className="fw-bold">{t('settings.appLanguage') || 'Application Language'}</Form.Label>
                        <div className="mt-2">
                            <div className="language-selector-container" style={{ background: '#f8f9fa', padding: '10px', borderRadius: '5px' }}>
                                <LanguageSelector />
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
