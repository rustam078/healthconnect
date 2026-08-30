package in.healthconnect.specificationFilter;

import in.healthconnect.dto.request.DoctorFilterDto;
import in.healthconnect.entity.Doctor;
import in.healthconnect.entity.DoctorSpecialtyMap;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
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
             * Specialty filter
             */
            if (StringUtils.hasText(doctorFilterDto.getSpecialties())) {

                String specialtyName =
                        "%" + doctorFilterDto.getSpecialties().trim().toLowerCase() + "%";

                Subquery<Integer> specialtySubquery = query.subquery(Integer.class);  //

                Root<DoctorSpecialtyMap> doctorSpecialtyRoot = specialtySubquery.from(DoctorSpecialtyMap.class);

                specialtySubquery.select(doctorSpecialtyRoot.get("id"));

                specialtySubquery.where(
                        criteriaBuilder.and(

                                // Mapping belongs to current doctor
                                criteriaBuilder.equal(
                                        doctorSpecialtyRoot.get("doctor").get("id"), root.get("id")),

                                // Specialty name matches
                                criteriaBuilder.like(
                                        criteriaBuilder.lower(
                                                doctorSpecialtyRoot.get("specialty").get("name")), specialtyName),

                                // Specialty is not deleted
                                criteriaBuilder.equal(
                                        doctorSpecialtyRoot.get("specialty").get("deleted"), false)
                        )
                );

                predicates.add(
                        criteriaBuilder.exists(specialtySubquery)
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

