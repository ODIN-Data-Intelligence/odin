import { useState } from 'react';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemText from '@mui/material/ListItemText';
import Chip from '@mui/material/Chip';
import Checkbox from '@mui/material/Checkbox';
import ListItem from '@mui/material/ListItem';
import Divider from '@mui/material/Divider';
import IconButton from '@mui/material/IconButton';
import Button from '@mui/material/Button';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import type { SearchFacets } from '@datacatalog/shared';
import { iriFragment, useIriTranslations } from '@datacatalog/shared';
import { useSearchStore } from '../store/searchStore';

export const humanizeConcept = iriFragment;

interface FacetPanelProps {
  facets: SearchFacets;
}

const ENTITY_TYPE_LABELS: Record<string, string> = {
  DATASET: 'Dataset',
  DATA_PRODUCT: 'Data Product',
};

export default function FacetPanel({ facets }: FacetPanelProps) {
  const { filters, setFilter, clearFilters } = useSearchStore();
  const [collapsed, setCollapsed] = useState(false);

  const iriKeys = [
    ...(facets.fiboConcepts ?? []).map(f => f.key),
    ...(facets.vocabConcepts ?? []).map(f => f.key),
    ...(facets.themes ?? []).map(f => f.key),
  ];
  const translations = useIriTranslations(iriKeys);
  const label = (k: string) => translations[k] ?? iriFragment(k);

  const hasFilters = Object.values(filters).some(v => v !== undefined && v !== '');

  if (collapsed) {
    return (
      <Box sx={{ width: 32, flexShrink: 0, display: 'flex', justifyContent: 'center', pt: 1 }}>
        <IconButton size="small" onClick={() => setCollapsed(false)} title="Expand filters">
          <ChevronRightIcon />
        </IconButton>
      </Box>
    );
  }

  return (
    <Paper variant="outlined" sx={{ width: 224, flexShrink: 0, p: 2 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1.5 }}>
        <Typography variant="subtitle2" fontWeight={600}>Filters</Typography>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
          {hasFilters && (
            <Button size="small" onClick={clearFilters} sx={{ fontSize: 11, minWidth: 0, p: 0.5, textTransform: 'none' }}>
              Clear all
            </Button>
          )}
          <IconButton size="small" onClick={() => setCollapsed(true)} title="Collapse">
            <ChevronLeftIcon />
          </IconButton>
        </Box>
      </Box>

      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        <FacetGroup
          title="Type"
          items={(facets.entityTypes ?? []).filter(f => f.key !== 'DISTRIBUTION')}
          selected={filters.types && !filters.types.includes(',') ? filters.types : undefined}
          getLabel={k => ENTITY_TYPE_LABELS[k] ?? k}
          onSelect={v => {
            const current = filters.types && !filters.types.includes(',') ? filters.types : undefined;
            setFilter('types', v === current ? 'DATASET,DATA_PRODUCT' : v);
          }}
        />
        <FacetGroup
          title="Format"
          items={facets.formats ?? []}
          selected={filters.format}
          getLabel={k => k}
          onSelect={v => setFilter('format', v === filters.format ? undefined : v)}
        />
        <FacetGroup
          title="Lifecycle"
          items={facets.lifecycleStatuses ?? []}
          selected={filters.lifecycleStatus}
          getLabel={k => k}
          onSelect={v => setFilter('lifecycleStatus', v === filters.lifecycleStatus ? undefined : v)}
        />
        <FacetGroup
          title="Vocabulary"
          items={facets.vocabularyTypes ?? []}
          selected={filters.vocab}
          getLabel={k => k.charAt(0).toUpperCase() + k.slice(1)}
          onSelect={v => setFilter('vocab', v === filters.vocab ? undefined : v)}
        />
        {(facets.semanticTypes ?? []).length > 0 && (
          <FacetGroup
            title="Semantic Types"
            items={facets.semanticTypes ?? []}
            selected={filters.semanticType}
            getLabel={k => k}
            onSelect={v => setFilter('semanticType', v === filters.semanticType ? undefined : v)}
          />
        )}
        {(facets.fiboConcepts ?? []).length > 0 && (
          <FacetGroup
            title="FIBO Concepts"
            items={facets.fiboConcepts ?? []}
            selected={filters.fiboConcept}
            getLabel={label}
            onSelect={v => setFilter('fiboConcept', v === filters.fiboConcept ? undefined : v)}
          />
        )}
        <FacetGroup
          title="Keywords"
          items={facets.keywords ?? []}
          selected={filters.keyword}
          getLabel={k => k}
          onSelect={v => setFilter('keyword', v === filters.keyword ? undefined : v)}
        />
        <FacetGroup
          title="Themes"
          items={facets.themes ?? []}
          selected={filters.theme}
          getLabel={label}
          onSelect={v => setFilter('theme', v === filters.theme ? undefined : v)}
        />
        <FacetGroup
          title="Vocabulary Concepts"
          items={facets.vocabConcepts ?? []}
          selected={filters.vocabConcept}
          getLabel={label}
          onSelect={v => setFilter('vocabConcept', v === filters.vocabConcept ? undefined : v)}
        />

        <Box>
          <Typography variant="caption" fontWeight={600} color="text.secondary" sx={{ textTransform: 'uppercase', letterSpacing: 0.5, display: 'block', mb: 0.75 }}>
            Has Lineage
          </Typography>
          <ListItem disablePadding>
            <ListItemButton
              dense
              onClick={() => setFilter('hasLineage', filters.hasLineage ? undefined : true)}
              sx={{ borderRadius: 1, px: 1, py: 0.5 }}
            >
              <Checkbox
                size="small"
                checked={!!filters.hasLineage}
                disableRipple
                sx={{ p: 0, mr: 1 }}
              />
              <ListItemText primary="With lineage" primaryTypographyProps={{ variant: 'body2' }} />
            </ListItemButton>
          </ListItem>
        </Box>
      </Box>
    </Paper>
  );
}

function FacetGroup({
  title, items, selected, getLabel, onSelect,
}: {
  title: string;
  items: { key: string; count: number }[];
  selected?: string;
  getLabel: (k: string) => string;
  onSelect: (k: string) => void;
}) {
  if (items.length === 0) return null;
  return (
    <Box>
      <Typography variant="caption" fontWeight={600} color="text.secondary" sx={{ textTransform: 'uppercase', letterSpacing: 0.5, display: 'block', mb: 0.75 }}>
        {title}
      </Typography>
      <Divider sx={{ mb: 0.5 }} />
      <Box sx={{ maxHeight: 160, overflowY: 'auto', pr: 0.5 }}>
        <List dense disablePadding>
          {items.map(f => (
            <ListItemButton
              key={f.key}
              onClick={() => onSelect(f.key)}
              selected={selected === f.key}
              sx={{ borderRadius: 1, px: 1, py: 0.5, mb: 0.25 }}
            >
              <ListItemText
                primary={getLabel(f.key)}
                primaryTypographyProps={{ variant: 'body2', noWrap: true }}
              />
              <Chip
                label={f.count}
                size="small"
                color={selected === f.key ? 'primary' : 'default'}
                sx={{ height: 18, fontSize: 11, ml: 0.5 }}
              />
            </ListItemButton>
          ))}
        </List>
      </Box>
    </Box>
  );
}
