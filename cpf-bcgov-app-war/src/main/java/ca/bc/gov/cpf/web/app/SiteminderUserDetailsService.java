package ca.bc.gov.cpf.web.app;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Properties;
import java.util.Arrays;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.annotation.PreDestroy;
import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsChecker;
import org.springframework.security.core.userdetails.UserDetailsService;

import ca.bc.gov.open.cpf.api.domain.CpfDataAccessObject;
import ca.bc.gov.open.cpf.api.domain.UserAccount;
import ca.bc.gov.open.cpf.api.security.UsernamePasswordAuthenticationToken;
import ca.bc.gov.open.cpf.api.security.service.GroupNameService;
import ca.bc.gov.open.cpf.api.security.service.UserAccountSecurityService;

import com.revolsys.record.Record;
import com.revolsys.record.Records;
import com.revolsys.transaction.Propagation;
import com.revolsys.transaction.Transaction;
import com.revolsys.ui.web.utils.HttpServletUtils;

public class SiteminderUserDetailsService implements UserDetailsService, GroupNameService {

  private static final String BCGOV_ALL = "BCGOV_ALL";

  private static final String BCGOV_BUSINESS = "BCGOV_BUSINESS";

  private static final String BCGOV_EXTERNAL = "BCGOV_EXTERNAL";

  private static final String BCGOV_INDIVIDUAL = "BCGOV_INDIVIDUAL";

  private static final String BCGOV_INTERNAL = "BCGOV_INTERNAL";

  private static final String BCGOV_VERIFIED_INDIVIDUAL = "BCGOV_VERIFIED_INDIVIDUAL";

  private static final String USER_ACCOUNT_CLASS = "BCGOV";

  private CpfDataAccessObject dataAccessObject;

  private UserAccountSecurityService userAccountSecurityService;

  /** The class to use to check that the user is valid. */
  private UserDetailsChecker userDetailsChecker = new AccountStatusUserDetailsChecker();

  private List<String> admins = new ArrayList<String>();

  @PreDestroy
  public void close() {
    this.userAccountSecurityService = null;
    this.userDetailsChecker = null;
    this.dataAccessObject = null;
  }

  @Override
  public List<String> getGroupNames(final Record userAccount) {
    final List<String> groupNames = new ArrayList<String>();
    if (userAccount.getValue(UserAccount.USER_ACCOUNT_CLASS).equals(USER_ACCOUNT_CLASS)) {
      final String username = userAccount.getValue(UserAccount.CONSUMER_KEY);
      if (username.startsWith("idir:")) {
        groupNames.add(BCGOV_ALL);
        groupNames.add(BCGOV_INTERNAL);
      } else if (username.startsWith("bceid:")) {
        groupNames.add(BCGOV_ALL);
        groupNames.add(BCGOV_EXTERNAL);
        groupNames.add(BCGOV_BUSINESS);
      } else if (username.startsWith("vin:")) {
        groupNames.add(BCGOV_ALL);
        groupNames.add(BCGOV_EXTERNAL);
        groupNames.add(BCGOV_VERIFIED_INDIVIDUAL);
      } else if (username.startsWith("ind:")) {
        groupNames.add(BCGOV_ALL);
        groupNames.add(BCGOV_EXTERNAL);
        groupNames.add(BCGOV_INDIVIDUAL);
      }
    }
    return groupNames;
  }

  public UserAccountSecurityService getUserAccountSecurityService() {
    return this.userAccountSecurityService;
  }

  public UserDetailsChecker getUserDetailsChecker() {
    return this.userDetailsChecker;
  }

  private void init() {
    try (
      Transaction transaction = this.dataAccessObject.newTransaction(Propagation.REQUIRES_NEW)) {
      try {
        this.dataAccessObject.newUserGroup("USER_TYPE", "BCGOV_ALL", "BC Government All Users");
        this.dataAccessObject.newUserGroup("USER_TYPE", "BCGOV_INTERNAL",
          "BC Government Internal Users");
        this.dataAccessObject.newUserGroup("USER_TYPE", "BCGOV_EXTERNAL",
          "BC Government External Users");
        this.dataAccessObject.newUserGroup("USER_TYPE", "BCGOV_BUSINESS",
          "BC Government External Business Users");
        this.dataAccessObject.newUserGroup("USER_TYPE", "BCGOV_INDIVIDUAL",
          "BC Government External Individual Users");
        this.dataAccessObject.newUserGroup("USER_TYPE", "BCGOV_VERIFIED_INDIVIDUAL",
          "BC Government External Verified Individual Users");
        this.userAccountSecurityService.addGrantedAuthorityService(this);
      } catch (final Throwable e) {
        throw transaction.setRollbackOnly(e);
      } finally {
          initPropertiesFile();
      }
    }
  }

  @Override
  public UserDetails loadUserByUsername(final String userGuid) {
    try (
      Transaction transaction = this.dataAccessObject.newTransaction(Propagation.REQUIRES_NEW)) {
      try {
        Record user = this.dataAccessObject.getUserAccount(USER_ACCOUNT_CLASS, userGuid);
        final SecurityContext context = SecurityContextHolder.getContext();
        String consumerSecret = null;
        String username;
        if (user == null) {
          final HttpServletRequest request = HttpServletUtils.getRequest();
          final String userType = request.getHeader("SMGOV_USERTYPE");
          username = request.getHeader("SM_UNIVERSALID").toLowerCase();
          username = username.replace('\\', ':');
          final int index = username.indexOf(':');
          if (index == -1) {
            if (userType.equalsIgnoreCase("INTERNAL")) {
              username = "idir:" + username;
            } else if (userType.equalsIgnoreCase("BUSINESS")) {
              username = "bceid:" + username;
            } else if (userType.equalsIgnoreCase("VERIFIED INDIVIDUAL")) {
              username = "vin:" + username;
            } else if (userType.equalsIgnoreCase("INDIVIDUAL")) {
              username = "ind:" + username;
            }
          }

          consumerSecret = UUID.randomUUID().toString().replaceAll("-", "");

          user = this.dataAccessObject.newUserAccount(USER_ACCOUNT_CLASS, userGuid, username,
            consumerSecret);

          if (userType.equalsIgnoreCase("INTERNAL")) {
            for (String admin : admins) {
              if (username.endsWith(admin)) {
                final Record userGroup = this.dataAccessObject.getUserGroup("ADMIN");
                this.dataAccessObject.newUserGroupAccountXref(userGroup, user);
              }
            }
          }
        } else {
          username = user.getValue(UserAccount.CONSUMER_KEY);

        }

        final String userPassword = user.getValue(UserAccount.CONSUMER_SECRET);
        final boolean active = Records.getBoolean(user, UserAccount.ACTIVE_IND);
        final List<String> groupNames = this.userAccountSecurityService.getGroupNames(user);
        final List<GrantedAuthority> authorities = new ArrayList<GrantedAuthority>();
        for (final String groupName : groupNames) {
          authorities.add(new SimpleGrantedAuthority(groupName));
          authorities.add(new SimpleGrantedAuthority("ROLE_" + groupName));
        }

        if (consumerSecret != null) {
          final UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            username, consumerSecret, authorities);
          context.setAuthentication(authentication);
        }
        final User userDetails = new User(username, userPassword, active, true, true, true,
          authorities);

        if (this.userDetailsChecker != null) {
          this.userDetailsChecker.check(userDetails);
        }
        return userDetails;
      } catch (final Throwable e) {
        throw transaction.setRollbackOnly(e);
      }
    }
  }

  @Required
  public void setUserAccountSecurityService(
    final UserAccountSecurityService userAccountSecurityService) {
    this.userAccountSecurityService = userAccountSecurityService;
    this.dataAccessObject = userAccountSecurityService.getDataAccessObject();
    init();
  }

  public void setUserDetailsChecker(final UserDetailsChecker userDetailsChecker) {
    this.userDetailsChecker = userDetailsChecker;
  }

  private void initPropertiesFile() {
    final Path file = Paths.get("/apps/config/cpf/cpf.properties");
    if (Files.exists(file)) {
      try {
        final Properties properties = new Properties();
        try (
          FileReader reader = new FileReader(file.toFile())) {
            properties.load(reader);
            for (final Object key : properties.keySet()) {
              final String name = key.toString();
              final String value = properties.getProperty(name);
              if (value != null) {
                if (name.equals("cpfAdmins")) {
                  List<String> cpf_admins = Arrays.asList(value.split(",[ ]*"));
                  for (String admin : cpf_admins) {
                    admins.add(admin);
                  }
                }
              }
            }
          }
      } catch (final Exception e) { }
    }
  }

}
