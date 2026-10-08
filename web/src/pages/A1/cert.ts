/**
 * 数字证书登录的「本机证书读取」模拟。
 *
 * 真实部署中由证书驱动从本机 UKey 读出证书主体对应的账号,只在客户端使用;
 * 公开的登录页数据(GET /api/v1/pages/A1,登录前即可读取)不再下发任何登录名。
 * 演示环境里本机插着的是陈志远的证书。
 */
export const DETECTED_CERT_ACCOUNT = 'chenzy'
