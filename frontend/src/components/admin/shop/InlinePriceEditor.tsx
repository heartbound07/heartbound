import React, { useState } from 'react';
import { HiOutlineCheck, HiOutlinePencil, HiOutlineX } from 'react-icons/hi';
import httpClient from '@/lib/api/httpClient';

interface InlinePriceEditorProps {
    itemId: string;
    initialPrice: number;
    onSave: () => void; // Callback to refresh data
}

const InlinePriceEditor: React.FC<InlinePriceEditorProps> = ({ itemId, initialPrice, onSave }) => {
    const [isEditing, setIsEditing] = useState(false);
    const [price, setPrice] = useState(initialPrice);
    const [isSaving, setIsSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const handleSave = async () => {
        if (price < 0) {
            setError("Price cannot be negative.");
            return;
        }
        setIsSaving(true);
        setError(null);
        try {
            await httpClient.patch(`/shop/admin/items/${itemId}/price`, { price });
            setIsEditing(false);
            onSave(); // Trigger data refresh
        } catch (err) {
            setError("Failed to save. Please try again.");
            console.error(err);
        } finally {
            setIsSaving(false);
        }
    };

    if (isEditing) {
        return (
            <div className="flex items-center space-x-2">
                <input
                    type="number"
                    value={price}
                    onChange={(e) => setPrice(Number(e.target.value))}
                    className="w-24 bg-slate-700 border border-primary rounded-md px-2 py-1 text-white focus:outline-none focus:ring-1 focus:ring-primary"
                    autoFocus
                />
                <button onClick={handleSave} disabled={isSaving} className="text-green-400 hover:text-green-300">
                    {isSaving ? <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin"></div> : <HiOutlineCheck size={18} />}
                </button>
                <button onClick={() => { setIsEditing(false); setPrice(initialPrice); setError(null); }} className="text-red-400 hover:text-red-300">
                    <HiOutlineX size={18} />
                </button>
                {error && <p className="text-xs text-red-400">{error}</p>}
            </div>
        );
    }

    return (
        <div className="flex items-center space-x-2">
            <span>{initialPrice}</span>
            <button onClick={() => setIsEditing(true)} className="text-slate-400 hover:text-white">
                <HiOutlinePencil size={14} />
            </button>
        </div>
    );
};

export default InlinePriceEditor; 