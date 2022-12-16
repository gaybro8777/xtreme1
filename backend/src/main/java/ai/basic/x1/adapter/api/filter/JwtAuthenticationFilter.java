package ai.basic.x1.adapter.api.filter;

import ai.basic.x1.adapter.api.context.RequestContext;
import ai.basic.x1.adapter.api.context.RequestContextHolder;
import ai.basic.x1.adapter.api.context.RequestInfo;
import ai.basic.x1.adapter.api.context.UserInfo;
import ai.basic.x1.adapter.dto.LoggedUserDTO;
import ai.basic.x1.usecase.UserTokenUseCase;
import ai.basic.x1.usecase.UserUseCase;
import cn.hutool.core.util.ObjectUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

import static ai.basic.x1.util.Constants.*;

/**
 * @author Jagger Wang
 */
@Slf4j
@Component
@Order(2)
public class JwtAuthenticationFilter implements Filter {

    private UserTokenUseCase userTokenUseCase;

    private UserUseCase userUseCase;

    public JwtAuthenticationFilter(UserTokenUseCase userTokenUseCase, UserUseCase userUseCase) {
        this.userTokenUseCase = userTokenUseCase;
        this.userUseCase = userUseCase;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        String token = null;
        var req = (HttpServletRequest) request;
        buildRequestContext(req);
        var authorization = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            token = authorization.split(" ")[1].trim();
        }

        if (token == null) {
            token = req.getParameter("token");
        }

        if (token == null) {
            chain.doFilter(request, response);
            return;
        }

        var payload = userTokenUseCase.getPayload(token);
        if (payload == null) {
            chain.doFilter(request, response);
            return;
        }

        var userBO = userUseCase.findById(payload.getUserId());
        if (userBO == null) {
            chain.doFilter(request, response);
            return;
        }

        var loggedUserDTO = new LoggedUserDTO(userBO.getUsername(), userBO.getPassword(), userBO.getId());
        var authentication = new UsernamePasswordAuthenticationToken(
                loggedUserDTO, loggedUserDTO.getPassword(), loggedUserDTO.getAuthorities());
        var securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        chain.doFilter(request, response);
        return;
    }

    @Override
    public void destroy() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.cleanContext();
    }

    private void buildRequestContext(HttpServletRequest httpServletRequest) {

        if (ObjectUtil.isNull(RequestContextHolder.getContext())) {
            RequestContext requestContext = RequestContextHolder.createEmptyContent();
            LoggedUserDTO loggedUserDTO = getLoggedUserDTO();
            if (ObjectUtil.isNotNull(loggedUserDTO)) {
                requestContext.setUserInfo(UserInfo.builder().id(loggedUserDTO.getId()).build());
            }
            requestContext.setRequestInfo(RequestInfo.builder().host(httpServletRequest.getHeader(HOST)).forwardedProto(httpServletRequest.getHeader(X_FORWARDED_PROTO))
                    .realIp(httpServletRequest.getHeader(X_REAL_IP)).forwardedFor(httpServletRequest.getHeader(X_FORWARDED_FOR)).userAgent(httpServletRequest.getHeader(X_UA)).build());
            RequestContextHolder.setContext(requestContext);
        }
    }

    private LoggedUserDTO getLoggedUserDTO() {
        var securityContext = SecurityContextHolder.getContext();
        var authentication = securityContext.getAuthentication();
        if (authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }

        return (LoggedUserDTO) authentication.getPrincipal();
    }
}
