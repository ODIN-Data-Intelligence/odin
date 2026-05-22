import { useState } from 'react';
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
    <div className="flex flex-wrap gap-2">
      {distributions.map(d => {
        const url = d.accessUrl ?? d.downloadUrl;
        const label = d.format ?? d.mediaType ?? 'Download';
        return (
          <div key={d.id} className="flex items-center gap-1">
            <span className="px-2 py-1 bg-blue-50 border border-blue-200 text-blue-700 text-xs rounded-l font-medium">
              {label}
            </span>
            {url && (
              <>
                <button
                  onClick={() => copyUrl(url)}
                  title="Copy URL"
                  className="px-2 py-1 bg-white border border-l-0 border-gray-200 text-gray-500 hover:text-gray-700 text-xs"
                >
                  {copied === url ? '✓' : '⎘'}
                </button>
                {d.downloadUrl && (
                  <a
                    href={d.downloadUrl}
                    target="_blank"
                    rel="noreferrer"
                    title="Download"
                    className="px-2 py-1 bg-white border border-l-0 border-gray-200 text-gray-500 hover:text-gray-700 text-xs rounded-r"
                  >
                    ↓
                  </a>
                )}
                {d.accessUrl && (
                  <a
                    href={d.accessUrl}
                    target="_blank"
                    rel="noreferrer"
                    title="Open in new tab"
                    className="px-2 py-1 bg-white border border-l-0 border-gray-200 text-gray-500 hover:text-gray-700 text-xs rounded-r"
                  >
                    ↗
                  </a>
                )}
              </>
            )}
          </div>
        );
      })}
    </div>
  );
}
