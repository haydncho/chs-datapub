package gov.ybj.chsdpub.publish;

import gov.ybj.chsdpub.audit.AuditService;
import gov.ybj.chsdpub.auth.AuthUser;
import gov.ybj.chsdpub.auth.CurrentUser;
import gov.ybj.chsdpub.common.ApiException;
import gov.ybj.chsdpub.common.Roles;
import org.springframework.stereotype.Component;

/**
 * 发布审批的身份校验：A8 页面召集人与行政管理组都可进入（可查看、调整定向范围、提交），
 * 但「批准 / 驳回」只限召集人——行政管理组调用审批接口一律 403，并写审计「越权尝试」。
 */
@Component
public class ConvenerGuard {

    /** 审计日志类型「审批」（A14 的类型芯片目前只有六类，按「全部」可见）。 */
    public static final String AUDIT_APPROVAL = gov.ybj.chsdpub.audit.AuditService.APPROVAL;

    private final AuditService audit;

    public ConvenerGuard(AuditService audit) {
        this.audit = audit;
    }

    public AuthUser require(String action) {
        AuthUser u = CurrentUser.get();
        if (!Roles.CONVENER.equals(u.role())) {
            audit.overreach(u, action, AuditService.currentIp());
            throw ApiException.forbidden("仅召集人可审批;" + u.roleLabel() + "可提交、不可审批");
        }
        return u;
    }

    /** 留痕中的操作人：「陈志远 · 召集人」「李华 · 行政管理组」。 */
    public static String who(AuthUser u) {
        String role = Roles.CONVENER.equals(u.role()) ? "召集人" : u.roleLabel();
        return u.name() + " · " + role;
    }

    public void record(AuthUser u, String object, String result) {
        audit.record(u, AUDIT_APPROVAL, object, result);
    }
}
