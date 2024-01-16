/*
 * Copyright © 2008-2016, Province of British Columbia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ca.bc.gov.open.cpf.api.security.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsChecker;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import ca.bc.gov.open.cpf.api.domain.CpfDataAccessObject;
import ca.bc.gov.open.cpf.api.domain.UserAccount;

import com.revolsys.record.Record;
import com.revolsys.record.Records;
import com.revolsys.transaction.Propagation;
import com.revolsys.transaction.Transaction;
import com.revolsys.util.Property;

public class UserAccountByConsumerKeyDetailsService implements UserDetailsService {

  private UserDetailsChecker userDetailsChecker = new AccountStatusUserDetailsChecker();

  private CpfDataAccessObject dataAccessObject;

  private UserAccountSecurityService userAccountSecurityService;

  public UserAccountSecurityService getUserAccountSecurityService() {
    return this.userAccountSecurityService;
  }

  public UserDetailsChecker getUserDetailsChecker() {
    return this.userDetailsChecker;
  }

  @Override
  public UserDetails loadUserByUsername(final String username)
    throws UsernameNotFoundException, DataAccessException {
    if (Property.hasValue(username)) {
      try (
        Transaction transaction = this.dataAccessObject.newTransaction(Propagation.REQUIRES_NEW)) {
        try {
          final String name = username.toLowerCase();
          final Record user = this.dataAccessObject.getUserAccount(name);
          if (user == null) {
            throw new UsernameNotFoundException("Username or password incorrect");
          } else {
            final String userPassword = user.getValue(UserAccount.CONSUMER_SECRET);
            final boolean active = Records.getBoolean(user, UserAccount.ACTIVE_IND);
            final List<String> groupNames = this.userAccountSecurityService.getGroupNames(user);
            final List<GrantedAuthority> authorities = new ArrayList<>();
            for (final String groupName : groupNames) {
              authorities.add(new SimpleGrantedAuthority(groupName));
              authorities.add(new SimpleGrantedAuthority("ROLE_" + groupName));
            }
            final User userDetails = new User(name, userPassword, active, true, true, true,
              authorities);
            if (this.userDetailsChecker != null) {
              this.userDetailsChecker.check(userDetails);
            }
            return userDetails;
          }
        } catch (final AuthenticationException e) {
          throw e;
        } catch (final Throwable e) {
          throw transaction.setRollbackOnly(e);
        }
      }
    } else {
      throw new UsernameNotFoundException("Username or password incorrect");
    }
  }

  @Autowired
  public void setUserAccountSecurityService(
    final UserAccountSecurityService userAccountSecurityService) {
    this.userAccountSecurityService = userAccountSecurityService;
    this.dataAccessObject = userAccountSecurityService.getDataAccessObject();
  }

  @Autowired
  public void setUserDetailsChecker(final UserDetailsChecker userDetailsChecker) {
    this.userDetailsChecker = userDetailsChecker;
  }
}
