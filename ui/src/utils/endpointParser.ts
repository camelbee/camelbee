import type { CamelRouteOutput } from '@/types';

/**
 * Extract the component-type prefix from a URI.
 * e.g. "kafka:topic" → "kafka", "http://host" → "http"
 */
export function extractComponentType(uri: string): string {
  const idx = uri.indexOf(':');
  return idx > 0 ? uri.substring(0, idx).toLowerCase() : uri.toLowerCase();
}

/** Returns true when the URI is an internal (direct/seda) or dynamic endpoint. */
function isInternal(uri: string): boolean {
  const lower = uri.toLowerCase();
  return (
    lower.startsWith('${') ||
    lower.startsWith('direct:') ||
    lower.startsWith('seda:')
  );
}

/** Extract the value between the first `{` and last `}`. */
function extractBetweenBraces(s: string): string | null {
  const start = s.indexOf('{');
  const end = s.lastIndexOf('}');
  if (start === -1 || end === -1 || end <= start) return null;
  return s.substring(start + 1, end);
}

/** Strip prefix like "To[" and trailing "]". */
function stripWrapper(s: string, prefix: string): string {
  return s.substring(prefix.length, s.length - 1);
}

/**
 * Extract static endpoint URIs from an output description.
 * Port of MessageStaticEndpointUtil.cs
 */
export function extractStaticEndpointsFromOutput(
  output: CamelRouteOutput,
): string[] | null {
  const desc = output.description;
  if (!desc) return null;

  const lower = desc.toLowerCase();

  // To[X], DynamicTo[X], DynamicTo[toD[X]], WireTap[X]
  for (const prefix of ['To[', 'DynamicTo[', 'WireTap[']) {
    if (lower.startsWith(prefix.toLowerCase())) {
      let uri = stripWrapper(desc, prefix);
      // Handle DynamicTo[toD[X]] — strip the inner toD[] wrapper
      if (uri.toLowerCase().startsWith('tod[') && uri.endsWith(']')) {
        uri = uri.substring(4, uri.length - 1);
      }
      return isInternal(uri) ? null : [uri];
    }
  }

  // Enrich[...{X}...], PollEnrich[...{X}...]
  for (const prefix of ['Enrich[', 'PollEnrich[']) {
    if (lower.startsWith(prefix.toLowerCase())) {
      const inner = extractBetweenBraces(desc);
      if (!inner) return null;
      return isInternal(inner) ? null : [inner];
    }
  }

  // RecipientList[...{A,B,C}...], RoutingSlip[...{A,B}...]
  for (const prefix of ['RecipientList[', 'RoutingSlip[']) {
    if (lower.startsWith(prefix.toLowerCase())) {
      const inner = extractBetweenBraces(desc);
      if (!inner) return null;
      const delimiter = output.delimiter ?? ',';
      const parts = inner.split(delimiter).map((s) => s.trim()).filter(Boolean);
      const external = parts.filter((p) => !isInternal(p));
      return external.length > 0 ? external : null;
    }
  }

  return null;
}

/**
 * Check whether a route output description references a given input URI.
 * Port of RouteService.doDrawSiblings matching logic.
 */
export function outputReferencesInput(
  output: CamelRouteOutput,
  inputTrimmed: string,
): boolean {
  const desc = output.description;
  if (!desc) return false;

  const descLower = desc.toLowerCase();
  const inputLower = inputTrimmed.toLowerCase();

  // Direct match: To[input], DynamicTo[input], DynamicTo[toD[input]], WireTap[input]
  for (const prefix of ['To[', 'DynamicTo[', 'WireTap[']) {
    if (descLower === `${prefix.toLowerCase()}${inputLower}]`) {
      return true;
    }
  }
  // Handle DynamicTo[toD[input]]
  if (descLower === `dynamicto[tod[${inputLower}]]`) {
    return true;
  }

  // Contained match for Enrich, PollEnrich, RecipientList, RoutingSlip
  const containerPrefixes = [
    'enrich[',
    'pollenrich[',
    'recipientlist[',
    'routingslip[',
  ];
  const startsWithContainer = containerPrefixes.some((p) =>
    descLower.startsWith(p),
  );
  if (startsWithContainer) {
    // Check if input appears inside braces, possibly comma-delimited
    return (
      desc.includes(`{${inputTrimmed}}`) ||
      desc.includes(`{${inputTrimmed},`) ||
      desc.includes(`,${inputTrimmed},`) ||
      desc.includes(`,${inputTrimmed}}`)
    );
  }

  return false;
}

/**
 * Extract the input URI from a route's input field.
 * "From[direct:myRoute]" → "direct:myRoute"
 */
export function extractInputUri(input: string): string {
  if (input.startsWith('From[') && input.endsWith(']')) {
    return input.substring(5, input.length - 1);
  }
  return input;
}
