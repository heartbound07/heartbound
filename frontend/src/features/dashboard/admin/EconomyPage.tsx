import { useEffect, useState } from 'react';
import { useAuth } from '@/contexts/auth';
import { Navigate } from 'react-router-dom';
import httpClient from '@/lib/api/httpClient';
import { Leaderboard } from '@/components/ui/leaderboard/Leaderboard';
import { LeaderboardEntryDTO } from '@/config/userService';
import { SkeletonLeaderboard } from '@/components/ui/SkeletonUI';

interface EconomyStats {
    totalCredits: number;
    totalUsers: number;
    averageCreditsPerUser: number;
    itemsInCirculation: number;
    recommendedPriceRanges: Record<string, string>;
}

export function EconomyPage() {
    const { hasRole } = useAuth();
    const [stats, setStats] = useState<EconomyStats | null>(null);
    const [leaderboard, setLeaderboard] = useState<LeaderboardEntryDTO[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        const fetchData = async () => {
            try {
                setLoading(true);
                const [statsResponse, leaderboardResponse] = await Promise.all([
                    httpClient.get('/users/admin/economy-stats'),
                    httpClient.get('/users/leaderboard?sortBy=credits'),
                ]);
                setStats(statsResponse.data);
                setLeaderboard(leaderboardResponse.data);
            } catch (err) {
                setError('Failed to fetch economy data.');
                console.error(err);
            } finally {
                setLoading(false);
            }
        };

        fetchData();
    }, []);

    if (!hasRole('ADMIN')) {
        return <Navigate to="/dashboard" replace />;
    }

    const StatCard = ({ title, value }: { title: string; value: string | number }) => (
        <div className="bg-slate-800/50 rounded-lg p-5 border border-white/5">
            <h3 className="text-lg font-semibold text-slate-300 mb-2">{title}</h3>
            <p className="text-3xl font-bold text-white">{value}</p>
        </div>
    );

    return (
        <div className="container mx-auto p-6">
            <div className="bg-gradient-to-b from-slate-900/90 to-slate-800/90 backdrop-blur-sm rounded-xl shadow-xl p-6 border border-white/10">
                <h1 className="text-2xl font-bold text-white mb-6">Economy Management</h1>

                {loading && (
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-6">
                        {[...Array(4)].map((_, i) => (
                            <div key={i} className="bg-slate-800/50 rounded-lg p-5 border border-white/5">
                                <div className="h-8 bg-slate-700 rounded w-3/4 mb-2 animate-pulse"></div>
                                <div className="h-10 bg-slate-700 rounded w-1/2 animate-pulse"></div>
                            </div>
                        ))}
                    </div>
                )}


                {error && <p className="text-red-500">{error}</p>}

                {stats && !loading && (
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-6">
                        <StatCard title="Total Credits" value={stats.totalCredits.toLocaleString()} />
                        <StatCard title="Total Users" value={stats.totalUsers.toLocaleString()} />
                        <StatCard title="Avg. Credits Per User" value={stats.averageCreditsPerUser.toFixed(2)} />
                        <StatCard title="Items in Circulation" value={stats.itemsInCirculation.toLocaleString()} />
                    </div>
                )}

                <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                    <div>
                        <h2 className="text-xl font-semibold text-white mb-4">Price Recommendations</h2>
                        <div className="bg-slate-800/50 rounded-lg p-5 border border-white/5">
                            <table className="w-full text-left">
                                <thead>
                                    <tr className="border-b border-slate-700">
                                        <th className="py-2 text-slate-300">Rarity</th>
                                        <th className="py-2 text-slate-300">Recommended Price Range</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {stats && Object.entries(stats.recommendedPriceRanges).map(([rarity, range]) => (
                                        <tr key={rarity} className="border-b border-slate-800">
                                            <td className="py-3 capitalize">{rarity.toLowerCase()}</td>
                                            <td>{range}</td>
                                        </tr>
                                    ))}
                                    {loading && [...Array(5)].map((_, i) => (
                                        <tr key={i} className="border-b border-slate-800">
                                            <td className="py-3"><div className="h-5 bg-slate-700 rounded w-24 animate-pulse"></div></td>
                                            <td><div className="h-5 bg-slate-700 rounded w-40 animate-pulse"></div></td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    </div>
                    <div>
                        <h2 className="text-xl font-semibold text-white mb-4">Top 100 Users by Credits</h2>
                        {loading ? <SkeletonLeaderboard /> : <Leaderboard users={leaderboard} leaderboardType="credits" />}
                    </div>
                </div>
            </div>
        </div>
    );
} 