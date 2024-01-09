package ca.bc.gov.cpf.web.app;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

import com.revolsys.ui.web.utils.HttpServletUtils;
import com.revolsys.util.Property;
import com.revolsys.util.UrlUtil;

public class SiteminderLogout implements LogoutSuccessHandler {
  private static final List<String> DELETE_COOKIES = Arrays.asList(
    "WEBADEUSERGUID", "SMSESSION");

  private String logoutUrl;

  public String getLogoutUrl() {
    return this.logoutUrl;
  }

  @Override
  public void onLogoutSuccess(final HttpServletRequest request,
    final HttpServletResponse response, final Authentication authentication)
    throws IOException, ServletException {
    for (final Cookie cookie : request.getCookies()) {
      if (DELETE_COOKIES.contains(cookie.getName())) {
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
      }
    }

    String url = HttpServletUtils.getAbsoluteUrl("/secure/");

    if (Property.hasValue(this.logoutUrl)) {
      final Map<String, String> parameters = new LinkedHashMap<String, String>();
      parameters.put("returl", url);
      parameters.put("retname", "Concurrent Processing Framework");
      url = UrlUtil.getUrl(HttpServletUtils.getAbsoluteUrl(this.logoutUrl),
        parameters);
    }
    response.sendRedirect(url);
  }

  public void setLogoutUrl(final String logoutUrl) {
    this.logoutUrl = logoutUrl;
  }
}
