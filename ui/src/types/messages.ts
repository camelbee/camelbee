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
  /**
   * These three are nullable on the wire. `routeId`/`endpoint` are absent on the CREATED and
   * COMPLETED markers that bracket an exchange (they describe the exchange, not a hop), and
   * `endpointId` is only present when Camel exposes a history node id for the send — never for
   * a redelivered attempt, and not for sends performed inside wireTap/enrich/recipientList.
   */
  routeId: string | null;
  endpoint: string | null;
  endpointId: string | null;
  messageType: MessageType;
  exception: string | null;
  timeStamp: string;
  /** Elapsed ms from ExchangeSentEvent.getTimeTaken(); 0 for non-SENT message types. */
  timeTaken: number;
}

/** Matches Java: org.camelbee.debugger.model.exchange.MessageListInfo */
export interface MessageListInfo {
  count: number;
  resetVersion: number;
  addVersion: number;
  lastModified: string;
  lastResetTime: string;
  /** True once camelbee.tracer-max-messages-count has been hit and messages are being dropped. */
  capReached: boolean;
}

/** Matches Java: org.camelbee.debugger.model.exchange.MessageListWithInfo */
export interface MessageListWithInfo {
  messages: Message[];
  info: MessageListInfo;
}

/** TraceStatus sent to POST /camelbee/tracer/status */
export type TraceStatus = 'ACTIVE' | 'INACTIVE';
