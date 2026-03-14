/** Maps Camel component-type prefixes to Tailwind color classes. */

const colorTable: [string[], string][] = [
  [['direct', 'seda'], 'blue-500'],
  [['rest', 'http', 'platform-http'], 'green-500'],
  [['kafka'], 'orange-500'],
  [['jms', 'amqp'], 'purple-500'],
  [['mongodb'], 'green-600'],
  [['sql', 'jpa'], 'cyan-500'],
  [['file'], 'yellow-500'],
  [['rabbitmq', 'spring-rabbitmq'], 'orange-400'],
  [['mqtt', 'paho-mqtt5', 'paho-mqtt'], 'amber-500'],
  [['cxf', 'soap'], 'indigo-500'],
  [['error'], 'red-500'],
  [['aws-s3'], 'orange-300'],
  [['mcp', 'netty-http'], 'pink-500'],
  [['graphql'], 'violet-500'],
  [['grpc'], 'teal-500'],
  [['websocket'], 'sky-500'],
];

const prefixMap = new Map<string, string>();
for (const [prefixes, color] of colorTable) {
  for (const p of prefixes) {
    prefixMap.set(p, color);
  }
}

export function getComponentColors(type: string): {
  bg: string;
  border: string;
  text: string;
} {
  const color = prefixMap.get(type.toLowerCase()) ?? 'gray-500';
  return {
    bg: `bg-${color}/20`,
    border: `border-${color}`,
    text: `text-${color}`,
  };
}

/** All possible color tokens so Tailwind can detect them at build time. */
export const _safelistColors = [
  'bg-blue-500/20', 'border-blue-500', 'text-blue-500',
  'bg-green-500/20', 'border-green-500', 'text-green-500',
  'bg-orange-500/20', 'border-orange-500', 'text-orange-500',
  'bg-purple-500/20', 'border-purple-500', 'text-purple-500',
  'bg-green-600/20', 'border-green-600', 'text-green-600',
  'bg-cyan-500/20', 'border-cyan-500', 'text-cyan-500',
  'bg-yellow-500/20', 'border-yellow-500', 'text-yellow-500',
  'bg-orange-400/20', 'border-orange-400', 'text-orange-400',
  'bg-amber-500/20', 'border-amber-500', 'text-amber-500',
  'bg-indigo-500/20', 'border-indigo-500', 'text-indigo-500',
  'bg-red-500/20', 'border-red-500', 'text-red-500',
  'bg-orange-300/20', 'border-orange-300', 'text-orange-300',
  'bg-pink-500/20', 'border-pink-500', 'text-pink-500',
  'bg-violet-500/20', 'border-violet-500', 'text-violet-500',
  'bg-teal-500/20', 'border-teal-500', 'text-teal-500',
  'bg-sky-500/20', 'border-sky-500', 'text-sky-500',
  'bg-gray-500/20', 'border-gray-500', 'text-gray-500',
];
