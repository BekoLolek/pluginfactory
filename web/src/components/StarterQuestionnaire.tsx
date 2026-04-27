import { useState } from 'react';

/**
 * One-shot questionnaire that replaces the empty-state prompt when a user
 * first lands in a fresh build session. Every question has a
 * "Doesn't matter" escape hatch so the user is never forced into a choice,
 * and the whole thing can be skipped with a single click to go straight to
 * free-form chat.
 *
 * When submitted, the selected answers are stitched into a natural-language
 * first message and passed to {@link StarterQuestionnaireProps.onSubmit},
 * which is wired to the normal send flow — so the user sees their composed
 * message appear instantly in the chat just like any other send.
 */
export interface StarterQuestionnaireProps {
  onSubmit: (composedMessage: string) => void;
  onSkip: () => void;
  disabled?: boolean;
}

// Sentinel for the "Doesn't matter" choice. Using a distinct value (rather
// than `null`) lets us distinguish "user picked 'doesn't matter'" from
// "user hasn't answered yet" — the former still counts as answered and
// shows up in the composed message as a permissive statement.
const DONT_CARE = '__dont_care__' as const;

interface Question {
  id: string;
  label: string;
  helper?: string;
  options: { value: string; label: string }[];
  /** How the answer is phrased in the final composed message. */
  render: (answer: string) => string | null;
  /**
   * If true, suppress the "Doesn't matter" button for this question.
   * Used for fields that get templated into pom.xml verbatim — a vague
   * answer like "1.21.x" leaks into the Maven coordinates and breaks
   * the build. The user can still skip the whole questionnaire.
   */
  required?: boolean;
}

const QUESTIONS: Question[] = [
  {
    id: 'platform',
    label: 'Which server platform?',
    helper: 'Paper is the most common choice for modern servers.',
    options: [
      { value: 'paper', label: 'Paper' },
      { value: 'spigot', label: 'Spigot' },
      { value: 'bukkit', label: 'Bukkit' },
      { value: 'folia', label: 'Folia' },
    ],
    render: (a) =>
      a === DONT_CARE
        ? 'Server platform: no strong preference.'
        : `Server platform: ${a.charAt(0).toUpperCase() + a.slice(1)}.`,
  },
  {
    id: 'version',
    label: 'Which Minecraft version?',
    helper:
      'Pick the exact Paper release you target. This sets the paper-api Maven version, so it must be a real release — no wildcards.',
    required: true,
    options: [
      { value: '1.21.4', label: '1.21.4' },
      { value: '1.21.3', label: '1.21.3' },
      { value: '1.21.1', label: '1.21.1' },
      { value: '1.20.6', label: '1.20.6' },
      { value: '1.20.4', label: '1.20.4' },
      { value: '1.20.1', label: '1.20.1' },
      { value: '1.19.4', label: '1.19.4' },
    ],
    render: (a) => `Minecraft version: ${a} (use exactly this paper-api version).`,
  },
  {
    id: 'category',
    label: 'What kind of plugin is it?',
    options: [
      { value: 'gameplay', label: 'Gameplay feature' },
      { value: 'economy', label: 'Economy / shop' },
      { value: 'admin', label: 'Admin / moderation' },
      { value: 'minigame', label: 'Mini-game' },
      { value: 'utility', label: 'Utility / QoL' },
      { value: 'other', label: 'Something else' },
    ],
    render: (a) => {
      if (a === DONT_CARE) return 'Category: open to suggestions.';
      const labels: Record<string, string> = {
        gameplay: 'a gameplay feature',
        economy: 'an economy / shop plugin',
        admin: 'an admin / moderation tool',
        minigame: 'a mini-game',
        utility: 'a utility / QoL plugin',
        other: 'something outside the usual categories',
      };
      return `Category: ${labels[a] ?? a}.`;
    },
  },
  {
    id: 'persistence',
    label: 'Does it need to remember data across restarts?',
    helper:
      'E.g. player balances, claimed land, high scores. "No" means in-memory only.',
    options: [
      { value: 'yes', label: 'Yes' },
      { value: 'no', label: 'No' },
    ],
    render: (a) => {
      if (a === DONT_CARE) return 'Persistence: undecided.';
      return a === 'yes'
        ? 'Persistence: yes, data should survive restarts.'
        : 'Persistence: no, in-memory is fine.';
    },
  },
  {
    id: 'scale',
    label: 'What size server is this for?',
    options: [
      { value: 'solo', label: 'Just me / friends' },
      { value: 'small', label: 'Small server (10–50)' },
      { value: 'large', label: 'Large server (100+)' },
    ],
    render: (a) => {
      if (a === DONT_CARE) return 'Server size: no strong preference.';
      const labels: Record<string, string> = {
        solo: 'just me and a few friends',
        small: 'a small server of 10–50 players',
        large: 'a large server of 100+ players',
      };
      return `Target audience: ${labels[a] ?? a}.`;
    },
  },
];

const IDEA_MAX = 1000;
const COMMANDS_MAX = 500;

/**
 * Stitches all free-text fields, multi-choice answers, and the
 * always-included permissions requirement into a single first message
 * for the assistant.
 */
function composeFullMessage(
  idea: string,
  commands: string,
  answers: Record<string, string>,
): string {
  const sections: string[] = [];

  const trimmedIdea = idea.trim();
  const trimmedCommands = commands.trim();

  if (trimmedIdea) {
    sections.push(`**Idea**\n${trimmedIdea}`);
  }

  const bulletLines: string[] = [];
  for (const q of QUESTIONS) {
    const a = answers[q.id];
    if (a === undefined) continue;
    const rendered = q.render(a);
    if (rendered) bulletLines.push(`- ${rendered}`);
  }
  if (bulletLines.length > 0) {
    sections.push(`**Basics**\n${bulletLines.join('\n')}`);
  }

  if (trimmedCommands) {
    sections.push(`**Commands I have in mind**\n${trimmedCommands}`);
  }

  // Permissions are non-negotiable — every plugin we generate should
  // ship with proper permission nodes, so we tell the assistant that up
  // front rather than asking the user about it.
  sections.push(
    '**Permissions**\nPlease design proper permission nodes for every command and sensitive feature, and document them in plugin.yml.',
  );

  if (sections.length === 1) {
    // Only the permissions rule — user gave us nothing else. Fall back
    // to an open-ended kickoff so the assistant drives the conversation.
    return "Let's get started. I'd like help figuring out what plugin to build. Please design proper permission nodes for every command and sensitive feature.";
  }

  sections.push("Let's figure out the rest together.");
  return sections.join('\n\n');
}

export default function StarterQuestionnaire({
  onSubmit,
  onSkip,
  disabled = false,
}: StarterQuestionnaireProps) {
  const [answers, setAnswers] = useState<Record<string, string>>({});
  const [idea, setIdea] = useState('');
  const [commands, setCommands] = useState('');

  const setAnswer = (questionId: string, value: string) => {
    setAnswers((prev) => ({ ...prev, [questionId]: value }));
  };

  const handleSubmit = () => {
    if (disabled) return;
    onSubmit(composeFullMessage(idea, commands, answers));
  };

  const answeredCount = Object.keys(answers).length;
  const hasAnyInput =
    answeredCount > 0 || idea.trim().length > 0 || commands.trim().length > 0;

  return (
    <div className="w-full max-w-2xl mx-auto">
      <div className="bg-slate-800/50 border border-slate-700 rounded-2xl p-6 shadow-lg">
        <div className="mb-5">
          <h2 className="text-lg font-semibold text-white mb-1">
            Let's get the basics out of the way
          </h2>
          <p className="text-sm text-slate-400">
            A few quick questions so we can skip the back-and-forth. Every
            question has a{' '}
            <span className="text-slate-300 font-medium">
              Doesn't matter
            </span>{' '}
            option — pick whatever you actually care about and leave the rest.
          </p>
        </div>

        <div className="space-y-5">
          {/* Free-text: the core idea. Optional but encouraged. */}
          <div>
            <label
              htmlFor="starter-idea"
              className="text-sm font-medium text-slate-200 mb-1 block"
            >
              Basic idea
            </label>
            <p className="text-xs text-slate-500 mb-2">
              One or two sentences on what you want the plugin to do. Don't
              worry about polish — it's just a starting point.
            </p>
            <textarea
              id="starter-idea"
              value={idea}
              onChange={(e) => {
                const v = e.target.value;
                if (v.length <= IDEA_MAX) setIdea(v);
              }}
              disabled={disabled}
              rows={3}
              placeholder="e.g. A shop plugin where players can buy and sell items using in-game currency, with a GUI for browsing."
              className="w-full resize-none rounded-lg bg-slate-900/80 border border-slate-700 px-3 py-2 text-sm text-white placeholder-slate-600 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            />
            <div className="mt-1 text-right text-[10px] text-slate-600">
              {idea.length > 0 && `${idea.length} / ${IDEA_MAX}`}
            </div>
          </div>

          {QUESTIONS.map((q) => {
            const selected = answers[q.id];
            return (
              <fieldset key={q.id}>
                <legend className="text-sm font-medium text-slate-200 mb-1">
                  {q.label}
                </legend>
                {q.helper && (
                  <p className="text-xs text-slate-500 mb-2">{q.helper}</p>
                )}
                <div className="flex flex-wrap gap-2">
                  {q.options.map((opt) => {
                    const isSelected = selected === opt.value;
                    return (
                      <button
                        key={opt.value}
                        type="button"
                        onClick={() => setAnswer(q.id, opt.value)}
                        disabled={disabled}
                        className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors border ${
                          isSelected
                            ? 'bg-blue-600 border-blue-500 text-white'
                            : 'bg-slate-900/60 border-slate-700 text-slate-300 hover:border-slate-600 hover:text-white'
                        } disabled:opacity-50 disabled:cursor-not-allowed`}
                      >
                        {opt.label}
                      </button>
                    );
                  })}
                  {!q.required && (
                    <button
                      type="button"
                      onClick={() => setAnswer(q.id, DONT_CARE)}
                      disabled={disabled}
                      className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors border ${
                        selected === DONT_CARE
                          ? 'bg-slate-600 border-slate-500 text-white'
                          : 'bg-transparent border-dashed border-slate-700 text-slate-400 hover:border-slate-500 hover:text-slate-200'
                      } disabled:opacity-50 disabled:cursor-not-allowed`}
                      title="Skip this question"
                    >
                      Doesn't matter
                    </button>
                  )}
                </div>
              </fieldset>
            );
          })}

          {/* Free-text: commands the user already has in mind. Optional. */}
          <div>
            <label
              htmlFor="starter-commands"
              className="text-sm font-medium text-slate-200 mb-1 block"
            >
              Commands (if any)
            </label>
            <p className="text-xs text-slate-500 mb-2">
              Any commands you already know you want? E.g. <code className="text-slate-400">/shop open</code>,{' '}
              <code className="text-slate-400">/balance</code>. Leave blank
              if you'd rather figure them out with the assistant.
            </p>
            <textarea
              id="starter-commands"
              value={commands}
              onChange={(e) => {
                const v = e.target.value;
                if (v.length <= COMMANDS_MAX) setCommands(v);
              }}
              disabled={disabled}
              rows={2}
              placeholder="/shop open, /shop sell, /balance"
              className="w-full resize-none rounded-lg bg-slate-900/80 border border-slate-700 px-3 py-2 text-sm text-white placeholder-slate-600 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            />
            <div className="mt-1 text-right text-[10px] text-slate-600">
              {commands.length > 0 && `${commands.length} / ${COMMANDS_MAX}`}
            </div>
          </div>

          {/* Permissions are always included — not a question. */}
          <div className="flex items-start gap-2 rounded-lg bg-slate-900/40 border border-slate-700/60 px-3 py-2">
            <svg
              className="w-4 h-4 text-emerald-400 mt-0.5 shrink-0"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"
              />
            </svg>
            <p className="text-xs text-slate-400">
              <span className="text-slate-200 font-medium">
                Permissions are always included.
              </span>{' '}
              Every plugin we generate ships with proper permission nodes
              for each command and sensitive feature, documented in{' '}
              <code className="text-slate-300">plugin.yml</code>.
            </p>
          </div>
        </div>

        <div className="mt-6 flex items-center justify-between gap-3 pt-4 border-t border-slate-700/60">
          <button
            type="button"
            onClick={onSkip}
            disabled={disabled}
            className="text-xs text-slate-400 hover:text-slate-200 transition-colors disabled:opacity-50"
          >
            Skip — I'll just type it myself
          </button>
          <button
            type="button"
            onClick={handleSubmit}
            disabled={disabled}
            className="px-4 py-2 rounded-xl bg-blue-600 hover:bg-blue-700 disabled:opacity-40 disabled:cursor-not-allowed text-white text-sm font-medium transition-colors"
          >
            {hasAnyInput ? 'Start building' : 'Start chatting'}
          </button>
        </div>
      </div>
    </div>
  );
}
