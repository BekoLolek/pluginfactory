import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuthStore } from '@/stores/authStore';
import { getDiscordUrl } from '@/api/auth';

const DISCORD_SUPPORT_URL = 'https://discord.com/invite/PRNEMA9xgR';

interface FaqItem {
  q: string;
  a: string;
}

const FAQS: FaqItem[] = [
  {
    q: 'What is Plugin Factory?',
    a: 'Plugin Factory is an AI-powered platform that turns natural-language descriptions into working Minecraft Spigot/Paper plugins. Describe the commands, events, and behavior you want, and the AI plans, writes, compiles, and tests the plugin for you inside a secure sandbox.',
  },
  {
    q: 'Which Minecraft server versions are supported?',
    a: 'Generated plugins target modern Paper/Spigot APIs. You can specify the target Minecraft version when starting a build, and the factory will pin the correct API dependencies for you.',
  },
  {
    q: 'Do I own the plugins I generate?',
    a: 'Yes. Any plugin you create is yours. You can download the JAR, publish it to the built-in marketplace, or ship it on your own server — no royalties, no attribution required.',
  },
  {
    q: 'How does the AI write the code?',
    a: 'The factory uses a planning step that breaks your request into commands, listeners, and data models, then asks you to approve the plan before any code is written. Once approved, it generates source, compiles it, runs tests, and streams progress to your dashboard.',
  },
  {
    q: 'What happens if a build fails?',
    a: 'Builds run in a sandboxed Docker container with full logs. If compilation or tests fail, the AI reads the error output and iterates automatically — and you can jump in at any point to steer it.',
  },
  {
    q: 'Is there a free tier?',
    a: 'Yes. The Free tier lets you try the full flow with a monthly token budget, perfect for small plugins or evaluating the platform before upgrading.',
  },
];

interface PricingTier {
  name: string;
  price: string;
  period: string;
  tagline: string;
  features: string[];
  cta: string;
  highlighted?: boolean;
}

const TIERS: PricingTier[] = [
  {
    name: 'Free',
    price: '$0',
    period: '/mo',
    tagline: 'Kick the tires.',
    features: [
      'Monthly token budget',
      '1 concurrent build',
      'Public marketplace access',
      'Community support',
    ],
    cta: 'Start free',
  },
  {
    name: 'Basic',
    price: '$9.99',
    period: '/mo',
    tagline: 'For solo plugin authors.',
    features: [
      'Expanded token budget',
      '3 concurrent builds',
      'Private plugins',
      'Email support',
    ],
    cta: 'Go Basic',
  },
  {
    name: 'Pro',
    price: '$29.99',
    period: '/mo',
    tagline: 'Ship serious plugins.',
    features: [
      'Large token budget',
      '8 concurrent builds',
      'Publish to marketplace',
      'Priority build queue',
      'Priority support',
    ],
    cta: 'Go Pro',
    highlighted: true,
  },
  {
    name: 'Team',
    price: '$79.99',
    period: '/mo',
    tagline: 'Collaborate with your team.',
    features: [
      'Shared team workspace',
      'Unlimited concurrent builds',
      'Role-based access control',
      'Shared token pool',
      'Dedicated support',
    ],
    cta: 'Go Team',
  },
];

interface DocSection {
  title: string;
  body: string;
  steps?: string[];
}

const DOC_SECTIONS: DocSection[] = [
  {
    title: 'Quickstart',
    body: 'Sign in with Discord, describe your plugin, approve the plan, and download your JAR.',
    steps: [
      'Click "Continue with Discord" to create your account.',
      'From the dashboard, click "New build" and describe the plugin in plain English.',
      'Review the generated plan — commands, listeners, permissions — and approve or edit.',
      'Watch the sandbox compile and test. Download the JAR when it\'s green.',
    ],
  },
  {
    title: 'Commands & Events',
    body: 'Describe commands and events in natural language. The planner infers argument parsing, tab-completion, permissions, and event priorities so your plugin integrates cleanly with Paper/Spigot.',
  },
  {
    title: 'Marketplace',
    body: 'Publish finished plugins to the built-in marketplace, browse what the community has built, and install plugins into your own builds as a starting point.',
  },
  {
    title: 'Teams',
    body: 'Invite collaborators into a shared workspace with role-based access, a shared token pool, and real-time build status for every member.',
  },
];

export default function LandingPage() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)();
  const [signingIn, setSigningIn] = useState(false);
  const [signInError, setSignInError] = useState<string | null>(null);
  const [openFaq, setOpenFaq] = useState<number | null>(0);

  const handleSignIn = async () => {
    setSigningIn(true);
    setSignInError(null);
    try {
      const url = await getDiscordUrl();
      if (
        !url.startsWith('https://discord.com/') &&
        !url.startsWith('https://discordapp.com/')
      ) {
        setSignInError('Invalid redirect URL received. Please try again.');
        setSigningIn(false);
        return;
      }
      window.location.href = url;
    } catch {
      setSignInError('Failed to connect to Discord. Please try again.');
      setSigningIn(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 selection:bg-blue-500/40 selection:text-white">
      {/* ========================= NAV ========================= */}
      <header className="sticky top-0 z-40 backdrop-blur-xl bg-slate-950/70 border-b border-slate-800/80">
        <nav className="max-w-6xl mx-auto px-6 h-16 flex items-center justify-between">
          <a href="#top" className="flex items-center gap-2.5 group">
            <img
              src="/favicon.svg"
              alt=""
              className="w-8 h-8 transition-transform group-hover:rotate-6"
            />
            <span className="text-lg font-semibold tracking-tight">
              Plugin<span className="text-blue-400">Factory</span>
            </span>
          </a>

          <div className="hidden md:flex items-center gap-8 text-sm text-slate-400">
            <a href="#features" className="hover:text-slate-100 transition-colors">
              Features
            </a>
            <a href="#docs" className="hover:text-slate-100 transition-colors">
              Docs
            </a>
            <a href="#pricing" className="hover:text-slate-100 transition-colors">
              Pricing
            </a>
            <a href="#faq" className="hover:text-slate-100 transition-colors">
              FAQ
            </a>
            <a
              href={DISCORD_SUPPORT_URL}
              target="_blank"
              rel="noreferrer noopener"
              className="flex items-center gap-1.5 hover:text-slate-100 transition-colors"
            >
              <DiscordGlyph />
              Support
            </a>
            <Link to="/status" className="hover:text-slate-100 transition-colors">
              Status
            </Link>
          </div>

          {isAuthenticated ? (
            <Link
              to="/dashboard"
              className="px-4 py-2 rounded-lg bg-blue-500 hover:bg-blue-400 text-white text-sm font-medium transition-colors shadow-lg shadow-blue-500/20"
            >
              Dashboard
            </Link>
          ) : (
            <a
              href="#signin"
              className="px-4 py-2 rounded-lg bg-blue-500 hover:bg-blue-400 text-white text-sm font-medium transition-colors shadow-lg shadow-blue-500/20"
            >
              Sign in
            </a>
          )}
        </nav>
      </header>

      {/* ========================= HERO ========================= */}
      <section id="top" className="relative overflow-hidden">
        {/* Glow backdrop */}
        <div
          aria-hidden
          className="pointer-events-none absolute inset-0"
          style={{
            background:
              'radial-gradient(ellipse at 50% 0%, rgba(224,123,44,0.22), transparent 55%), radial-gradient(ellipse at 80% 40%, rgba(232,132,47,0.12), transparent 60%)',
          }}
        />

        <div className="relative max-w-6xl mx-auto px-6 pt-24 pb-28 text-center">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full border border-slate-700/80 bg-slate-900/60 text-xs text-slate-300 mb-6">
            <span className="w-1.5 h-1.5 rounded-full bg-blue-400 animate-pulse" />
            AI-powered Minecraft plugin development
          </div>

          <h1 className="text-5xl md:text-6xl lg:text-7xl font-bold tracking-tight leading-[1.05]">
            Describe it.
            <br />
            <span className="bg-gradient-to-br from-blue-300 via-blue-400 to-indigo-500 bg-clip-text text-transparent">
              Ship the plugin.
            </span>
          </h1>

          <p className="mt-7 text-lg md:text-xl text-slate-400 max-w-2xl mx-auto leading-relaxed">
            Turn plain-English plugin specs into working Paper/Spigot JARs.
            Commands, listeners, and tests — planned, written, compiled, and
            shipped by AI inside a secure sandbox.
          </p>

          <div className="mt-10 flex items-center justify-center gap-3">
            <a
              href="#signin"
              className="px-6 py-3 rounded-xl bg-blue-500 hover:bg-blue-400 text-white font-medium transition-colors shadow-lg shadow-blue-500/25"
            >
              Get started free
            </a>
            <a
              href="#docs"
              className="px-6 py-3 rounded-xl bg-slate-900/80 hover:bg-slate-800 text-slate-200 font-medium border border-slate-800 transition-colors"
            >
              Read the docs
            </a>
          </div>

          <p className="mt-4 text-xs text-slate-500">
            No credit card required · Sign in with Discord
          </p>
        </div>
      </section>

      {/* ========================= FEATURES ========================= */}
      <section id="features" className="max-w-6xl mx-auto px-6 py-20">
        <div className="text-center mb-14">
          <p className="text-sm font-medium text-blue-400 uppercase tracking-wider">
            Features
          </p>
          <h2 className="mt-2 text-3xl md:text-4xl font-bold tracking-tight">
            Everything you need to build plugins
          </h2>
        </div>

        <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-5">
          <FeatureCard
            icon={<SparkleIcon />}
            title="Plan-first generation"
            description="The AI breaks your request into commands, listeners, and data models — you approve the plan before a line of code is written."
          />
          <FeatureCard
            icon={<CommandIcon />}
            title="Commands & tab-complete"
            description="Describe commands in plain English. The factory wires up arguments, permissions, tab-completion, and help text automatically."
          />
          <FeatureCard
            icon={<BoltIcon />}
            title="Event listeners"
            description="Player joins, block breaks, damage events — describe the behavior and the correct listeners are registered with the right priority."
          />
          <FeatureCard
            icon={<ShieldIcon />}
            title="Sandboxed builds"
            description="Every build runs inside an isolated Docker container. Real compilation, real tests, zero risk to your machine."
          />
          <FeatureCard
            icon={<PackageIcon />}
            title="Built-in marketplace"
            description="Publish polished plugins, browse what the community ships, and remix existing projects into new builds."
          />
          <FeatureCard
            icon={<UsersIcon />}
            title="Teams & sharing"
            description="Shared workspaces, role-based access, and a pooled token budget for studios building plugins together."
          />
        </div>
      </section>

      {/* ========================= DOCS ========================= */}
      <section id="docs" className="relative">
        <div className="max-w-6xl mx-auto px-6 py-20">
          <div className="text-center mb-14">
            <p className="text-sm font-medium text-blue-400 uppercase tracking-wider">
              Documentation
            </p>
            <h2 className="mt-2 text-3xl md:text-4xl font-bold tracking-tight">
              From sign-in to shipped JAR
            </h2>
            <p className="mt-3 text-slate-400 max-w-2xl mx-auto">
              A quick tour of the build flow. Full docs live inside the
              dashboard.
            </p>
          </div>

          <div className="grid md:grid-cols-2 gap-5">
            {DOC_SECTIONS.map((s, i) => (
              <div
                key={s.title}
                className="p-6 rounded-2xl bg-slate-900/60 border border-slate-800 hover:border-slate-700 transition-colors"
              >
                <div className="flex items-center gap-3 mb-3">
                  <div className="w-8 h-8 rounded-lg bg-blue-500/15 text-blue-300 flex items-center justify-center text-sm font-semibold">
                    {i + 1}
                  </div>
                  <h3 className="text-lg font-semibold text-slate-100">
                    {s.title}
                  </h3>
                </div>
                <p className="text-sm text-slate-400 leading-relaxed">
                  {s.body}
                </p>
                {s.steps && (
                  <ol className="mt-4 space-y-2 text-sm text-slate-400 list-decimal list-inside marker:text-blue-400">
                    {s.steps.map((step, idx) => (
                      <li key={idx}>{step}</li>
                    ))}
                  </ol>
                )}
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ========================= PRICING ========================= */}
      <section id="pricing" className="max-w-6xl mx-auto px-6 py-20">
        <div className="text-center mb-14">
          <p className="text-sm font-medium text-blue-400 uppercase tracking-wider">
            Pricing
          </p>
          <h2 className="mt-2 text-3xl md:text-4xl font-bold tracking-tight">
            Simple plans that scale with you
          </h2>
          <p className="mt-3 text-slate-400">
            Cancel or change plans anytime.
          </p>
        </div>

        <div className="grid md:grid-cols-2 lg:grid-cols-4 gap-5">
          {TIERS.map((t) => (
            <div
              key={t.name}
              className={`relative p-6 rounded-2xl border transition-colors ${
                t.highlighted
                  ? 'bg-gradient-to-b from-blue-500/10 to-slate-900/60 border-blue-500/40 shadow-xl shadow-blue-500/10'
                  : 'bg-slate-900/60 border-slate-800 hover:border-slate-700'
              }`}
            >
              {t.highlighted && (
                <div className="absolute -top-3 left-1/2 -translate-x-1/2 px-3 py-1 rounded-full text-[10px] font-semibold uppercase tracking-wider bg-blue-500 text-white">
                  Most popular
                </div>
              )}
              <h3 className="text-sm font-semibold uppercase tracking-wider text-slate-300">
                {t.name}
              </h3>
              <p className="mt-1 text-xs text-slate-500">{t.tagline}</p>
              <div className="mt-5 flex items-baseline gap-1">
                <span className="text-4xl font-bold text-slate-100">
                  {t.price}
                </span>
                <span className="text-sm text-slate-500">{t.period}</span>
              </div>
              <ul className="mt-6 space-y-2.5 text-sm text-slate-300">
                {t.features.map((f) => (
                  <li key={f} className="flex items-start gap-2">
                    <CheckIcon />
                    <span>{f}</span>
                  </li>
                ))}
              </ul>
              <a
                href="#signin"
                className={`mt-7 block text-center px-4 py-2.5 rounded-xl text-sm font-medium transition-colors ${
                  t.highlighted
                    ? 'bg-blue-500 hover:bg-blue-400 text-white'
                    : 'bg-slate-800 hover:bg-slate-700 text-slate-100'
                }`}
              >
                {t.cta}
              </a>
            </div>
          ))}
        </div>
      </section>

      {/* ========================= FAQ ========================= */}
      <section id="faq" className="max-w-3xl mx-auto px-6 py-20">
        <div className="text-center mb-12">
          <p className="text-sm font-medium text-blue-400 uppercase tracking-wider">
            FAQ
          </p>
          <h2 className="mt-2 text-3xl md:text-4xl font-bold tracking-tight">
            Questions, answered
          </h2>
        </div>

        <div className="space-y-3">
          {FAQS.map((item, i) => {
            const open = openFaq === i;
            return (
              <div
                key={item.q}
                className="rounded-xl border border-slate-800 bg-slate-900/60 overflow-hidden"
              >
                <button
                  type="button"
                  onClick={() => setOpenFaq(open ? null : i)}
                  className="w-full flex items-center justify-between gap-4 px-5 py-4 text-left"
                  aria-expanded={open}
                >
                  <span className="font-medium text-slate-100">{item.q}</span>
                  <span
                    className={`w-6 h-6 flex items-center justify-center rounded-full bg-slate-800 text-slate-400 transition-transform ${
                      open ? 'rotate-45' : ''
                    }`}
                    aria-hidden
                  >
                    +
                  </span>
                </button>
                {open && (
                  <div className="px-5 pb-5 -mt-1 text-sm text-slate-400 leading-relaxed">
                    {item.a}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </section>

      {/* ========================= SIGN IN / CTA ========================= */}
      <section id="signin" className="relative">
        <div
          aria-hidden
          className="pointer-events-none absolute inset-0"
          style={{
            background:
              'radial-gradient(ellipse at 50% 50%, rgba(224,123,44,0.18), transparent 60%)',
          }}
        />
        <div className="relative max-w-xl mx-auto px-6 py-24 text-center">
          <h2 className="text-3xl md:text-4xl font-bold tracking-tight">
            {isAuthenticated ? 'Pick up where you left off' : 'Ready to build?'}
          </h2>
          <p className="mt-3 text-slate-400">
            {isAuthenticated
              ? 'Jump back into your dashboard or drop into the community on Discord.'
              : 'Sign in with Discord and ship your first plugin in minutes.'}
          </p>

          <div className="mt-10 p-7 rounded-2xl bg-slate-900/80 border border-slate-800 shadow-2xl">
            {isAuthenticated ? (
              <div className="flex flex-col gap-3">
                <Link
                  to="/dashboard"
                  className="w-full flex items-center justify-center gap-3 px-6 py-3.5 rounded-xl bg-blue-500 hover:bg-blue-400 text-white font-medium transition-colors"
                >
                  Go to dashboard
                </Link>
                <a
                  href={DISCORD_SUPPORT_URL}
                  target="_blank"
                  rel="noreferrer noopener"
                  className="w-full flex items-center justify-center gap-3 px-6 py-3.5 rounded-xl bg-[#5865F2] hover:bg-[#4752C4] text-white font-medium transition-colors"
                >
                  <DiscordGlyph />
                  Join the Discord community
                </a>
              </div>
            ) : (
              <>
                {signInError && (
                  <div className="mb-5 p-3 rounded-lg bg-red-500/10 border border-red-500/20 text-red-400 text-sm">
                    {signInError}
                  </div>
                )}
                <button
                  onClick={handleSignIn}
                  disabled={signingIn}
                  className="w-full flex items-center justify-center gap-3 px-6 py-3.5 rounded-xl bg-[#5865F2] hover:bg-[#4752C4] disabled:opacity-50 disabled:cursor-not-allowed text-white font-medium transition-colors"
                >
                  <DiscordGlyph />
                  {signingIn ? 'Connecting...' : 'Continue with Discord'}
                </button>
                <p className="mt-4 text-xs text-slate-500">
                  By signing in, you agree to our Terms of Service and Privacy
                  Policy.
                </p>
                <div className="mt-4 pt-4 border-t border-slate-800 text-xs text-slate-500">
                  Need help first?{' '}
                  <a
                    href={DISCORD_SUPPORT_URL}
                    target="_blank"
                    rel="noreferrer noopener"
                    className="text-blue-400 hover:text-blue-300 transition-colors"
                  >
                    Ask in our Discord
                  </a>
                </div>
              </>
            )}
          </div>
        </div>
      </section>

      {/* ========================= FOOTER ========================= */}
      <footer className="border-t border-slate-800/80">
        <div className="max-w-6xl mx-auto px-6 py-10 flex flex-col md:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-2.5">
            <img src="/favicon.svg" alt="" className="w-6 h-6" />
            <span className="text-sm text-slate-400">
              © {new Date().getFullYear()} Plugin Factory
            </span>
          </div>
          <div className="flex items-center gap-6 text-sm text-slate-500">
            <a href="#features" className="hover:text-slate-300 transition-colors">
              Features
            </a>
            <a href="#docs" className="hover:text-slate-300 transition-colors">
              Docs
            </a>
            <a href="#pricing" className="hover:text-slate-300 transition-colors">
              Pricing
            </a>
            <Link to="/status" className="hover:text-slate-300 transition-colors">
              Status
            </Link>
            <a
              href={DISCORD_SUPPORT_URL}
              target="_blank"
              rel="noreferrer noopener"
              className="flex items-center gap-1.5 hover:text-slate-300 transition-colors"
            >
              <DiscordGlyph />
              Support
            </a>
            {isAuthenticated ? (
              <Link to="/dashboard" className="hover:text-slate-300 transition-colors">
                Dashboard
              </Link>
            ) : (
              <Link to="/login" className="hover:text-slate-300 transition-colors">
                Sign in
              </Link>
            )}
          </div>
        </div>
      </footer>
    </div>
  );
}

// ============================================================================
// Sub-components
// ============================================================================

function FeatureCard({
  icon,
  title,
  description,
}: {
  icon: React.ReactNode;
  title: string;
  description: string;
}) {
  return (
    <div className="p-6 rounded-2xl bg-slate-900/60 border border-slate-800 hover:border-slate-700 hover:bg-slate-900/80 transition-colors">
      <div className="w-11 h-11 rounded-xl bg-blue-500/15 text-blue-300 flex items-center justify-center mb-4">
        {icon}
      </div>
      <h3 className="text-lg font-semibold text-slate-100">{title}</h3>
      <p className="mt-2 text-sm text-slate-400 leading-relaxed">
        {description}
      </p>
    </div>
  );
}

function CheckIcon() {
  return (
    <svg
      className="w-4 h-4 mt-0.5 text-blue-400 flex-shrink-0"
      viewBox="0 0 20 20"
      fill="currentColor"
      aria-hidden
    >
      <path
        fillRule="evenodd"
        d="M16.704 5.29a1 1 0 010 1.42l-8 8a1 1 0 01-1.42 0l-4-4a1 1 0 111.42-1.42L8 12.584l7.29-7.294a1 1 0 011.414 0z"
        clipRule="evenodd"
      />
    </svg>
  );
}

function SparkleIcon() {
  return (
    <svg className="w-5 h-5" viewBox="0 0 24 24" fill="currentColor" aria-hidden>
      <path d="M12 2l1.5 6.5L20 10l-6.5 1.5L12 18l-1.5-6.5L4 10l6.5-1.5L12 2zm7 12l.8 2.7L22 17.5l-2.2.8L19 21l-.8-2.7L16 17.5l2.2-.8L19 14z" />
    </svg>
  );
}

function CommandIcon() {
  return (
    <svg
      className="w-5 h-5"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden
    >
      <polyline points="4 17 10 11 4 5" />
      <line x1="12" y1="19" x2="20" y2="19" />
    </svg>
  );
}

function BoltIcon() {
  return (
    <svg className="w-5 h-5" viewBox="0 0 24 24" fill="currentColor" aria-hidden>
      <path d="M13 2L3 14h7l-1 8 10-12h-7l1-8z" />
    </svg>
  );
}

function ShieldIcon() {
  return (
    <svg
      className="w-5 h-5"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden
    >
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
      <path d="M9 12l2 2 4-4" />
    </svg>
  );
}

function PackageIcon() {
  return (
    <svg
      className="w-5 h-5"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden
    >
      <path d="M21 16V8a2 2 0 00-1-1.73l-7-4a2 2 0 00-2 0l-7 4A2 2 0 003 8v8a2 2 0 001 1.73l7 4a2 2 0 002 0l7-4A2 2 0 0021 16z" />
      <polyline points="3.27 6.96 12 12.01 20.73 6.96" />
      <line x1="12" y1="22.08" x2="12" y2="12" />
    </svg>
  );
}

function DiscordGlyph() {
  return (
    <svg
      className="w-4 h-4"
      viewBox="0 0 24 24"
      fill="currentColor"
      aria-hidden
    >
      <path d="M20.317 4.37a19.791 19.791 0 00-4.885-1.515.074.074 0 00-.079.037c-.21.375-.444.864-.608 1.25a18.27 18.27 0 00-5.487 0 12.64 12.64 0 00-.617-1.25.077.077 0 00-.079-.037A19.736 19.736 0 003.677 4.37a.07.07 0 00-.032.027C.533 9.046-.32 13.58.099 18.057a.082.082 0 00.031.057 19.9 19.9 0 005.993 3.03.078.078 0 00.084-.028c.462-.63.874-1.295 1.226-1.994a.076.076 0 00-.041-.106 13.107 13.107 0 01-1.872-.892.077.077 0 01-.008-.128 10.2 10.2 0 00.372-.292.074.074 0 01.077-.01c3.928 1.793 8.18 1.793 12.062 0a.074.074 0 01.078.01c.12.098.246.198.373.292a.077.077 0 01-.006.127 12.299 12.299 0 01-1.873.892.077.077 0 00-.041.107c.36.698.772 1.362 1.225 1.993a.076.076 0 00.084.028 19.839 19.839 0 006.002-3.03.077.077 0 00.032-.054c.5-5.177-.838-9.674-3.549-13.66a.061.061 0 00-.031-.03zM8.02 15.33c-1.183 0-2.157-1.085-2.157-2.419 0-1.333.956-2.419 2.157-2.419 1.21 0 2.176 1.096 2.157 2.42 0 1.333-.956 2.418-2.157 2.418zm7.975 0c-1.183 0-2.157-1.085-2.157-2.419 0-1.333.955-2.419 2.157-2.419 1.21 0 2.176 1.096 2.157 2.42 0 1.333-.946 2.418-2.157 2.418z" />
    </svg>
  );
}

function UsersIcon() {
  return (
    <svg
      className="w-5 h-5"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden
    >
      <path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2" />
      <circle cx="9" cy="7" r="4" />
      <path d="M23 21v-2a4 4 0 00-3-3.87" />
      <path d="M16 3.13a4 4 0 010 7.75" />
    </svg>
  );
}
