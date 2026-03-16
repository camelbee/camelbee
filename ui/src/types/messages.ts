/** Matches Java enum: org.camelbee.debugger.model.exchange.MessageEventType */
export type MessageEventType = 'CREATED' | 'SENDING' | 'SENT' | 'COMPLETED';

/** Matches Java enum: org.camelbee.debugger.model.exchange.MessageType */
export type MessageType = 'REQUEST' | 'RESPONSE' | 'ERROR_RESPONSE';

/** Matches Java: org.camelbee.debugger.model.exchange.Message */
export interface Message {
  exchangeId: string;
  exchangeEventType: MessageEventType;
  messageBody: string | null;
  headers: string | null;
  routeId: string;
  endpoint: string;
  endpointId: string;
  messageType: MessageType;
  exception: string | null;
  timeStamp: string;
}

/** Matches Java: org.camelbee.debugger.model.exchange.MessageListInfo */
export interface MessageListInfo {
  count: number;
  resetVersion: number;
  addVersion: number;
  lastModified: string;
  lastResetTime: string;
}

/** Matches Java: org.camelbee.debugger.model.exchange.MessageListWithInfo */
export interface MessageListWithInfo {
  messages: Message[];
  info: MessageListInfo;
}

/** TraceStatus sent to POST /camelbee/tracer/status */
export type TraceStatus = 'ACTIVE' | 'INACTIVE';
