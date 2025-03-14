import { useEffect, useRef, useState } from 'react';

// Custom hook for polling
export function usePolling(callback, delay, dependencies = []) {
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

// Custom hook for refreshing an image
export function useImageRefresh(imageUrl, refreshInterval) {
    const [refreshTrigger, setRefreshTrigger] = useState(0);

    useEffect(() => {
        if (!imageUrl) return;

        const interval = setInterval(() => {
            setRefreshTrigger(prev => prev + 1);
        }, refreshInterval);

        return () => clearInterval(interval);
    }, [imageUrl, refreshInterval]);

    // Add null check before calling includes
    if (!imageUrl) return '';
    return `${imageUrl}${imageUrl.includes('?') ? '&' : '?'}t=${refreshTrigger}`;
}