import React from 'react';

interface ConnectionStatusIndicatorProps {
  status: 'connected' | 'connecting' | 'disconnected';
}

const ConnectionStatusIndicator: React.FC<ConnectionStatusIndicatorProps> = ({ status }) => {
  let message: string;
  let bgColor: string;

  switch (status) {
    case 'connected':
      message = 'Connected';
      bgColor = 'bg-green-500';
      break;
    case 'connecting':
      message = 'Connecting...';
      bgColor = 'bg-yellow-500';
      break;
    case 'disconnected':
    default:
      message = 'Disconnected';
      bgColor = 'bg-red-500';
      break;
  }

  return (
    <div className={`py-2 px-4 rounded text-white ${bgColor}`}>
      {message}
    </div>
  );
};

export default ConnectionStatusIndicator;
