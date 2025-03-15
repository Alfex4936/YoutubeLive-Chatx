import React from 'react';
import { Card, Button, Image } from 'react-bootstrap';

function UserProfile({ userProfile, onLogout, t }) {
  if (!userProfile) return null;
  
  return (
    <Card className="shadow-sm mb-4">
      <Card.Body>
        <div className="d-flex align-items-center">
          <Image 
            src={userProfile.picture} 
            alt={userProfile.name}
            roundedCircle
            className="me-3"
            style={{ width: '64px', height: '64px' }}
          />
          <div>
            <h5 className="mb-1">{userProfile.name}</h5>
            <p className="text-muted mb-1">{userProfile.email}</p>
            <Button 
              variant="outline-danger" 
              size="sm" 
              onClick={onLogout}
            >
              <i className="bi bi-box-arrow-right me-1"></i>
              {t('auth.logout')}
            </Button>
          </div>
        </div>
      </Card.Body>
    </Card>
  );
}

export default UserProfile;