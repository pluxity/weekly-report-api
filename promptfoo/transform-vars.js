const fs = require('fs');
const path = require('path');

module.exports = function (vars) {
  const intentPrompt = fs.readFileSync(
    path.resolve(__dirname, '../src/main/resources/llm/intent-prompt.txt'),
    'utf-8'
  );
  const systemPrompt = fs.readFileSync(
    path.resolve(__dirname, '../src/main/resources/llm/system-prompt.txt'),
    'utf-8'
  );

  const contextFile = vars.contextFile || 'context-fixture.json';
  const context = fs.readFileSync(
    path.resolve(__dirname, contextFile),
    'utf-8'
  );

  // history가 있으면 히스토리 블록 생성, 없으면 빈 문자열
  const historyBlock = vars.history
    ? `\n## 대화 히스토리 (참조용, 이 형식으로 응답하지 마세요)\n${vars.history}\n`
    : '';

  return {
    ...vars,
    intent_prompt: intentPrompt,
    system_prompt: systemPrompt,
    context: context,
    intent: vars.intent || '',
    history_block: historyBlock,
  };
};
