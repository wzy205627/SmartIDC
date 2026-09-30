package io.agentscope.core.model;

/**
 * AgentScope ChatModel 接口桥接扩展 (兼容 implementation_plan4.3.md 命名规范)
 * 继承自 io.agentscope.core.model.Model，为单例 Service + 栈上瞬态 ReActAgent 提供统一模型抽象
 */
public interface ChatModel extends Model {
}
