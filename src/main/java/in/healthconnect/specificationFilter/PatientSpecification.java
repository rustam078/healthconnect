package in.healthconnect.specificationFilter;

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

                // The whole name, and it replaces separate firstName / lastName matching.
                //
                // Every screen shows a patient as "Alka Patel", so that is what people
                // type - and first OR last name alone never matches it, because neither
                // column holds the space.
                //
                // The two are not needed alongside it: "%term%" is wildcarded on both
                // sides, so anything found inside firstName or lastName is also found
                // inside the two joined. Checked against the data - "patel" 172, "alka"
                // 109, "sharma" 166, identical either way, and "alka patel" goes from 0
                // to 4. It also catches a term spanning the space, like "ka pat".
                //
                // COALESCE is required here: patient.last_name is NULLABLE, SQL CONCAT with
                // a NULL returns NULL, and NULL LIKE anything is never true - so a patient
                // with no last name would silently vanish from every search, findable by
                // neither name nor code. The Criteria API has no CONCAT_WS, so an empty
                // string stands in for the null.
                Predicate fullNamePredicate =
                        criteriaBuilder.like(
                                criteriaBuilder.lower(
                                        criteriaBuilder.concat(
                                                criteriaBuilder.concat(root.get("firstName"), " "),
                                                criteriaBuilder.coalesce(root.get("lastName"), "")
                                        )
                                ),
                                searchValue
                        );

                predicates.add(
                        criteriaBuilder.or(
                                fullNamePredicate,
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