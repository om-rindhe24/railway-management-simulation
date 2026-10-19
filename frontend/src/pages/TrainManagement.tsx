import React, { useState } from 'react';
import { Train, TrainType, TrainStatus } from '../types';
import { Train as TrainIcon, Search, Plus, Sliders, Ban, Clock } from 'lucide-react';

interface TrainManagementProps {
  trains: Train[];
  onInjectDelay: (trainId: number, delayMinutes: number, reason: string) => Promise<any>;
  onCancelTrain: (trainId: number, reason: string) => Promise<any>;
  onCreateTrain: (train: Partial<Train>) => Promise<any>;
}

export const TrainManagement: React.FC<TrainManagementProps> = ({
  trains,
  onInjectDelay,
  onCancelTrain,
  onCreateTrain,
}) => {
  const [searchTerm, setSearchTerm] = useState('');
  const [categoryFilter, setCategoryFilter] = useState<string>('ALL');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);

  // New Train Form State
  const [newTrainNumber, setNewTrainNumber] = useState('SIM-EXP-99');
  const [newTrainName, setNewTrainName] = useState('Simulation Surat Superfast');
  const [newTrainType, setNewTrainType] = useState<TrainType>('SUPERFAST');
  const [newSource, setNewSource] = useState('VR');
  const [newDest, setNewDest] = useState('MMCT');
  const [newPriority, setNewPriority] = useState<number>(3);
  const [newTargetStation, setNewTargetStation] = useState('BVI');

  const filteredTrains = trains.filter((t) => {
    const matchesSearch =
      t.trainNumber.toLowerCase().includes(searchTerm.toLowerCase()) ||
      t.trainName.toLowerCase().includes(searchTerm.toLowerCase());
    const matchesCategory = categoryFilter === 'ALL' || t.trainType === categoryFilter;
    const matchesStatus = statusFilter === 'ALL' || t.status === statusFilter;
    return matchesSearch && matchesCategory && matchesStatus;
  });

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    const now = new Date();
    const scheduledArr = new Date(now.getTime() + 15 * 60000).toISOString();
    const scheduledDep = new Date(now.getTime() + 25 * 60000).toISOString();

    await onCreateTrain({
      trainNumber: newTrainNumber,
      trainName: newTrainName,
      trainType: newTrainType,
      sourceStation: newSource,
      destinationStation: newDest,
      simulationPriority: newPriority,
      scheduledArrival: scheduledArr,
      scheduledDeparture: scheduledDep,
      actualArrival: scheduledArr,
      actualDeparture: scheduledDep,
      currentStationCode: newSource,
      targetStationCode: newTargetStation,
      status: 'SCHEDULED',
      delayMinutes: 0,
      isActive: true,
      isSimulated: true,
    });

    setIsAddModalOpen(false);
  };

  return (
    <div className="page-container">
      <div className="card-header" style={{ marginBottom: '20px' }}>
        <div>
          <h2 style={{ fontSize: '1.4rem', fontWeight: 800 }}>Train Fleet & Schedule Management</h2>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>
            Inspect, filter, inject delays, or schedule simulated passenger and freight services.
          </p>
        </div>

        <button className="btn btn-primary" onClick={() => setIsAddModalOpen(true)}>
          <Plus size={16} />
          <span>Register New Train</span>
        </button>
      </div>

      {/* Filter & Search Bar */}
      <div
        style={{
          display: 'flex',
          gap: '12px',
          marginBottom: '20px',
          background: 'var(--bg-secondary)',
          padding: '16px',
          borderRadius: 'var(--radius-md)',
          border: '1px solid var(--border-subtle)',
          flexWrap: 'wrap',
        }}
      >
        <div style={{ flex: 1, minWidth: '220px', position: 'relative' }}>
          <Search size={16} color="var(--text-dim)" style={{ position: 'absolute', top: '12px', left: '12px' }} />
          <input
            type="text"
            placeholder="Search by train number or name..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            style={{
              width: '100%',
              padding: '10px 14px 10px 38px',
              background: 'var(--bg-primary)',
              border: '1px solid var(--border-card)',
              color: 'var(--text-main)',
              borderRadius: 'var(--radius-sm)',
              outline: 'none',
            }}
          />
        </div>

        <select
          value={categoryFilter}
          onChange={(e) => setCategoryFilter(e.target.value)}
          style={{
            background: 'var(--bg-primary)',
            border: '1px solid var(--border-card)',
            color: 'var(--text-main)',
            padding: '10px 14px',
            borderRadius: 'var(--radius-sm)',
          }}
        >
          <option value="ALL">All Categories</option>
          <option value="SPECIAL">Special / Emergency</option>
          <option value="PREMIUM">Premium</option>
          <option value="SUPERFAST">Superfast</option>
          <option value="EXPRESS_MAIL">Express / Mail</option>
          <option value="LOCAL">Suburban Local</option>
          <option value="FREIGHT">Freight</option>
        </select>

        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          style={{
            background: 'var(--bg-primary)',
            border: '1px solid var(--border-card)',
            color: 'var(--text-main)',
            padding: '10px 14px',
            borderRadius: 'var(--radius-sm)',
          }}
        >
          <option value="ALL">All Statuses</option>
          <option value="SCHEDULED">SCHEDULED</option>
          <option value="APPROACHING">APPROACHING</option>
          <option value="ARRIVING">ARRIVING</option>
          <option value="AT_PLATFORM">AT PLATFORM</option>
          <option value="DELAYED">DELAYED</option>
          <option value="WAITING">WAITING</option>
          <option value="DEPARTED">DEPARTED</option>
          <option value="CANCELLED">CANCELLED</option>
        </select>
      </div>

      {/* Train Table */}
      <div className="surface-card" style={{ padding: 0, overflow: 'hidden' }}>
        <table className="data-table">
          <thead>
            <tr>
              <th>Number</th>
              <th>Name</th>
              <th>Category</th>
              <th>Route</th>
              <th>Simulation Priority</th>
              <th>Target Hub</th>
              <th>Platform</th>
              <th>Delay</th>
              <th>Status</th>
              <th style={{ textAlign: 'right' }}>Actions</th>
            </tr>
          </thead>
          <tbody>
            {filteredTrains.map((train) => (
              <tr key={train.trainId}>
                <td style={{ fontFamily: 'var(--font-mono)', fontWeight: 700 }}>{train.trainNumber}</td>
                <td style={{ fontWeight: 600 }}>{train.trainName}</td>
                <td>
                  <span className="badge" style={{ background: 'var(--bg-surface)' }}>
                    {train.trainType}
                  </span>
                </td>
                <td style={{ color: 'var(--text-muted)' }}>
                  {train.sourceStation} &rarr; {train.destinationStation}
                </td>
                <td style={{ textAlign: 'center', fontFamily: 'var(--font-mono)' }}>
                  Tier {train.simulationPriority}
                </td>
                <td style={{ fontFamily: 'var(--font-mono)' }}>{train.targetStationCode}</td>
                <td style={{ fontWeight: 700, color: 'var(--color-primary)' }}>
                  {train.assignedPlatformNumber ? `P${train.assignedPlatformNumber}` : '—'}
                </td>
                <td>
                  {train.delayMinutes > 0 ? (
                    <span style={{ color: 'var(--color-danger)', fontWeight: 700 }}>+{train.delayMinutes}m</span>
                  ) : (
                    <span style={{ color: 'var(--color-success)' }}>0m</span>
                  )}
                </td>
                <td>{train.status}</td>
                <td style={{ textAlign: 'right' }}>
                  <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '6px' }}>
                    <button
                      className="btn-icon"
                      title="Inject +10m Delay"
                      onClick={() => onInjectDelay(train.trainId, (train.delayMinutes || 0) + 10, 'Manual delay injection')}
                    >
                      <Clock size={14} color="var(--color-warning)" />
                    </button>
                    <button
                      className="btn-icon"
                      title="Cancel Train"
                      onClick={() => onCancelTrain(train.trainId, 'Operational cancellation')}
                    >
                      <Ban size={14} color="var(--color-danger)" />
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Add Train Modal */}
      {isAddModalOpen && (
        <div className="modal-overlay">
          <div className="modal-content">
            <h3 style={{ fontSize: '1.25rem', fontWeight: 800, marginBottom: '16px' }}>
              Register Simulation Train Service
            </h3>
            <form onSubmit={handleCreate}>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', marginBottom: '14px' }}>
                <div>
                  <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-dim)', marginBottom: '4px' }}>
                    Train Number
                  </label>
                  <input
                    type="text"
                    required
                    value={newTrainNumber}
                    onChange={(e) => setNewTrainNumber(e.target.value)}
                    style={{ width: '100%', padding: '8px 12px', background: 'var(--bg-primary)', border: '1px solid var(--border-card)', color: '#fff', borderRadius: '4px' }}
                  />
                </div>
                <div>
                  <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-dim)', marginBottom: '4px' }}>
                    Train Name
                  </label>
                  <input
                    type="text"
                    required
                    value={newTrainName}
                    onChange={(e) => setNewTrainName(e.target.value)}
                    style={{ width: '100%', padding: '8px 12px', background: 'var(--bg-primary)', border: '1px solid var(--border-card)', color: '#fff', borderRadius: '4px' }}
                  />
                </div>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', marginBottom: '14px' }}>
                <div>
                  <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-dim)', marginBottom: '4px' }}>
                    Category
                  </label>
                  <select
                    value={newTrainType}
                    onChange={(e) => setNewTrainType(e.target.value as TrainType)}
                    style={{ width: '100%', padding: '8px 12px', background: 'var(--bg-primary)', border: '1px solid var(--border-card)', color: '#fff', borderRadius: '4px' }}
                  >
                    <option value="SPECIAL">SPECIAL / EMERGENCY</option>
                    <option value="PREMIUM">PREMIUM (Rajdhani/Shatabdi style)</option>
                    <option value="SUPERFAST">SUPERFAST</option>
                    <option value="EXPRESS_MAIL">EXPRESS / MAIL</option>
                    <option value="LOCAL">SUBURBAN LOCAL</option>
                    <option value="FREIGHT">FREIGHT</option>
                  </select>
                </div>
                <div>
                  <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-dim)', marginBottom: '4px' }}>
                    Simulation Priority (1 to 6)
                  </label>
                  <input
                    type="number"
                    min="1"
                    max="6"
                    value={newPriority}
                    onChange={(e) => setNewPriority(Number(e.target.value))}
                    style={{ width: '100%', padding: '8px 12px', background: 'var(--bg-primary)', border: '1px solid var(--border-card)', color: '#fff', borderRadius: '4px' }}
                  />
                </div>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '12px', marginBottom: '24px' }}>
                <div>
                  <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-dim)', marginBottom: '4px' }}>
                    Source Code
                  </label>
                  <input
                    type="text"
                    value={newSource}
                    onChange={(e) => setNewSource(e.target.value)}
                    style={{ width: '100%', padding: '8px 12px', background: 'var(--bg-primary)', border: '1px solid var(--border-card)', color: '#fff', borderRadius: '4px' }}
                  />
                </div>
                <div>
                  <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-dim)', marginBottom: '4px' }}>
                    Destination Code
                  </label>
                  <input
                    type="text"
                    value={newDest}
                    onChange={(e) => setNewDest(e.target.value)}
                    style={{ width: '100%', padding: '8px 12px', background: 'var(--bg-primary)', border: '1px solid var(--border-card)', color: '#fff', borderRadius: '4px' }}
                  />
                </div>
                <div>
                  <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-dim)', marginBottom: '4px' }}>
                    Target Station Hub
                  </label>
                  <input
                    type="text"
                    value={newTargetStation}
                    onChange={(e) => setNewTargetStation(e.target.value)}
                    style={{ width: '100%', padding: '8px 12px', background: 'var(--bg-primary)', border: '1px solid var(--border-card)', color: '#fff', borderRadius: '4px' }}
                  />
                </div>
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px' }}>
                <button type="button" className="btn btn-secondary" onClick={() => setIsAddModalOpen(false)}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary">
                  Save Train Service
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
