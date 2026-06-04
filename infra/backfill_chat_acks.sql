-- One-off backfill: repair chat transcripts affected by the plan-gen prompt/tool
-- mismatch bug (fixed in c54e3ab).
--   A. Replace leaked tool-call emulations (<submit_plan>, <function_calls>,
--      <simulate_tool_call>, <thinking>, <trigger_plan_submission>) that reached
--      the chat as assistant messages, with a clean plan acknowledgment.
--   B. Insert an acknowledgment for PLANNING sessions whose last message is the
--      user's silently-applied plan revision, so the conversation reads correctly.
-- Idempotent-ish: re-running A is a no-op (the leaked patterns are gone); re-running
-- B would add a duplicate ack, so run B once (guarded by lm.role='user').

BEGIN;

UPDATE chat_messages cm
SET content = '✅ Your plan is ready'
            || CASE WHEN pd.version > 1 THEN ' (updated to v' || pd.version || ')' ELSE '' END
            || ': **' || COALESCE(NULLIF(pd.plugin_name, ''), 'your plugin')
            || '**. Review it in the plan panel and approve to start the build, '
            || 'or tell me what you''d like to change.',
    model_used = NULL
FROM plan_documents pd
WHERE cm.session_id = pd.session_id
  AND cm.role = 'assistant'
  AND (cm.content LIKE '%submit_plan%'
       OR cm.content LIKE '%trigger_plan_submission%'
       OR cm.content LIKE '%function_calls%'
       OR cm.content LIKE '%simulate_tool_call%'
       OR cm.content LIKE '%<thinking>%');

INSERT INTO chat_messages (id, session_id, role, content, model_used, tokens_consumed, created_at)
SELECT gen_random_uuid(), bs.id, 'assistant',
       '✅ Your plan is ready'
       || CASE WHEN pd.version > 1 THEN ' (updated to v' || pd.version || ')' ELSE '' END
       || ': **' || COALESCE(NULLIF(pd.plugin_name, ''), 'your plugin')
       || '**. Review it in the plan panel and approve to start the build, '
       || 'or tell me what you''d like to change.',
       NULL, 0, lm.created_at + interval '1 second'
FROM build_sessions bs
JOIN plan_documents pd ON pd.session_id = bs.id
JOIN (
    SELECT DISTINCT ON (session_id) session_id, role, created_at
    FROM chat_messages ORDER BY session_id, created_at DESC
) lm ON lm.session_id = bs.id
WHERE bs.status = 'PLANNING' AND lm.role = 'user';

COMMIT;
