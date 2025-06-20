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
import { DailyActivityDataDTO } from '@/config/userService';

interface DailyActivityChartProps {
  data: DailyActivityDataDTO[];
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
    
    return (
      <div className="bg-gray-900/90 border border-gray-700 rounded-lg p-3 backdrop-blur-sm">
        <p className="text-white text-sm font-medium mb-1">{formattedDate}</p>
        <p className="text-white text-sm">
          <span className="inline-block w-3 h-3 bg-[#57f287] rounded-full mr-2"></span>
          Messages: {payload[0].value}
        </p>
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
            top: 10,
            right: 15,
            left: 10,
            bottom: 25,
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
            dataKey="count"
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
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}; 