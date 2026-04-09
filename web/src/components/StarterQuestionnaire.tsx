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
    options: [
      { value: '1.21', label: '1.21.x' },
      { value: '1.20', label: '1.20.x' },
      { value: '1.19', label: '1.19.x' },
      { value: 'older', label: 'Something older' },
    ],
    render: (a) =>
      a === DONT_CARE
        ? 'Minecraft version: no strong preference.'
        : a === 'older'
          ? 'Minecraft version: something older than 1.19.'
          : `Minecraft version: ${a}.x.`,
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

function composeMessage(answers: Record<string, string>): string {
  const lines: string[] = [];
  for (const q of QUESTIONS) {
    const a = answers[q.id];
    if (a === undefined) continue;
    const rendered = q.render(a);
    if (rendered) lines.push(`- ${rendered}`);
  }
  if (lines.length === 0) {
    // No hard answers — user just wants to dive in.
    return "Let's get started. I'd like help figuring out what plugin to build.";
  }
  return [
    "Here's what I know so far:",
    ...lines,
    '',
    "Let's figure out the rest together.",
  ].join('\n');
}

export default function StarterQuestionnaire({
  onSubmit,
  onSkip,
  disabled = false,
}: StarterQuestionnaireProps) {
  const [answers, setAnswers] = useState<Record<string, string>>({});

  const setAnswer = (questionId: string, value: string) => {
    setAnswers((prev) => ({ ...prev, [questionId]: value }));
  };

  const handleSubmit = () => {
    if (disabled) return;
    onSubmit(composeMessage(answers));
  };

  const answeredCount = Object.keys(answers).length;

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
                </div>
              </fieldset>
            );
          })}
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
            {answeredCount === 0 ? 'Start chatting' : 'Start building'}
          </button>
        </div>
      </div>
    </div>
  );
}
