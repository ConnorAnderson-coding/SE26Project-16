package com.example.campusactivity.security;

import com.example.campusactivity.entity.UserAccount;
import com.example.campusactivity.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CampusUserDetailsService implements UserDetailsService {
    private static final String AUTHENTICATION_FAILED = "认证失败";

    private final UserRepository userRepository;
    private final RoleAuthorityMapper roleAuthorityMapper;

    public CampusUserDetailsService(
            UserRepository userRepository,
            RoleAuthorityMapper roleAuthorityMapper
    ) {
        this.userRepository = userRepository;
        this.roleAuthorityMapper = roleAuthorityMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String accountId)
            throws UsernameNotFoundException {
        UserAccount user = userRepository.findById(accountId)
                .orElseThrow(() -> new UsernameNotFoundException(
                        AUTHENTICATION_FAILED
                ));
        try {
            return new CampusUserPrincipal(
                    user.getId(),
                    user.getPassword(),
                    roleAuthorityMapper.authoritiesFor(user.getRole())
            );
        } catch (IllegalArgumentException _exception) {
            throw new UsernameNotFoundException(AUTHENTICATION_FAILED);
        }
    }
}
