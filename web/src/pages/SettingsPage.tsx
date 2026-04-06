import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '@/stores/authStore';
import { useToastStore } from '@/stores/toastStore';
import { logout as logoutApi } from '@/api/auth';
import {
  getApiKeys,
  createApiKey,
  revokeApiKey,
  updateProfile,
} from '@/api/users';
import type { ApiKeyDto } from '@/types';
import LoadingSkeleton from '@/components/LoadingSkeleton';
import EmptyState from '@/components/EmptyState';

export default function SettingsPage() {
  const user = useAuthStore((s) => s.user);
  const logoutStore = useAuthStore((s) => s.logout);
  const navigate = useNavigate();
  const addToast = useToastStore((s) => s.addToast);
  const queryClient = useQueryClient();

  const handleLogout = async () => {
    try {
      await logoutApi();
    } catch {
      // ignore logout API errors
    }
    queryClient.clear();
    logoutStore();
    navigate('/');
  };

  return (
    <div>
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-white">Settings</h1>
        <p className="mt-1 text-slate-400">
          Manage your account and preferences.
        </p>
      </div>

      <div className="space-y-6 max-w-2xl">
        {/* Profile section */}
        <ProfileSection user={user} addToast={addToast} />

        {/* API Keys section */}
        <ApiKeysSection addToast={addToast} />

        {/* Subscription link */}
        <div className="bg-slate-900 border border-slate-800 rounded-xl p-6">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-lg font-semibold text-white">Subscription</h2>
              <p className="text-sm text-slate-400 mt-1">
                View and manage your subscription plan and billing.
              </p>
            </div>
            <Link
              to="/dashboard/settings/subscription"
              className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-sm text-slate-300 font-medium transition-colors"
            >
              Manage
              <svg
                className="w-4 h-4"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M9 5l7 7-7 7"
                />
              </svg>
            </Link>
          </div>
        </div>

        {/* Danger zone */}
        <div className="bg-slate-900 border border-red-900/30 rounded-xl p-6">
          <h2 className="text-lg font-semibold text-red-400 mb-4">
            Danger Zone
          </h2>
          <p className="text-sm text-slate-400 mb-4">
            Sign out of your account on this device.
          </p>
          <button
            onClick={handleLogout}
            className="px-4 py-2 rounded-lg bg-red-600/10 border border-red-600/30 text-red-400 text-sm font-medium hover:bg-red-600/20 transition-colors"
          >
            Logout
          </button>
        </div>
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Profile Section                                                    */
/* ------------------------------------------------------------------ */

function ProfileSection({
  user,
  addToast,
}: {
  user: { id: string; email: string; displayName: string; discordId: string } | null;
  addToast: (type: 'success' | 'error' | 'info' | 'warning', message: string) => void;
}) {
  const [editing, setEditing] = useState(false);
  const [displayName, setDisplayName] = useState(user?.displayName ?? '');
  const [saving, setSaving] = useState(false);
  const updateUser = useAuthStore((s) => s.updateUser);

  const handleSave = async () => {
    if (!displayName.trim()) return;
    setSaving(true);
    try {
      await updateProfile(displayName.trim());
      addToast('success', 'Display name updated.');
      setEditing(false);
      updateUser({ displayName: displayName.trim() });
    } catch {
      addToast('error', 'Failed to update display name.');
    } finally {
      setSaving(false);
    }
  };

  const handleCancel = () => {
    setDisplayName(user?.displayName ?? '');
    setEditing(false);
  };

  return (
    <div className="bg-slate-900 border border-slate-800 rounded-xl p-6">
      <h2 className="text-lg font-semibold text-white mb-4">Profile</h2>
      {user ? (
        <div className="space-y-4">
          <div className="flex items-center gap-4">
            <div className="w-16 h-16 rounded-full bg-blue-600 flex items-center justify-center text-white text-2xl font-bold shrink-0">
              {(editing ? displayName : user.displayName).charAt(0).toUpperCase()}
            </div>
            <div className="flex-1 min-w-0">
              {editing ? (
                <div className="flex items-center gap-2">
                  <input
                    type="text"
                    value={displayName}
                    onChange={(e) => setDisplayName(e.target.value)}
                    maxLength={50}
                    className="flex-1 px-3 py-1.5 rounded-lg bg-slate-800 border border-slate-700 text-white text-sm focus:outline-none focus:border-blue-500"
                    autoFocus
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') void handleSave();
                      if (e.key === 'Escape') handleCancel();
                    }}
                  />
                  <button
                    onClick={() => void handleSave()}
                    disabled={saving || !displayName.trim()}
                    className="px-3 py-1.5 rounded-lg bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium disabled:opacity-50 transition-colors"
                  >
                    {saving ? 'Saving...' : 'Save'}
                  </button>
                  <button
                    onClick={handleCancel}
                    className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 text-sm transition-colors"
                  >
                    Cancel
                  </button>
                </div>
              ) : (
                <div className="flex items-center gap-2">
                  <p className="text-lg font-medium text-white">
                    {user.displayName}
                  </p>
                  <button
                    onClick={() => {
                      setDisplayName(user.displayName);
                      setEditing(true);
                    }}
                    className="p-1 rounded text-slate-400 hover:text-blue-400 transition-colors"
                    title="Edit display name"
                  >
                    <svg
                      className="w-4 h-4"
                      fill="none"
                      stroke="currentColor"
                      viewBox="0 0 24 24"
                    >
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={2}
                        d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z"
                      />
                    </svg>
                  </button>
                </div>
              )}
              <p className="text-sm text-slate-400 mt-0.5">{user.email}</p>
            </div>
          </div>

          <div className="pt-4 border-t border-slate-800 space-y-3">
            <div className="flex justify-between text-sm">
              <span className="text-slate-400">User ID</span>
              <span className="text-white font-mono text-xs">
                {user.id}
              </span>
            </div>
            <div className="flex justify-between text-sm">
              <span className="text-slate-400">Discord ID</span>
              <span className="text-white font-mono text-xs">
                {user.discordId}
              </span>
            </div>
          </div>
        </div>
      ) : (
        <p className="text-slate-400 text-sm">No user data available.</p>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* API Keys Section                                                   */
/* ------------------------------------------------------------------ */

function ApiKeysSection({
  addToast,
}: {
  addToast: (type: 'success' | 'error' | 'info' | 'warning', message: string) => void;
}) {
  const queryClient = useQueryClient();
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [newKeyName, setNewKeyName] = useState('');
  const [createdKey, setCreatedKey] = useState<string | null>(null);
  const [confirmRevoke, setConfirmRevoke] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const {
    data: apiKeys,
    isLoading,
    isError,
  } = useQuery({
    queryKey: ['apiKeys'],
    queryFn: getApiKeys,
    retry: false,
  });

  const createMutation = useMutation({
    mutationFn: (name: string) => createApiKey(name),
    onSuccess: (data) => {
      setCreatedKey(data.key);
      setNewKeyName('');
      setShowCreateForm(false);
      void queryClient.invalidateQueries({ queryKey: ['apiKeys'] });
      addToast('success', 'API key created successfully.');
    },
    onError: () => {
      addToast('error', 'Failed to create API key.');
    },
  });

  const revokeMutation = useMutation({
    mutationFn: (keyId: string) => revokeApiKey(keyId),
    onSuccess: () => {
      setConfirmRevoke(null);
      void queryClient.invalidateQueries({ queryKey: ['apiKeys'] });
      addToast('success', 'API key revoked.');
    },
    onError: () => {
      addToast('error', 'Failed to revoke API key.');
    },
  });

  const handleCreate = () => {
    if (!newKeyName.trim()) return;
    createMutation.mutate(newKeyName.trim());
  };

  const handleCopy = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      addToast('error', 'Failed to copy to clipboard.');
    }
  };

  return (
    <div className="bg-slate-900 border border-slate-800 rounded-xl p-6">
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-lg font-semibold text-white">API Keys</h2>
        {!showCreateForm && !createdKey && (
          <button
            onClick={() => setShowCreateForm(true)}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium transition-colors"
          >
            <svg
              className="w-4 h-4"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M12 4v16m8-8H4"
              />
            </svg>
            Create API Key
          </button>
        )}
      </div>

      <p className="text-sm text-slate-400 mb-4">
        Manage API keys for programmatic access to the PluginFactory API.
      </p>

      {/* Created key display (one-time view) */}
      {createdKey && (
        <div className="mb-4 p-4 rounded-lg bg-green-900/20 border border-green-700/30">
          <div className="flex items-start gap-2 mb-2">
            <svg
              className="w-5 h-5 text-amber-400 shrink-0 mt-0.5"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 16.5c-.77.833.192 2.5 1.732 2.5z"
              />
            </svg>
            <p className="text-sm text-amber-300">
              This is the only time you will see this key. Copy it now and store
              it securely.
            </p>
          </div>
          <div className="flex items-center gap-2">
            <code className="flex-1 px-3 py-2 rounded-lg bg-slate-950 text-green-400 text-sm font-mono break-all select-all">
              {createdKey}
            </code>
            <button
              onClick={() => void handleCopy(createdKey)}
              className="shrink-0 px-3 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 text-sm transition-colors"
            >
              {copied ? 'Copied!' : 'Copy'}
            </button>
          </div>
          <button
            onClick={() => setCreatedKey(null)}
            className="mt-3 text-xs text-slate-500 hover:text-slate-400 transition-colors"
          >
            Dismiss
          </button>
        </div>
      )}

      {/* Create form */}
      {showCreateForm && (
        <div className="mb-4 p-4 rounded-lg bg-slate-800/50 border border-slate-700/50">
          <p className="text-sm text-white font-medium mb-2">New API Key</p>
          <div className="flex items-center gap-2">
            <input
              type="text"
              value={newKeyName}
              onChange={(e) => setNewKeyName(e.target.value)}
              placeholder="Key name (e.g. CI Pipeline)"
              maxLength={100}
              className="flex-1 px-3 py-2 rounded-lg bg-slate-900 border border-slate-700 text-white text-sm placeholder-slate-500 focus:outline-none focus:border-blue-500"
              autoFocus
              onKeyDown={(e) => {
                if (e.key === 'Enter') handleCreate();
                if (e.key === 'Escape') {
                  setShowCreateForm(false);
                  setNewKeyName('');
                }
              }}
            />
            <button
              onClick={handleCreate}
              disabled={
                createMutation.isPending || !newKeyName.trim()
              }
              className="px-4 py-2 rounded-lg bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium disabled:opacity-50 transition-colors"
            >
              {createMutation.isPending ? 'Creating...' : 'Create'}
            </button>
            <button
              onClick={() => {
                setShowCreateForm(false);
                setNewKeyName('');
              }}
              className="px-3 py-2 rounded-lg bg-slate-700 hover:bg-slate-600 text-slate-300 text-sm transition-colors"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Keys list */}
      {isLoading ? (
        <LoadingSkeleton variant="table-row" count={2} />
      ) : isError ? (
        <div className="bg-slate-800/50 border border-slate-700/50 rounded-lg p-4 text-center">
          <p className="text-sm text-slate-500">
            Unable to load API keys.
          </p>
        </div>
      ) : !apiKeys || apiKeys.length === 0 ? (
        <EmptyState
          title="No API keys"
          description="Create an API key to access the PluginFactory API programmatically."
          actionLabel="Create API Key"
          onAction={() => setShowCreateForm(true)}
        />
      ) : (
        <div className="space-y-2">
          {apiKeys.map((key: ApiKeyDto) => (
            <div
              key={key.id}
              className="flex items-center justify-between bg-slate-800/50 border border-slate-700/50 rounded-lg px-4 py-3"
            >
              <div>
                <p className="text-sm font-medium text-white">{key.name}</p>
                <p className="text-xs text-slate-500 mt-0.5">
                  ****{key.lastFour} -- Created{' '}
                  {new Date(key.createdAt).toLocaleDateString()}
                </p>
              </div>
              <div>
                {confirmRevoke === key.id ? (
                  <div className="flex items-center gap-2">
                    <span className="text-xs text-slate-400">Revoke?</span>
                    <button
                      onClick={() => revokeMutation.mutate(key.id)}
                      disabled={revokeMutation.isPending}
                      className="px-2.5 py-1 rounded text-xs bg-red-600/20 text-red-400 hover:bg-red-600/30 disabled:opacity-50 transition-colors"
                    >
                      {revokeMutation.isPending ? 'Revoking...' : 'Confirm'}
                    </button>
                    <button
                      onClick={() => setConfirmRevoke(null)}
                      className="px-2.5 py-1 rounded text-xs bg-slate-700 text-slate-300 hover:bg-slate-600 transition-colors"
                    >
                      Cancel
                    </button>
                  </div>
                ) : (
                  <button
                    onClick={() => setConfirmRevoke(key.id)}
                    className="px-2.5 py-1 rounded text-xs text-red-400 hover:bg-red-600/10 transition-colors"
                  >
                    Revoke
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
