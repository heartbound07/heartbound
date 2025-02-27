import React from 'react';

interface NotificationBannerProps {
  type?: 'success' | 'error' | 'info' | 'warning';
  message: string;
  onClose?: () => void;
}

const NotificationBanner: React.FC<NotificationBannerProps> = ({ type = 'info', message, onClose }) => {
  let bgColor = '';
  switch (type) {
    case 'success':
      bgColor = 'bg-green-500';
      break;
    case 'error':
      bgColor = 'bg-red-500';
      break;
    case 'warning':
      bgColor = 'bg-yellow-500';
      break;
    case 'info':
    default:
      bgColor = 'bg-blue-500';
      break;
  }

  return (
    <div className={`p-4 text-white rounded ${bgColor} flex justify-between items-center`}>
      <span>{message}</span>
      {onClose && (
        <button onClick={onClose} className="text-white font-bold ml-4">
          X
        </button>
      )}
    </div>
  );
};

export default NotificationBanner;
