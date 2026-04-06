export interface User {
  id: string;
  email: string;
  displayName: string;
  discordId: string;
  status: string;
  tier: string;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  user: {
    id: string;
    email: string;
    displayName: string;
    discordId: string;
  };
}

export type BuildStatus =
  | 'CHATTING'
  | 'PLANNING'
  | 'APPROVED'
  | 'BUILDING'
  | 'TESTING'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED';

export type BuildPhase =
  | 'IDLE'
  | 'CLARIFICATION'
  | 'PLAN_GENERATION'
  | 'PLAN_REVIEW'
  | 'IMPLEMENTATION'
  | 'COMPILATION'
  | 'SECURITY_SCAN'
  | 'INTEGRATION_TEST'
  | 'DELIVERING';

export type ThresholdStatus = 'NORMAL' | 'WARNING' | 'CRITICAL' | 'EXHAUSTED';

export interface BuildSession {
  id: string;
  userId: string;
  status: BuildStatus;
  currentPhase: BuildPhase;
  complexityScore: number | null;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
}

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  modelUsed: string | null;
  tokensConsumed: number;
  createdAt: string;
}

export interface TokenBudget {
  sessionId: string;
  allocatedTokens: number;
  consumedTokens: number;
  planningTokens: number;
  implementationTokens: number;
  testingTokens: number;
  thresholdStatus: ThresholdStatus;
  remainingTokens: number;
}

export interface PlanDocument {
  id: string;
  sessionId: string;
  pluginName: string;
  description: string;
  minecraftVersion: string;
  serverType: string;
  commands: CommandSpec[];
  eventListeners: EventListenerSpec[];
  configSchema: ConfigEntry[];
  dependencies: DependencySpec[];
  testScenarios: TestScenario[];
  estimatedLoc: number;
  complexityScore: number;
  version: number;
  createdAt: string;
}

export interface CommandSpec {
  name: string;
  description: string;
  permission: string;
  usage: string;
  arguments: { name: string; type: string; required: boolean }[];
}

export interface EventListenerSpec {
  event: string;
  priority: string;
  description: string;
  conditions: string[];
}

export interface ConfigEntry {
  key: string;
  type: string;
  defaultValue: string;
  description: string;
}

export interface DependencySpec {
  groupId: string;
  artifactId: string;
  version: string;
  reason: string;
}

export interface TestScenario {
  name: string;
  description: string;
  type: string;
}

export interface Artifact {
  id: string;
  sessionId: string;
  iterationId: string;
  fileHash: string;
  fileSizeBytes: number;
  pluginVersion: string;
  securityPassed: boolean;
  createdAt: string;
}

export interface TierInfo {
  name: string;
  maxBuilds: number;
  tokenBudget: number;
  maxParallel: number;
  maxIterations: number;
  maxCommands: number;
  maxEventListeners: number;
  jarRetentionDays: number;
  marketplaceSlots: number;
  sourceCodeAccess: boolean;
}

export interface AgentResponse {
  content: string;
  model: string;
  inputTokens: number;
  outputTokens: number;
  phaseTransition: string | null;
}

export interface BuildIteration {
  id: string;
  sessionId: string;
  iterationNumber: number;
  status: string;
  trigger: string;
  startedAt: string;
  completedAt: string | null;
}

export interface ApiKeyDto {
  id: string;
  name: string;
  lastFour: string;
  createdAt: string;
}

export interface UsageStats {
  buildsUsed: number;
  buildsLimit: number;
  tier: string;
}

export interface MarketplaceListing {
  id: string;
  sellerId: string;
  sellerName?: string;
  artifactId: string;
  title: string;
  description: string;
  shortDescription: string;
  category: string;
  minecraftVersion: string;
  priceCents: number;
  downloadCount: number;
  averageRating: number;
  reviewCount: number;
  status: string;
  createdAt: string;
}

export interface ReviewDto {
  id: string;
  reviewerId: string;
  reviewerName?: string;
  rating: number;
  comment: string;
  createdAt: string;
}

export interface PurchaseDto {
  id: string;
  listingId: string;
  priceCents: number;
  status: string;
  createdAt: string;
}

export interface Team {
  id: string;
  name: string;
  ownerId: string;
  maxMembers: number;
  memberCount: number;
  createdAt: string;
}

export interface TeamMember {
  id: string;
  userId: string;
  username: string;
  displayName: string;
  role: 'OWNER' | 'ADMIN' | 'MEMBER';
  joinedAt: string;
}

export interface SharedWorkspace {
  id: string;
  name: string;
  description: string;
  teamId: string;
  createdById: string;
  createdAt: string;
}

export interface CreateTeamRequest {
  name: string;
}

export interface AddTeamMemberRequest {
  userId: string;
  role: 'ADMIN' | 'MEMBER';
}

export interface CreateWorkspaceRequest {
  name: string;
  description: string;
}
