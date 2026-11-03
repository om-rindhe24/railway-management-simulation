import React, { useState, useRef, useEffect, useMemo } from 'react';
import { Train } from '../types';
import { Search, ChevronDown, Check, X } from 'lucide-react';

export function formatTrainCategory(t: Train): string {
  const cat = (t.trainCategory || t.trainType || '').toUpperCase();
  if (cat.includes('SLOW')) return 'Slow Local';
  if (cat.includes('FAST')) return 'Fast Local';
  if (cat.includes('SUPERFAST')) return 'Superfast';
  if (cat.includes('PREMIUM')) return 'Premium';
  if (cat.includes('FREIGHT')) return 'Freight';
  if (cat.includes('SPECIAL')) return 'Special';
  if (cat.includes('EXPRESS') || cat.includes('MAIL')) return 'Express';
  if (cat.includes('LOCAL')) {
    if (t.trainName?.toLowerCase().includes('fast')) return 'Fast Local';
    return 'Slow Local';
  }
  return 'Slow Local';
}

export function formatTrainLocation(t: Train): string {
  if (t.status === 'AT_PLATFORM') {
    const stn = t.targetStationCode || t.currentStationCode || 'BVI';
    return `now at ${stn}`;
  }
  if (t.lastStationCode && t.upcomingStationCode && t.lastStationCode !== t.upcomingStationCode) {
    return `now between ${t.lastStationCode} and ${t.upcomingStationCode}`;
  }
  if (t.currentBlockId) {
    const match = t.currentBlockId.match(/BLK-([A-Z]{2,4})-([A-Z]{2,4})/);
    if (match) {
      return `now between ${match[1]} and ${match[2]}`;
    }
    const platMatch = t.currentBlockId.match(/BLK-([A-Z]{2,4})-P\d+/);
    if (platMatch) {
      return `now at ${platMatch[1]}`;
    }
  }
  if (t.lastStationCode) {
    return `now past ${t.lastStationCode}`;
  }
  if (t.currentStationCode) {
    return `now at ${t.currentStationCode}`;
  }
  return 'now in transit';
}

export function formatTrainOptionLabel(t: Train): string {
  const cat = formatTrainCategory(t);
  const dir = (t.direction || 'UP').toUpperCase();
  const loc = formatTrainLocation(t);
  const nextStop = t.nextStopStationCode || t.nextStationCode || t.targetStationCode || 'BVI';
  let platStr = 'no platform yet';
  if (t.assignedPlatformNumber && t.assignedPlatformNumber > 0) {
    const stn = t.targetStationCode || t.currentStationCode || 'BVI';
    platStr = `platform ${stn}-P${t.assignedPlatformNumber}`;
  }
  return `${t.trainNumber} · ${cat} · ${dir} · ${loc} · next ${nextStop} · ${platStr}`;
}

interface TrainComboboxProps {
  trains: Train[];
  selectedTrainId: number;
  onSelectTrain: (trainId: number) => void;
  disabled?: boolean;
}

export const TrainCombobox: React.FC<TrainComboboxProps> = ({
  trains,
  selectedTrainId,
  onSelectTrain,
  disabled = false,
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const containerRef = useRef<HTMLDivElement>(null);
  const searchInputRef = useRef<HTMLInputElement>(null);

  const selectedTrain = useMemo(
    () => trains.find((t) => t.trainId === selectedTrainId) || trains[0],
    [trains, selectedTrainId]
  );

  // Close when clicking outside
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setIsOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // Focus search input on open
  useEffect(() => {
    if (isOpen && searchInputRef.current) {
      searchInputRef.current.focus();
    }
  }, [isOpen]);

  // Filter trains
  const filteredTrains = useMemo(() => {
    if (!searchQuery.trim()) return trains;
    const q = searchQuery.toLowerCase();
    return trains.filter((t) => {
      const fullLabel = formatTrainOptionLabel(t).toLowerCase();
      const num = (t.trainNumber || '').toLowerCase();
      const name = (t.trainName || '').toLowerCase();
      return fullLabel.includes(q) || num.includes(q) || name.includes(q);
    });
  }, [trains, searchQuery]);

  // Group by direction -> category
  const groupedTrains = useMemo(() => {
    const directions = ['UP', 'DOWN'];
    const groups: {
      direction: string;
      categories: { category: string; trains: Train[] }[];
    }[] = [];

    directions.forEach((dir) => {
      const dirTrains = filteredTrains.filter(
        (t) => (t.direction || 'UP').toUpperCase() === dir
      );
      if (dirTrains.length === 0) return;

      const catMap = new Map<string, Train[]>();
      dirTrains.forEach((t) => {
        const cat = formatTrainCategory(t);
        if (!catMap.has(cat)) catMap.set(cat, []);
        catMap.get(cat)!.push(t);
      });

      const categories: { category: string; trains: Train[] }[] = [];
      const categoryOrder = [
        'Slow Local',
        'Fast Local',
        'Express',
        'Superfast',
        'Premium',
        'Freight',
        'Special',
      ];

      categoryOrder.forEach((c) => {
        if (catMap.has(c)) {
          categories.push({ category: c, trains: catMap.get(c)! });
          catMap.delete(c);
        }
      });
      catMap.forEach((list, c) => categories.push({ category: c, trains: list }));

      groups.push({ direction: dir, categories });
    });

    return groups;
  }, [filteredTrains]);

  return (
    <div
      ref={containerRef}
      style={{ position: 'relative', width: '100%' }}
      className="train-combobox-root"
    >
      {/* Combobox Trigger */}
      <button
        type="button"
        data-testid="train-combobox-trigger"
        disabled={disabled}
        onClick={() => setIsOpen((prev) => !prev)}
        style={{
          width: '100%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '10px 14px',
          background: 'rgba(15, 23, 42, 0.85)',
          border: isOpen ? '1px solid var(--color-primary)' : '1px solid rgba(255, 255, 255, 0.15)',
          borderRadius: 'var(--radius-sm, 6px)',
          color: '#f8fafc',
          fontSize: '0.82rem',
          fontWeight: 600,
          cursor: disabled ? 'not-allowed' : 'pointer',
          textAlign: 'left',
          boxShadow: '0 2px 4px rgba(0,0,0,0.2)',
          transition: 'all 0.15s ease',
        }}
      >
        <span
          style={{
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
            marginRight: '8px',
          }}
        >
          {selectedTrain
            ? formatTrainOptionLabel(selectedTrain)
            : 'Select target train service...'}
        </span>
        <ChevronDown
          size={16}
          style={{
            flexShrink: 0,
            transform: isOpen ? 'rotate(180deg)' : 'none',
            transition: 'transform 0.2s ease',
            color: 'var(--text-muted, #94a3b8)',
          }}
        />
      </button>

      {/* Dropdown Menu */}
      {isOpen && (
        <div
          data-testid="train-combobox-dropdown"
          style={{
            position: 'absolute',
            top: 'calc(100% + 6px)',
            left: 0,
            width: '100%',
            minWidth: '380px',
            maxHeight: '400px',
            background: '#0f172a',
            border: '1px solid rgba(255, 255, 255, 0.2)',
            borderRadius: 'var(--radius-sm, 8px)',
            boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.7), 0 10px 10px -5px rgba(0, 0, 0, 0.5)',
            zIndex: 1000,
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden',
          }}
        >
          {/* Search Box */}
          <div
            style={{
              padding: '10px 12px',
              borderBottom: '1px solid rgba(255, 255, 255, 0.1)',
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              background: '#1e293b',
            }}
          >
            <Search size={15} style={{ color: '#94a3b8', flexShrink: 0 }} />
            <input
              ref={searchInputRef}
              data-testid="train-combobox-search"
              type="text"
              placeholder="Search train (e.g. SIM-LOC-901, Fast Local, VR, BVI)..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              style={{
                flex: 1,
                background: 'transparent',
                border: 'none',
                outline: 'none',
                color: '#f8fafc',
                fontSize: '0.82rem',
                fontWeight: 500,
              }}
            />
            {searchQuery && (
              <button
                type="button"
                onClick={() => setSearchQuery('')}
                style={{
                  background: 'none',
                  border: 'none',
                  color: '#94a3b8',
                  cursor: 'pointer',
                  padding: '2px',
                }}
              >
                <X size={14} />
              </button>
            )}
            <span
              style={{
                fontSize: '0.7rem',
                color: '#64748b',
                background: '#0f172a',
                padding: '2px 6px',
                borderRadius: '4px',
                fontFamily: 'monospace',
              }}
            >
              {filteredTrains.length} / {trains.length}
            </span>
          </div>

          {/* Scrollable Grouped List */}
          <div
            style={{
              flex: 1,
              overflowY: 'auto',
              maxHeight: '340px',
              padding: '6px 0',
            }}
          >
            {groupedTrains.length === 0 ? (
              <div
                style={{
                  padding: '24px 16px',
                  textAlign: 'center',
                  color: '#94a3b8',
                  fontSize: '0.82rem',
                }}
              >
                No trains matching "{searchQuery}"
              </div>
            ) : (
              groupedTrains.map((dirGroup) => (
                <div key={dirGroup.direction} style={{ marginBottom: '8px' }}>
                  {/* Direction Header */}
                  <div
                    style={{
                      padding: '6px 14px',
                      fontSize: '0.7rem',
                      fontWeight: 800,
                      textTransform: 'uppercase',
                      letterSpacing: '0.08em',
                      color: dirGroup.direction === 'UP' ? '#38bdf8' : '#fb923c',
                      background: 'rgba(255, 255, 255, 0.03)',
                      borderTop: '1px solid rgba(255, 255, 255, 0.05)',
                      borderBottom: '1px solid rgba(255, 255, 255, 0.05)',
                    }}
                  >
                    {dirGroup.direction} Line Traffic
                  </div>

                  {dirGroup.categories.map((catGroup) => (
                    <div key={catGroup.category}>
                      {/* Category Subheader */}
                      <div
                        style={{
                          padding: '4px 14px 2px 20px',
                          fontSize: '0.68rem',
                          fontWeight: 700,
                          color: '#94a3b8',
                          letterSpacing: '0.04em',
                        }}
                      >
                        {catGroup.category} ({catGroup.trains.length})
                      </div>

                      {/* Trains inside category */}
                      {catGroup.trains.map((train) => {
                        const isSelected = train.trainId === selectedTrainId;
                        const label = formatTrainOptionLabel(train);

                        return (
                          <div
                            key={train.trainId}
                            data-testid="train-combobox-option"
                            data-train-id={train.trainId}
                            onClick={() => {
                              onSelectTrain(train.trainId);
                              setIsOpen(false);
                            }}
                            style={{
                              padding: '8px 14px 8px 24px',
                              cursor: 'pointer',
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'space-between',
                              background: isSelected
                                ? 'rgba(59, 130, 246, 0.25)'
                                : 'transparent',
                              borderLeft: isSelected
                                ? '3px solid #3b82f6'
                                : '3px solid transparent',
                              color: isSelected ? '#ffffff' : '#e2e8f0',
                              fontSize: '0.8rem',
                              fontFamily: 'system-ui, -apple-system, sans-serif',
                              lineHeight: 1.4,
                              transition: 'background 0.1s ease',
                            }}
                            onMouseEnter={(e) => {
                              if (!isSelected) {
                                e.currentTarget.style.background = 'rgba(255, 255, 255, 0.06)';
                              }
                            }}
                            onMouseLeave={(e) => {
                              if (!isSelected) {
                                e.currentTarget.style.background = 'transparent';
                              }
                            }}
                          >
                            <span style={{ marginRight: '8px' }}>{label}</span>
                            {isSelected && (
                              <Check
                                size={15}
                                style={{ color: '#38bdf8', flexShrink: 0 }}
                              />
                            )}
                          </div>
                        );
                      })}
                    </div>
                  ))}
                </div>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
};

