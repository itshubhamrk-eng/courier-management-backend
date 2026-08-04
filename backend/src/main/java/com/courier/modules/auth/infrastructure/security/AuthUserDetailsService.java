package com.courier.modules.auth.infrastructure.security;

import com.courier.modules.auth.application.AuthProperties;
import com.courier.modules.auth.domain.User;
import com.courier.modules.auth.domain.UserRepository;
import com.courier.shared.company.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads a user for authentication, <b>within the currently bound company</b>.
 *
 * <p>{@code loadUserByUsername} takes only an email because that is the interface
 * Spring Security defines. The missing half — which company — comes from
 * {@link CompanyContext}, which the login use case binds before calling the
 * authentication manager.
 *
 * <p>If no company is bound this throws rather than searching globally. A global
 * search would silently return the first matching email across all companies, which is
 * precisely the cross-company authentication hole the whole design exists to prevent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final AuthProperties properties;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        if (!CompanyContext.isSet()) {
            // Deliberately not a silent global lookup — see the class javadoc.
            log.error("loadUserByUsername called with no company bound; refusing to search across companies");
            throw new UsernameNotFoundException("No company bound for authentication");
        }

        User user = userRepository.findByEmail(User.normaliseEmail(email))
                .orElseThrow(() -> new UsernameNotFoundException("No such user in this company"));

        return AuthUserDetails.of(user, properties);
    }
}
