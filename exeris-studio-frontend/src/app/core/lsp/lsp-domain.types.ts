/**
 * Exeris LSP Protocol Projection Types
 * Models matching the wire representations emitted by exeris-platform-lsp
 * (ExerisProtocolExtensions.java, ADR-025 / ADR-084).
 */

export interface DomainSummaryProjection {
  qualifiedName: string;
  simpleName: string;
  packageName: string;
  sourcePath: string;
}

export interface FieldDescriptionProjection {
  name: string;
  type: string;
  required: boolean;
}

export interface ParamSummaryProjection {
  name: string;
  type: string;
  required: boolean;
}

export interface ActionDescriptionProjection {
  name: string;
  httpMethod: string;
  resultType: string;
  params: ParamSummaryProjection[];
}

/** Cardinality in the SDK RelationshipMetadata serialized form. */
export type RelationshipType = 'ONE_TO_ONE' | 'ONE_TO_MANY' | 'MANY_TO_ONE' | 'MANY_TO_MANY';

/**
 * One association as the LSP carries it. `targetEntity` is the declared type name (usually simple,
 * not a qualifiedName) and is not resolved against the workspace; `type` is omitted when the model
 * carries no cardinality.
 */
export interface RelationshipDescriptionProjection {
  name: string;
  targetEntity: string;
  type?: RelationshipType;
}

export interface DomainDescriptionProjection {
  qualifiedName: string;
  simpleName: string;
  packageName: string;
  sourcePath: string;
  fields: FieldDescriptionProjection[];
  actions: ActionDescriptionProjection[];
  artefacts: string[];
  /** Omitted when the pipeline does not carry the facet; `[]` when the domain declares none. */
  relationships?: RelationshipDescriptionProjection[] | null;
}

export interface ActionSummaryProjection {
  owningDomain: string;
  name: string;
  httpMethod: string;
  resultType: string;
  params: ParamSummaryProjection[];
}

export interface JsonRpcRequest<T = unknown> {
  jsonrpc: '2.0';
  id: number | string;
  method: string;
  params?: T;
}

export interface JsonRpcResponse<T = unknown> {
  jsonrpc: '2.0';
  id: number | string;
  result?: T;
  error?: JsonRpcError;
}

export interface JsonRpcNotification<T = unknown> {
  jsonrpc: '2.0';
  method: string;
  params?: T;
}

export interface JsonRpcError {
  code: number;
  message: string;
  data?: unknown;
}

export const SCHEMA_VERSION_SKEW_CODE = -32001;

export class SchemaVersionSkewError extends Error {
  constructor(message: string, public readonly details?: unknown) {
    super(message);
    this.name = 'SchemaVersionSkewError';
  }
}
