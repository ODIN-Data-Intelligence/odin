// Simple class-name joiner (no Tailwind merge needed post-MUI migration)
export function cn(...inputs: (string | undefined | null | false)[]): string {
  return inputs.filter(Boolean).join(' ');
}
