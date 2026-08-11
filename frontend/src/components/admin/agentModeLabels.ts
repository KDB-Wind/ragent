import type { OrchestrationMode } from "@/services/agentProfileService";

// 与 MODE_LABEL 反向的架构标签，用于标注非当前架构（如 Agent 页标注 WorkFlow 槽位）
export const OTHER_MODE_LABEL: Record<OrchestrationMode, string> = {
  WORKFLOW: "Agent",
  AGENT: "WorkFlow"
};
