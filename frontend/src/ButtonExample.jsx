import React, { useState } from 'react';
import { 
  Button, 
  PlayIcon, 
  StopIcon, 
  PlusIcon, 
  CheckIcon, 
  XIcon, 
  RefreshIcon,
  SettingsIcon,
  TrashIcon,
  EditIcon,
  DownloadIcon
} from './Button';

/**
 * Example component showcasing all Button variants and usage patterns
 * You can import this into your App.jsx to see all button styles
 */
export const ButtonExample = () => {
  const [loading, setLoading] = useState(false);

  const handleClick = () => {
    console.log('Button clicked!');
  };

  const handleLoadingDemo = () => {
    setLoading(true);
    setTimeout(() => setLoading(false), 2000);
  };

  return (
    <div className="p-8 space-y-8 bg-gradient-to-br from-slate-900 via-gray-900 to-slate-900 min-h-screen">
      <div className="max-w-6xl mx-auto">
        <h1 className="text-3xl font-bold text-cyan-300 mb-8">Button Component Examples</h1>

        {/* Variants Section */}
        <section className="space-y-4 mb-12">
          <h2 className="text-xl font-semibold text-white mb-4">Variants</h2>
          <div className="flex flex-wrap gap-4">
            <Button variant="primary" onClick={handleClick}>
              Primary Button
            </Button>
            <Button variant="secondary" onClick={handleClick}>
              Secondary Button
            </Button>
            <Button variant="success" onClick={handleClick}>
              Success Button
            </Button>
            <Button variant="danger" onClick={handleClick}>
              Danger Button
            </Button>
            <Button variant="warning" onClick={handleClick}>
              Warning Button
            </Button>
            <Button variant="ghost" onClick={handleClick}>
              Ghost Button
            </Button>
          </div>
        </section>

        {/* Sizes Section */}
        <section className="space-y-4 mb-12">
          <h2 className="text-xl font-semibold text-white mb-4">Sizes</h2>
          <div className="flex flex-wrap items-center gap-4">
            <Button variant="primary" size="sm" onClick={handleClick}>
              Small Button
            </Button>
            <Button variant="primary" size="md" onClick={handleClick}>
              Medium Button
            </Button>
            <Button variant="primary" size="lg" onClick={handleClick}>
              Large Button
            </Button>
          </div>
        </section>

        {/* With Icons Section */}
        <section className="space-y-4 mb-12">
          <h2 className="text-xl font-semibold text-white mb-4">With Icons</h2>
          <div className="flex flex-wrap gap-4">
            <Button variant="success" icon={<PlayIcon />} onClick={handleClick}>
              Start
            </Button>
            <Button variant="danger" icon={<StopIcon />} onClick={handleClick}>
              Stop
            </Button>
            <Button variant="primary" icon={<PlusIcon />} onClick={handleClick}>
              Add New
            </Button>
            <Button variant="success" icon={<CheckIcon />} onClick={handleClick}>
              Confirm
            </Button>
            <Button variant="danger" icon={<XIcon />} onClick={handleClick}>
              Cancel
            </Button>
            <Button variant="secondary" icon={<RefreshIcon />} onClick={handleClick}>
              Refresh
            </Button>
            <Button variant="secondary" icon={<SettingsIcon />} onClick={handleClick}>
              Settings
            </Button>
            <Button variant="danger" icon={<TrashIcon />} onClick={handleClick}>
              Delete
            </Button>
            <Button variant="secondary" icon={<EditIcon />} onClick={handleClick}>
              Edit
            </Button>
            <Button variant="primary" icon={<DownloadIcon />} onClick={handleClick}>
              Download
            </Button>
          </div>
        </section>

        {/* Icon Only Section */}
        <section className="space-y-4 mb-12">
          <h2 className="text-xl font-semibold text-white mb-4">Icon Only</h2>
          <div className="flex flex-wrap gap-4">
            <Button variant="primary" icon={<PlayIcon />} />
            <Button variant="danger" icon={<StopIcon />} />
            <Button variant="secondary" icon={<SettingsIcon />} />
            <Button variant="danger" icon={<TrashIcon />} />
            <Button variant="secondary" icon={<EditIcon />} />
          </div>
        </section>

        {/* Icon Right Section */}
        <section className="space-y-4 mb-12">
          <h2 className="text-xl font-semibold text-white mb-4">Icon Right</h2>
          <div className="flex flex-wrap gap-4">
            <Button variant="primary" iconRight={<DownloadIcon />} onClick={handleClick}>
              Download Report
            </Button>
            <Button variant="secondary" iconRight={<RefreshIcon />} onClick={handleClick}>
              Reload Data
            </Button>
          </div>
        </section>

        {/* States Section */}
        <section className="space-y-4 mb-12">
          <h2 className="text-xl font-semibold text-white mb-4">States</h2>
          <div className="flex flex-wrap gap-4">
            <Button variant="primary" onClick={handleClick}>
              Normal
            </Button>
            <Button variant="primary" disabled onClick={handleClick}>
              Disabled
            </Button>
            <Button variant="primary" loading={loading} onClick={handleLoadingDemo}>
              {loading ? 'Processing...' : 'Click to Load'}
            </Button>
          </div>
        </section>

        {/* Full Width Section */}
        <section className="space-y-4 mb-12">
          <h2 className="text-xl font-semibold text-white mb-4">Full Width</h2>
          <div className="space-y-3 max-w-md">
            <Button variant="primary" fullWidth onClick={handleClick}>
              Full Width Primary
            </Button>
            <Button variant="secondary" fullWidth onClick={handleClick}>
              Full Width Secondary
            </Button>
            <Button variant="success" fullWidth icon={<CheckIcon />} onClick={handleClick}>
              Full Width with Icon
            </Button>
          </div>
        </section>

        {/* Real-World Examples Section */}
        <section className="space-y-4 mb-12">
          <h2 className="text-xl font-semibold text-white mb-4">Real-World Examples</h2>
          
          {/* Trading Controls */}
          <div className="p-6 bg-slate-800/40 rounded-lg border border-slate-600/30">
            <h3 className="text-lg font-medium text-white mb-4">Trading Controls</h3>
            <div className="flex flex-wrap gap-3">
              <Button variant="success" icon={<PlayIcon />} onClick={handleClick}>
                Start Trading
              </Button>
              <Button variant="danger" icon={<StopIcon />} onClick={handleClick}>
                Stop Trading
              </Button>
              <Button variant="secondary" icon={<RefreshIcon />} onClick={handleClick}>
                Refresh Data
              </Button>
            </div>
          </div>

          {/* Form Actions */}
          <div className="p-6 bg-slate-800/40 rounded-lg border border-slate-600/30 mt-4">
            <h3 className="text-lg font-medium text-white mb-4">Form Actions</h3>
            <div className="flex justify-end gap-3">
              <Button variant="ghost" onClick={handleClick}>
                Cancel
              </Button>
              <Button variant="secondary" onClick={handleClick}>
                Save Draft
              </Button>
              <Button variant="primary" icon={<CheckIcon />} onClick={handleClick}>
                Submit
              </Button>
            </div>
          </div>

          {/* Bot Control Dashboard */}
          <div className="p-6 bg-slate-800/40 rounded-lg border border-slate-600/30 mt-4">
            <h3 className="text-lg font-medium text-white mb-4">Bot Control Dashboard</h3>
            <div className="space-y-3 max-w-sm">
              <Button variant="success" fullWidth icon={<PlayIcon />} onClick={handleClick}>
                Enable Bot
              </Button>
              <div className="flex gap-3">
                <Button variant="primary" size="sm" fullWidth onClick={handleClick}>
                  Start Trading
                </Button>
                <Button variant="warning" size="sm" fullWidth onClick={handleClick}>
                  Stop Trading
                </Button>
              </div>
              <Button variant="danger" fullWidth icon={<StopIcon />} onClick={handleClick}>
                Disable Bot
              </Button>
            </div>
          </div>

          {/* List Actions */}
          <div className="p-6 bg-slate-800/40 rounded-lg border border-slate-600/30 mt-4">
            <h3 className="text-lg font-medium text-white mb-4">List Item Actions</h3>
            <div className="flex items-center justify-between">
              <span className="text-white">Strategy Name</span>
              <div className="flex gap-2">
                <Button variant="secondary" size="sm" icon={<EditIcon />}>
                  Edit
                </Button>
                <Button variant="danger" size="sm" icon={<TrashIcon />}>
                  Delete
                </Button>
              </div>
            </div>
          </div>
        </section>

        {/* Code Examples */}
        <section className="space-y-4 mb-12">
          <h2 className="text-xl font-semibold text-white mb-4">Usage Examples</h2>
          <div className="bg-slate-800/60 rounded-lg p-6 border border-slate-600/30">
            <pre className="text-cyan-300 text-sm overflow-x-auto">
              <code>{`import { Button, PlayIcon, CheckIcon } from './Button';

// Basic button
<Button variant="primary" onClick={handleClick}>
  Click Me
</Button>

// Button with icon
<Button variant="success" icon={<PlayIcon />} onClick={handleStart}>
  Start Trading
</Button>

// Loading button
<Button variant="primary" loading={isLoading} onClick={handleSubmit}>
  Submit
</Button>

// Disabled button
<Button variant="secondary" disabled>
  Disabled
</Button>

// Full width button
<Button variant="primary" fullWidth onClick={handleSave}>
  Save Changes
</Button>

// Different sizes
<Button variant="primary" size="sm">Small</Button>
<Button variant="primary" size="md">Medium</Button>
<Button variant="primary" size="lg">Large</Button>

// All variants
<Button variant="primary">Primary</Button>
<Button variant="secondary">Secondary</Button>
<Button variant="success">Success</Button>
<Button variant="danger">Danger</Button>
<Button variant="warning">Warning</Button>
<Button variant="ghost">Ghost</Button>`}</code>
            </pre>
          </div>
        </section>
      </div>
    </div>
  );
};

export default ButtonExample;

