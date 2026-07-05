import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { VocabConceptBadge, iriFragment as humanize } from '@datacatalog/shared';

function renderBadge(props: { iri: string; label?: string; matchType?: string }) {
  return render(
    <MemoryRouter>
      <VocabConceptBadge {...props} />
    </MemoryRouter>
  );
}

describe('humanize', () => {
  it('should extract the fragment after last # or /', () => {
    expect(humanize('https://schema.org/MonetaryAmount')).toBe('Monetary Amount');
    expect(humanize('http://example.com/ontology#Price')).toBe('Price');
  });

  it('should insert space before capital letters', () => {
    expect(humanize('https://fibo.org/TradeAmount')).toBe('Trade Amount');
  });

  it('should return the whole string if no delimiter found', () => {
    expect(humanize('MonetaryAmount')).toBe('Monetary Amount');
  });

  it('should trim leading space', () => {
    // A string starting with uppercase would produce ' X' → 'X' after trim
    expect(humanize('Price')).toBe('Price');
  });
});

describe('VocabConceptBadge', () => {
  it('should display the provided label', () => {
    renderBadge({ iri: 'https://schema.org/price', label: 'Price' });
    expect(screen.getByText('Price')).toBeInTheDocument();
  });

  it('should fall back to humanized IRI when no label', () => {
    renderBadge({ iri: 'https://schema.org/MonetaryAmount' });
    expect(screen.getByText('Monetary Amount')).toBeInTheDocument();
  });

  it('should use the IRI as link title', () => {
    renderBadge({ iri: 'https://schema.org/price', label: 'Price' });
    expect(screen.getByTitle('https://schema.org/price')).toBeInTheDocument();
  });

  it('should apply green color for exactMatch', () => {
    renderBadge({ iri: 'https://schema.org/price', matchType: 'exactMatch' });
    const badge = screen.getByRole('link');
    expect(badge.className).toContain('green');
  });

  it('should apply blue color for closeMatch', () => {
    renderBadge({ iri: 'https://schema.org/price', matchType: 'closeMatch' });
    expect(screen.getByRole('link').className).toContain('blue');
  });

  it('should apply purple color for relatedMatch', () => {
    renderBadge({ iri: 'https://schema.org/price', matchType: 'relatedMatch' });
    expect(screen.getByRole('link').className).toContain('purple');
  });

  it('should apply orange color for broadMatch', () => {
    renderBadge({ iri: 'https://schema.org/price', matchType: 'broadMatch' });
    expect(screen.getByRole('link').className).toContain('orange');
  });

  it('should apply yellow color for narrowMatch', () => {
    renderBadge({ iri: 'https://schema.org/price', matchType: 'narrowMatch' });
    expect(screen.getByRole('link').className).toContain('yellow');
  });

  it('should apply gray color for unknown matchType', () => {
    renderBadge({ iri: 'https://schema.org/price', matchType: 'unknownType' });
    expect(screen.getByRole('link').className).toContain('gray');
  });

  it('should apply gray color when no matchType provided', () => {
    renderBadge({ iri: 'https://schema.org/price' });
    expect(screen.getByRole('link').className).toContain('gray');
  });

  it('should link to search with the display label', () => {
    renderBadge({ iri: 'https://schema.org/price', label: 'Price' });
    const link = screen.getByRole('link');
    expect(link.getAttribute('href')).toContain('Price');
  });
});
