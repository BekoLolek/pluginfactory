import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useApprovePlan, useRevisePlan } from '@/hooks/useBuilds';
import type { BudgetFeasibility, PlanDocument } from '@/types';
import type { AxiosError } from 'axios';
import { classifyComplexity } from '@/utils/complexity';

const fmtK = (n: number) => (n >= 1000 ? `${Math.round(n / 1000)}k` : `${n}`);

const VERDICT_CLASSES: Record<string, string> = {
  FITS: 'bg-green-500/15 text-green-300',
  TIGHT: 'bg-amber-500/15 text-amber-300',
  EXCEEDS: 'bg-red-500/15 text-red-300',
};

interface PlanReviewPanelProps {
  plan: PlanDocument;
  sessionId: string;
  readonly?: boolean;
}

interface ScopeViolationResponse {
  message: string;
  violations?: string[];
}

export default function PlanReviewPanel({
  plan,
  sessionId,
  readonly = false,
}: PlanReviewPanelProps) {
  const [showReviseInput, setShowReviseInput] = useState(false);
  const [feedback, setFeedback] = useState('');
  const [scopeViolation, setScopeViolation] = useState<string[] | null>(null);
  const [budgetBlock, setBudgetBlock] = useState<BudgetFeasibility | null>(null);

  const approveMutation = useApprovePlan(sessionId);
  const reviseMutation = useRevisePlan(sessionId);

  const handleApprove = () => {
    setScopeViolation(null);
    setBudgetBlock(null);
    approveMutation.mutate(undefined, {
      onError: (error: Error) => {
        const axiosError = error as AxiosError<
          ScopeViolationResponse & Partial<BudgetFeasibility>
        >;
        if (axiosError.response?.status === 422) {
          const data = axiosError.response.data;
          // Budget feasibility block carries an `estimate`; scope violations don't.
          if (data?.estimate) {
            setBudgetBlock({ estimate: data.estimate, message: data.message ?? '' });
          } else {
            setScopeViolation(
              data?.violations ?? [
                data?.message ?? 'Plan exceeds your tier scope.',
              ],
            );
          }
        }
      },
    });
  };

  const handleRevise = () => {
    if (!feedback.trim()) return;
    reviseMutation.mutate(feedback.trim(), {
      onSuccess: () => {
        setFeedback('');
        setShowReviseInput(false);
      },
    });
  };

  return (
    <div className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden">
      {/* Header */}
      <div className="border-b border-slate-800 px-6 py-4">
        <div className="flex items-center justify-between">
          <div>
            <h3 className="text-lg font-semibold text-white">
              {plan.pluginName}
            </h3>
            <p className="text-sm text-slate-400 mt-0.5">{plan.description}</p>
          </div>
          <div className="flex items-center gap-2">
            <span className="px-2 py-1 rounded bg-slate-800 text-xs text-slate-300">
              {plan.minecraftVersion}
            </span>
            <span className="px-2 py-1 rounded bg-slate-800 text-xs text-slate-300">
              {plan.serverType}
            </span>
          </div>
        </div>
        <div className="flex items-center gap-4 mt-3 text-xs text-slate-500">
          <span>~{plan.estimatedLoc} lines of code</span>
          <span>Version {plan.version}</span>
          {(() => {
            const info = classifyComplexity(plan.complexityScore);
            return (
              <span
                className={`px-2 py-0.5 rounded-full font-medium ${info.classes}`}
                title={`${info.description} (raw score: ${plan.complexityScore})`}
              >
                {info.label}
              </span>
            );
          })()}
          {plan.estimate && (
            <span
              className={`px-2 py-0.5 rounded-full font-medium ${
                VERDICT_CLASSES[plan.estimate.verdict] ?? ''
              }`}
              title={`Estimated build cost ~${fmtK(
                plan.estimate.estimatedTotalTokens,
              )} tokens over ~${plan.estimate.expectedAttempts} attempts (includes testing & retries). You have ~${fmtK(
                plan.estimate.remainingBudget,
              )} tokens left this month.`}
            >
              ~{fmtK(plan.estimate.estimatedTotalTokens)} tokens
              {plan.estimate.verdict === 'EXCEEDS'
                ? ' · over budget'
                : plan.estimate.verdict === 'TIGHT'
                  ? ' · tight'
                  : ' · fits budget'}
            </span>
          )}
        </div>
      </div>

      {/* Commands */}
      {plan.commands?.length > 0 && (
        <div className="border-b border-slate-800 px-6 py-4">
          <h4 className="text-sm font-medium text-white mb-3">Commands</h4>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-xs text-slate-500 border-b border-slate-800">
                  <th className="text-left py-2 pr-4 font-medium">Name</th>
                  <th className="text-left py-2 pr-4 font-medium">
                    Description
                  </th>
                  <th className="text-left py-2 pr-4 font-medium">
                    Permission
                  </th>
                  <th className="text-left py-2 font-medium">Usage</th>
                </tr>
              </thead>
              <tbody>
                {plan.commands.map((cmd) => (
                  <tr
                    key={cmd.name}
                    className="border-b border-slate-800/50 last:border-0"
                  >
                    <td className="py-2 pr-4 text-blue-400 font-mono text-xs">
                      /{cmd.name}
                    </td>
                    <td className="py-2 pr-4 text-slate-300">
                      {cmd.description}
                    </td>
                    <td className="py-2 pr-4 text-slate-400 font-mono text-xs">
                      {cmd.permission}
                    </td>
                    <td className="py-2 text-slate-400 font-mono text-xs">
                      {cmd.usage}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Event Listeners */}
      {plan.eventListeners?.length > 0 && (
        <div className="border-b border-slate-800 px-6 py-4">
          <h4 className="text-sm font-medium text-white mb-3">
            Event Listeners
          </h4>
          <div className="space-y-2">
            {plan.eventListeners.map((listener, i) => (
              <div
                key={`${listener.event}-${i}`}
                className="flex items-start gap-3 bg-slate-800/40 rounded-lg p-3"
              >
                <span className="px-2 py-0.5 rounded bg-slate-700 text-xs text-slate-300 font-mono shrink-0">
                  {listener.event}
                </span>
                <div className="flex-1">
                  <p className="text-sm text-slate-300">
                    {listener.description}
                  </p>
                  {listener.conditions?.length > 0 && (
                    <p className="text-xs text-slate-500 mt-1">
                      Conditions: {listener.conditions.join(', ')}
                    </p>
                  )}
                </div>
                <span className="text-xs text-slate-500">
                  {listener.priority}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Config Options */}
      {plan.configSchema?.length > 0 && (
        <div className="border-b border-slate-800 px-6 py-4">
          <h4 className="text-sm font-medium text-white mb-3">
            Configuration
          </h4>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-xs text-slate-500 border-b border-slate-800">
                  <th className="text-left py-2 pr-4 font-medium">Key</th>
                  <th className="text-left py-2 pr-4 font-medium">Type</th>
                  <th className="text-left py-2 pr-4 font-medium">Default</th>
                  <th className="text-left py-2 font-medium">Description</th>
                </tr>
              </thead>
              <tbody>
                {plan.configSchema.map((entry) => (
                  <tr
                    key={entry.key}
                    className="border-b border-slate-800/50 last:border-0"
                  >
                    <td className="py-2 pr-4 text-blue-400 font-mono text-xs">
                      {entry.key}
                    </td>
                    <td className="py-2 pr-4 text-slate-400 text-xs">
                      {entry.type}
                    </td>
                    <td className="py-2 pr-4 text-slate-400 font-mono text-xs">
                      {entry.defaultValue}
                    </td>
                    <td className="py-2 text-slate-300">{entry.description}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Dependencies */}
      {plan.dependencies?.length > 0 && (
        <div className="border-b border-slate-800 px-6 py-4">
          <h4 className="text-sm font-medium text-white mb-3">Dependencies</h4>
          <div className="space-y-2">
            {plan.dependencies.map((dep) => (
              <div
                key={`${dep.groupId}:${dep.artifactId}`}
                className="flex items-center justify-between bg-slate-800/40 rounded-lg px-3 py-2"
              >
                <span className="font-mono text-xs text-slate-300">
                  {dep.groupId}:{dep.artifactId}:{dep.version}
                </span>
                <span className="text-xs text-slate-500">{dep.reason}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Test Scenarios */}
      {plan.testScenarios?.length > 0 && (
        <div className="border-b border-slate-800 px-6 py-4">
          <h4 className="text-sm font-medium text-white mb-3">
            Test Scenarios
          </h4>
          <div className="space-y-2">
            {plan.testScenarios.map((test, i) => (
              <div
                key={`${test.name}-${i}`}
                className="flex items-start gap-3 bg-slate-800/40 rounded-lg p-3"
              >
                <span className="px-2 py-0.5 rounded bg-slate-700 text-xs text-slate-300 shrink-0 uppercase">
                  {test.type}
                </span>
                <div>
                  <p className="text-sm text-slate-300 font-medium">
                    {test.name}
                  </p>
                  <p className="text-xs text-slate-500 mt-0.5">
                    {test.description}
                  </p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Budget Feasibility Block */}
      {budgetBlock && (
        <div className="mx-6 mt-4 p-4 rounded-lg bg-red-500/10 border border-red-500/20">
          <p className="text-sm font-medium text-red-400 mb-1">
            This build won't fit your token budget
          </p>
          <p className="text-sm text-red-300">{budgetBlock.message}</p>
          <p className="text-xs text-red-300/70 mt-2">
            Estimated ~{fmtK(budgetBlock.estimate.estimatedTotalTokens)} tokens
            (≈{budgetBlock.estimate.expectedAttempts} attempts, incl. testing &
            retries) · ~{fmtK(budgetBlock.estimate.remainingBudget)} left.
          </p>
          <Link
            to="/dashboard/settings/subscription"
            className="inline-block mt-3 text-sm text-blue-400 hover:text-blue-300 transition-colors"
          >
            Upgrade your plan
          </Link>
        </div>
      )}

      {/* Scope Violation Alert */}
      {scopeViolation && (
        <div className="mx-6 mt-4 p-4 rounded-lg bg-red-500/10 border border-red-500/20">
          <p className="text-sm font-medium text-red-400 mb-2">
            Scope Violation
          </p>
          <ul className="space-y-1">
            {scopeViolation.map((v, i) => (
              <li key={i} className="text-sm text-red-300">
                {v}
              </li>
            ))}
          </ul>
          <Link
            to="/dashboard/settings/subscription"
            className="inline-block mt-3 text-sm text-blue-400 hover:text-blue-300 transition-colors"
          >
            Upgrade your plan
          </Link>
        </div>
      )}

      {/* Actions */}
      {!readonly && (
        <div className="px-6 py-4">
          {!showReviseInput ? (
            <div className="flex items-center gap-3">
              <button
                onClick={handleApprove}
                disabled={approveMutation.isPending}
                className="flex-1 py-2.5 rounded-xl bg-green-600 hover:bg-green-700 disabled:opacity-50 disabled:cursor-not-allowed text-white text-sm font-medium transition-colors"
              >
                {approveMutation.isPending
                  ? 'Approving...'
                  : 'Approve & Build'}
              </button>
              <button
                onClick={() => setShowReviseInput(true)}
                className="flex-1 py-2.5 rounded-xl bg-slate-700 hover:bg-slate-600 text-white text-sm font-medium transition-colors"
              >
                Request Changes
              </button>
            </div>
          ) : (
            <div className="space-y-3">
              <textarea
                value={feedback}
                onChange={(e) => setFeedback(e.target.value)}
                placeholder="Describe the changes you want..."
                rows={3}
                className="w-full resize-none rounded-xl bg-slate-800 border border-slate-700 px-4 py-3 text-sm text-white placeholder-slate-500 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
              />
              <div className="flex items-center gap-3">
                <button
                  onClick={handleRevise}
                  disabled={
                    reviseMutation.isPending || !feedback.trim()
                  }
                  className="flex-1 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed text-white text-sm font-medium transition-colors"
                >
                  {reviseMutation.isPending
                    ? 'Submitting...'
                    : 'Submit Feedback'}
                </button>
                <button
                  onClick={() => {
                    setShowReviseInput(false);
                    setFeedback('');
                  }}
                  className="px-4 py-2.5 rounded-xl bg-slate-700 hover:bg-slate-600 text-white text-sm font-medium transition-colors"
                >
                  Cancel
                </button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
