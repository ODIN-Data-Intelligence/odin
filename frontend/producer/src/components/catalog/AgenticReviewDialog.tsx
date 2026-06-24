import { useState, useEffect, useRef, useMemo } from 'react';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import Alert from '@mui/material/Alert';
import CircularProgress from '@mui/material/CircularProgress';
import Paper from '@mui/material/Paper';
import Accordion from '@mui/material/Accordion';
import AccordionSummary from '@mui/material/AccordionSummary';
import AccordionDetails from '@mui/material/AccordionDetails';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import LockIcon from '@mui/icons-material/Lock';
import { logicalModelApi, ClassificationBadge } from '@datacatalog/shared';
import type { AgenticEvent, AgenticElementProposal, AgenticPhase } from '@datacatalog/shared';

interface Props {
  open: boolean;
  datasetId: string;
  modelId: string | null;
  onClose: () => void;
  onApplied: () => void;
}

interface IterationView {
  iteration: number;
  proposal?: { elements: AgenticElementProposal[] };
  proposed: boolean;
  reviewing: boolean;
  verdict?: 'APPROVE' | 'REJECT';
  comments?: AgenticEvent['comments'];
  summary?: string;
}

const PHASE_LABEL: Record<AgenticPhase, string> = {
  CONTEXT: 'Gathering the full DCAT dataset context…',
  MEMORY: 'Recalling lessons from past reviews…',
  PROPOSING: 'Proposer is drafting combined recommendations…',
  PROPOSAL: 'Proposal ready — handing off to the reviewer…',
  REVIEWING: 'Reviewer is auditing the proposal against the whole dataset…',
  REVIEW: 'Reviewer responded.',
  LOCKED: 'Dimension settled and locked.',
  DONE: 'Approved.',
  MAX_REACHED: 'Reached the iteration limit.',
  ERROR: 'Failed.',
};

/** Folds the flat event stream into per-iteration proposer/reviewer views for rendering. */
function buildIterations(events: AgenticEvent[]): IterationView[] {
  const byIter = new Map<number, IterationView>();
  for (const e of events) {
    if (!e.iteration || e.iteration < 1) continue;
    const v = byIter.get(e.iteration) ?? { iteration: e.iteration, proposed: false, reviewing: false };
    if (e.phase === 'PROPOSING') v.proposed = false;
    if (e.phase === 'PROPOSAL') { v.proposal = e.proposal; v.proposed = true; }
    if (e.phase === 'REVIEWING') v.reviewing = true;
    if (e.phase === 'REVIEW') { v.verdict = e.verdict; v.comments = e.comments; v.summary = e.summary; v.reviewing = false; }
    byIter.set(e.iteration, v);
  }
  return [...byIter.values()].sort((a, b) => a.iteration - b.iteration);
}

function ProposalDetails({ proposal }: { proposal: { elements: AgenticElementProposal[] } }) {
  return (
    <Accordion disableGutters elevation={0} sx={{ '&:before': { display: 'none' }, bgcolor: 'transparent' }}>
      <AccordionSummary expandIcon={<ExpandMoreIcon />} sx={{ minHeight: 0, px: 0, '& .MuiAccordionSummary-content': { my: 0.5 } }}>
        <Typography variant="caption" color="text.secondary">
          Proposed recommendations for {proposal.elements.length} element{proposal.elements.length === 1 ? '' : 's'}
        </Typography>
      </AccordionSummary>
      <AccordionDetails sx={{ px: 0, pt: 0 }}>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
          {proposal.elements.map(el => (
            <Box key={el.elementId} sx={{ borderLeft: '2px solid', borderColor: 'divider', pl: 1 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, flexWrap: 'wrap' }}>
                <Typography variant="body2" fontWeight={600}>{el.name ?? el.elementId}</Typography>
                {el.classification && <ClassificationBadge level={el.classification} />}
                {el.isPersonalInformation && <Chip label="PII" size="small" color="error" sx={{ height: 16, fontSize: 10 }} />}
                {el.isDirectIdentifier && <Chip label="ID" size="small" color="warning" sx={{ height: 16, fontSize: 10 }} />}
                {(el.vocabConcepts?.length ?? 0) > 0 && (
                  <Chip label={`${el.vocabConcepts!.length} concept${el.vocabConcepts!.length === 1 ? '' : 's'}`}
                    size="small" color="secondary" variant="outlined" sx={{ height: 16, fontSize: 10 }} />
                )}
              </Box>
              {el.description && (
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>{el.description}</Typography>
              )}
            </Box>
          ))}
        </Box>
      </AccordionDetails>
    </Accordion>
  );
}

export default function AgenticReviewDialog({ open, datasetId, modelId, onClose, onApplied }: Props) {
  const [events, setEvents] = useState<AgenticEvent[]>([]);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);
  const scrollRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!open || !modelId) return;
    const controller = new AbortController();
    abortRef.current = controller;
    setEvents([]);
    setError(null);
    setRunning(true);

    logicalModelApi.agenticReview(
      datasetId,
      modelId,
      {
        onEvent: (event) => {
          setEvents(prev => [...prev, event]);
          if (event.phase === 'ERROR') setError(event.message ?? 'The agent reported an error.');
        },
        onDone: () => setRunning(false),
        onError: (e) => {
          if (controller.signal.aborted) return;
          setError(e instanceof Error ? e.message : 'Connection to the review service failed.');
          setRunning(false);
        },
      },
      controller.signal,
    );

    return () => controller.abort();
  }, [open, modelId, datasetId]);

  // Keep the transcript scrolled to the latest event.
  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: 'smooth' });
  }, [events]);

  const iterations = useMemo(() => buildIterations(events), [events]);
  const memoryEvent = events.find(e => e.phase === 'MEMORY');
  const lockedEvents = events.filter(e => e.phase === 'LOCKED');
  const lastPhase = events.length > 0 ? events[events.length - 1].phase : 'CONTEXT';
  const terminal = events.find(e => e.phase === 'DONE' || e.phase === 'MAX_REACHED');
  const finalProposalCount = terminal?.proposal?.elements.length ?? 0;

  const handleClose = () => {
    abortRef.current?.abort();
    onClose();
  };

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="md" fullWidth>
      <DialogTitle sx={{ pb: 0.5 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <AutoAwesomeIcon fontSize="small" color="primary" />
          Agentic AI Review
        </Box>
        <Typography variant="caption" color="text.secondary">
          A proposer drafts all recommendations; a reviewer audits them against the full dataset and sends rejections
          back for revision (up to 10 rounds).
        </Typography>
      </DialogTitle>

      <DialogContent dividers ref={scrollRef} sx={{ maxHeight: '60vh' }}>
        {/* Live status */}
        {running && (
          <Alert severity="info" icon={<CircularProgress size={16} color="inherit" />} sx={{ mb: 2 }}>
            {PHASE_LABEL[lastPhase]}
          </Alert>
        )}
        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
        {memoryEvent?.message && (
          <Alert severity="info" variant="outlined" icon={<AutoAwesomeIcon fontSize="small" />} sx={{ mb: 2 }}>
            {memoryEvent.message} — the proposer used these to start closer to an approvable answer.
          </Alert>
        )}
        {lockedEvents.length > 0 && (
          <Alert severity="info" variant="outlined" icon={<LockIcon fontSize="small" />} sx={{ mb: 2 }}>
            Dimensions are resolved in order (vocab → PII → classification → description) and locked once
            settled, so the review converges instead of looping:
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5, mt: 0.5 }}>
              {lockedEvents.map((e, i) => (
                <Chip key={i} label={e.message?.replace(/^Settled & locked:\s*/, '')} size="small"
                  color="success" variant="outlined" sx={{ height: 18, fontSize: 11 }} />
              ))}
            </Box>
          </Alert>
        )}
        {!running && terminal?.phase === 'DONE' && (
          <Alert severity="success" sx={{ mb: 2 }}>
            Reviewer approved after {terminal.iteration} iteration{terminal.iteration === 1 ? '' : 's'}.
            {finalProposalCount > 0 && ` ${finalProposalCount} element recommendation${finalProposalCount === 1 ? '' : 's'} are ready below the table for you to accept or reject.`}
          </Alert>
        )}
        {!running && terminal?.phase === 'MAX_REACHED' && (
          <Alert severity="warning" sx={{ mb: 2 }}>
            Reached the 10-iteration limit without full approval. The best-so-far recommendations
            ({finalProposalCount}) have been saved for your review.
          </Alert>
        )}

        {/* Iteration transcript */}
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
          {iterations.map(it => (
            <Paper key={it.iteration} variant="outlined" sx={{ p: 1.5 }}>
              <Typography variant="overline" color="text.secondary">Iteration {it.iteration}</Typography>

              {/* Proposer */}
              <Box sx={{ mt: 0.5 }}>
                <Typography variant="caption" fontWeight={700} color="primary.main">PROPOSER</Typography>
                {it.proposal ? (
                  <ProposalDetails proposal={it.proposal} />
                ) : (
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, py: 0.5 }}>
                    <CircularProgress size={12} /><Typography variant="caption" color="text.secondary">Drafting…</Typography>
                  </Box>
                )}
              </Box>

              {/* Reviewer */}
              <Box sx={{ mt: 1 }}>
                <Typography variant="caption" fontWeight={700} color="secondary.main">REVIEWER</Typography>
                {it.verdict ? (
                  <Box sx={{ mt: 0.5 }}>
                    <Chip
                      label={it.verdict === 'APPROVE' ? 'Approved' : 'Rejected — sent back to proposer'}
                      size="small"
                      color={it.verdict === 'APPROVE' ? 'success' : 'warning'}
                      sx={{ height: 20, fontSize: 11 }}
                    />
                    {it.summary && (
                      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5, fontStyle: 'italic' }}>
                        {it.summary}
                      </Typography>
                    )}
                    {(it.comments?.length ?? 0) > 0 && (
                      <Box component="ul" sx={{ m: 0, mt: 0.5, pl: 2 }}>
                        {it.comments!.map((c, i) => (
                          <Typography component="li" key={i} variant="caption" color="text.secondary">
                            {c.dimension ? <strong>[{c.dimension}] </strong> : null}{c.issue}
                          </Typography>
                        ))}
                      </Box>
                    )}
                  </Box>
                ) : it.reviewing ? (
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, py: 0.5 }}>
                    <CircularProgress size={12} /><Typography variant="caption" color="text.secondary">Evaluating…</Typography>
                  </Box>
                ) : (
                  <Typography variant="caption" color="text.disabled" sx={{ display: 'block' }}>Waiting…</Typography>
                )}
              </Box>
            </Paper>
          ))}

          {iterations.length === 0 && running && (
            <Typography variant="caption" color="text.secondary">Starting the agent loop…</Typography>
          )}
        </Box>
      </DialogContent>

      <DialogActions>
        <Button onClick={handleClose} sx={{ textTransform: 'none' }}>
          {running ? 'Cancel' : 'Close'}
        </Button>
        {!running && terminal && (
          <Button
            variant="contained"
            onClick={() => { onApplied(); onClose(); }}
            sx={{ textTransform: 'none' }}
          >
            View recommendations
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
}
