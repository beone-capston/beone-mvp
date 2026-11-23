export function Icon({
  name,
}: {
  name: 'plus' | 'more' | 'card' | 'benefit' | 'simulation' | 'chevron' | 'check'
}) {
  const paths = {
    plus: 'M12 4v16M4 12h16',
    more: 'M12 5h.01M12 12h.01M12 19h.01',
    card: 'M3 9h18M4 4h16a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1Z',
    benefit: 'M12 3v18M16 7h-6a3 3 0 0 0 0 6h4a3 3 0 0 1 0 6H7',
    simulation:
      'm9 8 7 4-7 4V8ZM5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Z',
    chevron: 'm6 9 6 6 6-6',
    check: 'm5 12 4 4L19 6',
  }
  return (
    <svg
      width="24"
      height="24"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={name === 'more' ? 3.5 : 1.5}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d={paths[name]} />
    </svg>
  )
}
