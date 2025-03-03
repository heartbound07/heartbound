import React, { useState, useEffect } from 'react';
import { ClockIcon } from 'lucide-react';

interface CountdownTimerProps {
  expiresAt: string;
  className?: string;
}

export const CountdownTimer: React.FC<CountdownTimerProps> = ({ expiresAt, className = '' }) => {
  const [timeLeft, setTimeLeft] = useState<{ minutes: number; seconds: number }>({ minutes: 0, seconds: 0 });
  const [isExpired, setIsExpired] = useState(false);

  useEffect(() => {
    // Function to calculate time remaining
    const calculateTimeLeft = () => {
      const expiryTime = new Date(expiresAt).getTime();
      const currentTime = new Date().getTime();
      const difference = expiryTime - currentTime;

      if (difference <= 0) {
        setIsExpired(true);
        setTimeLeft({ minutes: 0, seconds: 0 });
        return;
      }

      const minutes = Math.floor((difference / 1000 / 60) % 60);
      const seconds = Math.floor((difference / 1000) % 60);
      
      setTimeLeft({ minutes, seconds });
    };

    // Calculate immediately and then every second
    calculateTimeLeft();
    const timer = setInterval(calculateTimeLeft, 1000);

    // Cleanup interval on unmount
    return () => clearInterval(timer);
  }, [expiresAt]);

  // Display formats based on status
  const getTimerColor = () => {
    if (isExpired) return 'text-red-500';
    if (timeLeft.minutes < 1) return 'text-amber-500';
    if (timeLeft.minutes < 3) return 'text-amber-400';
    return 'text-green-400';
  };

  const formattedTime = `${timeLeft.minutes.toString().padStart(2, '0')}:${timeLeft.seconds.toString().padStart(2, '0')}`;
  
  return (
    <div className={`flex items-center gap-1.5 ${getTimerColor()} ${className}`}>
      <ClockIcon className="h-4 w-4" />
      <span className="font-medium">
        {isExpired ? 'Expired' : formattedTime}
      </span>
    </div>
  );
};
