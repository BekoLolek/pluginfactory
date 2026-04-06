interface StarRatingProps {
  rating: number;
  max?: number;
  onChange?: (rating: number) => void;
  size?: 'sm' | 'md';
}

export default function StarRating({
  rating,
  max = 5,
  onChange,
  size = 'md',
}: StarRatingProps) {
  const sizeClass = size === 'sm' ? 'text-sm' : 'text-lg';
  const interactive = !!onChange;

  return (
    <span className={`inline-flex gap-0.5 ${sizeClass}`}>
      {Array.from({ length: max }, (_, i) => {
        const starIndex = i + 1;
        const filled = starIndex <= Math.round(rating);

        return (
          <span
            key={i}
            onClick={interactive ? () => onChange(starIndex) : undefined}
            onKeyDown={
              interactive
                ? (e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault();
                      onChange(starIndex);
                    }
                  }
                : undefined
            }
            role={interactive ? 'button' : undefined}
            tabIndex={interactive ? 0 : undefined}
            aria-label={interactive ? `Rate ${starIndex} star${starIndex > 1 ? 's' : ''}` : undefined}
            className={`${
              filled ? 'text-yellow-400' : 'text-slate-600'
            } ${interactive ? 'cursor-pointer hover:text-yellow-300 transition-colors' : ''} select-none`}
          >
            {filled ? '\u2605' : '\u2606'}
          </span>
        );
      })}
    </span>
  );
}
