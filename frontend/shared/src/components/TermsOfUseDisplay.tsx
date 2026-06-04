import type { TermsOfUse } from '../types/catalog';

const ACCESS_LEVEL_CONFIG: Record<
  NonNullable<TermsOfUse['accessLevel']>,
  { label: string; badge: string }
> = {
  OPEN:              { label: 'Open',             badge: 'bg-green-100 text-green-800' },
  INTERNAL_ONLY:     { label: 'Internal Only',    badge: 'bg-blue-100 text-blue-800' },
  RESTRICTED:        { label: 'Restricted',       badge: 'bg-amber-100 text-amber-800' },
  HIGHLY_RESTRICTED: { label: 'Highly Restricted', badge: 'bg-red-100 text-red-800' },
};

interface TermsOfUseDisplayProps {
  terms: TermsOfUse;
}

export default function TermsOfUseDisplay({ terms }: TermsOfUseDisplayProps) {
  const levelConfig = terms.accessLevel ? ACCESS_LEVEL_CONFIG[terms.accessLevel] : null;

  return (
    <div className="space-y-4">
      {/* Access level badge + effective classification */}
      <div className="flex items-center gap-3">
        {levelConfig && (
          <span className={`px-3 py-1 rounded-full text-xs font-semibold ${levelConfig.badge}`}>
            {levelConfig.label}
          </span>
        )}
        {terms.effectiveClassification && (
          <span className="text-xs text-gray-500">
            Effective classification:{' '}
            <span className="font-medium text-gray-700">{terms.effectiveClassification}</span>
          </span>
        )}
      </div>

      {/* Source banners */}
      {terms.policySource === 'explicit' && (
        <div className="flex items-start gap-2 bg-blue-50 border border-blue-200 rounded-md px-3 py-2 text-xs text-blue-800">
          <span className="mt-0.5">ℹ</span>
          <span>This dataset has a custom policy set by the data owner.</span>
        </div>
      )}
      {terms.policySource === 'fallback' && (
        <div className="flex items-start gap-2 bg-gray-50 border border-gray-200 rounded-md px-3 py-2 text-xs text-gray-600">
          <span className="mt-0.5">ℹ</span>
          <span>No element classifications found. Terms are derived from the dataset's declared license.</span>
        </div>
      )}

      {/* Rule sections */}
      {terms.permissions && terms.permissions.length > 0 && (
        <RuleSection
          title="Permitted Uses"
          items={terms.permissions}
          icon="✓"
          iconClass="text-green-600"
          borderClass="border-green-200"
          titleClass="text-green-800"
        />
      )}
      {terms.prohibitions && terms.prohibitions.length > 0 && (
        <RuleSection
          title="Restrictions"
          items={terms.prohibitions}
          icon="✕"
          iconClass="text-red-500"
          borderClass="border-red-200"
          titleClass="text-red-800"
        />
      )}
      {terms.obligations && terms.obligations.length > 0 && (
        <RuleSection
          title="Obligations"
          items={terms.obligations}
          icon="!"
          iconClass="text-amber-600"
          borderClass="border-amber-200"
          titleClass="text-amber-800"
        />
      )}

      {/* Applicable regulations */}
      {terms.applicableRegulations && terms.applicableRegulations.length > 0 && (
        <div>
          <p className="text-xs font-semibold text-gray-700 mb-2">Applicable Regulations</p>
          <div className="flex flex-wrap gap-2">
            {terms.applicableRegulations.map(reg => (
              <span key={reg} className="px-2 py-1 bg-indigo-50 text-indigo-700 text-xs rounded font-medium">
                {reg}
              </span>
            ))}
          </div>
        </div>
      )}

      {/* Collapsible ODRL policy */}
      {terms.odrlPolicy && (
        <details className="border border-gray-200 rounded-md overflow-hidden">
          <summary className="px-3 py-2 text-xs font-medium text-gray-600 cursor-pointer hover:bg-gray-50 select-none">
            ODRL Policy (Technical)
          </summary>
          <pre className="px-3 py-3 text-xs font-mono text-gray-700 bg-gray-50 overflow-x-auto whitespace-pre-wrap border-t border-gray-200">
            {JSON.stringify(terms.odrlPolicy, null, 2)}
          </pre>
        </details>
      )}
    </div>
  );
}

function RuleSection({
  title, items, icon, iconClass, borderClass, titleClass,
}: {
  title: string;
  items: string[];
  icon: string;
  iconClass: string;
  borderClass: string;
  titleClass: string;
}) {
  return (
    <div className={`border ${borderClass} rounded-md p-3`}>
      <p className={`text-xs font-semibold mb-2 ${titleClass}`}>{title}</p>
      <ul className="space-y-1.5">
        {items.map(item => (
          <li key={item} className="flex items-start gap-2 text-xs text-gray-700">
            <span className={`mt-0.5 font-bold flex-shrink-0 ${iconClass}`}>{icon}</span>
            <span>{item}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
