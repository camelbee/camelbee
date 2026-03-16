/** Maps Camel component-type prefixes to Tailwind color classes. */

const colorTable: [string[], string][] = [
  // Internal routing
  [['direct', 'seda'], 'blue-500'],

  // HTTP / REST
  [['rest', 'http', 'platform-http', 'netty-http'], 'green-500'],

  // Messaging
  [['kafka'], 'orange-500'],
  [['jms', 'amqp'], 'purple-500'],
  [['rabbitmq', 'spring-rabbitmq'], 'orange-400'],
  [['mqtt', 'paho-mqtt5', 'paho-mqtt'], 'amber-500'],

  // SOAP / CXF
  [['cxf', 'soap'], 'indigo-500'],

  // Databases
  [['mongodb'], 'green-600'],
  [['sql', 'jpa', 'jdbc'], 'cyan-500'],
  [['cassandra', 'cql'], 'cyan-600'],

  // File / FTP
  [['file'], 'yellow-500'],
  [['ftp', 'ftps', 'sftp'], 'yellow-600'],

  // AWS
  [['aws-s3', 'aws2-s3'], 'orange-300'],
  [['aws-sqs', 'aws2-sqs'], 'orange-500'],
  [['aws-sns', 'aws2-sns'], 'orange-600'],
  [['aws-ddb', 'aws2-ddb', 'aws-dynamodb'], 'orange-400'],

  // RPC / API
  [['graphql'], 'violet-500'],
  [['grpc'], 'teal-500'],
  [['websocket', 'ahc-ws', 'atmosphere-websocket'], 'sky-500'],

  // AI
  [['langchain4j', 'langchain4j-chat', 'langchain4j-tools', 'langchain4j-web-search'], 'fuchsia-500'],
  [['mcp'], 'pink-500'],

  // Scheduling
  [['scheduler', 'timer', 'quartz', 'cron'], 'lime-500'],

  // Email
  [['mail', 'smtp', 'smtps', 'imap', 'imaps', 'pop3', 'pop3s'], 'rose-500'],

  // Cache
  [['infinispan', 'hazelcast', 'redis', 'redisson'], 'emerald-500'],

  // CRM / SaaS
  [['salesforce'], 'blue-600'],

  // Testing
  [['mock'], 'gray-400'],

  // Error
  [['error'], 'red-500'],
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
  /** Solid background for consumer/producer nodes — works on both themes */
  nodeBg: string;
} {
  const color = prefixMap.get(type.toLowerCase()) ?? 'gray-500';
  return {
    bg: `bg-${color}/20`,
    border: `border-${color}`,
    text: `text-${color}`,
    nodeBg: `bg-${color}`,
  };
}

/** All possible color tokens so Tailwind can detect them at build time. */
export const _safelistColors = [
  'bg-blue-500/20', 'border-blue-500', 'text-blue-500', 'bg-blue-500',
  'bg-blue-600/20', 'border-blue-600', 'text-blue-600', 'bg-blue-600',
  'bg-green-500/20', 'border-green-500', 'text-green-500', 'bg-green-500',
  'bg-green-600/20', 'border-green-600', 'text-green-600', 'bg-green-600',
  'bg-orange-300/20', 'border-orange-300', 'text-orange-300', 'bg-orange-300',
  'bg-orange-400/20', 'border-orange-400', 'text-orange-400', 'bg-orange-400',
  'bg-orange-500/20', 'border-orange-500', 'text-orange-500', 'bg-orange-500',
  'bg-orange-600/20', 'border-orange-600', 'text-orange-600', 'bg-orange-600',
  'bg-purple-500/20', 'border-purple-500', 'text-purple-500', 'bg-purple-500',
  'bg-cyan-500/20', 'border-cyan-500', 'text-cyan-500', 'bg-cyan-500',
  'bg-cyan-600/20', 'border-cyan-600', 'text-cyan-600', 'bg-cyan-600',
  'bg-yellow-500/20', 'border-yellow-500', 'text-yellow-500', 'bg-yellow-500',
  'bg-yellow-600/20', 'border-yellow-600', 'text-yellow-600', 'bg-yellow-600',
  'bg-amber-500/20', 'border-amber-500', 'text-amber-500', 'bg-amber-500',
  'bg-indigo-500/20', 'border-indigo-500', 'text-indigo-500', 'bg-indigo-500',
  'bg-red-500/20', 'border-red-500', 'text-red-500', 'bg-red-500',
  'bg-pink-500/20', 'border-pink-500', 'text-pink-500', 'bg-pink-500',
  'bg-fuchsia-500/20', 'border-fuchsia-500', 'text-fuchsia-500', 'bg-fuchsia-500',
  'bg-violet-500/20', 'border-violet-500', 'text-violet-500', 'bg-violet-500',
  'bg-teal-500/20', 'border-teal-500', 'text-teal-500', 'bg-teal-500',
  'bg-sky-500/20', 'border-sky-500', 'text-sky-500', 'bg-sky-500',
  'bg-lime-500/20', 'border-lime-500', 'text-lime-500', 'bg-lime-500',
  'bg-rose-500/20', 'border-rose-500', 'text-rose-500', 'bg-rose-500',
  'bg-emerald-500/20', 'border-emerald-500', 'text-emerald-500', 'bg-emerald-500',
  'bg-gray-400/20', 'border-gray-400', 'text-gray-400', 'bg-gray-400',
  'bg-gray-500/20', 'border-gray-500', 'text-gray-500', 'bg-gray-500',
];
