// 同一组浏览器断言分别验证本机与公网，不跳过 HTTPS 证书校验。
export const serverTarget = process.env.UPMS_E2E_TARGET === 'server';
export const platformUrl = serverTarget ? 'https://113.44.144.119' : 'http://localhost:18080';
export const oaUrl = serverTarget ? 'https://113.44.144.119:8443' : 'http://localhost:18081';
export const knowledgeUrl = serverTarget ? 'https://113.44.144.119:9443' : 'http://localhost:18082';
export const proofPrefix = serverTarget ? 'server-' : '';
