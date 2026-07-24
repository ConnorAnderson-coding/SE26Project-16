package com.example.demo.recommend;

import com.example.demo.repository.RegistrationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Implicit social strength between current user A and organizers B.
 * <p>
 * $s(A,B)=\log(1+c_{co})+0.8\log(1+c_{A\to B})+0.8\log(1+c_{B\to A})$
 */
@Service
@RequiredArgsConstructor
public class SocialAffinityService {

    private final RegistrationRepository registrationRepository;

    @Transactional(readOnly = true)
    public Map<String, Double> scoresForOrganizers(String userId, Collection<String> organizerIds) {
        Map<String, Double> out = new HashMap<>();
        if (userId == null || organizerIds == null || organizerIds.isEmpty()) {
            return out;
        }
        Set<String> unique = new HashSet<>();
        for (String id : organizerIds) {
            if (id != null && !id.isBlank() && !id.equals(userId)) {
                unique.add(id);
            }
        }
        for (String organizerId : unique) {
            out.put(organizerId, compute(userId, organizerId));
        }
        return out;
    }

    public static double formula(long coCount, long aToB, long bToA) {
        return Math.log(1.0 + Math.max(0, coCount))
                + 0.8 * Math.log(1.0 + Math.max(0, aToB))
                + 0.8 * Math.log(1.0 + Math.max(0, bToA));
    }

    private double compute(String userA, String userB) {
        long co = registrationRepository.countCoParticipation(userA, userB);
        long aToB = registrationRepository.countUserSignedOrganizer(userA, userB);
        long bToA = registrationRepository.countUserSignedOrganizer(userB, userA);
        return formula(co, aToB, bToA);
    }
}
