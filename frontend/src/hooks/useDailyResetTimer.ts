import { useState, useEffect } from 'react';

const calculateTimeUntilReset = () => {
    const now = new Date();
    const tomorrowUTC = new Date();
    tomorrowUTC.setUTCHours(24, 0, 0, 0); // Next day at 00:00:00 UTC

    const diff = tomorrowUTC.getTime() - now.getTime();

    if (diff <= 0) {
        return { hours: 0, minutes: 0, seconds: 0, formatted: 'Refreshing...' };
    }

    const hours = Math.floor(diff / (1000 * 60 * 60));
    const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
    const seconds = Math.floor((diff % (1000 * 60)) / 1000);

    return {
        hours,
        minutes,
        seconds,
        formatted: `Resets in ${String(hours).padStart(2, '0')}h ${String(minutes).padStart(2, '0')}m ${String(seconds).padStart(2, '0')}s`
    };
};

export const useDailyResetTimer = () => {
    const [timer, setTimer] = useState(calculateTimeUntilReset());

    useEffect(() => {
        const intervalId = setInterval(() => {
            setTimer(calculateTimeUntilReset());
        }, 1000);

        return () => clearInterval(intervalId);
    }, []);

    return timer;
}; 