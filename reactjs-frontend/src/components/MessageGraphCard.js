// MessageGraphCard.jsx
import React from 'react';
import { Card, Button } from 'react-bootstrap';

function MessageGraphCard({ t, refreshedGraphUrl }) {
    const handleDownload = () => {
        // Create a temporary link element
        const link = document.createElement('a');
        link.href = refreshedGraphUrl;
        link.rel = 'noopener noreferrer';
        link.target = '_blank';
        link.download = `message-graph-${new Date().toISOString().slice(0, 10)}.png`;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
    };

    return (
        <Card className="shadow-sm">
            <Card.Body>
                <div className="d-flex justify-content-between align-items-center border-bottom pb-2 mb-3">
                    <Card.Title className="mb-0">
                        {t('stats.messageGraph') || 'Message Activity Graph'}
                    </Card.Title>
                    {refreshedGraphUrl && (
                        <Button
                            variant="link"
                            size="sm"
                            onClick={handleDownload}
                            title={t('stats.downloadGraph') || 'Download Graph'}
                            className="text-dark p-0"
                        >
                            <i className="bi bi-download"></i>
                        </Button>
                    )}
                </div>
                <div className="text-center">
                    <img
                        src={refreshedGraphUrl}
                        alt="Message Activity Graph"
                        className="img-fluid"
                        style={{ maxWidth: '100%', height: 'auto' }}
                        onError={(e) => {
                            e.target.style.display = 'none';
                            e.target.nextSibling.style.display = 'block';
                        }}
                    />
                    <div className="text-muted py-5" style={{ display: 'none' }}>
                        {t('stats.graphNotAvailable')}
                    </div>
                </div>
            </Card.Body>
        </Card>
    );
}

export default MessageGraphCard;
