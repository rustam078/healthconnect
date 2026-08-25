package in.healthconnect;

import in.healthconnect.dto.request.DoctorFilterDto;
import in.healthconnect.entity.Doctor;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public final class DoctorSpecification {

    private DoctorSpecification() {
        // Utility class
    }

    public static Specification<Doctor> search(DoctorFilterDto doctorFilterDto, String search) {

        return (root, query, criteriaBuilder) -> {

            List<Predicate> predicates = new ArrayList<>();

            /*
             * Global search
             * firstNamePredicate
             * lastNamePredicate
             * phonePredicate
             * emailPredicate
             * doctorCodePredicate
             *
             */
            if (StringUtils.hasText(search)) {

                String searchValue =
                        "%" + search.trim().toLowerCase() + "%";

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

                Predicate doctorCodePredicate =
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("doctorCode")),
                                searchValue
                        );

                predicates.add(
                        criteriaBuilder.or(
                                firstNamePredicate,
                                lastNamePredicate,
                                phonePredicate,
                                emailPredicate,
                                doctorCodePredicate
                        )
                );
            }

            /*
             * qualification filter
             */
            if (doctorFilterDto.getQualification() != null) {

                predicates.add(
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("qualification")),
                                "%" + doctorFilterDto.getQualification().trim().toLowerCase() + "%"
                        )
                );
            }

            /*
             * Gender filter
             */
            if (doctorFilterDto.getGender() != null) {

                predicates.add(
                        criteriaBuilder.equal(
                                root.get("gender"),
                                doctorFilterDto.getGender()
                        )
                );
            }

            /*
             * maxConsultationFee filter
             */
            if (doctorFilterDto.getMaxConsultationFee() != null) {

                predicates.add(
                        criteriaBuilder.lessThanOrEqualTo(
                                root.get("consultationFee"),
                                doctorFilterDto.getMaxConsultationFee()
                        )
                );
            }
            /*
             *  minConsultationFee filter
             */
            if (doctorFilterDto.getMinConsultationFee() != null) {

                predicates.add(
                        criteriaBuilder.greaterThanOrEqualTo(
                                root.get("consultationFee"),
                                doctorFilterDto.getMinConsultationFee()
                        )
                );
            }

            /*
             *  maxExperience  filter
             */
            if (doctorFilterDto.getMaxExperience() != null) {

                predicates.add(
                        criteriaBuilder.lessThanOrEqualTo(
                                root.get("experienceYears"),
                                doctorFilterDto.getMaxExperience()
                        )
                );
            }
            /*
             *  MinExperience  filter
             */
            if (doctorFilterDto.getMinExperience() != null) {

                predicates.add(
                        criteriaBuilder.greaterThanOrEqualTo(
                                root.get("experienceYears"),
                                doctorFilterDto.getMinExperience()
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

