package ca.bc.gov.cpf.web.app;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedCredentialsNotFoundException;

public class SiteminderAuthenticationFilter extends
  AbstractPreAuthenticatedProcessingFilter {
  @Override
  protected Object getPreAuthenticatedCredentials(
    final HttpServletRequest request) {
    return "";
  }

  /**
   * Read and returns the header named by {@code principalRequestHeader} from
   * the request.
   * 
   * @throws PreAuthenticatedCredentialsNotFoundException if the header is
   *           missing and {@code exceptionIfHeaderMissing} is set to
   *           {@code true}.
   */
  @Override
  protected Object getPreAuthenticatedPrincipal(final HttpServletRequest request) {
    final String principal = request.getHeader("SMGOV_USERGUID");
    if (principal == null) {
      return null;
    } else {
      return principal;
    }
  }
}
