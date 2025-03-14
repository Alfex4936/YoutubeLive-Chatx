// HistoryModal.jsx
import React from 'react';
import { Modal, Button, ListGroup } from 'react-bootstrap';

function HistoryModal({
    t,
    showHistory,
    setShowHistory,
    videoHistory,
    loadFromHistory,
}) {
    return (
        <Modal show={showHistory} onHide={() => setShowHistory(false)}>
            <Modal.Header closeButton>
                <Modal.Title>{t('history.title')}</Modal.Title>
            </Modal.Header>
            <Modal.Body>
                {videoHistory.length === 0 ? (
                    <p className="text-center text-muted">
                        {t('history.noHistory')}
                    </p>
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
                                    <div
                                        className="fw-bold text-truncate"
                                        style={{ maxWidth: '80%' }}
                                    >
                                        {item.title}
                                    </div>
                                    <small className="text-muted">
                                        {new Date(item.timestamp).toLocaleDateString()}{' '}
                                        {new Date(item.timestamp).toLocaleTimeString([], {
                                            hour: '2-digit',
                                            minute: '2-digit',
                                        })}
                                    </small>
                                </div>
                                <div className="d-flex justify-content-between mt-1">
                                    <small className="text-muted">
                                        {t('history.id')} {item.id}
                                    </small>
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
                        setShowHistory(false);
                    }}
                >
                    {t('history.clearHistory')}
                </Button>
                <Button variant="primary" onClick={() => setShowHistory(false)}>
                    {t('history.close')}
                </Button>
            </Modal.Footer>
        </Modal>
    );
}

export default HistoryModal;
