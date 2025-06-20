import React from 'react';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  TooltipProps
} from 'recharts';
import { CombinedDailyActivityDTO } from '@/config/userService';

interface DailyActivityChartProps {
  data: CombinedDailyActivityDTO[];
  loading: boolean;
  error: string | null;
}

// Custom tooltip component - styled for dashboard theme
const CustomTooltip: React.FC<TooltipProps<number, string>> = ({ active, payload, label }) => {
  if (active && payload && payload.length) {
    const date = new Date(label || '');
    const formattedDate = date.toLocaleDateString('en-US', {
      weekday: 'short',
      month: 'short',
      day: 'numeric',
      year: 'numeric'
    });
    
    const formatVoiceTime = (minutes: number) => {
      if (minutes === 0) return "0 mins"
      const hours = Math.floor(minutes / 60)
      const remainingMinutes = minutes % 60
      if (hours === 0) {
        return `${remainingMinutes} mins`
      } else if (remainingMinutes === 0) {
        return hours === 1 ? `${hours} hour` : `${hours} hours`
      } else {
        return `${hours}h ${remainingMinutes}m`
      }
    }
    
    return (
      <div className="bg-gray-900/90 border border-gray-700 rounded-lg p-3 backdrop-blur-sm">
        <p className="text-white text-sm font-medium mb-2">{formattedDate}</p>
        {payload.map((entry, index) => (
          <p key={index} className="text-white text-sm mb-1">
            <span 
              className="inline-block w-3 h-3 rounded-full mr-2" 
              style={{ backgroundColor: entry.color }}
            ></span>
            {entry.dataKey === 'messages' ? `Messages: ${entry.value}` : `Voice: ${formatVoiceTime(Number(entry.value))}`}
          </p>
        ))}
      </div>
    );
  }
  return null;
};

// Format date for X-axis (show every few days to avoid crowding)
const formatXAxisLabel = (tickItem: string, index: number, totalTicks: number) => {
  const date = new Date(tickItem);
  
  // Show fewer labels on smaller screens or when there are many data points
  const shouldShowLabel = totalTicks <= 15 ? true : index % Math.ceil(totalTicks / 7) === 0;
  
  if (!shouldShowLabel) return '';
  
  return date.toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric'
  });
};

export const DailyActivityChart: React.FC<DailyActivityChartProps> = ({ data, loading, error }) => {
  if (loading) {
    return (
      <div className="w-full h-full flex items-center justify-center p-4">
        <div className="animate-pulse w-full h-full bg-white/5 rounded flex items-end justify-around p-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <div
              key={i}
              className="bg-white/20 rounded-t"
              style={{
                height: `${Math.random() * 80 + 20}%`,
                width: '8%'
              }}
            ></div>
          ))}
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="w-full h-full flex flex-col items-center justify-center text-center p-4">
        <div className="text-4xl mb-3">📊</div>
        <h4 className="text-white font-medium mb-2">Unable to load activity data</h4>
        <p className="text-gray-400 text-sm">{error}</p>
      </div>
    );
  }

  if (!data || data.length === 0) {
    return (
      <div className="w-full h-full flex flex-col items-center justify-center text-center p-4">
        <div className="text-4xl mb-3">💬</div>
        <h4 className="text-white font-medium mb-2">No activity to display yet</h4>
        <p className="text-gray-400 text-sm">Start chatting to see your stats!</p>
      </div>
    );
  }

  return (
    <div className="w-full h-full">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart
          data={data}
          margin={{
            top: 25,
            right: 15,
            left: 3,
            bottom: 10,
          }}
        >
          <CartesianGrid 
            strokeDasharray="3 3" 
            stroke="rgba(255, 255, 255, 0.1)"
            horizontal={true}
            vertical={false}
          />
          <XAxis
            dataKey="date"
            axisLine={false}
            tickLine={false}
            tick={{ 
              fill: 'rgba(255, 255, 255, 0.7)', 
              fontSize: 11,
              fontWeight: 500
            }}
            tickFormatter={(value, index) => formatXAxisLabel(value, index, data.length)}
            height={20}
          />
          <YAxis
            axisLine={false}
            tickLine={false}
            tick={{ 
              fill: 'rgba(255, 255, 255, 0.7)', 
              fontSize: 11,
              fontWeight: 500
            }}
            allowDecimals={false}
            width={30}
          />
          <Tooltip content={<CustomTooltip />} />
          <Line
            type="monotone"
            dataKey="messages"
            stroke="#57f287"
            strokeWidth={2.5}
            dot={false}
            activeDot={{
              r: 5,
              fill: '#57f287',
              stroke: '#ffffff',
              strokeWidth: 2
            }}
            connectNulls={false}
          />
          <Line
            type="monotone"
            dataKey="voiceMinutes"
            stroke="#eb459e"
            strokeWidth={2.5}
            dot={false}
            activeDot={{
              r: 5,
              fill: '#eb459e',
              stroke: '#ffffff',
              strokeWidth: 2
            }}
            connectNulls={false}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}; 