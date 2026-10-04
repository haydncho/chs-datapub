package gov.ybj.chsdpub.auth;

/** 当前会话身份（账号 × 本次选择的身份）：驱动菜单裁剪、顶栏、水印与接口鉴权。 */
public record AuthUser(long userId, long identityId, String username, String name, String role, String roleLabel,
                       String org, String scope) {
}
