import { useTheme } from '@mui/material/styles';
import useMediaQuery from '@mui/material/useMediaQuery';

/**
 * Responsive breakpoint helpers shared by the consumer and producer apps.
 *
 * Breakpoint map (MUI defaults):
 *   phone   < sm (600)
 *   tablet  sm – md (600–900)
 *   desktop ≥ md (900)
 *
 * `useIsMobile()` is the primary switch for layout changes (stacked vs.
 * side-by-side, temporary vs. permanent navigation). It returns true for
 * phones AND tablets, i.e. anything narrower than a desktop.
 */
export function useIsMobile(): boolean {
  const theme = useTheme();
  // `noSsr` avoids a desktop-first flash on first paint in our SPA context.
  return useMediaQuery(theme.breakpoints.down('md'), { noSsr: true });
}

/** True only on phone-width viewports (< sm / 600px). */
export function useIsPhone(): boolean {
  const theme = useTheme();
  return useMediaQuery(theme.breakpoints.down('sm'), { noSsr: true });
}

/** True only on tablet-width viewports (sm–md / 600–900px). */
export function useIsTablet(): boolean {
  const theme = useTheme();
  return useMediaQuery(theme.breakpoints.between('sm', 'md'), { noSsr: true });
}
