import { cn } from "@/lib/utils";
import { AGENT_AVATARS, type AgentAvatarPreset } from "@/components/admin/agentAvatarPresets";

// 后端只存 key 不校验白名单，认不出的取值按 seed 散列到某个预设，保证永远画得出东西
function presetFor(avatar?: string | null, seed?: string): AgentAvatarPreset {
  const matched = AGENT_AVATARS.find((preset) => preset.key === avatar);
  if (matched) {
    return matched;
  }
  let hash = 0;
  for (const char of seed || "") {
    hash = (hash * 31 + char.charCodeAt(0)) % 1_000_000_007;
  }
  return AGENT_AVATARS[hash % AGENT_AVATARS.length];
}

interface AgentAvatarProps {
  avatar?: string | null;
  /** 认不出 avatar 时的散列种子，传智能体 id 可保证同一个智能体每次同色 */
  seed?: string;
  /** 控制外框尺寸 如 "h-11 w-11" */
  className?: string;
  /** 控制内部字形尺寸 如 "h-5 w-5" */
  iconClassName?: string;
}

/**
 * 智能体头像：渐变底 + 线条字形，取值来自预设表
 * <p>
 * 样式全部走 utilities 而非 globals.css —— 弹窗里的选择器由 portal 渲染在 .admin-layout 之外
 */
export function AgentAvatar({ avatar, seed, className, iconClassName }: AgentAvatarProps) {
  const { Icon, gradient, shadow } = presetFor(avatar, seed);
  return (
    <span
      className={cn(
        "inline-flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl text-white",
        className
      )}
      style={{ backgroundImage: gradient, boxShadow: `0 6px 16px -8px ${shadow}` }}
    >
      <Icon className={cn("h-5 w-5", iconClassName)} />
    </span>
  );
}
