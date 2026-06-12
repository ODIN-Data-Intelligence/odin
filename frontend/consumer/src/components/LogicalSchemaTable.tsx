import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Tooltip from '@mui/material/Tooltip';
import IconButton from '@mui/material/IconButton';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import { logicalModelApi, logicalElementApi, useIriTranslations, resolveLabel, VocabConceptBadge, ClassificationBadge } from '@datacatalog/shared';
import type { LogicalDataElement } from '@datacatalog/shared';

interface LogicalSchemaTableProps {
  datasetId: string;
}

export default function LogicalSchemaTable({ datasetId }: LogicalSchemaTableProps) {
  const { data: models = [] } = useQuery({
    queryKey: ['logical-models', datasetId],
    queryFn: () => logicalModelApi.list(datasetId),
  });

  const publishedModel = models.find(m => m.status === 'published') ?? models[0];

  const { data: elements = [] } = useQuery({
    queryKey: ['logical-elements', publishedModel?.id],
    queryFn: () => logicalElementApi.list(publishedModel!.id),
    enabled: !!publishedModel,
  });

  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set());

  const unmappedIris = elements
    .flatMap(el => el.vocabMappings ?? [])
    .filter(m => !m.conceptLabel && !m.conceptDefinition)
    .map(m => m.conceptIri);
  const translations = useIriTranslations(unmappedIris);

  function toggleExpand(id: string) {
    setExpandedIds(prev => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  }

  if (!publishedModel) {
    return (
      <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center', py: 6 }}>
        No logical model available.
      </Typography>
    );
  }

  const classifiedCount = elements.filter(el => el.classification).length;

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1.5 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <Typography variant="caption" color="text.secondary">
            {publishedModel.name} v{publishedModel.version}
          </Typography>
          <Chip label="published" color="success" size="small" sx={{ height: 18, fontSize: 11 }} />
        </Box>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
          {classifiedCount > 0 && (
            <Typography variant="caption" color="text.secondary">{classifiedCount}/{elements.length} classified</Typography>
          )}
          <Typography variant="caption" color="text.secondary">{elements.length} elements</Typography>
        </Box>
      </Box>

      <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
        <Box sx={{ overflowX: 'auto' }}>
          <Table size="small">
            <TableHead>
              <TableRow sx={{ bgcolor: 'grey.50' }}>
                <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>Business Name</TableCell>
                <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>Description</TableCell>
                <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>Logical Type</TableCell>
                <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>Classification</TableCell>
                <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>PII</TableCell>
                <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>Vocabulary Concept</TableCell>
                <TableCell sx={{ width: 40 }} />
              </TableRow>
            </TableHead>
            <TableBody>
              {elements.length === 0 && (
                <TableRow>
                  <TableCell colSpan={7} sx={{ textAlign: 'center', py: 4, color: 'text.disabled', fontSize: 12 }}>
                    No elements defined
                  </TableCell>
                </TableRow>
              )}
              {elements.map(el => (
                <ElementRows
                  key={el.id}
                  el={el}
                  expanded={expandedIds.has(el.id)}
                  onToggle={() => toggleExpand(el.id)}
                  translations={translations}
                />
              ))}
            </TableBody>
          </Table>
        </Box>
      </Paper>
    </Box>
  );
}

function ElementRows({ el, expanded, onToggle, translations }: {
  el: LogicalDataElement;
  expanded: boolean;
  onToggle: () => void;
  translations: Record<string, string>;
}) {
  const hasPhysical = !!(el.physicalColumn || el.physicalColumnRef);

  return (
    <>
      <TableRow hover>
        <TableCell>
          <Typography
            component={Link}
            to={`/search?q=${encodeURIComponent(el.name)}`}
            variant="body2"
            fontWeight={600}
            color="primary"
            title={`Search all datasets with element "${el.name}"`}
            sx={{ textDecoration: 'none', '&:hover': { textDecoration: 'underline' } }}
          >
            {el.name}
          </Typography>
          {el.label && el.label !== el.name && (
            <Typography variant="caption" color="text.secondary" display="block">{el.label}</Typography>
          )}
        </TableCell>

        <TableCell sx={{ maxWidth: 200 }}>
          {el.description
            ? <Typography variant="caption" color="text.secondary" sx={{ display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{el.description}</Typography>
            : <Typography variant="caption" color="text.disabled">—</Typography>}
        </TableCell>

        <TableCell>
          {el.logicalType
            ? <Chip label={el.logicalType} size="small" color="secondary" variant="outlined" sx={{ height: 18, fontSize: 11 }} />
            : <Typography variant="caption" color="text.disabled">—</Typography>}
        </TableCell>

        <TableCell>
          {el.classification
            ? <ClassificationBadge level={el.classification} />
            : <Typography variant="caption" color="text.disabled">—</Typography>}
        </TableCell>

        <TableCell>
          <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'nowrap' }}>
            {el.isPersonalInformation && (
              <Tooltip title="Personal Information">
                <Chip label="PII" size="small" color="error" sx={{ height: 18, fontSize: 11, fontWeight: 700 }} />
              </Tooltip>
            )}
            {el.isDirectIdentifier && (
              <Tooltip title="Direct Identifier">
                <Chip label="ID" size="small" color="warning" sx={{ height: 18, fontSize: 11, fontWeight: 700 }} />
              </Tooltip>
            )}
            {!el.isPersonalInformation && !el.isDirectIdentifier && (
              <Typography variant="caption" color="text.disabled">—</Typography>
            )}
          </Box>
        </TableCell>

        <TableCell>
          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
            {el.vocabMappings?.slice(0, 2).map(m => (
              <VocabConceptBadge
                key={m.id}
                iri={m.conceptIri}
                label={resolveLabel(translations, m.conceptIri, m.conceptLabel, m.conceptDefinition)}
                matchType={m.matchType}
              />
            ))}
            {(el.vocabMappings?.length ?? 0) > 2 && (
              <Typography variant="caption" color="text.secondary">+{(el.vocabMappings!.length - 2)} more</Typography>
            )}
          </Box>
        </TableCell>

        <TableCell align="right" sx={{ pr: 0.5 }}>
          {hasPhysical && (
            <IconButton size="small" onClick={onToggle}>
              {expanded ? <ExpandLessIcon fontSize="small" /> : <ExpandMoreIcon fontSize="small" />}
            </IconButton>
          )}
        </TableCell>
      </TableRow>

      {expanded && (
        <TableRow sx={{ bgcolor: 'primary.50' }}>
          <TableCell colSpan={7} sx={{ py: 1.5, px: 3 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 3 }}>
              <Typography variant="caption" fontFamily="monospace" fontWeight={700}>
                {el.physicalColumn?.name ?? el.physicalColumnRef?.column ?? '—'}
              </Typography>
              {(el.physicalColumn?.datatype ?? el.physicalColumnRef?.type) && (
                <Chip label={el.physicalColumn?.datatype ?? el.physicalColumnRef?.type} size="small" variant="outlined" sx={{ height: 18, fontSize: 11, fontFamily: 'monospace' }} />
              )}
              {el.physicalColumn?.description && (
                <Typography variant="caption" color="text.secondary">{el.physicalColumn.description}</Typography>
              )}
              {(el.physicalColumn?.required || el.isRequired) && (
                <Chip label="required" color="error" size="small" sx={{ height: 18, fontSize: 11 }} />
              )}
            </Box>
          </TableCell>
        </TableRow>
      )}
    </>
  );
}
