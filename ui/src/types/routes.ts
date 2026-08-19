/** Matches Java: org.camelbee.debugger.model.route.CamelBeeContext */
export interface CamelBeeContext {
  routes: CamelRoute[];
  name: string;
  jvm: string;
  jvmInputParameters: string;
  garbageCollectors: string;
  framework: string;
  camelVersion: string;
}

/** Matches Java: org.camelbee.debugger.model.route.CamelRoute */
export interface CamelRoute {
  id: string;
  input: string;
  outputs: CamelRouteOutput[];
  rest: boolean;
  errorHandler: string | null;
  /** The route's <description> text (from getDescriptionText()), or null if unset. */
  routeDescription?: string | null;
}

/** Matches Java: org.camelbee.debugger.model.route.CamelRouteOutput */
export interface CamelRouteOutput {
  id: string;
  description: string;
  delimiter: string | null;
  type: string;
  outputs: CamelRouteOutput[];
  /** The processor's <description> text (from getDescriptionText()), or null if unset. */
  nodeDescription?: string | null;
}
