import { useLFG } from '@/contexts/lfg/LFGContext';

const REGIONS = ['NA', 'EU', 'AS', 'OCE'];
const LANGUAGES = ['English', 'Spanish', 'French', 'German', 'Japanese', 'Korean'];
const GAMES = ['Valorant', 'League of Legends', 'CS:GO', 'Dota 2', 'Overwatch'];

export function FilterSidebar() {
  const { state, dispatch } = useLFG();
  const { filters } = state;

  const handleFilterChange = (key: string, value: string | boolean | string[]) => {
    dispatch({
      type: 'SET_FILTERS',
      payload: {
        ...filters,
        [key]: value,
      },
    });
  };

  return (
    <div className="lfg-filters">
      <h2>Filters</h2>

      <div className="filter-section">
        <h3>Game</h3>
        <select
          value={filters.game || ''}
          onChange={(e) => handleFilterChange('game', e.target.value)}
          className="filter-select"
        >
          <option value="">All Games</option>
          {GAMES.map((game) => (
            <option key={game} value={game}>
              {game}
            </option>
          ))}
        </select>
      </div>

      <div className="filter-section">
        <h3>Region</h3>
        <select
          value={filters.region || ''}
          onChange={(e) => handleFilterChange('region', e.target.value)}
          className="filter-select"
        >
          <option value="">All Regions</option>
          {REGIONS.map((region) => (
            <option key={region} value={region}>
              {region}
            </option>
          ))}
        </select>
      </div>

      <div className="filter-section">
        <h3>Languages</h3>
        <div className="language-checkboxes">
          {LANGUAGES.map((lang) => (
            <label key={lang} className="checkbox-label">
              <input
                type="checkbox"
                checked={filters.language?.includes(lang) || false}
                onChange={(e) => {
                  const currentLangs = filters.language || [];
                  const newLangs = e.target.checked
                    ? [...currentLangs, lang]
                    : currentLangs.filter((l) => l !== lang);
                  handleFilterChange('language', newLangs);
                }}
              />
              {lang}
            </label>
          ))}
        </div>
      </div>

      <div className="filter-section">
        <label className="checkbox-label">
          <input
            type="checkbox"
            checked={filters.voiceChat || false}
            onChange={(e) => handleFilterChange('voiceChat', e.target.checked)}
          />
          Voice Chat Required
        </label>
      </div>

      <button
        onClick={() => dispatch({ type: 'SET_FILTERS', payload: {} })}
        className="clear-filters"
      >
        Clear Filters
      </button>
    </div>
  );
} 