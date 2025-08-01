import { useReducer, useEffect, useState } from 'react';
import httpClient from '@/lib/api/httpClient';
import { DiscordBotSettingsDTO } from '@/config/discordBotService';

interface DiscordBotSettingsData extends DiscordBotSettingsDTO {}

const initialSettings: DiscordBotSettingsData = {
  activityEnabled: true,
  creditsToAward: 5,
  messageThreshold: 5,
  timeWindowMinutes: 60,
  cooldownSeconds: 30,
  minMessageLength: 15,
  levelingEnabled: true,
  xpToAward: 15,
  baseXp: 100,
  levelMultiplier: 50,
  levelExponent: 2,
  levelFactor: 5,
  creditsPerLevel: 50,
  level5RoleId: "",
  level15RoleId: "",
  level30RoleId: "",
  level40RoleId: "",
  level50RoleId: "",
  level70RoleId: "",
  level100RoleId: "",
  starterRoleId: "",
  roleMultipliers: "",
  roleMultipliersEnabled: false,
  inactivityChannelId: "",
  countingGameEnabled: false,
  countingChannelId: "",
  countingTimeoutRoleId: "",
  creditsPerCount: 1,
  countingLives: 3,
  autoSlowmodeEnabled: false,
  slowmodeChannelIds: "",
  activityThreshold: 10,
  slowmodeTimeWindow: 5,
  slowmodeDuration: 30,
  slowmodeCooldown: 10,
  creditDropEnabled: false,
  creditDropChannelId: "",
  creditDropMinAmount: 1,
  creditDropMaxAmount: 1000,
  partDropEnabled: false,
  partDropChannelId: "",
  partDropChance: 0.05,
  age15RoleId: "",
  age16To17RoleId: "",
  age18PlusRoleId: "",
  genderSheHerRoleId: "",
  genderHeHimRoleId: "",
  genderAskRoleId: "",
  rankIronRoleId: "",
  rankBronzeRoleId: "",
  rankSilverRoleId: "",
  rankGoldRoleId: "",
  rankPlatinumRoleId: "",
  rankDiamondRoleId: "",
  ageRolesThumbnailUrl: "",
  genderRolesThumbnailUrl: "",
  rankRolesThumbnailUrl: "",
  regionNaRoleId: "",
  regionEuRoleId: "",
  regionSaRoleId: "",
  regionApRoleId: "",
  regionOceRoleId: "",
  regionRolesThumbnailUrl: "",
  fishingMinCatches: 500,
  fishingMaxCatches: 1500,
  fishingDefaultMaxCatches: 300,
  fishingCooldownHours: 6,
  fishingLimitWarningThreshold: 0.9,
  fishingPenaltyCredits: 50,
};

type Action =
  | { type: 'SET_SETTINGS'; payload: DiscordBotSettingsData }
  | { type: 'UPDATE_FIELD'; payload: { name: string; value: string | number | boolean } };

const settingsReducer = (state: DiscordBotSettingsData, action: Action): DiscordBotSettingsData => {
  switch (action.type) {
    case 'SET_SETTINGS':
      return action.payload;
    case 'UPDATE_FIELD':
      return { ...state, [action.payload.name]: action.payload.value };
    default:
      return state;
  }
};

export const useDiscordBotSettings = () => {
  const [state, dispatch] = useReducer(settingsReducer, initialSettings);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchData = async () => {
      try {
        setIsLoading(true);
        const response = await httpClient.get<DiscordBotSettingsData>('/admin/discord/settings');
        const fetchedSettings = response.data;
        
        // Create a new settings object based on initialSettings to guarantee all keys exist.
        // Then, overwrite with values from fetchedSettings, but only if they are not null or undefined.
        // This prevents nulls from the DB from overwriting frontend defaults for non-nullable fields.
        const mergedSettings = { ...initialSettings };
        for (const key of Object.keys(fetchedSettings)) {
            const typedKey = key as keyof DiscordBotSettingsData;
            const value = fetchedSettings[typedKey];
            if (value !== null && value !== undefined) {
                (mergedSettings as any)[typedKey] = value;
            }
        }
        
        dispatch({ type: 'SET_SETTINGS', payload: mergedSettings });
      } catch (err) {
        setError('Failed to load settings. Please try again.');
      } finally {
        setIsLoading(false);
      }
    };

    fetchData();
  }, []);

  const updateField = (name: string, value: string | number | boolean) => {
    dispatch({ type: 'UPDATE_FIELD', payload: { name, value } });
  };
  
  const setSettings = (settings: DiscordBotSettingsData) => {
    dispatch({ type: 'SET_SETTINGS', payload: settings });
  }

  return { settings: state, isLoading, error, updateField, setSettings };
};
