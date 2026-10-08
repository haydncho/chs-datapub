package cn.ybdata.core.page;

import cn.ybdata.core.security.AccessDeniedException;
import cn.ybdata.core.security.AccessPolicy;
import cn.ybdata.core.security.Actor;
import cn.ybdata.core.security.CurrentActor;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pages")
public class PageController {

    private final PageService pages;
    private final List<PageScopeFilter> scopes;

    public PageController(PageService pages, List<PageScopeFilter> scopes) {
        this.pages = pages;
        this.scopes = scopes;
    }

    /** Page codes; an access-checked viewer only gets the screens its identity may open. */
    @GetMapping
    public List<String> list(HttpServletRequest req) {
        Actor actor = CurrentActor.get(req);
        List<String> all = pages.codes();
        if (actor == null || !actor.enforced()) return all;
        return all.stream().filter(c -> AccessPolicy.canView(actor.role(), c)).toList();
    }

    @GetMapping("/{code}")
    public ObjectNode get(@PathVariable String code, HttpServletRequest req) {
        Actor actor = CurrentActor.get(req);
        // AuthFilter already checked this; repeated here so the read model never relies on one layer
        if (actor != null && actor.enforced() && !AccessPolicy.canView(actor.role(), code)) {
            throw new AccessDeniedException("当前身份无权访问页面 " + code);
        }
        ObjectNode payload = pages.get(code);
        if (actor != null) {
            for (PageScopeFilter f : scopes) {
                if (f.appliesTo(code, actor)) f.apply(code, payload, actor);
            }
        }
        return payload;
    }
}
