package in.healthconnect;

import in.healthconnect.dto.request.PatientSearchRequest;
import in.healthconnect.entity.Patient;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public final class PatientSpecification {

    private PatientSpecification() {
        // Utility class
    }

    public static Specification<Patient> search(PatientSearchRequest request) {

        return (root, query, criteriaBuilder) -> {

            List<Predicate> predicates = new ArrayList<>();

            /*
             * Global search
             *
             * Searches:
             * firstName
             * lastName
             * phone
             * email
             * patientCode
             */
            if (request.search() != null &&
                    StringUtils.hasText(request.search())) {

                String searchValue =
                        "%" + request.search().trim().toLowerCase() + "%";

                Predicate firstNamePredicate =
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("firstName")),
                                searchValue
                        );

                Predicate lastNamePredicate =
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("lastName")),
                                searchValue
                        );

                Predicate phonePredicate =
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("phone")),
                                searchValue
                        );

                Predicate emailPredicate =
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("email")),
                                searchValue
                        );

                Predicate patientCodePredicate =
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("patientCode")),
                                searchValue
                        );

                predicates.add(
                        criteriaBuilder.or(
                                firstNamePredicate,
                                lastNamePredicate,
                                phonePredicate,
                                emailPredicate,
                                patientCodePredicate
                        )
                );
            }

            /*
             * firstName filter
             */
            if (request.firstName() != null) {

                predicates.add(
                        criteriaBuilder.equal(
                                root.get("firstName"),
                                request.firstName()
                        )
                );
            }

            /*
             * Gender filter
             */
            if (request.gender() != null) {

                predicates.add(
                        criteriaBuilder.equal(
                                root.get("gender"),
                                request.gender()
                        )
                );
            }

            /*
             * Blood group filter
             */
            if (request.bloodGroup() != null) {

                predicates.add(
                        criteriaBuilder.equal(
                                root.get("bloodGroup"),
                                request.bloodGroup()
                        )
                );
            }

            /*
             * Ignore soft deleted patients
             */
            predicates.add(
                    criteriaBuilder.equal(
                            root.get("deleted"),
                            false
                    )
            );

            return criteriaBuilder.and(
                    predicates.toArray(new Predicate[0])
            );
        };
    }
}