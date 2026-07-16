package com.example.campusactivity.repository;

import com.example.campusactivity.entity.Signup;
import com.example.campusactivity.repository.projection.ApprovedSignupCategoryProjection;
import com.example.campusactivity.repository.projection.SignupCountProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SignupRepository extends JpaRepository<Signup, String> {
    List<Signup> findByActivityId(String activityId);
    List<Signup> findByUserId(String userId);
    Optional<Signup> findByActivityIdAndUserId(String activityId, String userId);

    @Query("""
            SELECT signup.userId AS userId,
                   COUNT(signup) AS signupCount,
                   SUM(CASE WHEN signup.status = :approvedStatus THEN 1 ELSE 0 END)
                       AS approvedSignupCount,
                   SUM(CASE
                       WHEN signup.status IS NULL OR signup.status NOT IN (:knownStatuses) THEN 1
                       ELSE 0
                   END) AS unknownSignupStatusCount
            FROM Signup signup
            GROUP BY signup.userId
            """)
    List<SignupCountProjection> aggregateCountsByUserId(
            @Param("approvedStatus") String approvedStatus,
            @Param("knownStatuses") Collection<String> knownStatuses
    );

    @Query("""
            SELECT signup.userId AS userId,
                   activity.id AS matchedActivityId,
                   activity.category AS category,
                   COUNT(signup) AS participationCount
            FROM Signup signup
            LEFT JOIN Activity activity ON activity.id = signup.activityId
            WHERE signup.status = :approvedStatus
            GROUP BY signup.userId, activity.id, activity.category
            """)
    List<ApprovedSignupCategoryProjection> aggregateApprovedCategoriesByUserId(
            @Param("approvedStatus") String approvedStatus
    );
}
