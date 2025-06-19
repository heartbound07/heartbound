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
import '@/assets/dashboard.css';

interface DailyActivityChartProps {
  data: DailyActivityDataDTO[];
  loading: boolean;
  error: string | null;
}

// Custom tooltip component
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
      <div className="chart-tooltip">
        <p className="chart-tooltip-label">{formattedDate}</p>
        <p className="chart-tooltip-value">
          <span className="chart-tooltip-indicator"></span>
          {`Messages: ${payload[0].value}`}
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
      <div className="chart-container">
        <div className="chart-header">
          <h3 className="chart-title">Daily Message Activity</h3>
          <p className="chart-subtitle">Your activity over the last 30 days</p>
        </div>
        <div className="chart-content chart-loading">
          <div className="animate-pulse">
            <div className="h-4 bg-white/20 rounded mb-4 w-1/3"></div>
            <div className="h-64 bg-white/10 rounded flex items-end justify-around p-4">
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
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="chart-container">
        <div className="chart-header">
          <h3 className="chart-title">Daily Message Activity</h3>
          <p className="chart-subtitle">Your activity over the last 30 days</p>
        </div>
        <div className="chart-content chart-error">
          <div className="error-content">
            <div className="error-icon">📊</div>
            <h4 className="error-title">Unable to load activity data</h4>
            <p className="error-message">{error}</p>
          </div>
        </div>
      </div>
    );
  }

  if (!data || data.length === 0) {
    return (
      <div className="chart-container">
        <div className="chart-header">
          <h3 className="chart-title">Daily Message Activity</h3>
          <p className="chart-subtitle">Your activity over the last 30 days</p>
        </div>
        <div className="chart-content chart-empty">
          <div className="empty-content">
            <div className="empty-icon">💬</div>
            <h4 className="empty-title">No activity to display yet</h4>
            <p className="empty-message">Start chatting to see your stats!</p>
          </div>
        </div>
      </div>
    );
  }

  // Calculate some basic statistics
  const totalMessages = data.reduce((sum, item) => sum + item.count, 0);
  const averageMessages = Math.round(totalMessages / data.length);
  const maxMessages = Math.max(...data.map(item => item.count));

  return (
    <div className="chart-container">
      <div className="chart-header">
        <div>
          <h3 className="chart-title">Daily Message Activity</h3>
          <p className="chart-subtitle">Your activity over the last 30 days</p>
        </div>
        <div className="chart-stats">
          <div className="chart-stat">
            <span className="chart-stat-value">{averageMessages}</span>
            <span className="chart-stat-label">Avg/day</span>
          </div>
          <div className="chart-stat">
            <span className="chart-stat-value">{maxMessages}</span>
            <span className="chart-stat-label">Peak day</span>
          </div>
        </div>
      </div>
      
      <div className="chart-content">
        <ResponsiveContainer width="100%" height={300}>
          <LineChart
            data={data}
            margin={{
              top: 20,
              right: 30,
              left: 20,
              bottom: 20,
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
                fontSize: 12,
                fontWeight: 500
              }}
              tickFormatter={(value, index) => formatXAxisLabel(value, index, data.length)}
            />
            <YAxis
              axisLine={false}
              tickLine={false}
              tick={{ 
                fill: 'rgba(255, 255, 255, 0.7)', 
                fontSize: 12,
                fontWeight: 500
              }}
              allowDecimals={false}
            />
            <Tooltip content={<CustomTooltip />} />
            <Line
              type="monotone"
              dataKey="count"
              stroke="#FF4655"
              strokeWidth={3}
              dot={{
                fill: '#FF4655',
                strokeWidth: 2,
                stroke: '#ffffff',
                r: 4
              }}
              activeDot={{
                r: 6,
                fill: '#FF4655',
                stroke: '#ffffff',
                strokeWidth: 2
              }}
              connectNulls={false}
            />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}; 