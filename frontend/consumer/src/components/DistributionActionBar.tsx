import { useState } from 'react';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import CheckIcon from '@mui/icons-material/Check';
import DownloadIcon from '@mui/icons-material/Download';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import type { Distribution } from '@datacatalog/shared';

interface DistributionActionBarProps {
  distributions: Distribution[];
  copyFn?: (text: string) => Promise<void>;
}

export default function DistributionActionBar({ distributions, copyFn }: DistributionActionBarProps) {
  const [copied, setCopied] = useState<string | null>(null);
  const doCopy = copyFn ?? ((text: string) => navigator.clipboard.writeText(text));

  function copyUrl(url: string) {
    doCopy(url).then(() => {
      setCopied(url);
      setTimeout(() => setCopied(null), 1500);
    });
  }

  if (distributions.length === 0) return null;

  return (
    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
      {distributions.map(d => {
        const url = d.accessUrl ?? d.downloadUrl;
        const label = d.format ?? d.mediaType ?? 'Download';
        return (
          <Box key={d.id} sx={{ display: 'flex', alignItems: 'center', border: 1, borderColor: 'divider', borderRadius: 1, overflow: 'hidden' }}>
            <Chip
              label={label}
              size="small"
              color="primary"
              variant="outlined"
              sx={{ height: 28, borderRadius: '4px 0 0 4px', borderRight: 'none', fontSize: 12 }}
            />
            {url && (
              <>
                <Tooltip title={copied === url ? 'Copied!' : 'Copy URL'}>
                  <IconButton size="small" onClick={() => copyUrl(url)} sx={{ borderRadius: 0, height: 28, px: 0.75 }}>
                    {copied === url ? <CheckIcon fontSize="small" color="success" /> : <ContentCopyIcon fontSize="small" />}
                  </IconButton>
                </Tooltip>
                {d.downloadUrl && (
                  <Tooltip title="Download">
                    <IconButton size="small" component="a" href={d.downloadUrl} target="_blank" rel="noreferrer" sx={{ borderRadius: 0, height: 28, px: 0.75 }}>
                      <DownloadIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                )}
                {d.accessUrl && (
                  <Tooltip title="Open in new tab">
                    <IconButton size="small" component="a" href={d.accessUrl} target="_blank" rel="noreferrer" sx={{ borderRadius: '0 4px 4px 0', height: 28, px: 0.75 }}>
                      <OpenInNewIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                )}
              </>
            )}
          </Box>
        );
      })}
    </Box>
  );
}
